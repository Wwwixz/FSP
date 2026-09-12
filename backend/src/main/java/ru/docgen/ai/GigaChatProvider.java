package ru.docgen.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ru.docgen.core.DocumentType;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Провайдер нейросети GigaChat (Сбер, OpenAI-совместимый API).
 *
 * <p>Авторизация двухступенчатая:
 * <ol>
 *   <li>получаем {@code access_token}: {@code POST {oauthUrl}} с заголовком
 *       {@code Authorization: Basic <authKey>}, обязательным {@code RqUID}
 *       (uuid4 на каждый запрос) и form-body {@code scope=GIGACHAT_API_*};</li>
 *   <li>этим токеном (Bearer) авторизуем обычные запросы к
 *       {@code {baseUrl}/api/v1/chat/completions}.</li>
 * </ol>
 * Токен живёт ~30 минут и отдаётся как {@code access_token} + {@code expires_at}
 * (unix-миллисекунды). Провайдер:
 *
 * <ul>
 *   <li>лениво получает токен при первом запросе;</li>
 *   <li>в фоне каждые 60 с перевыпускает токен, когда до истечения осталось
 *       меньше {@value #REFRESH_MARGIN_MINUTES} минут;</li>
 *   <li>при 401/403 во время запроса к модели разово обновляет токен и повторяет запрос;</li>
 *   <li>при недоступности/ошибке авторизации бросает {@link AIUnavailableException} —
 *       текст пользователя не теряется (сценарий 6 ТЗ).</li>
 * </ul>
 *
 * <p>Из-за сертификатов НУЦ Минцифры для локальной разработки предусмотрен
 * dev-флаг {@code AI_GIGACHAT_INSECURE_SSL=true} (TrustAll), который применяется
 * при сборке {@code RestClient} в {@link AIConfiguration}. В продакшене флаг
 * должен быть выключен.
 */
public class GigaChatProvider implements AIService {

    private static final Logger log = LoggerFactory.getLogger(GigaChatProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** За сколько минут до истечения токена начинаем перевыпускать его в фоне. */
    private static final long REFRESH_MARGIN_MINUTES = 10;
    private static final Duration REFRESH_MARGIN = Duration.ofMinutes(REFRESH_MARGIN_MINUTES);

    private final AIProperties properties;
    private final RestClient client;
    private final Object tokenLock = new Object();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "gigachat-token-refresh");
                t.setDaemon(true);
                return t;
            });

    private volatile Token token;

    /** Токен доступа с моментом истечения (null — срок неизвестен). */
    static final class Token {
        final String value;
        final Instant expiresAt;

        Token(String value, Instant expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }

        boolean isExpired(Instant now) {
            return expiresAt != null && !now.isBefore(expiresAt);
        }

        boolean needsRefresh(Instant now) {
            // Без известного срока истечения — никогда не обновляем заранее,
            // положимся на retry по 401/403.
            return expiresAt != null && !now.isBefore(expiresAt.minus(REFRESH_MARGIN));
        }
    }

    public GigaChatProvider(AIProperties properties, RestClient client) {
        this.properties = properties;
        this.client = client;
        // Разогрев фона: проверка токена вскоре после старта и далее каждые 60 с.
        scheduler.schedule(this::watchToken, 5, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::watchToken, 60, 60, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void close() {
        scheduler.shutdownNow();
    }

    @Override
    public AIResult process(String text, DocumentType documentType) {
        Token current = getValidToken();
        String content = chat(current, text, documentType, 1);
        return AIJsonParser.parse(content);
    }

    // ------------------------------------------------------------------
    // Токен: получение и автообновление
    // ------------------------------------------------------------------

    /**
     * Возвращает действующий токен, при необходимости получая новый.
     * Потокобезопасно, single-flight. Бросает {@link AIUnavailableException},
     * если получить токен не удалось.
     */
    private Token getValidToken() {
        Token current = token;
        if (current != null && !current.isExpired(Instant.now())) {
            return current;
        }
        synchronized (tokenLock) {
            current = token;
            if (current != null && !current.isExpired(Instant.now())) {
                return current;
            }
            Token fresh = fetchToken();
            token = fresh;
            return fresh;
        }
    }

    /** Фоновая задача: заблаговременно перевыпускает почти истёкший токен. */
    private void watchToken() {
        Token current = token;
        if (current == null || !current.needsRefresh(Instant.now())) {
            return;
        }
        synchronized (tokenLock) {
            current = token;
            if (current == null || !current.needsRefresh(Instant.now())) {
                return;
            }
            try {
                Token fresh = fetchToken();
                token = fresh;
                log.info("Токен GigaChat обновлён, истекает {}", fresh.expiresAt);
            } catch (Exception e) {
                // Старый токен ещё может быть валидным; повторим в следующем цикле.
                log.warn("Фоновое обновление токена GigaChat не удалось: {}", e.getMessage());
            }
        }
    }

    /** Сбрасывает кэш токена (например после 401) — принудительный перезапрос. */
    private void invalidateToken() {
        token = null;
    }

    /**
     * Запрашивает новый access_token по Authorization key (OAuth2, эндпоинт Сбера).
     * Требуемые заголовки: Basic auth, RqUID (uuid4), Accept: application/json.
     */
    private Token fetchToken() {
        try {
            Map<String, Object> response = client.post()
                    .uri(URI.create(properties.getGigachatOauthUrl()))
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + properties.getGigachatAuthKey())
                    .header("RqUID", UUID.randomUUID().toString())
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("scope=" + encode(properties.getGigachatScope()))
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                throw new AIUnavailableException("GigaChat вернул пустой ответ авторизации");
            }
            Token parsed = parseTokenResponse(MAPPER.valueToTree(response));
            log.info("Токен GigaChat получен, истекает {}", parsed.expiresAt);
            return parsed;
        } catch (AIException e) {
            throw e;
        } catch (Exception e) {
            throw new AIUnavailableException("Не удалось получить токен доступа GigaChat: " + e.getMessage(), e);
        }
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Разбирает ответ токен-эндпоинта. Принимает несколько распространённых
     * форматов: {@code access_token} (также вложенный в {@code data.…}), срок —
     * {@code expires_in} (секунды), {@code expires_at}/{@code expires} (epoch
     * секунды/миллисекунды или ISO-строка). У GigaChat используется
     * {@code access_token} + {@code expires_at} (unix-миллисекунды).
     */
    static Token parseTokenResponse(JsonNode root) {
        if (root == null || root.isNull() || !root.isObject()) {
            throw new AIUnavailableException("GigaChat вернул некорректный ответ авторизации");
        }
        JsonNode access = firstNode(root, "access_token", "token", "accessToken");
        if (access == null || !access.isTextual() || access.asText().isBlank()) {
            throw new AIUnavailableException("В ответе авторизации GigaChat отсутствует access_token");
        }
        return new Token(access.asText().trim(), parseExpiry(root));
    }

    private static JsonNode firstNode(JsonNode root, String... names) {
        for (String name : names) {
            JsonNode value = root.get(name);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        JsonNode data = root.path("data");
        if (data.isObject()) {
            for (String name : names) {
                JsonNode value = data.get(name);
                if (value != null && !value.isNull()) {
                    return value;
                }
            }
        }
        return null;
    }

    /** Момент истечения токена; null — если сервер срок не сообщил. */
    static Instant parseExpiry(JsonNode root) {
        JsonNode expiresIn = firstNode(root, "expires_in", "expiresIn", "ttl");
        if (expiresIn != null && expiresIn.isNumber()) {
            return Instant.now().plusSeconds(expiresIn.asLong());
        }
        JsonNode expiresAt = firstNode(root, "expires_at", "expiresAt", "expires", "expiry");
        if (expiresAt == null) {
            return null;
        }
        if (expiresAt.isNumber()) {
            return epochToInstant(expiresAt.asLong());
        }
        if (expiresAt.isTextual()) {
            String raw = expiresAt.asText().trim();
            try {
                return Instant.parse(raw);
            } catch (DateTimeParseException ignored) {
                // ниже — другие форматы
            }
            try {
                return OffsetDateTime.parse(raw).toInstant();
            } catch (DateTimeParseException ignored) {
                // ниже — epoch-число строкой
            }
            try {
                return epochToInstant(Long.parseLong(raw));
            } catch (NumberFormatException ignored) {
                // неизвестный формат — без срока
            }
        }
        return null;
    }

    private static Instant epochToInstant(long value) {
        return value > 1_000_000_000_000L ? Instant.ofEpochMilli(value) : Instant.ofEpochSecond(value);
    }

    // ------------------------------------------------------------------
    // Запрос к модели (chat completions)
    // ------------------------------------------------------------------

    private String chat(Token access, String text, DocumentType documentType, int retriesLeft) {
        Map<String, Object> request = buildRequest(text, documentType);
        try {
            Map<String, Object> response = client.post()
                    .uri(URI.create(chatCompletionsUrl()))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + access.value)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Map.class);
            return extractContent(response);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if ((status == 401 || status == 403) && retriesLeft > 0) {
                log.warn("GigaChat отклонил запрос (HTTP {}), обновляю токен и повторяю", status);
                invalidateToken();
                try {
                    Token fresh = getValidToken();
                    return chat(fresh, text, documentType, retriesLeft - 1);
                } catch (AIException retryFailure) {
                    throw retryFailure;
                } catch (Exception retryFailure) {
                    throw new AIUnavailableException(
                            "Не удалось повторить запрос после обновления токена: " + retryFailure.getMessage(),
                            retryFailure);
                }
            }
            String detail = e.getResponseBodyAsString();
            throw new AIUnavailableException("GigaChat вернул ошибку HTTP " + status
                    + (detail == null || detail.isBlank() ? "" : ": " + detail), e);
        } catch (AIException e) {
            throw e;
        } catch (Exception e) {
            throw new AIUnavailableException("Не удалось обратиться к GigaChat: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildRequest(String text, DocumentType documentType) {
        Map<String, Object> body = new LinkedHashMap<>();
        String model = properties.getGigachatModel();
        if (model != null && !model.isBlank()) {
            body.put("model", model);
        }
        body.put("messages", List.of(
                Map.of("role", "system", "content", OpenAICompatibleProvider.systemPrompt()),
                Map.of("role", "user", "content", userPrompt(text, documentType))));
        body.put("temperature", 0.1);
        // GigaChat не поддерживает response_format=json_object (вернёт 400).
        // Строгий разбор JSON делает AIJsonParser.
        return body;
    }

    private String userPrompt(String text, DocumentType documentType) {
        return """
                Тип документа: %s.

                Черновик пользователя:
                ---
                %s
                ---

                Обработай черновик по правилам и верни JSON.""".formatted(documentType.getLabel(), text);
    }

    private String chatCompletionsUrl() {
        String base = properties.getGigachatBaseUrl();
        if (base == null || base.isBlank()) {
            base = "https://api.giga.chat";
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        // OpenAI-совместимый путь GigaChat: /v1/chat/completions
        return base + "/v1/chat/completions";
    }

    /**
     * Извлекает содержимое ответа chat completions из {@code choices[0].message.content}.
     * Структура ответа та же, что у OpenAI-совместимых API.
     */
    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> response) {
        if (response == null) {
            throw new AIUnavailableException("GigaChat вернул пустой ответ");
        }
        Object error = response.get("error");
        if (error instanceof Map<?, ?> errorMap && errorMap.get("message") != null) {
            throw new AIUnavailableException("GigaChat вернул ошибку: " + errorMap.get("message"));
        }
        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new AIParseException("Ответ GigaChat не содержит choices");
            }
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            Object content = message == null ? null : message.get("content");
            if (!(content instanceof String s) || s.isBlank()) {
                throw new AIParseException("Ответ GigaChat не содержит текста");
            }
            return s;
        } catch (ClassCastException e) {
            throw new AIParseException("Неожиданная структура ответа GigaChat", e);
        }
    }
}