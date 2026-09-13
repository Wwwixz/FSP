package ru.docgen.docx;

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.docgen.core.DocumentType;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Общие примитивы построения тела документа.
 */
public abstract class AbstractLayoutBuilder {

    private static final Logger log = LoggerFactory.getLogger(AbstractLayoutBuilder.class);
    private static final Pattern DATA_URL = Pattern.compile(
            "^data:image/(png|jpeg|jpg);base64,(.+)$", Pattern.CASE_INSENSITIVE);

    protected final XWPFDocument document;
    protected final DocumentType type;
    protected final DocumentValues values;
    protected final StyleResolver styles;

    protected AbstractLayoutBuilder(XWPFDocument document, DocumentType type,
                                    DocumentValues values, StyleResolver styles) {
        this.document = document;
        this.type = type;
        this.values = values;
        this.styles = styles;
    }

    /** Строит содержательную часть документа. */
    public abstract void build();

    protected XWPFParagraph paragraph(String styleId, String text) {
        XWPFParagraph p = document.createParagraph();
        if (styleId != null) {
            p.setStyle(styleId);
        }
        if (text != null && !text.isEmpty()) {
            XWPFRun run = p.createRun();
            run.setText(text);
        }
        return p;
    }

    protected void paragraphs(String styleId, List<String> lines) {
        for (String line : lines) {
            paragraph(styleId, line);
        }
    }

    protected void bodyParagraphs(String styleId, String text) {
        String[] blocks = text.replace("\r\n", "\n").split("\\n\\s*\\n");
        for (String block : blocks) {
            String merged = block.replace("\n", " ").replaceAll("\\s{2,}", " ").trim();
            if (!merged.isEmpty()) {
                paragraph(styleId, merged);
            }
        }
    }

    /**
     * Парсит dataURL вида "data:image/png;base64,xxx" → [format, bytes, mime].
     *
     * @param dataUrl исходный dataURL
     * @param logPrefix префикс для логов (подпись/фото)
     * @return массив [0] — int-формат (Document.PICTURE_TYPE_*),
     *         [1] — байты изображения, [2] — mime-строка, или null при ошибке
     */
    private Object[] parseDataUrlImage(String dataUrl, String logPrefix) {
        if (dataUrl == null || dataUrl.isBlank()) {
            log.debug("{} не задан (null/blank)", logPrefix);
            return null;
        }
        try {
            String cleaned = dataUrl.replaceAll("\\s+", "").trim();
            Matcher m = DATA_URL.matcher(cleaned);
            if (!m.matches()) {
                log.warn("{} имеет неверный формат dataURL, начало: {}", logPrefix,
                        cleaned.length() > 40 ? cleaned.substring(0, 40) + "..." : cleaned);
                return null;
            }
            String mime = m.group(1).toLowerCase();
            String b64 = m.group(2);
            int format = switch (mime) {
                case "png" -> Document.PICTURE_TYPE_PNG;
                case "jpeg", "jpg" -> Document.PICTURE_TYPE_JPEG;
                default -> -1;
            };
            if (format < 0) {
                log.warn("Неподдерживаемый формат {}: {}", logPrefix, mime);
                return null;
            }
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(b64);
            } catch (IllegalArgumentException base64Ex) {
                bytes = Base64.getUrlDecoder().decode(b64);
            }
            if (bytes.length < 32) {
                log.warn("{} слишком маленькое ({}) — вероятно, битая base64-строка", logPrefix, bytes.length);
                return null;
            }
            log.debug("{} распознано: mime={}, размер={} байт", logPrefix, mime, bytes.length);
            return new Object[]{format, bytes, mime};
        } catch (Exception e) {
            log.warn("Не удалось декодировать {}: {} — {}", logPrefix,
                    e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * Вставляет изображение в абзац с сохранением пропорций исходной картинки:
     * ширина берётся из {@code targetWidthPx}, высота пересчитывается по
     * соотношению сторон (но не больше {@code maxHeightPx}). Так загруженная
     * печать или подпись не искажаются и не выглядят непропорционально мелкими.
     */
    private boolean appendImage(XWPFParagraph paragraph, Object[] parsed,
                                String fileNameBase, int targetWidthPx, int maxHeightPx) {
        if (parsed == null) {
            return false;
        }
        int format = (int) parsed[0];
        byte[] bytes = (byte[]) parsed[1];
        String mime = (String) parsed[2];
        int widthEmu = targetWidthPx * Units.EMU_PER_PIXEL;
        int heightEmu = maxHeightPx * Units.EMU_PER_PIXEL;
        // Реальные размеры картинки → пропорциональная высота
        try (InputStream probe = new ByteArrayInputStream(bytes)) {
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(probe);
            if (image != null && image.getWidth() > 0) {
                double scale = (double) targetWidthPx / image.getWidth();
                int scaledHeight = (int) Math.round(image.getHeight() * scale);
                if (scaledHeight > maxHeightPx) {
                    scale = (double) maxHeightPx / image.getHeight();
                    widthEmu = (int) Math.round(image.getWidth() * scale) * Units.EMU_PER_PIXEL;
                    heightEmu = maxHeightPx * Units.EMU_PER_PIXEL;
                } else {
                    heightEmu = scaledHeight * Units.EMU_PER_PIXEL;
                }
            }
        } catch (Exception e) {
            log.debug("Не удалось определить размер изображения {} — используем базовые пропорции: {}",
                    fileNameBase, e.getMessage());
        }
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            XWPFRun run = paragraph.createRun();
            String fileName = fileNameBase + "."
                    + (mime.equals("jpeg") || mime.equals("jpg") ? "jpg" : "png");
            run.addPicture(is, format, fileName, widthEmu, heightEmu);
            log.debug("Изображение вставлено: name={}, mime={}, size={} байт",
                    fileName, mime, bytes.length);
            return true;
        } catch (Exception e) {
            log.warn("Не удалось вставить изображение {}: {} — {}",
                    fileNameBase, e.getClass().getSimpleName(), e.getMessage(), e);
            return false;
        }
    }

    private Object[] parseSignatureImage() {
        Object[] parsed = parseDataUrlImage(values.signatureImageDataUrl(), "Подпись");
        if (parsed != null) {
            // Обрезаем прозрачные/белые поля, чтобы росчерк не «отрывался» от печати
            byte[] trimmed = ImageUtils.trim((byte[]) parsed[1]);
            parsed[1] = trimmed;
            parsed[0] = org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG;
            parsed[2] = "png";
        }
        return parsed;
    }

    private Object[] parsePhotoImage() {
        return parseDataUrlImage(values.photoImageDataUrl(), "Фото");
    }

    private Object[] parseStampImage() {
        Object[] parsed = parseDataUrlImage(values.stampImageDataUrl(), "Печать");
        if (parsed != null) {
            // Обрезаем белые/прозрачные поля, чтобы печать стояла вплотную к подписи
            byte[] trimmed = ImageUtils.trim((byte[]) parsed[1]);
            parsed[1] = trimmed;
            parsed[0] = org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG;
            parsed[2] = "png";
        }
        return parsed;
    }

    /**
     * Вставляет изображение подписи в абзац: ширина ~5,5 см, пропорции
     * исходной картинки сохраняются.
     */
    private boolean appendSignatureImage(XWPFParagraph paragraph) {
        return appendImage(paragraph, parseSignatureImage(), "signature", 207, 105);
    }

    /**
     * Вставляет фото (аватар) в абзац.
     */
    protected boolean appendPhotoImage(XWPFParagraph paragraph) {
        return appendImage(paragraph, parsePhotoImage(), "photo", 90, 120);
    }

    /**
     * Дописывает в существующий абзац (рядом с подписью) картинку печати:
     * около 4,2 см с сохранением пропорций. Два run в одном абзаце —
     * подпись и печать встают рядом.
     */
    protected void appendStampImage(XWPFParagraph paragraph) {
        appendImage(paragraph, parseStampImage(), "stamp", 160, 160);
    }

    private Object[] parseQrImage() {
        return parseDataUrlImage(values.qrImageDataUrl(), "QR-код");
    }

    /**
     * Блок «Проверка подлинности» в конце документа: QR-код со ссылкой на
     * просмотр документа + подпись-пояснение. Вызывается генератором после
     * построения содержательной части.
     */
    public void appendQrBlock() {
        if (values.qrImageDataUrl() == null || values.qrImageDataUrl().isBlank()) {
            return;
        }
        XWPFParagraph imageParagraph = document.createParagraph();
        boolean inserted = appendImage(imageParagraph, parseQrImage(), "qr", 100, 100);
        if (!inserted) {
            int pos = document.getPosOfParagraph(imageParagraph);
            if (pos >= 0) {
                document.removeBodyElement(pos);
            }
            return;
        }
        paragraph(null, "Проверка подлинности: наведите камеру телефона на QR-код —");
        paragraph(null, "документ откроется в браузере без установки программ.");
    }

    /**
     * Блок подписи: должность / (организация) / [картинка подписи + печать] / И.О. Фамилия.
     * Блок выровнен по правому краю — как в предпросмотре на сайте.
     */
    protected void signatureBlock(String styleId, boolean withOrg, String prefixLine) {
        if (prefixLine != null) {
            XWPFParagraph prefix = paragraph(styleId, prefixLine);
            prefix.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        }
        XWPFParagraph position = paragraph(styleId, values.positionLine());
        position.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        if (withOrg) {
            String orgLine = values.organizationOrNull();
            if (orgLine != null) {
                XWPFParagraph orgParagraph = paragraph(styleId, orgLine);
                orgParagraph.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
            }
        }
        // Создаём абзац для картинки ТОЛЬКО если подпись есть и вставилась
        XWPFParagraph sigImgParagraph = document.createParagraph();
        if (styleId != null) {
            sigImgParagraph.setStyle(styleId);
        }
        sigImgParagraph.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
        boolean hadImage = appendSignatureImage(sigImgParagraph);
        // Печать дописываем рядом с подписью; без подписи — отдельным абзацем
        if (values.stampImageDataUrl() != null && !values.stampImageDataUrl().isBlank()) {
            appendStampImage(sigImgParagraph);
            hadImage = true;
        }
        if (!hadImage) {
            int pos = document.getPosOfParagraph(sigImgParagraph);
            if (pos >= 0) {
                document.removeBodyElement(pos);
            }
        }
        XWPFParagraph name = paragraph(styleId, values.signatureNameLine());
        name.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.RIGHT);
    }
}
