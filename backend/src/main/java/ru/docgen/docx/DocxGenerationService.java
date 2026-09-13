package ru.docgen.docx;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import ru.docgen.core.DocumentType;
import ru.docgen.core.RequisiteKey;
import ru.docgen.core.TemplateKind;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Модуль генерации DOCX (раздел 1.4 постановки задачи).
 *
 * Оформление определяется программно по правилам выбранного шаблона:
 * за основу берётся оригинальный .docx-шаблон из стартовых материалов
 * (сохраняются поля страницы, стили, колонтитулы), содержательная часть
 * выстраивается по типу документа. ИИ на оформление не влияет.
 */
@Service
public class DocxGenerationService {

    private static final Logger log = LoggerFactory.getLogger(DocxGenerationService.class);
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final String defaultOrganization;

    public DocxGenerationService(@Value("${app.documents.default-organization:}") String defaultOrganization) {
        this.defaultOrganization = defaultOrganization;
    }

    /**
     * @param type            тип документа
     * @param template        шаблон оформления
     * @param requisites      подготовленные значения реквизитов
     * @param bodyText        улучшенный содержательный текст (без служебных строк)
     * @param signatureImage  изображение подписи в base64-dataURL (опционально)
     * @param photoImage      фото автора документа в base64-dataURL (опционально)
     * @param stampImage      печать организации в base64-dataURL (опционально)
     * @param templateOptions параметры «своего шаблона» (опционально)
     * @param qrImage         QR-код проверки подлинности в base64-dataURL (опционально)
     * @return готовый DOCX и предупреждения (например о запасном шаблоне)
     */
    public GenerationResult generate(DocumentType type, TemplateKind template,
                                     Map<RequisiteKey, String> requisites, String bodyText,
                                     String signatureImage, String photoImage,
                                     String stampImage, String qrImage,
                                     String uploadedTemplate, Map<String, String> templateOptions) {
        TemplateKind chosen = template;
        List<String> warnings = new ArrayList<>();
        byte[] docx;
        try {
            docx = buildDocx(type, chosen, requisites, bodyText, signatureImage, photoImage,
                    stampImage, qrImage, uploadedTemplate, templateOptions);
        } catch (Exception e) {
            log.warn("Шаблон {} повреждён или недоступен: {}", chosen.getId(), e.getMessage());
            TemplateKind fallback = chosen == TemplateKind.STANDARD ? TemplateKind.MODERN : TemplateKind.STANDARD;
            try {
                docx = buildDocx(type, fallback, requisites, bodyText, signatureImage, photoImage,
                        stampImage, qrImage, uploadedTemplate, templateOptions);
                warnings.add("Выбранный шаблон повреждён или недоступен, применён запасной шаблон «"
                        + fallback.getTitle() + "».");
                chosen = fallback;
            } catch (Exception fallbackError) {
                throw new IllegalStateException("Не удалось сформировать документ ни по одному шаблону",
                        fallbackError);
            }
        }
        return new GenerationResult(docx, buildFileName(type, "docx"), warnings);
    }

    private byte[] buildDocx(DocumentType type, TemplateKind template,
                             Map<RequisiteKey, String> requisites, String bodyText,
                             String signatureImage, String photoImage, String stampImage,
                             String qrImage, String uploadedTemplate,
                             Map<String, String> templateOptions) throws IOException {
        // «Свой шаблон» строится программно, без базового .docx из ресурсов
        if (template == TemplateKind.CUSTOM) {
            return buildCustomDocx(type, requisites, bodyText, signatureImage, photoImage,
                    stampImage, qrImage, templateOptions);
        }
        // «Бланк моей организации»: оформление по загруженному пользователем .docx —
        // колонтитулы, поля и стили бланка сохраняются (преимущество по ТЗ 1.6).
        InputStream in;
        if (template == TemplateKind.UPLOADED) {
            in = new ByteArrayInputStream(decodeDocx(uploadedTemplate));
        } else {
            in = new ClassPathResource(template.getResourcePath()).getInputStream();
        }
        try (in; XWPFDocument document = new XWPFDocument(in)) {

            // Очищаем содержательную часть, сохраняя параметры раздела и колонтитулы
            for (int i = document.getBodyElements().size() - 1; i >= 0; i--) {
                document.removeBodyElement(i);
            }

            StyleResolver styles = StyleResolver.of(document);
            DocumentValues values = new DocumentValues(type, requisites, bodyText,
                    signatureImage, photoImage, stampImage, qrImage);

            AbstractLayoutBuilder builder = template == TemplateKind.MODERN
                    ? new ModernLayoutBuilder(document, type, values, styles)
                    : new StandardLayoutBuilder(document, type, values, styles);
            builder.build();
            builder.appendQrBlock();

            replaceHeaderFooterPlaceholders(document, type, values);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();
        }
    }

    /** Декодирует base64-dataURL бланка; ошибка => запасной шаблон (устойчивость по ТЗ 2.3). */
    private static byte[] decodeDocx(String dataUrl) throws IOException {
        if (dataUrl == null || dataUrl.isBlank()) {
            throw new IOException("Бланк не передан");
        }
        String cleaned = dataUrl.replaceAll("\\s+", "");
        int comma = cleaned.indexOf(',');
        String b64 = comma >= 0 ? cleaned.substring(comma + 1) : cleaned;
        byte[] bytes;
        try {
            try {
                bytes = Base64.getDecoder().decode(b64);
            } catch (IllegalArgumentException e) {
                bytes = Base64.getUrlDecoder().decode(b64);
            }
        } catch (Exception e) {
            throw new IOException("Некорректная base64-строка бланка");
        }
        // .docx — это ZIP-архив: проверяем сигнатуру PK
        if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') {
            throw new IOException("Файл не является документом .docx");
        }
        return bytes;
    }

    private byte[] buildCustomDocx(DocumentType type, Map<RequisiteKey, String> requisites,
                                   String bodyText, String signatureImage, String photoImage,
                                   String stampImage, String qrImage,
                                   Map<String, String> templateOptions) throws IOException {
        try (XWPFDocument document = CustomLayoutBuilder.createBaseDocument(templateOptions)) {
            StyleResolver styles = StyleResolver.of(document);
            DocumentValues values = new DocumentValues(type, requisites, bodyText,
                    signatureImage, photoImage, stampImage, qrImage);
            CustomLayoutBuilder customBuilder = new CustomLayoutBuilder(document, type, values, styles, templateOptions);
            customBuilder.build();
            customBuilder.appendQrBlock();
            replaceHeaderFooterPlaceholders(document, type, values);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();
        }
    }

    private void replaceHeaderFooterPlaceholders(XWPFDocument document, DocumentType type, DocumentValues values) {
        String organization = values.organizationOrNull();
        if (organization == null) {
            organization = defaultOrganization;
        }
        Map<String, String> replacements = Map.of(
                "[Название организации]", organization == null ? "" : organization,
                "[Название документа]", type.getLabel(),
                "[Дата]", values.date());

        for (var header : document.getHeaderList()) {
            for (XWPFParagraph p : header.getParagraphs()) {
                replacePlaceholdersInParagraph(p, replacements);
            }
        }
        for (var footer : document.getFooterList()) {
            for (XWPFParagraph p : footer.getParagraphs()) {
                replacePlaceholdersInParagraph(p, replacements);
            }
        }
    }

    /**
     * Заменяет плейсхолдеры в абзаце. Текст может быть разбит на несколько
     * runs — сначала сливаем его в первый run, затем заменяем.
     */
    private void replacePlaceholdersInParagraph(XWPFParagraph paragraph, Map<String, String> replacements) {
        String full = paragraph.getText();
        if (full == null || full.isEmpty()) {
            return;
        }
        boolean contains = replacements.keySet().stream().anyMatch(full::contains);
        if (!contains) {
            return;
        }
        String replaced = full;
        for (Map.Entry<String, String> e : replacements.entrySet()) {
            replaced = replaced.replace(e.getKey(), e.getValue());
        }
        List<XWPFRun> runs = paragraph.getRuns();
        for (int i = runs.size() - 1; i > 0; i--) {
            paragraph.removeRun(i);
        }
        if (!runs.isEmpty()) {
            runs.get(0).setText(replaced, 0);
        } else {
            paragraph.createRun().setText(replaced);
        }
    }

    private String buildFileName(DocumentType type, String extension) {
        String base = type.getLabel()
                .toLowerCase(Locale.ROOT)
                .replace(' ', '_');
        return base + "_" + LocalDate.now().format(FILE_DATE) + "." + extension;
    }

    /**
     * @param content  DOCX-файл
     * @param fileName имя файла для скачивания
     * @param warnings предупреждения для пользователя (запасной шаблон и т.п.)
     */
    public record GenerationResult(byte[] content, String fileName, List<String> warnings) {
    }
}
