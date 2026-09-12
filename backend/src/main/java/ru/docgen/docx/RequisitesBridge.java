package ru.docgen.docx;

import ru.docgen.core.RequisiteKey;

/**
 * Мост для проверки заполненности значения без циклической зависимости
 * между модулями docx и requisites.
 */
public final class RequisitesBridge {

    private RequisitesBridge() {
    }

    public static boolean isFilled(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim().toLowerCase(java.util.Locale.ROOT);
        return !v.isEmpty() && !v.equals("null") && !v.equals("[заполнить]")
                && !v.equals(RequisiteKey.RECIPIENT.getPlaceholder().toLowerCase(java.util.Locale.ROOT))
                && !v.equals(RequisiteKey.AUTHOR.getPlaceholder().toLowerCase(java.util.Locale.ROOT))
                && !v.equals(RequisiteKey.SUBJECT.getPlaceholder().toLowerCase(java.util.Locale.ROOT))
                && !v.equals(RequisiteKey.SIGNATURE.getPlaceholder().toLowerCase(java.util.Locale.ROOT))
                && !v.equals(RequisiteKey.DATE.getPlaceholder().toLowerCase(java.util.Locale.ROOT))
                && !v.equals(RequisiteKey.NUMBER.getPlaceholder().toLowerCase(java.util.Locale.ROOT))
                && !v.equals("н/д");
    }
}
