package ru.docgen.docx;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.docgen.core.DocumentType;
import ru.docgen.core.RequisiteKey;
import ru.docgen.core.TemplateKind;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Экспорт документа в PDF (выбор «конверт в PDF» при скачивании).
 *
 * <p>Содержательная часть и реквизиты те же, что при генерации DOCX —
 * рендер выполняется программно по правилам шаблона (разделение
 * содержания и оформления из ТЗ сохраняется). PDFBox с встраиванием
 * системных TTF-шрифтов гарантирует корректную кириллицу.
 */
@Service
public class PdfExportService {

    private static final Logger log = LoggerFactory.getLogger(PdfExportService.class);

    private static final float PAGE_MARGIN_LEFT = 85f;
    private static final float PAGE_MARGIN_RIGHT = 42f;
    private static final float PAGE_MARGIN_TOP = 64f;
    private static final float PAGE_MARGIN_BOTTOM = 57f;

    /** Преобразует имя шрифта в список TTF-файлов Windows/Unix для загрузки. */
    private static final Map<String, List<String>> FONT_FILES = Map.of(
            "Times New Roman", List.of("times.ttf", "timesbd.ttf", "Times-New-Roman.ttf"),
            "Arial", List.of("arial.ttf", "arialbd.ttf"),
            "Calibri", List.of("calibri.ttf", "calibrib.ttf"));

    private final String defaultOrganization;

    public PdfExportService(@Value("${app.documents.default-organization:}") String defaultOrganization) {
        this.defaultOrganization = defaultOrganization;
    }

    public byte[] export(DocumentType type, TemplateKind template,
                         Map<RequisiteKey, String> requisites, String bodyText,
                         String signatureImage, String photoImage, String stampImage,
                         String qrDataUrl,
                         Map<String, String> templateOptions) {
        Map<String, String> opts = templateOptions == null ? Map.of() : templateOptions;
        String fontName = template == TemplateKind.MODERN ? "Arial"
                : template == TemplateKind.CUSTOM
                ? opts.getOrDefault("font", CustomLayoutBuilder.DEFAULT_FONT)
                : "Times New Roman";
        float fontSize = template == TemplateKind.MODERN ? 12f
                : template == TemplateKind.CUSTOM
                ? clamp(parseFloat(opts.get("fontSize"), CustomLayoutBuilder.DEFAULT_FONT_SIZE), 8, 28)
                : 14f;
        float lineSpacing = template == TemplateKind.MODERN ? 1.15f
                : template == TemplateKind.CUSTOM
                ? clamp(parseFloat(opts.get("lineSpacing"), (float) CustomLayoutBuilder.DEFAULT_LINE_SPACING), 1.0f, 3.0f)
                : 1.5f;

        try (PDDocument doc = new PDDocument()) {
            PDFont regular = loadFont(doc, fontName, false);
            PDFont bold = loadFont(doc, fontName, true);
            render(doc, type, template, opts, requisites, bodyText, signatureImage, photoImage, stampImage,
                    decodeDataUrl(qrDataUrl), regular, bold, fontSize, lineSpacing);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось сформировать PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Печатный почтовый конверт (E65, 220×110 мм) по реквизитам документа:
     * отправитель слева сверху, адресат справа снизу, место для марки слева
     * сверху пунктиром. Распечатать на A4 и согнуть, либо на готовом конверте.
     */
    public byte[] envelope(Map<RequisiteKey, String> requisites, String organization, String author) {
        DocumentValues values = new DocumentValues(null, requisites, "", null, null, null, null);
        float w = 623.62f; // 220 мм
        float h = 311.81f; // 110 мм
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(w, h));
            doc.addPage(page);
            PDFont font = loadFont(doc, "Times New Roman", false);
            PDFont bold = loadFont(doc, "Times New Roman", true);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float fontSize = 12f;

                // Место для марки (пунктирный прямоугольник, слева сверху)
                cs.setLineDashPattern(new float[]{4f, 3f}, 0f);
                cs.setLineWidth(0.8f);
                cs.addRect(40f, h - 88f, 128f, 56f);
                cs.stroke();
                cs.setLineDashPattern(new float[]{}, 0f);
                cs.beginText();
                cs.setFont(font, 7.5f);
                cs.newLineAtOffset(56f, h - 62f);
                cs.showText("Место для марки");
                cs.endText();

                // Отправитель — слева сверху под маркой
                float y = h - 108f;
                for (String line : new String[]{
                        organization != null && !organization.isBlank() ? organization : null,
                        author}) {
                    if (line == null || line.isBlank()) continue;
                    cs.beginText();
                    cs.setFont(font, 10f);
                    cs.newLineAtOffset(40f, y);
                    cs.showText(line);
                    cs.endText();
                    y -= 14f;
                }

                // Адресат — справа, ниже середины конверта
                float right = w - 40f;
                float ry = h * 0.48f;
                for (String line : values.recipientLines()) {
                    float lw = stringWidth(line, bold, fontSize);
                    cs.beginText();
                    cs.setFont(bold, fontSize);
                    cs.newLineAtOffset(right - lw, ry);
                    cs.showText(line);
                    cs.endText();
                    ry -= 17f;
                }
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось сформировать конверт: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // Рендер
    // ------------------------------------------------------------------

    private void render(PDDocument doc, DocumentType type, TemplateKind template, Map<String, String> opts,
                        Map<RequisiteKey, String> requisites,
                        String bodyText, String signatureImage, String photoImage, String stampImage,
                        byte[] qrImage,
                        PDFont regular, PDFont bold, float fontSize, float lineSpacing) throws IOException {
        boolean indentFirst = true;
        DocumentValues values = new DocumentValues(type, requisites, bodyText,
                signatureImage, photoImage, stampImage,
                qrImage == null ? null : "data:image/png;base64,"
                        + Base64.getEncoder().encodeToString(qrImage));
        PDPage page = newPage(doc);
        PDPageContentStream cs = new PDPageContentStream(doc, page);
        float width = page.getMediaBox().getWidth();
        float y = page.getMediaBox().getHeight() - PAGE_MARGIN_TOP;
        float lineHeight = fontSize * lineSpacing * 1.15f;

        Cursor cursor = new Cursor(cs, doc, page, width, y, lineHeight);

        // Шапка: организация (колонтитул-строка)
        String org = values.organizationOrNull() != null ? values.organizationOrNull() : defaultOrganization;
        if (org != null && !org.isBlank()) {
            cursor.newLine();
            cursor.text(org, regular, fontSize - 2f, "center", null);
            cursor.space(0.6f);
        }

        // Адресат справа
        cursor.newLine();
        for (String line : values.recipientLines()) {
            cursor.text(line, regular, fontSize, "right", null);
            cursor.newLine();
        }

        // Дата и номер
        if (type.getNumberSuffix().isEmpty()) {
            cursor.text("Дата: " + values.date(), regular, fontSize, "left", null);
        } else {
            cursor.text("Дата: " + values.date() + " Номер: " + values.number(), regular, fontSize, "left", null);
        }
        cursor.newLine();

        // Заголовочная часть
        if (type != DocumentType.LETTER) {
            cursor.text(type.getTitleLine(), bold, fontSize + 1f, "center", null);
            cursor.newLine();
        }
        cursor.text(values.subject(), bold, fontSize, "center", null);
        cursor.space(0.5f);

        // Обращение
        String salutation = values.salutationOrNull();
        if (salutation != null) {
            cursor.newLine();
            cursor.text(salutation, regular, fontSize, "left", null);
        }

        // Основной текст (выравнивание по ширине — как в DOCX)
        boolean justify = switch (template) {
            case MODERN -> false;
            case CUSTOM -> "justify".equalsIgnoreCase(opts.getOrDefault("align", "justify"));
            default -> true;
        };
        float indentPt = switch (template) {
            case CUSTOM -> clamp(parseFloat(opts.get("indent"), (float) CustomLayoutBuilder.DEFAULT_INDENT_CM), 0f, 3f) * 28.35f;
            default -> 35.4f; // 1,25 см
        };
        for (String block : values.bodyText().replace("\r\n", "\n").split("\\n\\s*\\n")) {
            String merged = block.replace("\n", " ").replaceAll("\\s{2,}", " ").trim();
            if (merged.isEmpty()) {
                continue;
            }
            cursor.newLine();
            List<String> wrapped = wrap(merged, regular, fontSize, width - PAGE_MARGIN_LEFT - PAGE_MARGIN_RIGHT - indentPt);
            for (int i = 0; i < wrapped.size(); i++) {
                boolean lastLine = i == wrapped.size() - 1;
                cursor.text(wrapped.get(i), regular, fontSize,
                        justify && !lastLine ? "justify" : "left",
                        indentFirst ? indentPt : 0f);
                indentFirst = false;
                cursor.newLine();
            }
            cursor.space(0.4f);
        }

        // Подпись + печать — блок справа, как в предпросмотре на сайте.
        // Подпись стоит вплотную к печати и выровнена по её центру.
        cursor.newLine();
        cursor.text(values.positionLine(), regular, fontSize, "right", null);
        cursor.newLine();
        byte[] sig = decodeDataUrl(signatureImage);
        if (sig != null) {
            sig = ImageUtils.trim(sig);
        }
        byte[] stamp = decodeDataUrl(stampImage);
        if (stamp != null) {
            stamp = ImageUtils.trim(stamp);
        }
        if (sig != null || stamp != null) {
            float sigW = 0, sigH = 0, stampW = 0, stampH = 0;
            PDImageXObject sigImg = null, stampImg = null;
            if (sig != null) {
                sigImg = PDImageXObject.createFromByteArray(doc, sig, "signature");
                sigW = 156f; // ~5,5 см
                sigH = sigW * sigImg.getHeight() / sigImg.getWidth();
                if (sigH > 80f) {
                    sigH = 80f;
                    sigW = sigH * sigImg.getWidth() / sigImg.getHeight();
                }
            }
            if (stamp != null) {
                stampImg = PDImageXObject.createFromByteArray(doc, stamp, "stamp");
                stampH = 119f; // ~4,2 см
                stampW = stampH * stampImg.getWidth() / stampImg.getHeight();
                if (stampW > 119f) {
                    stampW = 119f;
                    stampH = stampW * stampImg.getHeight() / stampImg.getWidth();
                }
            }
            float gap = sigImg != null && stampImg != null ? 6f : 0f;
            float rowWidth = sigW + gap + stampW;
            float x = width - PAGE_MARGIN_RIGHT - rowWidth;
            float rowHeight = Math.max(sigH, stampH);
            if (stampImg != null) {
                cursor.drawImage(stampImg, x + sigW + gap, stampH, stampW);
            }
            if (sigImg != null) {
                // подпись по центру высоты печати — как росчерк рядом с оттиском
                cursor.drawImageCentered(sigImg, x, rowHeight, sigW, sigH);
            }
            cursor.advance(rowHeight);
            cursor.newLine();
        }
        cursor.text(values.signatureNameLine(), regular, fontSize, "right", null);

        String executor = values.executorOrNull();
        if (executor != null) {
            cursor.newLine();
            cursor.text("Исполнитель: " + executor, regular, fontSize - 1f, "left", null);
        }

        // QR-код проверки подлинности: сканируешь — открывается документ
        if (qrImage != null) {
            cursor.newLine();
            cursor.space(0.4f);
            PDImageXObject img = PDImageXObject.createFromByteArray(doc, qrImage, "qr");
            cursor.drawImage(img, PAGE_MARGIN_LEFT, 70f, 70f);
            cursor.advance(76f);
            cursor.text("Проверка подлинности: наведите камеру телефона на QR-код —", regular, 8.5f, "left", null);
            cursor.newLine();
            cursor.text("документ откроется в браузере без установки программ.", regular, 8.5f, "left", null);
        }
        cursor.close();
    }

    private PDPage newPage(PDDocument doc) {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        return page;
    }

    // ------------------------------------------------------------------
    // Курсор по странице: перенос страниц, отрисовка строк и картинок
    // ------------------------------------------------------------------

    private final class Cursor {
        private final PDDocument doc;
        private PDPage page;
        private PDPageContentStream cs;
        private final float width;
        private float y;
        private final float lineHeight;

        private Cursor(PDPageContentStream cs, PDDocument doc, PDPage page,
                       float width, float y, float lineHeight) {
            this.cs = cs;
            this.doc = doc;
            this.page = page;
            this.width = width;
            this.y = y;
            this.lineHeight = lineHeight;
        }

        void newLine() throws IOException {
            y -= lineHeight;
            ensureSpace();
        }

        void space(float factor) {
            y -= lineHeight * factor;
        }

        void advance(float delta) {
            y -= delta;
        }

        void close() throws IOException {
            cs.close();
        }

        void drawImage(PDImageXObject img, float x, float height, float width) throws IOException {
            ensureSpace(height);
            cs.drawImage(img, x, y - height, width, height);
        }

        /** Отрисовка с явной верхней координатой строки (для центрирования подписи у печати). */
        void drawImageAt(PDImageXObject img, float x, float yTop, float width, float height) throws IOException {
            cs.drawImage(img, x, yTop - height, width, height);
        }

        /** Картинка, центрированная по высоте строки rowHeight (текущая позиция курсора). */
        void drawImageCentered(PDImageXObject img, float x, float rowHeight, float width, float height) throws IOException {
            float offset = (rowHeight - height) / 2f;
            cs.drawImage(img, x, y - offset - height, width, height);
        }

        void text(String text, PDFont font, float size, String align, Float indent) throws IOException {
            if (text == null || text.isBlank()) {
                return;
            }
            if ("justify".equals(align)) {
                justifyLine(text, font, size, indent == null ? 0f : indent);
                return;
            }
            ensureSpace();
            float w = stringWidth(text, font, size);
            float x = switch (align) {
                case "right" -> width - PAGE_MARGIN_RIGHT - w;
                case "center" -> (width - w) / 2;
                default -> PAGE_MARGIN_LEFT + (indent == null ? 0 : indent);
            };
            drawRun(text, font, size, x);
        }

        /** Выравнивание по ширине: слова растягиваются на всю строку, как в Word. */
        private void justifyLine(String text, PDFont font, float size, float indent) throws IOException {
            ensureSpace();
            String[] words = text.trim().split("\\s+");
            float x = PAGE_MARGIN_LEFT + indent;
            float maxW = width - PAGE_MARGIN_RIGHT - x;
            float spaceW = stringWidth(" ", font, size);
            if (words.length < 2) {
                drawRun(text, font, size, x);
                return;
            }
            float natural = 0;
            for (int i = 0; i < words.length; i++) {
                natural += stringWidth(words[i], font, size);
                if (i < words.length - 1) {
                    natural += spaceW;
                }
            }
            float gapExtra = Math.max(0f, (maxW - natural) / (words.length - 1));
            for (int i = 0; i < words.length; i++) {
                drawRun(words[i], font, size, x);
                x += stringWidth(words[i], font, size) + spaceW + gapExtra;
            }
        }

        private void drawRun(String text, PDFont font, float size, float x) throws IOException {
            cs.beginText();
            cs.setFont(font, size);
            cs.newLineAtOffset(x, y - size);
            cs.showText(text);
            cs.endText();
        }

        private void ensureSpace() throws IOException {
            ensureSpace(lineHeight);
        }

        private void ensureSpace(float needed) throws IOException {
            if (y - needed >= PAGE_MARGIN_BOTTOM) {
                return;
            }
            cs.close();
            page = newPage(doc);
            cs = new PDPageContentStream(doc, page);
            y = page.getMediaBox().getHeight() - PAGE_MARGIN_TOP;
        }
    }

    private static float stringWidth(String text, PDFont font, float size) throws IOException {
        try {
            return font.getStringWidth(text) / 1000f * size;
        } catch (IllegalArgumentException e) {
            // Символ вне шрифта — считаем по средней ширине, чтобы не падать
            return text.length() * size * 0.6f;
        }
    }

    private static List<String> wrap(String text, PDFont font, float size, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n")) {
            StringBuilder current = new StringBuilder();
            for (String word : paragraph.split("\\s+")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (stringWidth(candidate, font, size) > maxWidth && !current.isEmpty()) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    current = new StringBuilder(candidate);
                }
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
        }
        return lines;
    }

    // ------------------------------------------------------------------
    // Шрифты и картинки
    // ------------------------------------------------------------------

    private PDFont loadFont(PDDocument doc, String family, boolean bold) throws IOException {
        List<String> candidates = FONT_FILES.getOrDefault(family, FONT_FILES.get("Times New Roman"));
        List<String> dirs = List.of("C:\\Windows\\Fonts\\", "/usr/share/fonts/truetype/msttcorefonts/",
                "/usr/share/fonts/TTF/", "/Library/Fonts/");
        String primary = bold ? candidates.get(1) : candidates.get(0);
        List<String> order = new ArrayList<>();
        order.add(primary);
        order.addAll(candidates);
        for (String dir : dirs) {
            for (String file : order) {
                File f = new File(dir + file);
                if (f.isFile()) {
                    try (java.io.InputStream in = new java.io.FileInputStream(f)) {
                        return PDType0Font.load(doc, in, true);
                    }
                }
            }
        }
        log.warn("TTF-шрифт {} не найден — используется Helvetica (кириллица может не отобразиться)", family);
        return new org.apache.pdfbox.pdmodel.font.PDType1Font(
                org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA);
    }

    /** Декодирует base64-dataURL (png/jpeg) в байты; null при ошибке/отсутствии. */
    static byte[] decodeDataUrl(String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank()) {
            return null;
        }
        try {
            String cleaned = dataUrl.replaceAll("\\s+", "");
            int comma = cleaned.indexOf(',');
            if (!cleaned.startsWith("data:image/") || comma < 0) {
                return null;
            }
            String b64 = cleaned.substring(comma + 1);
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(b64);
            } catch (IllegalArgumentException e) {
                bytes = Base64.getUrlDecoder().decode(b64);
            }
            return bytes.length < 32 ? null : bytes;
        } catch (Exception e) {
            log.warn("Не удалось декодировать изображение: {}", e.getMessage());
            return null;
        }
    }

    private static float parseFloat(String raw, float def) {
        if (raw == null || raw.isBlank()) {
            return def;
        }
        try {
            return Float.parseFloat(raw.replace(',', '.'));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
