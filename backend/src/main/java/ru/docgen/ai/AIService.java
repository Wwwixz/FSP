package ru.docgen.ai;

import ru.docgen.core.DocumentType;

/**
 * Контракт модуля ИИ-обработки текста.
 *
 * Реализации:
 * <ul>
 *   <li>{@link MockAIProvider} — детерминированный офлайн-провайдер (демо и тесты);</li>
 *   <li>{@link OpenAICompatibleProvider} — любой OpenAI-совместимый API.</li>
 * </ul>
 */
public interface AIService {

    /**
     * Обрабатывает черновой текст: исправляет ошибки, приводит к
     * официально-деловому стилю, структурирует и выписывает реквизиты.
     *
     * @param text         исходный черновик пользователя
     * @param documentType выбранный тип документа (влияет на структурирование)
     * @return структурированный результат; отсутствующие в тексте сведения
     *         возвращаются как null — придумывать их запрещено
     * @throws AIUnavailableException сервис недоступен
     * @throws AIParseException       ответ не удалось разобрать
     */
    AIResult process(String text, DocumentType documentType);

    /**
     * Дорабатывает уже улучшенный текст по инструкции пользователя
     * («сделай короче», «более официально», «добавь вежливости» и т.п.).
     * Реализации должны сохранять все факты и ничего не выдумывать.
     *
     * @throws AIUnavailableException сервис недоступен
     */
    default String refine(String text, String instruction) {
        throw new AIUnavailableException("Доработка текста недоступна у текущего ИИ-провайдера");
    }
}
