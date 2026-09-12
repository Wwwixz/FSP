package ru.docgen;

import org.junit.jupiter.api.Test;
import ru.docgen.core.DocumentType;
import ru.docgen.core.RequisiteKey;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Валидация обязательных реквизитов по типам документов. */
class RequisitesServiceTest {

    private final ru.docgen.requisites.RequisitesService service =
            new ru.docgen.requisites.RequisitesService();

    @Test
    void memoRequiresRecipientAuthorDateNumberSubjectSignature() {
        var validation = service.validate(DocumentType.MEMO, Map.of());
        assertEquals(List.of(RequisiteKey.RECIPIENT, RequisiteKey.AUTHOR, RequisiteKey.DATE,
                RequisiteKey.NUMBER, RequisiteKey.SUBJECT, RequisiteKey.SIGNATURE), validation.missing());
        assertTrue(validation.hasMissing());
    }

    @Test
    void certificateHasNoNumber() {
        var validation = service.validate(DocumentType.CERTIFICATE, Map.of());
        assertFalse(validation.missing().contains(RequisiteKey.NUMBER),
                "У информационной справки нет обязательного номера");
        assertTrue(validation.missing().contains(RequisiteKey.AUTHOR));
    }

    @Test
    void filledRequisitesAreDone() {
        var validation = service.validate(DocumentType.MEMO, Map.of(
                RequisiteKey.RECIPIENT, "Иванову И.И.",
                RequisiteKey.AUTHOR, "Петров П.П.",
                RequisiteKey.SUBJECT, "О закупке",
                RequisiteKey.DATE, "12.03.2025",
                RequisiteKey.NUMBER, "47-СЗ",
                RequisiteKey.SIGNATURE, "Петров П.П."));
        assertTrue(validation.missing().isEmpty());
        validation.checks().forEach(c -> assertEquals("done", c.status()));
    }

    @Test
    void dateAndNumberAutoFilledOnGeneration() {
        var session = new ru.docgen.core.ProcessedDocument("t1", DocumentType.MEMO, "standard", "текст");
        session.setRequisites(new java.util.EnumMap<>(ru.docgen.core.RequisiteKey.class));
        var prepared = service.prepareForGeneration(session);

        assertTrue(prepared.get(RequisiteKey.DATE).matches("\\d{2}\\.\\d{2}\\.\\d{4}"),
                "Дата автозаполняется текущей датой");
        assertTrue(prepared.get(RequisiteKey.NUMBER).matches("\\d+-СЗ"),
                "Номер автозаполняется с суффиксом типа");
        assertEquals(null, prepared.get(RequisiteKey.RECIPIENT),
                "Адресат не автозаполняется — запрет галлюцинаций");
    }

    @Test
    void contentRequisitesNeverAutoFilled() {
        var session = new ru.docgen.core.ProcessedDocument("t2", DocumentType.LETTER, "modern", "текст");
        session.setRequisites(new java.util.EnumMap<>(ru.docgen.core.RequisiteKey.class));
        var prepared = service.prepareForGeneration(session);
        assertTrue(service.validate(DocumentType.LETTER, prepared).missing()
                .containsAll(List.of(RequisiteKey.RECIPIENT, RequisiteKey.AUTHOR, RequisiteKey.SUBJECT,
                        RequisiteKey.SIGNATURE)));
    }

    @Test
    void placeholderUsedForMissingValues() {
        assertEquals("[Адресат]", service.displayValue(RequisiteKey.RECIPIENT, null));
        assertEquals("Иванову И.И.", service.displayValue(RequisiteKey.RECIPIENT, "Иванову И.И."));
    }

    @Test
    void dateFormatValidation() {
        assertTrue(service.isValidDateFormat("12.03.2025"));
        assertTrue(service.isValidDateFormat("12 марта 2025"));
        assertFalse(service.isValidDateFormat("завтра"));
        assertFalse(service.isValidDateFormat(""));
    }
}
