package ru.docgen.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Общие текстовые паттерны для реквизитов (используются ИИ-модулем
 * и построителем документа).
 */
public final class TextPatterns {

    /** ФИО в формате «Фамилия И.О.» или «И.О. Фамилия». */
    public static final Pattern FIO = Pattern.compile(
            "([А-ЯЁ][а-яё-]+)\\s+([А-ЯЁ]\\.\\s?[А-ЯЁ]\\.)|((?:[А-ЯЁ]\\.\\s?){2}\\s?[А-ЯЁ][а-яё-]+)");

    /** «Фамилия + инициал» — рыхлый вариант для отсечения хвостов. */
    private static final Pattern SURNAME_INITIAL = Pattern.compile(
            "[А-ЯЁ][а-яё-]+\\s+[А-ЯЁ]\\.");

    private static final Pattern ORG = Pattern.compile(
            "(ООО|ОАО|ЗАО|АО|ПАО|ИП)\\s*[«\"]?([^»\"\\n,.;]{0,40})[»\"]?");

    private static final Pattern TRAILING_INITIALS = Pattern.compile("\\s+[А-ЯЁ]\\.([А-ЯЁ]\\.)?$");

    private TextPatterns() {
    }

    /**
     * Извлекает организацию вида «ООО «Ромашка»» из строки реквизита.
     * Название организации отсекается от ФИО адресата и инициалов.
     */
    public static String organization(String source) {
        if (source == null) {
            return null;
        }
        Matcher m = ORG.matcher(source);
        if (!m.find()) {
            return null;
        }
        String name = m.group(2).trim();
        Matcher surname = SURNAME_INITIAL.matcher(name);
        if (surname.find()) {
            name = name.substring(0, surname.start());
        }
        name = TRAILING_INITIALS.matcher(name).replaceAll("").trim();
        name = name.replaceAll("[\\s«»\"]+$", "").trim();
        if (name.isEmpty()) {
            return null;
        }
        return m.group(1) + " «" + name + "»";
    }
}
