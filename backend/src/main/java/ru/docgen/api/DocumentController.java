package ru.docgen.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.docgen.api.dto.Dto;
import ru.docgen.core.GeneratedDocument;
import ru.docgen.store.DocumentStore;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * API подготовки и генерации документов.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService service;
    private final DocumentStore store;

    public DocumentController(DocumentService service, DocumentStore store) {
        this.service = service;
        this.store = store;
    }

    /** Обработать черновик ИИ и проверить реквизиты. */
    @PostMapping("/process")
    public Dto.ProcessResponse process(@RequestBody Dto.ProcessRequest request) {
        return service.process(request);
    }

    /** Проверить реквизиты текста без создания сессии. */
    @PostMapping("/validate")
    public Dto.ValidateResponse validate(@RequestBody Dto.ValidateRequest request) {
        return service.validate(request);
    }

    /** Заполнить недостающие реквизиты. */
    @PostMapping("/{id}/requisites")
    public Dto.ProcessResponse updateRequisites(@PathVariable String id,
                                                @RequestBody(required = false) Dto.RequisitesUpdateRequest request) {
        return service.updateRequisites(id, request);
    }

    /** Сгенерировать DOCX по выбранному шаблону. */
    @PostMapping("/{id}/generate")
    public Dto.GenerateResponse generate(@PathVariable String id,
                                         @RequestBody(required = false) Dto.GenerateRequest request) {
        return service.generate(id, request);
    }

    /** Скачать готовый DOCX. */
    @GetMapping("/{documentId}/download")
    public ResponseEntity<byte[]> download(@PathVariable String documentId) {
        GeneratedDocument document = store.getGenerated(documentId);
        String asciiName = "document_" + document.getId() + ".docx";
        String utf8Name = URLEncoder.encode(document.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + asciiName + "\"; filename*=UTF-8''" + utf8Name)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(document.getContent());
    }

    /** Отправить готовый DOCX в подключённую СЭД. */
    @PostMapping("/{documentId}/send-to-sed")
    public Dto.SedSendResponse sendToSed(@PathVariable String documentId,
                                         @RequestBody Dto.SedSendRequest request) {
        return service.sendToSed(documentId, request);
    }

    /** Список сформированных документов (для раздела «Мои документы»). */
    @GetMapping
    public List<Dto.GeneratedDocumentDto> list() {
        return store.listGenerated().stream()
                .map(d -> new Dto.GeneratedDocumentDto(
                        d.getId(),
                        d.getFileName(),
                        d.getSession().getDocumentType().getId(),
                        d.getSession().getTemplateId(),
                        d.getCreatedAt().toString()))
                .toList();
    }
}
