package ru.docgen;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import ru.docgen.core.DocumentType;
import ru.docgen.core.RequisiteKey;
import ru.docgen.core.TemplateKind;
import ru.docgen.docx.DocxGenerationService;

import java.io.ByteArrayInputStream;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Генерация DOCX: редактируемый текст, кириллица, пометки незаполненных
 * реквизитов, оформление обоих шаблонов.
 */
class DocxGenerationServiceTest {

    private final DocxGenerationService service = new DocxGenerationService("");

    private Map<RequisiteKey, String> fullRequisites() {
        Map<RequisiteKey, String> map = new EnumMap<>(RequisiteKey.class);
        map.put(RequisiteKey.RECIPIENT, "Генеральному директору ООО «Ромашка» Иванову И.И.");
        map.put(RequisiteKey.AUTHOR, "Начальник отдела аналитики Петров П.П.");
        map.put(RequisiteKey.SUBJECT, "О закупке офисной техники");
        map.put(RequisiteKey.DATE, "12.03.2025");
        map.put(RequisiteKey.NUMBER, "47-СЗ");
        map.put(RequisiteKey.SIGNATURE, "Петров П.П.");
        map.put(RequisiteKey.EXECUTOR, "Смирнова А.А., тел. 8 (900) 000-00-00");
        return map;
    }

    private XWPFDocument open(byte[] bytes) throws Exception {
        return new XWPFDocument(new ByteArrayInputStream(bytes));
    }

    private String allText(XWPFDocument doc) {
        return doc.getParagraphs().stream()
                .map(XWPFParagraph::getText)
                .collect(Collectors.joining("\n"));
    }

    @Test
    void standardTemplateContainsRequisitesAndBody() throws Exception {
        var result = service.generate(DocumentType.MEMO, TemplateKind.STANDARD,
                fullRequisites(), "Прошу выделить средства на закупку техники.", null, null);
        try (XWPFDocument doc = open(result.content())) {
            String text = allText(doc);
            assertTrue(text.contains("СЛУЖЕБНАЯ ЗАПИСКА"));
            assertTrue(text.contains("Генеральному директору"));
            assertTrue(text.contains("ООО «Ромашка»"));
            assertTrue(text.contains("Иванову И.И."));
            assertTrue(text.contains("Дата: 12.03.2025"));
            assertTrue(text.contains("Номер: 47-СЗ"));
            assertTrue(text.contains("О закупке офисной техники"));
            assertTrue(text.contains("Прошу выделить средства"));
            assertTrue(text.contains("Петров П.П."));
            assertTrue(text.contains("Исполнитель: Смирнова А.А."));
            assertTrue(result.warnings().isEmpty());
        }
    }

    @Test
    void modernTemplateHasHeaderTableAndFooter() throws Exception {
        var result = service.generate(DocumentType.MEMO, TemplateKind.MODERN,
                fullRequisites(), "Прошу выделить средства.", null, null);
        try (XWPFDocument doc = open(result.content())) {
            assertEquals(1, doc.getTables().size(), "Табличная шапка «Кому / От кого»");
            String tableText = doc.getTables().get(0).getText();
            assertTrue(tableText.contains("Кому:"));
            assertTrue(tableText.contains("От кого:"));
            assertTrue(tableText.contains("Иванову И.И."));

            String footerText = doc.getFooterList().stream()
                    .flatMap(f -> f.getParagraphs().stream())
                    .map(XWPFParagraph::getText)
                    .collect(Collectors.joining("\n"));
            assertTrue(footerText.contains("Служебная записка"), "Нижний колонтитул с названием документа");
            assertTrue(footerText.contains("12.03.2025"));
            assertTrue(doc.getHeaderList().isEmpty(), "Верхний колонтитул отсутствует в шаблоне B");
        }
    }

    @Test
    void standardTemplateHasOrganizationHeader() throws Exception {
        var result = service.generate(DocumentType.MEMO, TemplateKind.STANDARD,
                fullRequisites(), "Текст.", null, null);
        try (XWPFDocument doc = open(result.content())) {
            String headerText = doc.getHeaderList().stream()
                    .flatMap(h -> h.getParagraphs().stream())
                    .map(XWPFParagraph::getText)
                    .collect(Collectors.joining("\n"));
            assertTrue(headerText.contains("ООО «Ромашка»"),
                    "Название организации подставляется в верхний колонтитул");
        }
    }

    @Test
    void missingRequisitesBecomeExplicitPlaceholders() throws Exception {
        var result = service.generate(DocumentType.MEMO, TemplateKind.STANDARD,
                Map.of(RequisiteKey.DATE, "12.03.2025", RequisiteKey.SUBJECT, "Тема"),
                "Текст без реквизитов.", null, null);
        try (XWPFDocument doc = open(result.content())) {
            String text = allText(doc);
            assertTrue(text.contains("[Адресат]"), "Отсутствующий адресат явно помечен");
            assertTrue(text.contains("[Заполнить]"), "Отсутствующая должность автора явно помечена");
            assertTrue(text.contains("[Подпись]"), "Отсутствующая подпись явно помечена");
        }
    }

    @Test
    void cyrillicSurvivesRoundTrip() throws Exception {
        String body = "Проверка кириллицы: съезд, ёлка, объём, 180 000 рублей.";
        var result = service.generate(DocumentType.MEMO, TemplateKind.STANDARD,
                fullRequisites(), body, null, null);
        try (XWPFDocument doc = open(result.content())) {
            String text = allText(doc);
            assertTrue(text.contains("съезд"));
            assertTrue(text.contains("ёлка"));
            assertTrue(text.contains("объём"));
            assertTrue(text.contains("180 000 рублей"));
        }
    }

    @Test
    void allTypesGenerateInBothTemplates() throws Exception {
        for (DocumentType type : DocumentType.values()) {
            for (TemplateKind template : TemplateKind.values()) {
                var result = service.generate(type, template, fullRequisites(), "Текст документа.", null, null);
                assertNotNull(result.content());
                assertTrue(result.content().length > 5000, "docx не пустой");
                try (XWPFDocument doc = open(result.content())) {
                    String text = allText(doc);
                    assertFalse(text.isBlank());
                    assertTrue(text.contains("Текст документа."));
                }
            }
        }
    }

    @Test
    void letterHasNoTitleLineButHasSubject() throws Exception {
        var result = service.generate(DocumentType.LETTER, TemplateKind.STANDARD,
                fullRequisites(), "Текст письма.", null, null);
        try (XWPFDocument doc = open(result.content())) {
            String text = allText(doc);
            assertFalse(text.contains("ПИСЬМО"), "У письма нет строки названия типа");
            assertTrue(text.contains("О закупке офисной техники"));
        }
    }

    @Test
    void fileIsEditableDocxNotImage() throws Exception {
        var result = service.generate(DocumentType.MEMO, TemplateKind.STANDARD,
                fullRequisites(), "Редактируемый текст.", null, null);
        // ZIP-магия DOCX
        assertEquals('P', (char) (result.content()[0] & 0xFF));
        assertEquals('K', (char) (result.content()[1] & 0xFF));
    }
}
