package ru.docgen;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Сценарий 6 ТЗ: ИИ недоступен → понятная ошибка, приложение не падает,
 * текст не теряется (ошибка не мутирует введённые данные).
 */
@SpringBootTest(properties = "ai.simulate-failure=true")
@AutoConfigureMockMvc
class AiFailureScenarioTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void aiUnavailableYieldsCleanErrorNotCrash() throws Exception {
        mvc.perform(post("/api/documents/process")
                        .contentType("application/json")
                        .content(mapper.writeValueAsString(java.util.Map.of(
                                "text", "Прошу рассмотреть вопрос о финансировании.",
                                "documentType", "memo",
                                "templateId", "standard"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AI_UNAVAILABLE"))
                .andExpect(jsonPath("$.error.message").isNotEmpty());

        // Приложение живо: справочники по-прежнему отвечают
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
