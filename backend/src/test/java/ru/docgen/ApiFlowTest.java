package ru.docgen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Полный пользовательский сценарий через REST API (mock-ИИ):
 * справочники → обработка черновика → заполнение реквизитов →
 * генерация → скачивание, а также сценарии ошибок.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiFlowTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    private static final String MEMO_DRAFT = """
            Кому: Генеральному директору ООО «Ромашка» Иванову И.И.
            От кого: начальник отдела аналитики Петров П.П.
            Дата: 12.03.2025
            Номер: 47-СЗ
            Заголовок: О закупке офисной техники

            Прошу выделить средства на закупку трёх компьютеров. Стоимость 180 000 рублей.

            Подпись: Петров П.П.""";

    @Test
    void referenceEndpointsWork() throws Exception {
        mvc.perform(get("/api/document-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].id").value("memo"))
                .andExpect(jsonPath("$[0].required.length()").value(6));

        mvc.perform(get("/api/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("standard"))
                .andExpect(jsonPath("$[1].id").value("modern"));

        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mvc.perform(get("/api/demo-drafts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(16));
    }

    @Test
    void fullFlowFromDraftToDownload() throws Exception {
        // 1. Обработка черновика
        MvcResult processResult = mvc.perform(post("/api/documents/process")
                        .contentType("application/json")
                        .content(mapper.writeValueAsString(java.util.Map.of(
                                "text", MEMO_DRAFT,
                                "documentType", "memo",
                                "templateId", "standard"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").isNotEmpty())
                .andExpect(jsonPath("$.improvedText").isNotEmpty())
                .andExpect(jsonPath("$.requisites.recipient").value(
                        "Генеральному директору ООО «Ромашка» Иванову И.И."))
                .andExpect(jsonPath("$.missing.length()").value(0))
                .andReturn();

        JsonNode processJson = mapper.readTree(processResult.getResponse().getContentAsString());
        String documentId = processJson.get("documentId").asText();

        // 2. Генерация DOCX
        MvcResult generateResult = mvc.perform(post("/api/documents/{id}/generate", documentId)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.downloadUrl").isNotEmpty())
                .andReturn();

        JsonNode generateJson = mapper.readTree(generateResult.getResponse().getContentAsString());
        String downloadUrl = generateJson.get("downloadUrl").asText();

        // 3. Скачивание
        MvcResult download = mvc.perform(get(downloadUrl))
                .andExpect(status().isOk())
                .andReturn();
        byte[] docx = download.getResponse().getContentAsByteArray();
        assertTrue(docx.length > 5000, "DOCX не пустой");
        assertTrue((char) (docx[0] & 0xFF) == 'P' && (char) (docx[1] & 0xFF) == 'K',
                "Файл является ZIP/DOCX");

        // 4. Документ появился в списке
        mvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileName").isNotEmpty())
                .andExpect(jsonPath("$[0].id").isNotEmpty());
    }

    @Test
    void missingRequisitesDetectedAndUserCanFillThem() throws Exception {
        // Черновик без адресата и без даты
        String draft = """
                От кого: начальник отдела аналитики Петров П.П.
                Заголовок: О закупке офисной техники

                Прошу выделить средства.

                Подпись: Петров П.П.""";

        MvcResult processResult = mvc.perform(post("/api/documents/process")
                        .contentType("application/json")
                        .content(mapper.writeValueAsString(java.util.Map.of(
                                "text", draft, "documentType", "memo", "templateId", "standard"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requisites.recipient").doesNotExist())
                // отсутствуют адресат, дата и номер
                .andExpect(jsonPath("$.missing.length()").value(3))
                .andReturn();

        JsonNode processJson = mapper.readTree(processResult.getResponse().getContentAsString());
        String documentId = processJson.get("documentId").asText();

        // Пользователь вводит адресат
        mvc.perform(post("/api/documents/{id}/requisites", documentId)
                        .contentType("application/json")
                        .content(mapper.writeValueAsString(java.util.Map.of(
                                "values", java.util.Map.of("recipient", "Иванову И.И.")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requisites.recipient").value("Иванову И.И."))
                // остались дата и номер — обе автозаполнятся при генерации
                .andExpect(jsonPath("$.missing.length()").value(2));

        // Пользователь редактирует текст (сценарий 7)
        mvc.perform(post("/api/documents/{id}/generate", documentId)
                        .contentType("application/json")
                        .content(mapper.writeValueAsString(java.util.Map.of(
                                "finalText", "Отредактированный пользователем текст."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void emptyTextRejected() throws Exception {
        mvc.perform(post("/api/documents/process")
                        .contentType("application/json")
                        .content("{\"text\": \"\", \"documentType\": \"memo\", \"templateId\": \"standard\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("EMPTY_TEXT"));
    }

    @Test
    void invalidTypeAndTemplateRejected() throws Exception {
        mvc.perform(post("/api/documents/process")
                        .contentType("application/json")
                        .content("{\"text\": \"Текст\", \"documentType\": \"unknown\", \"templateId\": \"standard\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_DOCUMENT_TYPE"));

        mvc.perform(post("/api/documents/process")
                        .contentType("application/json")
                        .content("{\"text\": \"Текст\", \"documentType\": \"memo\", \"templateId\": \"nope\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_TEMPLATE"));
    }

    @Test
    void unknownDocumentAndSessionGive404() throws Exception {
        mvc.perform(get("/api/documents/unknown-id/download"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ERROR"));

        mvc.perform(post("/api/documents/unknown-id/generate")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void allFourTypesEndToEnd() throws Exception {
        String[] types = {"memo", "report", "certificate", "letter"};
        String[] templates = {"standard", "modern"};
        String[] drafts = {
                MEMO_DRAFT,
                """
                Кому: Руководителю департамента продаж ООО «Ромашка» Сидорову С.С.
                От кого: специалист по контролю качества Николаева Н.Н.
                Дата: 14.03.2025
                Номер: 12-ДЗ
                Заголовок: О результатах проверки склада

                Докладываю, что проверка склада проведена.

                Подпись: Николаева Н.Н.""",
                """
                Дата: 15.03.2025
                Заголовок: О выполнении плана мероприятий

                Отделом развития выполнены задачи плана.

                Составитель: руководитель отдела развития Козлов К.К.""",
                """
                Кому: Генеральному директору ООО «Василёк» Фёдорову Ф.Ф.
                От кого: генеральный директор ООО «Ромашка» Иванов И.И.
                Дата: 16.03.2025
                Номер: 88-П
                Тема: О сотрудничестве в сфере поставок

                Просим предоставить коммерческое предложение.

                Подпись: Иванов И.И."""
        };
        for (int i = 0; i < types.length; i++) {
            for (String template : templates) {
                MvcResult result = mvc.perform(post("/api/documents/process")
                                .contentType("application/json")
                                .content(mapper.writeValueAsString(java.util.Map.of(
                                        "text", drafts[i], "documentType", types[i],
                                        "templateId", template))))
                        .andExpect(status().isOk())
                        .andReturn();
                String documentId = mapper.readTree(result.getResponse().getContentAsString())
                        .get("documentId").asText();
                mvc.perform(post("/api/documents/{id}/generate", documentId)
                                .contentType("application/json").content("{}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.success").value(true));
            }
        }
    }
}
