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
     * Вставляет изображение в абзац.
     * ВАЖНО: используем только один вызов XWPFRun.addPicture — он сам регистрирует
     * данные картинки и создаёт правильный relation с run. Предварительный вызов
     * document.addPictureData приводит к «осиротевшим» данным и отсутствию
     * картинки в просмотрщиках (например, в Word).
     */
    private boolean appendImage(XWPFParagraph paragraph, Object[] parsed,
                                String fileNameBase, int widthPx, int heightPx) {
        if (parsed == null) {
            return false;
        }
        int format = (int) parsed[0];
        byte[] bytes = (byte[]) parsed[1];
        String mime = (String) parsed[2];
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            XWPFRun run = paragraph.createRun();
            String fileName = fileNameBase + "."
                    + (mime.equals("jpeg") || mime.equals("jpg") ? "jpg" : "png");
            int widthEmu = widthPx * Units.EMU_PER_PIXEL;
            int heightEmu = heightPx * Units.EMU_PER_PIXEL;
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
        return parseDataUrlImage(values.signatureImageDataUrl(), "Подпись");
    }

    private Object[] parsePhotoImage() {
        return parseDataUrlImage(values.photoImageDataUrl(), "Фото");
    }

    /**
     * Вставляет изображение подписи в абзац.
     */
    private boolean appendSignatureImage(XWPFParagraph paragraph) {
        return appendImage(paragraph, parseSignatureImage(), "signature", 170, 70);
    }

    /**
     * Вставляет фото (аватар) в абзац.
     */
    protected boolean appendPhotoImage(XWPFParagraph paragraph) {
        return appendImage(paragraph, parsePhotoImage(), "photo", 90, 120);
    }

    /** Блок подписи: должность / (организация) / [картинка подписи] / И.О. Фамилия. */
    protected void signatureBlock(String styleId, boolean withOrg, String prefixLine) {
        if (prefixLine != null) {
            paragraph(styleId, prefixLine);
        }
        paragraph(styleId, values.positionLine());
        if (withOrg) {
            String org = values.organizationOrNull();
            if (org != null) {
                paragraph(styleId, org);
            }
        }
        // Создаём абзац для картинки ТОЛЬКО если подпись есть и вставилась
        XWPFParagraph sigImgParagraph = document.createParagraph();
        if (styleId != null) {
            sigImgParagraph.setStyle(styleId);
        }
        boolean hadImage = appendSignatureImage(sigImgParagraph);
        if (!hadImage) {
            int pos = document.getPosOfParagraph(sigImgParagraph);
            if (pos >= 0) {
                document.removeBodyElement(pos);
            }
        }
        paragraph(styleId, values.signatureNameLine());
    }
}
