package ru.docgen;

import org.junit.jupiter.api.Test;
import ru.docgen.ai.AIParseException;
import ru.docgen.ai.AIJsonParser;
import ru.docgen.ai.AIResult;
import ru.docgen.core.RequisiteKey;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Парсинг и валидация ответа ИИ. */
class AIJsonParserTest {

    @Test
    void parsesValidResponse() {
        String json = """
                {
                  "improved_text": "Прошу выделить средства.",
                  "requisites": {
                    "recipient": "Генеральному директору ООО «Ромашка» Иванову И.И.",
                    "author": null,
                    "date": "12.03.2025"
                  }
                }""";
        AIResult result = AIJsonParser.parse(json);
        assertEquals("Прошу выделить средства.", result.improvedText());
        assertEquals("Генеральному директору ООО «Ромашка» Иванову И.И.",
                result.requisites().get(RequisiteKey.RECIPIENT));
        assertEquals("12.03.2025", result.requisites().get(RequisiteKey.DATE));
        assertNull(result.requisites().get(RequisiteKey.AUTHOR));
    }

    @Test
    void stripsMarkdownFence() {
        String fenced = "```json\n{\"improved_text\": \"Текст.\", \"requisites\": {}}\n```";
        AIResult result = AIJsonParser.parse(fenced);
        assertEquals("Текст.", result.improvedText());
    }

    @Test
    void rejectsGarbage() {
        assertThrows(AIParseException.class, () -> AIJsonParser.parse("Извините, я не могу помочь"));
        assertThrows(AIParseException.class, () -> AIJsonParser.parse("{\"requisites\": {}}"));
        assertThrows(AIParseException.class, () -> AIJsonParser.parse(""));
        assertThrows(AIParseException.class, () -> AIJsonParser.parse("[1, 2, 3]"));
    }

    @Test
    void rejectsNonStringImprovedText() {
        assertThrows(AIParseException.class,
                () -> AIJsonParser.parse("{\"improved_text\": 42, \"requisites\": {}}"));
    }

    @Test
    void treatsPlaceholderStringsAsNull() {
        String json = """
                {"improved_text": "Текст.", "requisites": {"recipient": "не указано", "author": "null"}}""";
        AIResult result = AIJsonParser.parse(json);
        assertNull(result.requisites().get(RequisiteKey.RECIPIENT));
        assertNull(result.requisites().get(RequisiteKey.AUTHOR));
    }

    @Test
    void missingRequisitesNodeYieldsEmptyMap() {
        AIResult result = AIJsonParser.parse("{\"improved_text\": \"Текст.\"}");
        assertNotNull(result.requisites());
        assertTrue(result.requisites().isEmpty());
    }

    @Test
    void numericRequisiteAccepted() {
        AIResult result = AIJsonParser.parse(
                "{\"improved_text\": \"Текст.\", \"requisites\": {\"number\": 47}}");
        assertEquals("47", result.requisites().get(RequisiteKey.NUMBER));
    }

    @Test
    void extraTextAroundJsonTolerated() {
        String noisy = "Вот результат:\n{\"improved_text\": \"Текст.\", \"requisites\": {}}\nСпасибо.";
        assertEquals("Текст.", AIJsonParser.parse(noisy).improvedText());
    }
}
