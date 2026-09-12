package ru.docgen.core;

import java.util.List;
import java.util.Map;

/**
 * Четыре обязательных типа документов из постановки задачи.
 *
 * Тип определяет содержательную структуру и набор обязательных реквизитов.
 * Шаблон определяет только оформление.
 */
public enum DocumentType {

    MEMO(
            "memo",
            "Служебная записка",
            "СЛУЖЕБНАЯ ЗАПИСКА",
            "СЗ",
            List.of(RequisiteKey.RECIPIENT, RequisiteKey.AUTHOR, RequisiteKey.DATE,
                    RequisiteKey.NUMBER, RequisiteKey.SUBJECT, RequisiteKey.SIGNATURE),
            List.of(RequisiteKey.EXECUTOR),
            Map.of(RequisiteKey.SUBJECT, "Заголовок")
    ),
    REPORT(
            "report",
            "Докладная записка",
            "ДОКЛАДНАЯ ЗАПИСКА",
            "ДЗ",
            List.of(RequisiteKey.RECIPIENT, RequisiteKey.AUTHOR, RequisiteKey.DATE,
                    RequisiteKey.NUMBER, RequisiteKey.SUBJECT, RequisiteKey.SIGNATURE),
            List.of(RequisiteKey.EXECUTOR),
            Map.of(RequisiteKey.SUBJECT, "Заголовок")
    ),
    CERTIFICATE(
            "certificate",
            "Информационная справка",
            "ИНФОРМАЦИОННАЯ СПРАВКА",
            "",
            // У справки нет исходящего номера (см. перечень реквизитов и эталонный пример)
            List.of(RequisiteKey.AUTHOR, RequisiteKey.DATE, RequisiteKey.SUBJECT, RequisiteKey.SIGNATURE),
            List.of(RequisiteKey.RECIPIENT, RequisiteKey.EXECUTOR),
            Map.of(RequisiteKey.SUBJECT, "Заголовок", RequisiteKey.AUTHOR, "Составитель")
    ),
    LETTER(
            "letter",
            "Письмо",
            "ПИСЬМО",
            "П",
            List.of(RequisiteKey.RECIPIENT, RequisiteKey.AUTHOR, RequisiteKey.DATE,
                    RequisiteKey.NUMBER, RequisiteKey.SUBJECT, RequisiteKey.SIGNATURE),
            List.of(RequisiteKey.SALUTATION, RequisiteKey.EXECUTOR),
            Map.of(RequisiteKey.SUBJECT, "Тема", RequisiteKey.AUTHOR, "Отправитель")
    );

    private final String id;
    private final String label;
    /** Название типа, выводимое в шапке документа (для письма не выводится). */
    private final String titleLine;
    /** Суффикс исходящего номера: 47-СЗ, 12-ДЗ, 88-П. */
    private final String numberSuffix;
    private final List<RequisiteKey> requiredRequisites;
    private final List<RequisiteKey> optionalRequisites;
    /** Уточнённые подписи реквизитов для конкретного типа документа. */
    private final Map<RequisiteKey, String> labelOverrides;

    DocumentType(String id, String label, String titleLine, String numberSuffix,
                 List<RequisiteKey> requiredRequisites, List<RequisiteKey> optionalRequisites,
                 Map<RequisiteKey, String> labelOverrides) {
        this.id = id;
        this.label = label;
        this.titleLine = titleLine;
        this.numberSuffix = numberSuffix;
        this.requiredRequisites = requiredRequisites;
        this.optionalRequisites = optionalRequisites;
        this.labelOverrides = labelOverrides;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getTitleLine() {
        return titleLine;
    }

    public String getNumberSuffix() {
        return numberSuffix;
    }

    public List<RequisiteKey> getRequiredRequisites() {
        return requiredRequisites;
    }

    public List<RequisiteKey> getOptionalRequisites() {
        return optionalRequisites;
    }

    public String labelOf(RequisiteKey key) {
        return labelOverrides.getOrDefault(key, key.getLabel());
    }

    public static DocumentType fromId(String id) {
        for (DocumentType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Неизвестный тип документа: " + id);
    }
}
