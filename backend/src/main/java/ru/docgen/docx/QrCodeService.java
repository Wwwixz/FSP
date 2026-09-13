package ru.docgen.docx;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;

/**
 * Генератор QR-кода проверки подлинности документа.
 *
 * <p>В сгенерированный файл встраивается QR со ссылкой на просмотр
 * документа: отсканировал камерой — открыл документ в браузере.
 */
@Service
public class QrCodeService {

    private static final Logger log = LoggerFactory.getLogger(QrCodeService.class);

    /**
     * @param url  содержимое QR (ссылка на просмотр документа)
     * @param size размер картинки в пикселях
     * @return PNG-байты или null, если сгенерировать не удалось
     */
    public byte[] png(String url, int size) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            BitMatrix matrix = new QRCodeWriter().encode(
                    url, BarcodeFormat.QR_CODE, size, size,
                    Map.of(EncodeHintType.MARGIN, 2));
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, size, size);
            g.setColor(Color.BLACK);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    if (matrix.get(x, y)) {
                        g.fillRect(x, y, 1, 1);
                    }
                }
            }
            g.dispose();
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                ImageIO.write(image, "png", out);
                return out.toByteArray();
            }
        } catch (WriterException | java.io.IOException e) {
            log.warn("Не удалось сгенерировать QR-код: {}", e.getMessage());
            return null;
        }
    }

    /** PNG в виде base64-dataURL для вставки в документ/показ на экране. */
    public String dataUrl(String url, int size) {
        byte[] png = png(url, size);
        return png == null ? null : "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
    }
}
