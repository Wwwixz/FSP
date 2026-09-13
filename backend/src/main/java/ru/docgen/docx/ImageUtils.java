package ru.docgen.docx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Обрезка пустых полей загруженных картинок (подпись, печать).
 *
 * <p>Пользователи часто загружают подпись с большим прозрачным или белым
 * фоном вокруг росчерка — из-за этого в документе видимый росчерк «отрывается»
 * от печати. Метод находит границы видимых пикселей и обрезает всё лишнее.
 */
public final class ImageUtils {

    private static final Logger log = LoggerFactory.getLogger(ImageUtils.class);
    private static final int PADDING = 6;

    private ImageUtils() {
    }

    /**
     * Обрезает прозрачные (для PNG) или белые (для JPEG) поля вокруг контента.
     * При любой ошибке возвращает исходные байты — обрезка не должна ломать вставку.
     */
    public static byte[] trim(byte[] imageBytes) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (source == null) {
                return imageBytes;
            }
            int width = source.getWidth();
            int height = source.getHeight();
            boolean hasAlpha = source.getColorModel().hasAlpha();

            int minX = width, minY = height, maxX = -1, maxY = -1;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (isInk(source.getRGB(x, y), hasAlpha)) {
                        if (x < minX) minX = x;
                        if (x > maxX) maxX = x;
                        if (y < minY) minY = y;
                        if (y > maxY) maxY = y;
                    }
                }
            }
            if (maxX < 0 || (minX == 0 && minY == 0 && maxX == width - 1 && maxY == height - 1)) {
                return imageBytes; // пусто или обрезать нечего
            }
            minX = Math.max(0, minX - PADDING);
            minY = Math.max(0, minY - PADDING);
            maxX = Math.min(width - 1, maxX + PADDING);
            maxY = Math.min(height - 1, maxY + PADDING);

            int outW = maxX - minX + 1;
            int outH = maxY - minY + 1;
            BufferedImage cropped = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_ARGB);
            var g = cropped.createGraphics();
            g.drawImage(source, 0, 0, outW, outH, minX, minY, maxX + 1, maxY + 1, null);
            g.dispose();

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                ImageIO.write(cropped, "png", out);
                log.debug("Картинка обрезана: {}x{} -> {}x{}", width, height, outW, outH);
                return out.toByteArray();
            }
        } catch (IOException | RuntimeException e) {
            log.debug("Обрезка картинки не удалась, используется оригинал: {}", e.getMessage());
            return imageBytes;
        }
    }

    private static boolean isInk(int argb, boolean hasAlpha) {
        if (hasAlpha) {
            return (argb >>> 24) > 16; // непрозрачный пиксель
        }
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int luminance = (r * 299 + g * 587 + b * 114) / 1000;
        return luminance < 245; // не белый
    }
}
