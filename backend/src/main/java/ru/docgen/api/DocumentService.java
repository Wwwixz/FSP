package ru.docgen.api;

import org.springframework.http.HttpStatus;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import ru.docgen.ai.AIResult;
import ru.docgen.ai.AIService;
import ru.docgen.api.dto.Dto;
import ru.docgen.core.DocumentType;
import ru.docgen.core.GeneratedDocument;
import ru.docgen.core.ProcessedDocument;
import ru.docgen.core.RequisiteKey;
import ru.docgen.core.TemplateKind;
import ru.docgen.docx.DocxGenerationService;
import ru.docgen.requisites.RequisitesService;
import ru.docgen.requisites.RequisitesValidation;
import ru.docgen.store.DocumentStore;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.net.URI;

/**
 * Оркестрация основного сценария: обработка черновика → заполнение
 * реквизитов → генерация DOCX.
 */
@Service
public class DocumentService {

    private final AIService aiService;
    private final RequisitesService requisitesService;
    private final DocxGenerationService docxService;
    private final DocumentStore store;
    private final RestTemplate restTemplate = new RestTemplate();

    public DocumentService(AIService aiService, RequisitesService requisitesService,
                           DocxGenerationService docxService, DocumentStore store) {
        this.aiService = aiService;
        this.requisitesService = requisitesService;
        this.docxService = docxService;
        this.store = store;
    }

    public Dto.SedSendResponse sendToSed(String documentId, Dto.SedSendRequest request) {
        if (request == null || request.apiUrl() == null || request.apiUrl().isBlank()
                || request.apiKey() == null || request.apiKey().isBlank()) {
            throw badRequest("SED_CONFIG_REQUIRED", "Укажите URL СЭД и ключ доступа");
        }

        URI target;
        try {
            target = URI.create(request.apiUrl().trim());
        } catch (IllegalArgumentException e) {
            throw badRequest("SED_INVALID_URL", "Укажите корректный URL СЭД");
        }
        if (!"http".equalsIgnoreCase(target.getScheme()) && !"https".equalsIgnoreCase(target.getScheme())) {
            throw badRequest("SED_INVALID_URL", "URL СЭД должен начинаться с http:// или https://");
        }

        GeneratedDocument document = store.getGenerated(documentId);
        ByteArrayResource file = new ByteArrayResource(document.getContent()) {
            @Override
            public String getFilename() {
                return document.getFileName();
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file);
        body.add("documentId", documentId);
        body.add("fileName", document.getFileName());
        body.add("systemName", request.systemName() == null ? "" : request.systemName());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(request.apiKey().trim());
        ResponseEntity<String> response = restTemplate.postForEntity(
                target, new HttpEntity<>(body, headers), String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new ResponseStatusException(response.getStatusCode(),
                    "СЭД вернула ошибку HTTP " + response.getStatusCode().value());
        }
        return new Dto.SedSendResponse(true, "Документ передан в СЭД");
    }

    // ------------------------------------------------------------------
    // Шаг 3: обработка черновика ИИ + валидация реквизитов
    // ------------------------------------------------------------------
    public Dto.ProcessResponse process(Dto.ProcessRequest request) {
        if (request == null || request.text() == null || request.text().isBlank()) {
            throw badRequest("EMPTY_TEXT", "Введите текст черновика — поле не может быть пустым");
        }
        DocumentType type = parseType(request.documentType());
        TemplateKind template = parseTemplate(request.templateId());

        // AIUnavailableException/AIParseException пробрасываются дальше и
        // переводятся GlobalExceptionHandler в понятный формат ошибки.
        AIResult result = aiService.process(request.text(), type);
        if (result.improvedText() == null || result.improvedText().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "ИИ вернул пустой текст. Попробуйте ещё раз.");
        }

        ProcessedDocument session = store.createSession(new ProcessedDocument(
                store.newId(), type, template.getId(), request.text()));
        session.setImprovedText(result.improvedText());
        session.setRequisites(new EnumMap<>(result.requisites()));

        return toProcessResponse(session);
    }

    // ------------------------------------------------------------------
    // Заполнение недостающих реквизитов пользователем
    // ------------------------------------------------------------------
    public Dto.ProcessResponse updateRequisites(String documentId, Dto.RequisitesUpdateRequest request) {
        ProcessedDocument session = store.getSession(documentId);
        if (request != null) {
            if (request.values() != null) {
                for (Map.Entry<String, String> entry : request.values().entrySet()) {
                    RequisiteKey key = parseRequisiteKey(entry.getKey());
                    if (key == null) {
                        continue;
                    }
                    String value = entry.getValue() == null ? "" : entry.getValue().trim();
                    if (value.isEmpty()) {
                        session.getRequisites().remove(key);
                        continue;
                    }
                    if (key == RequisiteKey.DATE
                            && !requisitesService.isValidDateFormat(value)
                            && !value.toLowerCase(Locale.ROOT).contains("заполнить")) {
                        throw badRequest("INVALID_DATE_VALUE",
                                "Дата должна быть в формате ДД.ММ.ГГГГ, например 12.03.2025");
                    }
                    if (key == RequisiteKey.NUMBER
                            && !requisitesService.isValidNumberFormat(value)
                            && !value.toLowerCase(Locale.ROOT).contains("заполнить")) {
                        throw badRequest("INVALID_NUMBER_VALUE",
                                "Номер должен содержать цифры. Допустимы буквы, дефис, слэш (например 47-СЗ, 12/23)");
                    }
                    session.getRequisites().put(key, value);
                }
            }
            if (request.signatureImage() != null) {
                if (request.signatureImage().isBlank()) {
                    session.setSignatureImage(null);
                } else {
                    session.setSignatureImage(request.signatureImage());
                }
            }
            if (request.photoImage() != null) {
                if (request.photoImage().isBlank()) {
                    session.setPhotoImage(null);
                } else {
                    session.setPhotoImage(request.photoImage());
                }
            }
        }
        return toProcessResponse(session);
    }

    // ------------------------------------------------------------------
    // Генерация DOCX
    // ------------------------------------------------------------------
    public Dto.GenerateResponse generate(String documentId, Dto.GenerateRequest request) {
        ProcessedDocument session = store.getSession(documentId);

        // Сценарий 7: пользователь мог отредактировать улучшенный текст
        if (request != null && request.finalText() != null && !request.finalText().isBlank()) {
            session.setImprovedText(request.finalText().trim());
        }
        // Принимаем подпись из запроса (приоритет) и сохраняем в сессию
        if (request != null && request.signatureImage() != null) {
            if (request.signatureImage().isBlank()) {
                session.setSignatureImage(null);
            } else {
                session.setSignatureImage(request.signatureImage());
            }
        }
        // Принимаем фото из запроса (приоритет) и сохраняем в сессию
        if (request != null && request.photoImage() != null) {
            if (request.photoImage().isBlank()) {
                session.setPhotoImage(null);
            } else {
                session.setPhotoImage(request.photoImage());
            }
        }

        Map<RequisiteKey, String> prepared = requisitesService.prepareForGeneration(session);
        session.setRequisites(prepared);

        TemplateKind template;
        List<String> warnings = new ArrayList<>();
        try {
            template = TemplateKind.fromId(session.getTemplateId());
        } catch (IllegalArgumentException e) {
            template = TemplateKind.STANDARD;
            warnings.add("Выбранный шаблон недоступен, применён запасной шаблон «"
                    + TemplateKind.STANDARD.getTitle() + "».");
        }

        DocxGenerationService.GenerationResult generated =
                docxService.generate(session.getDocumentType(), template, prepared,
                        session.getImprovedText(), session.getSignatureImage(),
                        session.getPhotoImage());
        warnings.addAll(generated.warnings());

        GeneratedDocument document = store.saveGenerated(new GeneratedDocument(
                store.newId(), session, generated.fileName(), generated.content()));

        return new Dto.GenerateResponse(true, document.getId(), document.getFileName(),
                "/api/documents/" + document.getId() + "/download", warnings);
    }

    // ------------------------------------------------------------------
    // Валидация без создания сессии (быстрая проверка реквизитов)
    // ------------------------------------------------------------------
    public Dto.ValidateResponse validate(Dto.ValidateRequest request) {
        if (request == null || request.text() == null || request.text().isBlank()) {
            throw badRequest("EMPTY_TEXT", "Введите текст черновика — поле не может быть пустым");
        }
        DocumentType type = parseType(request.documentType());
        AIResult result = new ru.docgen.ai.MockAIProvider().process(request.text(), type);
        RequisitesValidation validation = requisitesService.validate(type, result.requisites());
        return new Dto.ValidateResponse(
                toRequisitesDto(result.requisites()),
                validation.checks().stream().map(Dto.RequisiteCheckDto::from).toList(),
                validation.missing().stream().map(k -> k.name().toLowerCase(Locale.ROOT)).toList());
    }

    // ------------------------------------------------------------------
    // Вспомогательные методы
    // ------------------------------------------------------------------
    private Dto.ProcessResponse toProcessResponse(ProcessedDocument session) {
        RequisitesValidation validation = requisitesService.validate(
                session.getDocumentType(), session.getRequisites());
        return new Dto.ProcessResponse(
                session.getId(),
                session.getImprovedText(),
                session.getDocumentType().getId(),
                session.getTemplateId(),
                toRequisitesDto(session.getRequisites()),
                validation.checks().stream().map(Dto.RequisiteCheckDto::from).toList(),
                validation.missing().stream().map(k -> k.name().toLowerCase(Locale.ROOT)).toList());
    }

    private Dto.RequisitesDto toRequisitesDto(Map<RequisiteKey, String> requisites) {
        return new Dto.RequisitesDto(
                requisites.get(RequisiteKey.RECIPIENT),
                requisites.get(RequisiteKey.AUTHOR),
                requisites.get(RequisiteKey.SUBJECT),
                requisites.get(RequisiteKey.DATE),
                requisites.get(RequisiteKey.NUMBER),
                requisites.get(RequisiteKey.SIGNATURE),
                requisites.get(RequisiteKey.SALUTATION),
                requisites.get(RequisiteKey.EXECUTOR),
                requisites.get(RequisiteKey.ORGANIZATION));
    }

    public static DocumentType parseType(String id) {
        if (id == null || id.isBlank()) {
            throw badRequest("INVALID_DOCUMENT_TYPE", "Не выбран тип документа");
        }
        try {
            return DocumentType.fromId(id);
        } catch (IllegalArgumentException e) {
            throw badRequest("INVALID_DOCUMENT_TYPE", "Выбранный тип документа недоступен: " + id);
        }
    }

    public static TemplateKind parseTemplate(String id) {
        if (id == null || id.isBlank()) {
            throw badRequest("INVALID_TEMPLATE", "Не выбран шаблон оформления");
        }
        try {
            return TemplateKind.fromId(id);
        } catch (IllegalArgumentException e) {
            throw badRequest("INVALID_TEMPLATE", "Выбранный шаблон недоступен: " + id);
        }
    }

    public static RequisiteKey parseRequisiteKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            return RequisiteKey.valueOf(key.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ResponseStatusException badRequest(String code, String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, code + ": " + message);
    }
}
