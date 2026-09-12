package ru.docgen.docx;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import ru.docgen.core.DocumentType;
import ru.docgen.core.TemplateKind;

/**
 * Шаблон A «Классический корпоративный».
 *
 * Расположение реквизитов (по «Описанию шаблонов оформления» и эталонным
 * примерам): шапка справа (стиль «Адресат»), дата и номер сверху слева
 * («Подпись1»), заголовок по центру («Заголовок»), основной текст по ширине
 * («Текст1»), подпись слева («Подпись1»).
 */
public class StandardLayoutBuilder extends AbstractLayoutBuilder {

    public StandardLayoutBuilder(XWPFDocument document, DocumentType type,
                                 DocumentValues values, StyleResolver styles) {
        super(document, type, values, styles);
    }

    @Override
    public void build() {
        String address = styles.addressStyle();
        String signature = styles.signatureStyle();
        String heading = styles.headingStyle(TemplateKind.STANDARD);
        String text = styles.textStyle();

        // 1. Фото автора (опционально, справа в шапке) + адресат справа
        boolean recipientFilled = values.recipientLines().size() != 1
                || !values.recipientLines().get(0).startsWith("[");
        if (type != DocumentType.CERTIFICATE || recipientFilled) {
            // Блок справа: если есть фото — вставляем его над адресатом, выравнивание вправо
            XWPFParagraph photoParagraph = document.createParagraph();
            if (address != null) photoParagraph.setStyle(address);
            photoParagraph.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
            boolean hadPhoto = appendPhotoImage(photoParagraph);
            if (!hadPhoto) {
                int pos = document.getPosOfParagraph(photoParagraph);
                if (pos >= 0) document.removeBodyElement(pos);
            }
            for (String line : values.recipientLines()) {
                XWPFParagraph p = paragraph(address, line);
                p.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
            }
        }

        // 2. Дата и номер
        if (type.getNumberSuffix().isEmpty()) {
            paragraph(signature, "Дата: " + values.date());
        } else {
            paragraph(signature, "Дата: " + values.date() + " Номер: " + values.number());
        }

        // 3. Заголовочная часть
        if (type != DocumentType.LETTER) {
            paragraph(heading, type.getTitleLine());
            paragraph(heading, "");
        }
        paragraph(heading, values.subject());

        // 4. Обращение (письмо, опционально)
        String salutation = values.salutationOrNull();
        if (salutation != null) {
            paragraph(text, salutation);
        }

        // 5. Основной текст
        bodyParagraphs(text, values.bodyText());

        // 6. Подпись слева
        String composerPrefix = type == DocumentType.CERTIFICATE ? "Составитель:" : null;
        signatureBlock(signature, type == DocumentType.LETTER, composerPrefix);

        // 7. Исполнитель (опционально)
        String executor = values.executorOrNull();
        if (executor != null) {
            paragraph(signature, "Исполнитель: " + executor);
        }
    }
}
