package ru.docgen.docx;

import org.apache.poi.xwpf.usermodel.LineSpacingRule;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.docgen.core.DocumentType;

import java.math.BigInteger;
import java.util.Map;

/**
 * Шаблон C «Свой шаблон»: правила оформления задаёт пользователь.
 *
 * <p>Возможность добавлять шаблоны без переписывания основной логики —
 * желательное требование ТЗ (раздел 1.6). Параметры приходят из
 * {@code GenerateRequest.templateOptions}; не указанные заменяются
 * значениями по умолчанию (Times New Roman 14 pt, интервал 1,5,
 * отступ 1,25 см, по ширине).
 *
 * <p>Поддерживаемые ключи: {@code font}, {@code fontSize}, {@code lineSpacing},
 * {@code indent} (см), {@code align} (justify|left|right|center),
 * {@code header}, {@code headerAlign} (left|center|right), {@code footer}.
 *
 * <p>Базовый документ строится программно ({@link #createBaseDocument}):
 * А4, поля, колонтитулы и стиль Normal с выбранным шрифтом — благодаря этому
 * и текст, и блок подписи, и колонтитулы оформлены единообразно.
 */
public class CustomLayoutBuilder extends AbstractLayoutBuilder {

    private static final Logger log = LoggerFactory.getLogger(CustomLayoutBuilder.class);

    static final String DEFAULT_FONT = "Times New Roman";
    static final int DEFAULT_FONT_SIZE = 14;
    static final double DEFAULT_LINE_SPACING = 1.5;
    static final double DEFAULT_INDENT_CM = 1.25;

    private final String fontName;
    private final int fontSize;
    private final double lineSpacing;
    private final double indentCm;
    private final ParagraphAlignment bodyAlignment;

    public CustomLayoutBuilder(XWPFDocument document, DocumentType type,
                               DocumentValues values, StyleResolver styles,
                               Map<String, String> options) {
        super(document, type, values, styles);
        Map<String, String> opts = options == null ? Map.of() : options;
        this.fontName = opt(opts, "font", DEFAULT_FONT);
        this.fontSize = (int) clamp(parseInt(opt(opts, "fontSize", String.valueOf(DEFAULT_FONT_SIZE)), DEFAULT_FONT_SIZE), 8, 28);
        this.lineSpacing = clamp(parseDouble(opt(opts, "lineSpacing", String.valueOf(DEFAULT_LINE_SPACING)), DEFAULT_LINE_SPACING), 1.0, 3.0);
        this.indentCm = clamp(parseDouble(opt(opts, "indent", String.valueOf(DEFAULT_INDENT_CM)), DEFAULT_INDENT_CM), 0.0, 3.0);
        this.bodyAlignment = parseAlignment(opt(opts, "align", "justify"));
    }

    // ------------------------------------------------------------------
    // Базовый документ под «свой шаблон»
    // ------------------------------------------------------------------

    /**
     * Создаёт пустой документ с настройками страницы (А4, поля) и
     * колонтитулами по опциям. Верхний колонтитул по умолчанию —
     * «[Название организации]», нижний — «[Название документа] — [Дата]»
     * (плейсхолдеры подставляются генератором как в обычных шаблонах).
     */
    public static XWPFDocument createBaseDocument(Map<String, String> options) {
        Map<String, String> opts = options == null ? Map.of() : options;
        XWPFDocument doc = new XWPFDocument();

        // Страница: А4 (11906 x 16838 twips), поля: левое 3 см, правое 1,5 см, верх/низ 2 см
        CTSectPr sectPr = doc.getDocument().getBody().addNewSectPr();
        CTPageSz size = sectPr.addNewPgSz();
        size.setW(BigInteger.valueOf(11906));
        size.setH(BigInteger.valueOf(16838));
        CTPageMar mar = sectPr.addNewPgMar();
        mar.setLeft(BigInteger.valueOf(1701));
        mar.setRight(BigInteger.valueOf(851));
        mar.setTop(BigInteger.valueOf(1134));
        mar.setBottom(BigInteger.valueOf(1134));
        mar.setHeader(BigInteger.valueOf(708));
        mar.setFooter(BigInteger.valueOf(708));

        // Стиль Normal: шрифт, размер, интервал — наследуются всеми абзацами и колонтитулами
        applyNormalStyle(doc,
                opt(opts, "font", DEFAULT_FONT),
                (int) clamp(parseInt(opt(opts, "fontSize", String.valueOf(DEFAULT_FONT_SIZE)), DEFAULT_FONT_SIZE), 8, 28),
                clamp(parseDouble(opt(opts, "lineSpacing", String.valueOf(DEFAULT_LINE_SPACING)), DEFAULT_LINE_SPACING), 1.0, 3.0));

        // Верхний колонтитул
        String headerText = opt(opts, "header", "[Название организации]");
        if (!headerText.isBlank()) {
            XWPFParagraph p = doc.getHeaderList().isEmpty()
                    ? doc.createHeader(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT).createParagraph()
                    : doc.getHeaderList().get(0).createParagraph();
            p.setAlignment(parseAlignment(opt(opts, "headerAlign", "center")));
            p.createRun().setText(headerText);
        }
        // Нижний колонтитул
        String footerText = opt(opts, "footer", "[Название документа] — [Дата]");
        if (!footerText.isBlank()) {
            XWPFParagraph p = doc.getFooterList().isEmpty()
                    ? doc.createFooter(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT).createParagraph()
                    : doc.getFooterList().get(0).createParagraph();
            p.setAlignment(ParagraphAlignment.CENTER);
            p.createRun().setText(footerText);
        }
        return doc;
    }

    /** Стиль Normal с выбранным шрифтом/размером/интервалом. */
    private static void applyNormalStyle(XWPFDocument doc, String fontName, int fontSize, double lineSpacing) {
        try {
            CTStyle style = CTStyle.Factory.newInstance();
            style.setStyleId("Normal");
            style.setType(STStyleType.PARAGRAPH);
            style.addNewName().setVal("Normal");
            style.addNewQFormat();
            CTFonts fonts = style.addNewRPr().addNewRFonts();
            fonts.setAscii(fontName);
            fonts.setHAnsi(fontName);
            fonts.setCs(fontName);
            fonts.setEastAsia(fontName);
            style.getRPr().addNewSz().setVal(BigInteger.valueOf((long) fontSize * 2));
            style.getRPr().addNewSzCs().setVal(BigInteger.valueOf((long) fontSize * 2));
            CTSpacing spacing = style.addNewPPr().addNewSpacing();
            spacing.setAfter(BigInteger.ZERO);
            spacing.setLine(BigInteger.valueOf((long) (lineSpacing * 240)));
            spacing.setLineRule(org.openxmlformats.schemas.wordprocessingml.x2006.main.STLineSpacingRule.AUTO);
            doc.createStyles().addStyle(new org.apache.poi.xwpf.usermodel.XWPFStyle(style));
        } catch (Exception e) {
            // Без стиля Normal документ останется читаемым — шрифт по умолчанию Word
            log.warn("Не удалось задать стиль Normal для своего шаблона: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Тело документа
    // ------------------------------------------------------------------

    @Override
    public void build() {
        // 1. Адресат справа (фото — над адресатом)
        for (String line : values.recipientLines()) {
            XWPFParagraph p = styledParagraph(ParagraphAlignment.RIGHT, false);
            setText(p, line);
        }

        // 2. Дата и номер
        if (type.getNumberSuffix().isEmpty()) {
            setText(styledParagraph(ParagraphAlignment.LEFT, false), "Дата: " + values.date());
        } else {
            setText(styledParagraph(ParagraphAlignment.LEFT, false),
                    "Дата: " + values.date() + " Номер: " + values.number());
        }

        // 3. Заголовочная часть
        if (type != DocumentType.LETTER) {
            setText(styledParagraph(ParagraphAlignment.CENTER, false), type.getTitleLine());
            setText(styledParagraph(ParagraphAlignment.CENTER, false), "");
        }
        setText(styledParagraph(ParagraphAlignment.CENTER, false), values.subject());

        // 4. Обращение (письмо, опционально)
        String salutation = values.salutationOrNull();
        if (salutation != null) {
            setText(styledParagraph(bodyAlignment, false), salutation);
        }

        // 5. Основной текст — с абзацным отступом
        for (String block : values.bodyText().replace("\r\n", "\n").split("\\n\\s*\\n")) {
            String merged = block.replace("\n", " ").replaceAll("\\s{2,}", " ").trim();
            if (!merged.isEmpty()) {
                setText(styledParagraph(bodyAlignment, true), merged);
            }
        }

        // 6. Подпись + печать (шрифт наследуется из Normal)
        String composerPrefix = type == DocumentType.CERTIFICATE ? "Составитель:" : null;
        signatureBlock(null, type == DocumentType.LETTER, composerPrefix);

        // 7. Исполнитель (опционально)
        String executor = values.executorOrNull();
        if (executor != null) {
            setText(styledParagraph(ParagraphAlignment.LEFT, false), "Исполнитель: " + executor);
        }
    }

    /** Абзац с настроенными интервалом и (опционально) абзацным отступом. */
    private XWPFParagraph styledParagraph(ParagraphAlignment alignment, boolean withIndent) {
        XWPFParagraph p = document.createParagraph();
        p.setAlignment(alignment);
        p.setSpacingBetween(lineSpacing, LineSpacingRule.AUTO);
        p.setSpacingAfter(0);
        if (withIndent && indentCm > 0) {
            p.setFirstLineIndent((int) (indentCm * 567)); // 1 см = 567 twips
        }
        return p;
    }

    /** Добавляет текст в абзац; шрифт задан стилем Normal, но продублируем в run. */
    private void setText(XWPFParagraph p, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setFontFamily(fontName);
        run.setFontSize(fontSize);
    }

    // ------------------------------------------------------------------
    // Разбор опций
    // ------------------------------------------------------------------

    private static String opt(Map<String, String> opts, String key, String def) {
        String value = opts.get(key);
        return value == null || value.isBlank() ? def : value.trim();
    }

    private static int parseInt(String raw, int def) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static double parseDouble(String raw, double def) {
        try {
            return Double.parseDouble(raw.replace(',', '.'));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static ParagraphAlignment parseAlignment(String raw) {
        return switch (raw.toLowerCase(java.util.Locale.ROOT)) {
            case "left" -> ParagraphAlignment.LEFT;
            case "right" -> ParagraphAlignment.RIGHT;
            case "center" -> ParagraphAlignment.CENTER;
            default -> ParagraphAlignment.BOTH;
        };
    }
}
