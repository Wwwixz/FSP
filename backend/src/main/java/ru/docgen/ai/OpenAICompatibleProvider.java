package ru.docgen.ai;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import ru.docgen.core.DocumentType;

import java.util.List;
import java.util.Map;

/**
 * Провайдер любого OpenAI-совместимого API (OpenAI, vLLM, Ollama,
 * YandexGPT/NGC-шлюзы и т.п.), активируется настройкой:
 *
 * <pre>
 * AI_PROVIDER=openai
 * AI_API_KEY=sk-...
 * AI_BASE_URL=https://api.openai.com/v1   # или адрес локальной модели
 * AI_MODEL=gpt-4o-mini
 * </pre>
 *
 * От модели требуется строго JSON-ответ установленной структуры
 * (см. {@link #systemPrompt()}); ответ дополнительно валидируется
 * {@link AIJsonParser}, поэтому некорректный ответ не сломает приложение.
 */
public class OpenAICompatibleProvider implements AIService {

    private final AIProperties properties;
    private final RestClient restClient;

    public OpenAICompatibleProvider(AIProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public AIResult process(String text, DocumentType documentType) {
        if (properties.isSimulateFailure()) {
            throw new AIUnavailableException("Симулированная недоступность ИИ (AI_SIMULATE_FAILURE=true)");
        }

        Map<String, Object> requestBody = buildRequest(text, documentType);
        String content;
        try {
            Map<String, Object> response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);
            content = extractContent(response);
        } catch (AIParseException e) {
            throw e;
        } catch (AIException e) {
            throw e;
        } catch (Exception e) {
            throw new AIUnavailableException("Не удалось обратиться к ИИ-сервису: " + e.getMessage(), e);
        }

        return AIJsonParser.parse(content);
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> response) {
        if (response == null) {
            throw new AIUnavailableException("ИИ-сервис вернул пустой ответ");
        }
        Object error = response.get("error");
        if (error instanceof Map<?, ?> errorMap && errorMap.get("message") != null) {
            throw new AIUnavailableException("ИИ-сервис вернул ошибку: " + errorMap.get("message"));
        }
        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new AIParseException("Ответ ИИ не содержит choices");
            }
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            Object content = message == null ? null : message.get("content");
            if (!(content instanceof String s) || s.isBlank()) {
                throw new AIParseException("Ответ ИИ не содержит текста");
            }
            return s;
        } catch (ClassCastException e) {
            throw new AIParseException("Неожиданная структура ответа ИИ", e);
        }
    }

    private Map<String, Object> buildRequest(String text, DocumentType documentType) {
        return Map.of(
                "model", properties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt()),
                        Map.of("role", "user", "content", userPrompt(text, documentType))),
                "temperature", 0.1,
                "response_format", Map.of("type", "json_object"));
    }

    /**
     * Системный промпт: правила обработки и строгая JSON-схема.
     * Документируется в README (требование ТЗ — описание логики работы с ИИ).
     */
    public static String systemPrompt() {
        return """
                Ты — редактор служебных документов российской организации. Твоя задача — подготовить \
                черновик пользователя к оформлению по ГОСТ-подобным правилам делопроизводства.

                ПРАВИЛА (нарушать запрещено):

                ГРАМОТНОСТЬ:
                1. Исправь все орфографические ошибки: падежи, окончания, «тся/ться», «не/ни», \
                «так же/также», «что бы/чтобы», «в течение/в течении» и т.п.
                2. Расставь знаки препинания: точки, запятые при обособлениях и вводных словах, \
                двоеточия/тире, кавычки, точку в конце предложения.
                3. Устрани грамматические ошибки: согласование, управление, род/число, повтор слов.

                ОФИЦИАЛЬНО-ДЕЛОВОЙ СТИЛЬ:
                4. Перепиши текст строгим деловым языком: канцелярская точность, нейтральная \
                лексика, пассивно-безличные конструкции («просим», «довожу до сведения», \
                «в соответствии с»).
                5. Убери просторечия, разговорные и жаргонные обороты, лишние эмоции, оценку.
                6. Приведи структуру в соответствие с типом документа (кому → от кого → суть → подпись).

                ДОСТОВЕРНОСТЬ:
                7. ЗАПРЕЩЕНО добавлять факты, даты, фамилии, номера, суммы и иные сведения, которых \
                нет в исходном тексте. Ничего не выдумывай и не угадывай.
                8. ЗАПРЕЩЕНО искажать смысл: все конкретные даты, суммы, фамилии, номера, адреса, \
                e-mail и условия из исходного текста должны остаться.

                ФОРМАТ ОТВЕТА:
                9. В improved_text включай ТОЛЬКО содержательный текст документа (без служебных строк \
                «Кому:», «От кого:», «Дата:», «Номер:», «Заголовок:», «Тема:», «Подпись:», \
                «Составитель:», «Исполнитель:», без обращения «Уважаемый …!») — абзацы разделяй переводом строки.
                10. Реквизиты выписывай ТОЛЬКО если они явно есть в тексте. Если реквизита нет — верни null.
                11. Дату документа нормализуй к формату ДД.ММ.ГГГГ. Обращение (если есть) — строкой \
                вида «Уважаемый Имя Отчество!». Подпись — «И.О. Фамилия».

                Ответ верни ИСКЛЮЧИТЕЛЬНО в виде JSON по схеме:
                {
                  "improved_text": "исправленный текст документа",
                  "requisites": {
                    "recipient": "адресат: должность, организация, ФИО — или null",
                    "author": "автор/отправитель/составитель: должность, ФИО — или null",
                    "subject": "заголовок/тема — или null",
                    "date": "дата документа ДД.ММ.ГГГГ — или null",
                    "number": "номер документа — или null",
                    "signature": "И.О. Фамилия подписанта — или null",
                    "salutation": "обращение (для письма) — или null",
                    "executor": "исполнитель: ФИО, телефон — или null",
                    "organization": "организация отправителя — или null"
                  }
                }""";
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
}
