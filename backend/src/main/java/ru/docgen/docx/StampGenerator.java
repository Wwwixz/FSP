package ru.docgen.docx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Генератор круглой печати организации (раздел «добавление печати»).
 *
 * <p>Рисует классическую печать: двойная окружность, наименование
 * организации по центру, город и год внизу. Возвращает PNG в base64-dataURL —
 * дальше он вставляется в документ как обычная картинка печати
 * (см. {@link AbstractLayoutBuilder#appendStampImage}).
 */
@Service
public class StampGenerator {

    private static final Logger log = LoggerFactory.getLogger(StampGenerator.class);
    private static final int SIZE = 600;
    private static final Color INK = new Color(0x1E, 0x3A, 0x8A, 230);

    /**
     * @param organization наименование организации (обязательное)
     * @param city         город (опционально)
     * @return dataURL "data:image/png;base64,..." или null, если organization пуста
     */
    public String generate(String organization, String city) {
        if (organization == null || organization.isBlank()) {
            return null;
        }
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

            int cx = SIZE / 2;
            int cy = SIZE / 2;
            g.setColor(INK);
            g.setStroke(new BasicStroke(10f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
            g.drawOval(15, 15, SIZE - 30, SIZE - 30);
            g.setStroke(new BasicStroke(4f));
            g.drawOval(48, 48, SIZE - 96, SIZE - 96);

            // Наименование организации: перенос на строки по ширине, максимум 4 строки
            Font orgFont = serifFont(Font.BOLD, 44);
            List<String> lines = wrap(organization.trim().toUpperCase(), orgFont, SIZE - 150);
            if (lines.size() > 4) {
                lines = lines.subList(0, 4);
            }
            FontMetrics fm = g.getFontMetrics(orgFont);
            int lineHeight = fm.getHeight() + 6;
            int textTop = cy - (lines.size() * lineHeight) / 2 - 14;
            g.setFont(orgFont);
            int lineIndex = 0;
            for (String line : lines) {
                int width = fm.stringWidth(line);
                g.drawString(line, cx - width / 2, textTop + lineIndex * lineHeight);
                lineIndex++;
            }

            // Разделительная линия и город + год внизу
            g.setStroke(new BasicStroke(3f));
            g.drawLine(cx - 150, cy + lines.size() * lineHeight / 2 + 6, cx + 150, cy + lines.size() * lineHeight / 2 + 6);
            String bottom = (city == null || city.isBlank() ? "" : city.trim() + ", ")
                    + java.time.LocalDate.now().getYear() + " г.";
            Font small = serifFont(Font.PLAIN, 34);
            g.setFont(small);
            int bw = g.getFontMetrics(small).stringWidth(bottom);
            g.drawString(bottom, cx - bw / 2, SIZE - 110);
        } finally {
            g.dispose();
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            log.warn("Не удалось закодировать печать в PNG: {}", e.getMessage());
            return null;
        }
    }

    private static Font serifFont(int style, int size) {
        return new Font("Times New Roman", style, size).deriveFont((float) size);
    }

    /** Перенос текста по ширине на слова. */
    private static List<String> wrap(String text, Font font, int maxWidth) {
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = probe.createGraphics();
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\\s+\\n\\s*")) {
            StringBuilder current = new StringBuilder();
            for (String word : paragraph.split("\\s+")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (fm.stringWidth(candidate) > maxWidth && !current.isEmpty()) {
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
        g.dispose();
        return lines;
    }
}
