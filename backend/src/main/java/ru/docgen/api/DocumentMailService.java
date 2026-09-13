package ru.docgen.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;

/**
 * Отправка готового документа вложением на почту.
 *
 * <p>SMTP настраивается переменными MAIL_USERNAME/MAIL_PASSWORD
 * (см. .env.example). Пока учётная запись не задана, сервис честно
 * сообщает, что почта не настроена, — остальные функции не затрагивает.
 */
@Service
public class DocumentMailService {

    private static final Logger log = LoggerFactory.getLogger(DocumentMailService.class);

    private final JavaMailSender mailSender;
    private final String username;
    private final String fromName;

    public DocumentMailService(JavaMailSender mailSender,
                               @Value("${spring.mail.username:}") String username,
                               @Value("${app.mail.from-name:DocHelper}") String fromName) {
        this.mailSender = mailSender;
        this.username = username;
        this.fromName = fromName;
    }

    public void sendDocument(String to, String fileName, byte[] content) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "MAIL_NOT_CONFIGURED: Отправка почты не настроена. Заполните MAIL_USERNAME и "
                            + "MAIL_PASSWORD в backend/.env (см. .env.example) и перезапустите бэкенд.");
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(username);
            helper.setTo(to);
            helper.setSubject("Документ от DocHelper: " + fileName);
            helper.setText("Здравствуйте!\n\n"
                    + "Во вложении документ, созданный в сервисе DocHelper («Документ за 3 шага»).\n"
                    + "Файл: " + fileName + "\n\n"
                    + "Это автоматическое письмо — отвечать на него не нужно.", false);
            helper.addAttachment(fileName, new org.springframework.core.io.ByteArrayResource(content));
            mailSender.send(message);
            log.info("Документ {} отправлен на {}", fileName, to);
        } catch (Exception e) {
            log.warn("Не удалось отправить письмо на {}: {}", to, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "MAIL_SEND_FAILED: Не удалось отправить письмо — проверьте адрес и настройки почты. "
                            + "Документ остался в разделе «Мои документы», его можно скачать.");
        }
    }
}
