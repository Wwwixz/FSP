package ru.docgen.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.docgen.core.RequisiteKey;

import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Строгий парсер ответа ИИ.
 *
 * Ответу модели доверять нельзя: здесь проверяется структура JSON,
 * типы полей, срезаются возможные markdown-ограждения ```json ... ```,
 * лишние ключи игнорируются, некорректные значения приводятся к null.
 */
public final class AIJsonParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Pattern FENCE = Pattern.compile(
            "```(?:json)?\\s*(\\{.*\\})\\s*```", Pattern.DOTALL);

    private static final Pattern FIRST_OBJECT = Pattern.compile("\\{.*\\}", Pattern.DOTALL);

    private AIJsonParser() {
    }

    /**
     * Разбирает ответ модели. Бросает {@link AIParseException}, если
     * корректный JSON установленной структуры извлечь не удалось.
     */
    public static AIResult parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AIParseException("ИИ вернул пустой ответ");
        }
        String candidate = extractJsonObject(raw.trim());
        JsonNode root;
        try {
            root = MAPPER.readTree(candidate);
        } catch (Exception e) {
            throw new AIParseException("ИИ вернул некорректный JSON", e);
        }
        if (root == null || !root.isObject()) {
            throw new AIParseException("Ответ ИИ не является JSON-объектом");
        }

        JsonNode improved = root.get("improved_text");
        if (improved == null || !improved.isTextual() || improved.asText().isBlank()) {
            throw new AIParseException("В ответе ИИ отсутствует непустое поле improved_text");
        }

        Map<RequisiteKey, String> requisites = parseRequisites(root.get("requisites"));
        return new AIResult(improved.asText().trim(), requisites);
    }

    private static Map<RequisiteKey, String> parseRequisites(JsonNode node) {
        Map<RequisiteKey, String> result = new EnumMap<>(RequisiteKey.class);
        if (node == null || !node.isObject()) {
            // Реквизиты необязательны в ответе — валидатор определит их как отсутствующие.
            return result;
        }
        for (RequisiteKey key : RequisiteKey.values()) {
            JsonNode value = node.get(key.name().toLowerCase());
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isTextual()) {
                String text = value.asText().trim();
                if (!text.isBlank() && !isNullMarker(text)) {
                    result.put(key, text);
                }
            } else if (value.isNumber()) {
                result.put(key, value.asText());
            }
            // Остальные типы (объекты, массивы) игнорируем — это невалидное значение.
        }
        return result;
    }

    /** Модели иногда пишут в поле строки-заглушки: "null", "не указано" и т.п. */
    private static boolean isNullMarker(String text) {
        String lower = text.toLowerCase().strip();
        return lower.equals("null") || lower.equals("none") || lower.equals("-")
                || lower.equals("не указано") || lower.equals("не указан") || lower.equals("н/д");
    }

    private static String extractJsonObject(String raw) {
        Matcher fenced = FENCE.matcher(raw);
        if (fenced.find()) {
            return fenced.group(1);
        }
        if (raw.startsWith("{")) {
            return raw;
        }
        // Модель могла добавить пояснительный текст вокруг JSON.
        Matcher obj = FIRST_OBJECT.matcher(raw);
        if (obj.find()) {
            return obj.group();
        }
        return raw;
    }
}
