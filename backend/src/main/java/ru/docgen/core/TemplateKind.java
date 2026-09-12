package ru.docgen.core;

/**
 * Два шаблона оформления из стартовых материалов
 * («Описание шаблонов оформления.docx»).
 *
 * Шаблон определяет только визуальное оформление: поля страницы, шрифты,
 * интервалы, колонтитулы, расположение реквизитов.
 */
public enum TemplateKind {

    STANDARD(
            "standard",
            "Классический корпоративный",
            "Times New Roman 14 pt, интервал 1,5, абзацный отступ 1,25 см, выравнивание "
                    + "по ширине, шапка справа, подпись слева, верхний колонтитул с названием организации.",
            "/templates/standard.docx"
    ),
    MODERN(
            "modern",
            "Современный регламентный",
            "Arial 12 pt, интервал 1,15, без абзацного отступа, табличная шапка "
                    + "«Кому / От кого», подпись по центру, нижний колонтитул с названием документа и датой.",
            "/templates/modern.docx"
    );

    private final String id;
    private final String title;
    private final String description;
    private final String resourcePath;

    TemplateKind(String id, String title, String description, String resourcePath) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.resourcePath = resourcePath;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public static TemplateKind fromId(String id) {
        for (TemplateKind kind : values()) {
            if (kind.id.equalsIgnoreCase(id)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Неизвестный шаблон: " + id);
    }
}
