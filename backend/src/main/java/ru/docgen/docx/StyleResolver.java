package ru.docgen.docx;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import ru.docgen.core.TemplateKind;

/**
 * Находит внутренние идентификаторы стилей по их именам в styles.xml
 * конкретного шаблона. Идентификаторы генерируются Word («a», «11», «13»…),
 * поэтому привязка по именам надёжнее: «Адресат», «Текст1», «Подпись1»,
 * «Заголовок»/«Title» описаны в «Описании шаблонов оформления».
 */
public final class StyleResolver {

    private final XWPFDocument document;

    private StyleResolver(XWPFDocument document) {
        this.document = document;
    }

    public static StyleResolver of(XWPFDocument document) {
        return new StyleResolver(document);
    }

    public String textStyle() {
        return resolve("Текст1");
    }

    public String addressStyle() {
        return resolve("Адресат");
    }

    public String signatureStyle() {
        return resolve("Подпись1");
    }

    /** Стиль заголовка: в шаблоне A это «Заголовок»/«Title», в шаблоне B — «Заголовок1». */
    public String headingStyle(TemplateKind kind) {
        if (kind == TemplateKind.STANDARD) {
            return resolve("Заголовок", "Title");
        }
        return resolve("Заголовок1", "Заголовок", "Title");
    }

    private String resolve(String... candidates) {
        var styles = document.getStyles();
        if (styles == null) {
            return null;
        }
        for (String name : candidates) {
            XWPFStyle style = styles.getStyleWithName(name);
            if (style != null) {
                return style.getStyleId();
            }
        }
        return null;
    }
}
