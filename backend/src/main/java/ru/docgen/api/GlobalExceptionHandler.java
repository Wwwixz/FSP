package ru.docgen.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import ru.docgen.ai.AIParseException;
import ru.docgen.ai.AIUnavailableException;
import ru.docgen.api.dto.Dto;

/**
 * Единый формат ошибок для фронтенда:
 * {"success": false, "error": {"code": "...", "message": "..."}}
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AIUnavailableException.class)
    public ResponseEntity<Dto.ApiError> aiUnavailable(AIUnavailableException e) {
        log.warn("ИИ недоступен: {}", e.getMessage());
        return respond(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE",
                "Сервис обработки текста временно недоступен. Попробуйте ещё раз — введённый текст сохранён.");
    }

    @ExceptionHandler(AIParseException.class)
    public ResponseEntity<Dto.ApiError> aiParse(AIParseException e) {
        log.warn("Ответ ИИ не удалось разобрать: {}", e.getMessage());
        return respond(HttpStatus.BAD_GATEWAY, "AI_BAD_RESPONSE",
                "Не удалось обработать текст. Попробуйте ещё раз.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Dto.ApiError> responseStatus(ResponseStatusException e) {
        String reason = e.getReason() == null ? e.getMessage() : e.getReason();
        String code = "ERROR";
        String message = reason;
        int idx = reason != null ? reason.indexOf(": ") : -1;
        if (idx > 0 && reason.substring(0, idx).matches("[A-Z_]+")) {
            code = reason.substring(0, idx);
            message = reason.substring(idx + 2);
        }
        return respond(HttpStatus.resolve(e.getStatusCode().value()), code, message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<Dto.ApiError> badRequest(Exception e) {
        return respond(HttpStatus.BAD_REQUEST, "BAD_REQUEST",
                "Некорректный запрос. Проверьте переданные данные.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Dto.ApiError> internal(Exception e) {
        log.error("Внутренняя ошибка", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Внутренняя ошибка сервиса. Попробуйте ещё раз.");
    }

    private ResponseEntity<Dto.ApiError> respond(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Dto.ApiError.of(code, message));
    }
}
