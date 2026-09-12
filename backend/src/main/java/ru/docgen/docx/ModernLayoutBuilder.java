package ru.docgen.docx;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import ru.docgen.core.DocumentType;
import ru.docgen.core.TemplateKind;

import java.util.List;

/**
 * Шаблон B «Современный регламентный».
 *
 * Расположение реквизитов: тема сверху слева («Заголовок1»), блок
 * «Кому / От кого» — таблицей («Адресат»), основной текст («Текст1»),
 * подпись по центру («Подпись1»). Нижний колонтитул: название документа и дата.
 */
public class ModernLayoutBuilder extends AbstractLayoutBuilder {

    public ModernLayoutBuilder(XWPFDocument document, DocumentType type,
                               DocumentValues values, StyleResolver styles) {
        super(document, type, values, styles);
    }

    @Override
    public void build() {
        String address = styles.addressStyle();
        String signature = styles.signatureStyle();
        String heading = styles.headingStyle(TemplateKind.MODERN);
        String text = styles.textStyle();

        // 1. Тема сверху слева + фото справа (если есть)
        XWPFParagraph titleP1 = null;
        if (type != DocumentType.LETTER) {
            titleP1 = paragraph(heading, type.getTitleLine());
        }
        XWPFParagraph titleP2 = paragraph(heading, values.subject());
        // Вставляем фото справа как отдельный абзац с выравниванием вправо (до таблицы)
        XWPFParagraph photoParagraph = document.createParagraph();
        if (heading != null) photoParagraph.setStyle(heading);
        photoParagraph.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        boolean hadPhoto = appendPhotoImage(photoParagraph);
        if (!hadPhoto) {
            int pos = document.getPosOfParagraph(photoParagraph);
            if (pos >= 0) document.removeBodyElement(pos);
        }

        // 2. Блок «Кому / От кого» — таблица без видимых границ
        addHeaderTable(address);

        // 3. Обращение (письмо, опционально)
        String salutation = values.salutationOrNull();
        if (salutation != null) {
            paragraph(text, salutation);
        }

        // 4. Основной текст
        bodyParagraphs(text, values.bodyText());

        // 5. Подпись по центру
        signatureBlock(signature, type == DocumentType.LETTER, null);

        // 6. Исполнитель (опционально)
        String executor = values.executorOrNull();
        if (executor != null) {
            paragraph(signature, "Исполнитель: " + executor);
        }
    }

    private void addHeaderTable(String addressStyle) {
        boolean recipientVisible = type != DocumentType.CERTIFICATE
                || values.recipientLines().size() != 1
                || !values.recipientLines().get(0).startsWith("[");

        boolean authorVisible = RequisitesBridge.isFilled(values.authorLine())
                || !values.authorLine().startsWith("[");

        boolean needRecipient = switch (type) {
            case MEMO, REPORT, LETTER -> true;
            case CERTIFICATE -> recipientVisible;
        };
        boolean needAuthor = switch (type) {
            case MEMO, REPORT, LETTER -> true;
            case CERTIFICATE -> authorVisible;
        };
        if (!needRecipient && !needAuthor) {
            return;
        }

        int rows = (needRecipient ? 1 : 0) + (needAuthor ? 1 : 0);
        XWPFTable table = document.createTable(rows, 2);
        table.setWidth("100%");
        setBordersNone(table);

        int row = 0;
        if (needRecipient) {
            String label = type == DocumentType.CERTIFICATE ? "Кому:" : "Кому:";
            fillRow(table.getRow(row++), addressStyle, label, joinLines(values.recipientLines()));
        }
        if (needAuthor) {
            String label = type == DocumentType.CERTIFICATE ? "Составитель:" : "От кого:";
            fillRow(table.getRow(row), addressStyle, label, values.authorLine());
        }
    }

    private void fillRow(XWPFTableRow row, String style, String label, String value) {
        setCellText(row.getCell(0), style, label);
        setCellText(row.getCell(1), style, value);
    }

    private void setCellText(XWPFTableCell cell, String style, String value) {
        List<String> lines = value.lines().map(String::trim).filter(s -> !s.isEmpty()).toList();
        cell.removeParagraph(0);
        if (lines.isEmpty()) {
            XWPFParagraph p = cell.addParagraph();
            p.setStyle(style);
            p.createRun();
            return;
        }
        for (String line : lines) {
            XWPFParagraph p = cell.addParagraph();
            p.setStyle(style);
            p.createRun().setText(line);
        }
    }

    private void setBordersNone(XWPFTable table) {
        CTTblBorders borders = table.getCTTbl().getTblPr().isSetTblBorders()
                ? table.getCTTbl().getTblPr().getTblBorders()
                : table.getCTTbl().getTblPr().addNewTblBorders();
        borders.addNewTop().setVal(STBorder.NONE);
        borders.addNewLeft().setVal(STBorder.NONE);
        borders.addNewBottom().setVal(STBorder.NONE);
        borders.addNewRight().setVal(STBorder.NONE);
        borders.addNewInsideH().setVal(STBorder.NONE);
        borders.addNewInsideV().setVal(STBorder.NONE);
    }

    private String joinLines(List<String> lines) {
        return String.join("\n", lines);
    }
}
