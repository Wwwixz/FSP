package ru.docgen.ai;

/**
 * ИИ вернул ответ, который не удалось разобрать как корректный JSON
 * установленной структуры. Ответ модели нельзя считать достоверным.
 */
public class AIParseException extends AIException {

    public AIParseException(String message) {
        super(message);
    }

    public AIParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
