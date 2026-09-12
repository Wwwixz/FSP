package ru.docgen.ai;

/**
 * ИИ недоступен (нет сети, истёк таймаут, провайдер вернул ошибку).
 * Приложение обязано показать понятное сообщение и не потерять текст.
 */
public class AIUnavailableException extends AIException {

    public AIUnavailableException(String message) {
        super(message);
    }

    public AIUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
