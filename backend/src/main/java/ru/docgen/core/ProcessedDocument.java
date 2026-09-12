package ru.docgen.core;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Сессия подготовки одного документа (in-memory, без БД согласно ТЗ).
 */
public class ProcessedDocument {

    private final String id;
    private final DocumentType documentType;
    private final String templateId;
    private final String rawText;
    private final Instant createdAt;
    private Instant lastAccessAt;

    private String improvedText;
    private Map<RequisiteKey, String> requisites = new LinkedHashMap<>();
    /** Изображение подписи в формате base64-dataURL (опционально). */
    private String signatureImage;
    /** Фото (аватар) автора документа в формате base64-dataURL (опционально). */
    private String photoImage;
    /** Порядковый номер документа внутри сессии, используется при автозаполнении номера. */
    private int numberSequence = 1;

    public ProcessedDocument(String id, DocumentType documentType, String templateId, String rawText) {
        this.id = id;
        this.documentType = documentType;
        this.templateId = templateId;
        this.rawText = rawText;
        this.createdAt = Instant.now();
        this.lastAccessAt = this.createdAt;
    }

    public void touch() {
        this.lastAccessAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public String getTemplateId() {
        return templateId;
    }

    public String getRawText() {
        return rawText;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastAccessAt() {
        return lastAccessAt;
    }

    public String getImprovedText() {
        return improvedText;
    }

    public void setImprovedText(String improvedText) {
        this.improvedText = improvedText;
    }

    public Map<RequisiteKey, String> getRequisites() {
        return requisites;
    }

    public void setRequisites(Map<RequisiteKey, String> requisites) {
        this.requisites = requisites;
    }

    public String getSignatureImage() {
        return signatureImage;
    }

    public void setSignatureImage(String signatureImage) {
        this.signatureImage = signatureImage;
    }

    public String getPhotoImage() {
        return photoImage;
    }

    public void setPhotoImage(String photoImage) {
        this.photoImage = photoImage;
    }

    public synchronized int nextNumber() {
        return numberSequence++;
    }

    public synchronized void bumpNumberSequence() {
        numberSequence++;
    }
}
