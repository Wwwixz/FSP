package ru.docgen.requisites;

import ru.docgen.core.DocumentType;
import ru.docgen.core.RequisiteKey;

import java.util.List;

/**
 * Результат проверки реквизитов по выбранному типу документа.
 */
public record RequisitesValidation(
        List<RequisiteKey> missing,
        List<RequisiteCheck> checks
) {

    /**
     * Состояние одного реквизита для отображения во фронтенде.
     *
     * @param key  машинный ключ реквизита
     * @param label человекочитаемое название (с учётом типа документа)
     * @param status "done" | "missing"
     * @param hint  пояснение для незаполненного реквизита
     */
    public record RequisiteCheck(String key, String label, String status, String hint) {
        public static RequisiteCheck done(RequisiteKey key, String label) {
            return new RequisiteCheck(key.name().toLowerCase(), label, "done", null);
        }

        public static RequisiteCheck missing(RequisiteKey key, String label) {
            return new RequisiteCheck(key.name().toLowerCase(), label, "missing",
                    "Не указано в тексте — заполните или оставьте пометку в документе");
        }
    }

    public boolean hasMissing() {
        return !missing.isEmpty();
    }
}
