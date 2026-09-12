package ru.docgen.ai;

import ru.docgen.core.RequisiteKey;

import java.util.Map;

/**
 * Структурированный результат работы ИИ.
 *
 * ИИ возвращает только содержание (улучшенный текст и выписанные из него
 * реквизиты). Какие реквизиты обязательны и какие отсутствуют — решает
 * модуль валидации, а не модель. Это защищает от «галлюцинаций»:
 * отсутствующий реквизит остаётся null.
 */
public record AIResult(
        String improvedText,
        Map<RequisiteKey, String> requisites
) {
}
