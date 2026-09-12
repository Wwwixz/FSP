package ru.docgen.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Фабрика ИИ-сервиса. Провайдер выбирается настройкой AI_PROVIDER:
 * <ul>
 *   <li>mock (по умолчанию) — детерминированный офлайн-режим;</li>
 *   <li>openai — OpenAI-совместимый API, ключ задаётся через AI_API_KEY.</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(AIProperties.class)
public class AIConfiguration {

    @Bean
    public AIService aiService(AIProperties properties) {
        // Демонстрация сценария 6 ТЗ: явная имитация недоступности ИИ
        if (properties.isSimulateFailure()) {
            return (text, documentType) -> {
                throw new AIUnavailableException(
                        "Симулированная недоступность ИИ (AI_SIMULATE_FAILURE=true)");
            };
        }
        if ("openai".equalsIgnoreCase(properties.getProvider()) && properties.isOpenAiConfigured()) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout((int) properties.getTimeoutMs());
            factory.setReadTimeout((int) properties.getTimeoutMs());
            RestClient restClient = RestClient.builder()
                    .baseUrl(properties.getBaseUrl())
                    .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                    .defaultHeader("Content-Type", "application/json")
                    .requestFactory(factory)
                    .build();
            return new OpenAICompatibleProvider(properties, restClient);
        }
        return new MockAIProvider();
    }
}
