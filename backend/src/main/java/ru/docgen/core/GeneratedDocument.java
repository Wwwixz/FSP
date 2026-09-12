package ru.docgen.core;

import java.time.Instant;

/**
 * Сгенерированный и сохранённый (в памяти) DOCX-файл.
 */
public class GeneratedDocument {

    private final String id;
    private final ProcessedDocument session;
    private final String fileName;
    private final byte[] content;
    private final Instant createdAt;

    public GeneratedDocument(String id, ProcessedDocument session, String fileName, byte[] content) {
        this.id = id;
        this.session = session;
        this.fileName = fileName;
        this.content = content;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public ProcessedDocument getSession() {
        return session;
    }

    public String getFileName() {
        return fileName;
    }

    public byte[] getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
