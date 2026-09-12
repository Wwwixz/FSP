package ru.docgen;

import org.junit.jupiter.api.Test;
import ru.docgen.ai.MockAIProvider;
import ru.docgen.core.DocumentType;
import ru.docgen.core.RequisiteKey;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Требование запрета на галлюцинации: отсутствующие в тексте сведения
 * не придумываются, а присутствующие факты сохраняются.
 */
class MockAIProviderTest {

    private final MockAIProvider provider = new MockAIProvider();

    @Test
    void missingRecipientIsNotInvented() {
        String draft = """
                От кого: начальник отдела аналитики Петров П.П.
                Заголовок: О закупке офисной техники

                Прошу выделить средства на закупку трёх компьютеров.

                Подпись: Петров П.П.""";

        var result = provider.process(draft, DocumentType.MEMO);
        Map<RequisiteKey, String> req = result.requisites();

        assertNull(req.get(RequisiteKey.RECIPIENT), "Адресат отсутствует — он не должен быть придуман");
        assertEquals("О закупке офисной техники", req.get(RequisiteKey.SUBJECT));
        assertEquals("начальник отдела аналитики Петров П.П.", req.get(RequisiteKey.AUTHOR));
        assertEquals("Петров П.П.", req.get(RequisiteKey.SIGNATURE));
    }

    @Test
    void presentFactsArePreserved() {
        String draft = """
                Кому: Генеральному директору ООО «Ромашка» Иванову И.И.
                От кого: начальник отдела аналитики Петров П.П.
                Дата: 12.03.2025
                Номер: 47-СЗ
                Заголовок: О закупке офисной техники

                Прошу выделить средства на закупку трёх компьютеров. Стоимость 180 000 рублей.
                Поставщик ООО «ТехноСнаб» осуществит поставку в течение десяти рабочих дней.

                Подпись: Петров П.П.""";

        var result = provider.process(draft, DocumentType.MEMO);
        String text = result.improvedText();

        assertTrue(text.contains("180 000 рублей"), "Сумма должна сохраниться");
        assertTrue(text.contains("ТехноСнаб"), "Поставщик должен сохраниться");
        assertTrue(text.contains("компьютеров"), "Ключевой факт должен сохраниться");
        assertEquals("Генеральному директору ООО «Ромашка» Иванову И.И.",
                result.requisites().get(RequisiteKey.RECIPIENT));
        assertEquals("12.03.2025", result.requisites().get(RequisiteKey.DATE));
        assertEquals("47-СЗ", result.requisites().get(RequisiteKey.NUMBER));
    }

    @Test
    void colloquialStyleIsImproved() {
        String draft = """
                Кому: генеральному директору ООО Ромашка Иванову И.И.
                От кого: начальник отдела аналитики Петров П.П.
                Заголовок О закупке офисной техники

                Здрасьте! Нам надо купить три компа для отдела аналитики, потому что старые уже не работают.
                Вообщем, цена 180000 рублей. Поставщик ТехноСнаб обещал привезти за 10 дней.

                Подпись Петров.""";

        var result = provider.process(draft, DocumentType.MEMO);
        String text = result.improvedText().toLowerCase();

        assertTrue(!text.contains("здрасьте"), "Разговорное приветствие должно быть убрано");
        assertTrue(!text.contains("компа"), "Просторечие «компа» должно быть заменено");
        assertTrue(text.contains("компьютера"), "Должна быть официальная формулировка");
        assertTrue(text.contains("таким образом"), "«Вообщем» заменено деловым оборотом");
        assertTrue(text.contains("180000"), "Сумма сохранена");
    }

    @Test
    void serviceLinesRemovedFromBody() {
        String draft = """
                Кому: Иванову И.И.
                Дата: 12.03.2025

                Текст сообщения содержательный.

                Подпись: Петров П.П.""";

        var result = provider.process(draft, DocumentType.MEMO);
        String text = result.improvedText();
        assertTrue(!text.contains("Кому:"), "Служебные строки не должны попасть в тело");
        assertTrue(!text.contains("Подпись:"), "Служебные строки не должны попасть в тело");
        assertTrue(text.contains("Текст сообщения содержательный."));
    }

    @Test
    void dateNormalizedToRuFormat() {
        var result = provider.process("""
                Дата: 12 марта 2025

                Текст сообщения.

                Подпись: Петров П.П.""", DocumentType.CERTIFICATE);
        assertEquals("12.03.2025", result.requisites().get(RequisiteKey.DATE));
    }

    @Test
    void proseAuthorExtracted() {
        var result = provider.process("""
                Начальник отдела аналитики Петров П.П. сообщает: возникла необходимость закупки
                трёх компьютеров. Общая стоимость 180 000 рублей.

                Подпись: Петров П.П.""", DocumentType.MEMO);
        String author = result.requisites().get(RequisiteKey.AUTHOR);
        assertNotNull(author);
        assertTrue(author.contains("Петров П.П."));
        assertTrue(author.toLowerCase().contains("начальник"));
    }

    @Test
    void organizationExtractedForHeader() {
        var result = provider.process("""
                Кому: Генеральному директору ООО «Ромашка» Иванову И.И.

                Прошу рассмотреть вопрос.

                Подпись: Петров П.П.""", DocumentType.MEMO);
        String org = result.requisites().get(RequisiteKey.ORGANIZATION);
        assertNotNull(org);
        assertTrue(org.contains("Ромашка"));
    }
}
