package ru.docgen.core;

/**
 * Ключи реквизитов служебных документов.
 *
 * Значения перечня обязательных реквизитов взяты из стартовых материалов
 * («ПЕРЕЧЕНЬ ОБЯЗАТЕЛЬНЫХ РЕКВИЗИТОВ ДЛЯ ТИПОВ ДОКУМЕНТОВ.docx»).
 *
 * Автозаполнение разрешено только для DATE (текущая дата формирования)
 * и NUMBER (порядковый номер). Все остальные реквизиты система не
 * придумывает: при отсутствии они помечаются как незаполненные.
 */
public enum RequisiteKey {

    /** Адресат (кому): должность, ФИО, организация. */
    RECIPIENT("Адресат", "[Адресат]", false),

    /**
     * Автор документа — «От кого» для записок, «Отправитель» для письма,
     * «Составитель» для справки. Формат: должность, ФИО.
     */
    AUTHOR("Автор (от кого)", "[Автор]", false),

    /** Заголовок документа / тема письма. */
    SUBJECT("Заголовок (тема)", "[Заголовок]", false),

    /** Дата документа. Разрешено автозаполнение текущей датой. */
    DATE("Дата", "[Дата]", true),

    /** Номер документа. Разрешено автозаполнение порядковым номером. */
    NUMBER("Номер", "[Номер]", true),

    /** Подписант: И.О. Фамилия. */
    SIGNATURE("Подпись", "[Подпись]", false),

    /** Обращение в письме (опционально): «Уважаемый …!». */
    SALUTATION("Обращение", "[Обращение]", false),

    /** Исполнитель (опционально): ФИО, телефон. */
    EXECUTOR("Исполнитель", "[Исполнитель]", false),

    /** Организация — используется в колонтитулах и подписи письма. */
    ORGANIZATION("Организация", "[Организация]", false);

    private final String label;
    private final String placeholder;
    private final boolean autoFillAllowed;

    RequisiteKey(String label, String placeholder, boolean autoFillAllowed) {
        this.label = label;
        this.placeholder = placeholder;
        this.autoFillAllowed = autoFillAllowed;
    }

    public String getLabel() {
        return label;
    }

    /** Обозначение незаполненного реквизита в итоговом документе. */
    public String getPlaceholder() {
        return placeholder;
    }

    public boolean isAutoFillAllowed() {
        return autoFillAllowed;
    }
}
