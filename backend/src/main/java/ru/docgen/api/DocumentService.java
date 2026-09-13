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
    private final ru.docgen.docx.PdfExportService pdfExportService;
    private final ru.docgen.docx.QrCodeService qrCodeService;
    private final DocumentMailService mailService;
    private final DocumentStore store;
    private final RestTemplate restTemplate = new RestTemplate();

    public DocumentService(AIService aiService, RequisitesService requisitesService,
                           DocxGenerationService docxService,
                           ru.docgen.docx.PdfExportService pdfExportService,
                           ru.docgen.docx.QrCodeService qrCodeService,
                           DocumentMailService mailService,
                           DocumentStore store) {
        this.aiService = aiService;
        this.requisitesService = requisitesService;
        this.docxService = docxService;
        this.pdfExportService = pdfExportService;
        this.qrCodeService = qrCodeService;
        this.mailService = mailService;
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

        // Предохранитель фактов: если ИИ потерял числа (даты, суммы, номера)
        // из черновика — включается резервный офлайн-обработчик, сохраняющий
        // текст полностью (сценарий 4 ТЗ: факты искажать нельзя).
        List<String> warnings = new ArrayList<>();
        if (numericTokensLost(request.text(), result.improvedText())) {
            AIResult fallback = new ru.docgen.ai.MockAIProvider().process(request.text(), type);
            if (!numericTokensLost(request.text(), fallback.improvedText())) {
                result = fallback;
                warnings.add("Основной ИИ пропустил часть фактов из черновика — применён резервный "
                        + "обработчик, сохранивший текст полностью. Можно доработать текст кнопками ниже.");
            }
        }

        if (result.improvedText() == null || result.improvedText().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "ИИ вернул пустой текст. Попробуйте ещё раз.");
        }

        ProcessedDocument session = store.createSession(new ProcessedDocument(
                store.newId(), type, template.getId(), request.text()));
        session.setImprovedText(result.improvedText());
        session.setRequisites(new EnumMap<>(result.requisites()));

        Dto.ProcessResponse response = toProcessResponse(session);
        if (!warnings.isEmpty()) {
            response = new Dto.ProcessResponse(response.documentId(), response.improvedText(),
                    response.documentType(), response.templateId(), response.requisites(),
                    response.checks(), response.missing(), warnings);
        }
        return response;
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
            if (request.stampImage() != null) {
                if (request.stampImage().isBlank()) {
                    session.setStampImage(null);
                } else {
                    session.setStampImage(request.stampImage());
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
        // Принимаем печать из запроса (приоритет) и сохраняем в сессию
        if (request != null && request.stampImage() != null) {
            if (request.stampImage().isBlank()) {
                session.setStampImage(null);
            } else {
                session.setStampImage(request.stampImage());
            }
        }
        boolean pdf = request != null && "pdf".equalsIgnoreCase(request.format());
        // Сохраняем параметры «своего шаблона» для повторного просмотра и экспорта
        if (request != null && request.templateOptions() != null && !request.templateOptions().isEmpty()) {
            session.setTemplateOptions(request.templateOptions());
        }
        // Фирменный бланк пользователя (.docx в base64) для шаблона «Бланк моей организации»
        if (request != null && request.uploadedTemplate() != null && !request.uploadedTemplate().isBlank()) {
            session.setUploadedTemplate(request.uploadedTemplate());
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

        // QR со ссылкой на просмотр: id документа выделяем заранее, чтобы
        // вшить ссылку прямо в файл. Ссылка строится от адреса сайта,
        // переданного фронтом (window.location.origin).
        String newDocumentId = store.newId();
        String previewUrl = null;
        String qrDataUrl = null;
        String origin = request == null ? null : request.origin();
        if (origin != null && (origin.startsWith("http://") || origin.startsWith("https://"))) {
            previewUrl = origin.replaceAll("/+$", "") + "/api/documents/" + newDocumentId + "/preview";
            qrDataUrl = qrCodeService.dataUrl(previewUrl, 300);
        }

        DocxGenerationService.GenerationResult generated =
                docxService.generate(session.getDocumentType(), template, prepared,
                        session.getImprovedText(), session.getSignatureImage(),
                        session.getPhotoImage(), session.getStampImage(), qrDataUrl,
                        session.getUploadedTemplate(),
                        request == null ? null : request.templateOptions());
        warnings.addAll(generated.warnings());

        // PDF — программная конвертация тех же данных и правил оформления
        byte[] content = generated.content();
        String fileName = generated.fileName();
        if (pdf) {
            try {
                content = pdfExportService.export(session.getDocumentType(), template, prepared,
                        session.getImprovedText(), session.getSignatureImage(),
                        session.getPhotoImage(), session.getStampImage(), qrDataUrl,
                        request == null ? null : request.templateOptions());
                fileName = fileName.substring(0, fileName.length() - ".docx".length()) + ".pdf";
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "PDF_EXPORT_FAILED: Не удалось конвертировать документ в PDF. "
                                + "Попробуйте скачать в формате Word.");
            }
        }

        GeneratedDocument document = store.saveGenerated(new GeneratedDocument(
                newDocumentId, session, fileName, content));

        return new Dto.GenerateResponse(true, document.getId(), document.getFileName(),
                "/api/documents/" + document.getId() + "/download", warnings,
                previewUrl, qrDataUrl);
    }

    // ------------------------------------------------------------------
    // Отправка готового документа по почте
    // ------------------------------------------------------------------
    public Dto.EmailResponse email(String documentId, Dto.EmailRequest request) {
        if (request == null || request.to() == null || !request.to().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")) {
            throw badRequest("INVALID_EMAIL", "Укажите корректный адрес получателя, например ivanova@example.ru");
        }
        GeneratedDocument document = store.getGenerated(documentId);
        mailService.sendDocument(request.to(), document.getFileName(), document.getContent());
        return new Dto.EmailResponse(true, "Документ отправлен на " + request.to());
    }

    // ------------------------------------------------------------------
    // Просмотр: DOCX-документ → PDF по тем же данным и правилам шаблона
    // ------------------------------------------------------------------
    public byte[] toPdf(ProcessedDocument session, TemplateKind template) {
        return pdfExportService.export(session.getDocumentType(), template,
                session.getRequisites(), session.getImprovedText(),
                session.getSignatureImage(), session.getPhotoImage(),
                session.getStampImage(), null, session.getTemplateOptions());
    }

    /** Печатный почтовый конверт по реквизитам документа. */
    public byte[] toEnvelope(ProcessedDocument session) {
        return pdfExportService.envelope(session.getRequisites(),
                session.getRequisites().get(RequisiteKey.ORGANIZATION),
                session.getRequisites().get(RequisiteKey.AUTHOR));
    }

    // ------------------------------------------------------------------
    // Разбор готового файла пользователя (DOCX/PDF/TXT) — «реанимация документа»
    // ------------------------------------------------------------------

    /**
     * Извлекает текст из загруженного файла и определяет тип документа.
     * Дальше текст проходит обычный конвейер: ИИ исправляет ошибки и стиль,
     * реквизиты дополняются из профиля, документ собирается по шаблону
     * с печатью и подписью. Дополнительное преимущество по ТЗ (раздел 1.6).
     */
    public Dto.ExtractResponse extract(org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("EMPTY_FILE", "Файл не передан или пустой");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String text;
        try {
            if (name.endsWith(".docx")) {
                text = extractDocx(file);
            } else if (name.endsWith(".pdf")) {
                text = extractPdf(file);
            } else if (name.endsWith(".txt")) {
                text = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
            } else {
                throw badRequest("UNSUPPORTED_FORMAT",
                        "Поддерживаются файлы .docx, .pdf и .txt — конвертируйте документ и попробуйте снова");
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw badRequest("EXTRACT_FAILED",
                    "Не удалось прочитать файл. Если это скан или фото, вставьте текст вручную.");
        }
        if (text == null || text.isBlank()) {
            throw badRequest("NO_TEXT_LAYER",
                    "В файле не найден текст — похоже, это скан или фотография. Вставьте текст вручную.");
        }
        text = text.replace("\u0000", "").replaceAll("\\n{3,}", "\n\n").trim();
        if (text.length() > 20_000) {
            text = text.substring(0, 20_000);
        }

        String detected = detectDocumentType(text);
        String warning = detected == null
                ? "Тип документа определить не удалось — выберите его на следующем шаге вручную."
                : null;
        return new Dto.ExtractResponse(true, text, detected, warning);
    }

    private String extractDocx(org.springframework.web.multipart.MultipartFile file) throws Exception {
        try (var in = file.getInputStream();
             var document = new org.apache.poi.xwpf.usermodel.XWPFDocument(in);
             var extractor = new org.apache.poi.xwpf.extractor.XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extractPdf(org.springframework.web.multipart.MultipartFile file) throws Exception {
        try (var document = org.apache.pdfbox.Loader.loadPDF(file.getBytes())) {
            var stripper = new org.apache.pdfbox.text.PDFTextStripper();
            return stripper.getText(document);
        }
    }

    /** Определяет тип документа по характерным словам заголовка. */
    static String detectDocumentType(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("служебная записка") || lower.contains("служебную записку")) {
            return DocumentType.MEMO.getId();
        }
        if (lower.contains("докладная записка") || lower.contains("докладную записку")) {
            return DocumentType.REPORT.getId();
        }
        if (lower.contains("информационная справка") || lower.contains("справка")) {
            return DocumentType.CERTIFICATE.getId();
        }
        if (lower.contains("письмо") || lower.startsWith("уважаемый") || lower.startsWith("уважаемая")) {
            return DocumentType.LETTER.getId();
        }
        return null;
    }

    // ------------------------------------------------------------------
    // ИИ-доработка улучшенного текста по инструкции пользователя
    // ------------------------------------------------------------------
    public Dto.RefineResponse refine(String documentId, Dto.RefineRequest request) {
        ProcessedDocument session = store.getSession(documentId);
        if (request == null || request.instruction() == null || request.instruction().isBlank()) {
            throw badRequest("EMPTY_INSTRUCTION", "Опишите, что сделать с текстом — например «сделай короче»");
        }
        String current = session.getImprovedText();
        String refined = aiService.refine(current, request.instruction().trim());
        if (refined == null || refined.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "ИИ вернул пустой текст. Исходный вариант не изменился — попробуйте другую формулировку.");
        }
        refined = refined.trim();

        // Предохранитель фактов: если при доработке пропало любое число
        // (дата, сумма, номер), изменение отклоняется — документ не может
        // потерять сведения из-за прихоти модели (сценарий 4 ТЗ).
        if (numericTokensLost(current, refined)) {
            return new Dto.RefineResponse(true, current,
                    "ИИ при доработке потерял даты или суммы — изменение отклонено, текст остался прежним. "
                            + "Попробуйте переформулировать инструкцию.");
        }

        String warning = refined.equals(current)
                ? "ИИ не внёс изменений — попробуйте сформулировать инструкцию иначе."
                : null;
        session.setImprovedText(refined);
        return new Dto.RefineResponse(true, refined, warning);
    }

    /** true, если в новом тексте пропали числа, которые были в исходном. */
    static boolean numericTokensLost(String before, String after) {
        java.util.Set<Long> beforeValues = numericValues(before);
        if (beforeValues.isEmpty()) {
            return false;
        }
        java.util.Set<Long> afterValues = numericValues(after);
        for (Long value : beforeValues) {
            if (!afterValues.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static java.util.Set<Long> numericValues(String text) {
        java.util.Set<Long> values = new java.util.HashSet<>();
        if (text == null) {
            return values;
        }
        var matcher = java.util.regex.Pattern.compile("\\d+").matcher(text);
        while (matcher.find()) {
            try {
                values.add(Long.parseLong(matcher.group()));
            } catch (NumberFormatException ignored) {
                // слишком длинная последовательность цифр — пропускаем
            }
        }
        return values;
    }

    // ------------------------------------------------------------------
    // Валидация без создания сессии (быстрая проверка реквизитов)
    // ------------------------------------------------------------------
    public Dto.ValidateResponse validate(Dto.ValidateRequest request) {        if (request == null || request.text() == null || request.text().isBlank()) {
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
                validation.missing().stream().map(k -> k.name().toLowerCase(Locale.ROOT)).toList(),
                List.of());
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
