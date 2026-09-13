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
import org.springframework.web.server.ResponseStatusException;
import ru.docgen.api.dto.Dto;
import ru.docgen.core.GeneratedDocument;
import ru.docgen.core.ProcessedDocument;
import ru.docgen.core.TemplateKind;
import ru.docgen.store.DocumentStore;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

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

    /** Извлечь текст из готового файла пользователя (DOCX/PDF/TXT) для «реанимации» документа. */
    @PostMapping(value = "/extract", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public Dto.ExtractResponse extract(@org.springframework.web.bind.annotation.RequestParam("file")
                                       org.springframework.web.multipart.MultipartFile file) {
        return service.extract(file);
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

    /** Скачать готовый документ (DOCX или PDF — по расширению имени файла). */
    @GetMapping("/{documentId}/download")
    public ResponseEntity<byte[]> download(@PathVariable String documentId) {
        GeneratedDocument document = store.getGenerated(documentId);
        String asciiName = "document_" + document.getId() + "."
                + extensionOf(document.getFileName());
        String utf8Name = URLEncoder.encode(document.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        String mime = document.getFileName().toLowerCase(Locale.ROOT).endsWith(".pdf")
                ? "application/pdf"
                : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + asciiName + "\"; filename*=UTF-8''" + utf8Name)
                .contentType(MediaType.parseMediaType(mime))
                .body(document.getContent());
    }

    /**
     * Просмотр документа прямо в браузере (без скачивания): PDF отдаётся как
     * есть, DOCX конвертируется в PDF по тем же данным и правилам шаблона.
     * Content-Disposition: inline — рендерится во встроенном просмотрщике.
     */
    @GetMapping("/{documentId}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable String documentId) {
        GeneratedDocument document = store.getGenerated(documentId);
        byte[] content = document.getContent();
        if (!document.getFileName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            ProcessedDocument session = document.getSession();
            TemplateKind template;
            try {
                template = TemplateKind.fromId(session.getTemplateId());
            } catch (IllegalArgumentException e) {
                template = TemplateKind.STANDARD;
            }
            try {
                content = service.toPdf(session, template);
            } catch (Exception e) {
                // Не смогли показать на сайте — отдадим исходный файл на скачивание
                return download(documentId);
            }
        }
        String utf8Name = URLEncoder.encode(
                document.getFileName().replaceAll("\\.docx$", ".pdf"), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"preview.pdf\"; filename*=UTF-8''" + utf8Name)
                .contentType(MediaType.parseMediaType("application/pdf"))
                .body(content);
    }

    private static String extensionOf(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1) : "docx";
    }

    /** Доработать улучшенный текст по инструкции ИИ («сделай короче» и т.п.). */
    @PostMapping("/{documentId}/refine")
    public Dto.RefineResponse refine(@PathVariable String documentId,
                                     @RequestBody Dto.RefineRequest request) {
        return service.refine(documentId, request);
    }

    /** Отправить готовый документ вложением на почту. */
    @PostMapping("/{documentId}/email")
    public Dto.EmailResponse email(@PathVariable String documentId,
                                   @RequestBody Dto.EmailRequest request) {
        return service.email(documentId, request);
    }

    /** Печатный почтовый конверт (PDF E65) по реквизитам документа. */
    @GetMapping("/{documentId}/envelope")
    public ResponseEntity<byte[]> envelope(@PathVariable String documentId) {
        GeneratedDocument document = store.getGenerated(documentId);
        byte[] pdf = service.toEnvelope(document.getSession());
        return ResponseEntity.status(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"konvert.pdf\"")
                .contentType(MediaType.parseMediaType("application/pdf"))
                .body(pdf);
    }

    /**
     * Скачать все созданные документы одним ZIP-архивом
     * (с манифестом — списком файлов).
     */
    @GetMapping("/archive")
    public ResponseEntity<byte[]> archive() {
        List<GeneratedDocument> documents = store.listGenerated();
        if (documents.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "NO_DOCUMENTS: Пока нет ни одного созданного документа");
        }
        String stamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
        try (java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
             java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(buffer,
                     java.nio.charset.StandardCharsets.UTF_8)) {

            StringBuilder manifest = new StringBuilder("DocHelper — созданные документы (")
                    .append(documents.size()).append(" шт.)\n\n");
            java.util.Set<String> usedNames = new java.util.HashSet<>();
            int index = 1;
            for (GeneratedDocument document : documents) {
                String name = document.getFileName();
                if (!usedNames.add(name)) {
                    String base = name.replaceFirst("\\.[^.]+$", "");
                    String ext = name.substring(base.length());
                    name = base + "_" + index + ext;
                    usedNames.add(name);
                }
                zip.putNextEntry(new java.util.zip.ZipEntry(index + "_" + name));
                zip.write(document.getContent());
                zip.closeEntry();
                manifest.append(index).append(". ").append(name)
                        .append(" — ").append(document.getSession().getDocumentType().getLabel())
                        .append(", шаблон «").append(ru.docgen.core.TemplateKind.fromId(
                                document.getSession().getTemplateId()).getTitle())
                        .append("»\n");
                index++;
            }
            manifest.append("\nСервис: DocHelper — ИИ-конструктор служебных документов.\n");
            zip.putNextEntry(new java.util.zip.ZipEntry("README.txt"));
            zip.write(manifest.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();

            return ResponseEntity.status(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"dochelper_documents_" + stamp + ".zip\"")
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .body(buffer.toByteArray());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Не удалось собрать архив документов");
        }
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
                        d.getCreatedAt().toString(),
                        "/api/documents/" + d.getId() + "/download"))
                .toList();
    }
}
