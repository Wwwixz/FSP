package ru.docgen.store;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import ru.docgen.core.GeneratedDocument;
import ru.docgen.core.ProcessedDocument;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Простое потокобезопасное in-memory хранилище сессий и готовых файлов.
 * По требованиям ТЗ базы данных не требуется.
 */
@Component
public class DocumentStore {

    private final Map<String, ProcessedDocument> sessions = new ConcurrentHashMap<>();
    private final Map<String, GeneratedDocument> generated = new ConcurrentHashMap<>();
    private final Map<String, Long> sequenceByType = new ConcurrentHashMap<>();

    private final int maxSessions;
    private final long sessionTtlMinutes;
    private final AtomicLong counter = new AtomicLong();

    public DocumentStore(@Value("${app.documents.max-sessions:200}") int maxSessions,
                         @Value("${app.documents.session-ttl-minutes:240}") long sessionTtlMinutes) {
        this.maxSessions = maxSessions;
        this.sessionTtlMinutes = sessionTtlMinutes;
    }

    public ProcessedDocument createSession(ProcessedDocument session) {
        evictExpired();
        if (sessions.size() >= maxSessions) {
            sessions.entrySet().stream()
                    .min(Comparator.comparing(e -> e.getValue().getLastAccessAt()))
                    .ifPresent(e -> sessions.remove(e.getKey()));
        }
        sessions.put(session.getId(), session);
        return session;
    }

    public ProcessedDocument getSession(String id) {
        ProcessedDocument doc = sessions.get(id);
        if (doc == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Сессия документа не найдена или истекла. Начните создание документа заново.");
        }
        doc.touch();
        return doc;
    }

    public ProcessedDocument findSessionOpt(String id) {
        return id == null ? null : sessions.get(id);
    }

    public GeneratedDocument saveGenerated(GeneratedDocument document) {
        generated.put(document.getId(), document);
        return document;
    }

    public GeneratedDocument getGenerated(String id) {
        GeneratedDocument doc = generated.get(id);
        if (doc == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Документ не найден. Сгенерируйте файл заново.");
        }
        return doc;
    }

    public List<GeneratedDocument> listGenerated() {
        return generated.values().stream()
                .sorted(Comparator.comparing(GeneratedDocument::getCreatedAt).reversed())
                .toList();
    }

    public String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /** Глобальный порядковый счётчик для автозаполнения номера документа. */
    public long nextGlobalNumber(String typeSuffix) {
        long n = counter.incrementAndGet();
        return typeSuffix == null || typeSuffix.isBlank() ? n : n;
    }

    private void evictExpired() {
        Instant deadline = Instant.now().minusSeconds(sessionTtlMinutes * 60);
        sessions.entrySet().removeIf(e -> e.getValue().getLastAccessAt().isBefore(deadline));
    }
}
