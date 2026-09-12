package ru.docgen.ai;

import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.security.cert.X509Certificate;

/**
 * Фабрика ИИ-сервиса. Провайдер выбирается настройкой AI_PROVIDER:
 * <ul>
 *   <li>mock (по умолчанию) — детерминированный офлайн-режим;</li>
 *   <li>openai — OpenAI-совместимый API, ключ задаётся через AI_API_KEY;</li>
 *   <li>gigachat — нейросеть GigaChat (Сбер): OAuth2 по Authorization key
 *       (AI_GIGACHAT_AUTH_KEY), access-токен обновляется автоматически.
 *       Для локальной разработки при сертификатах НУЦ Минцифры есть dev-флаг
 *       AI_GIGACHAT_INSECURE_SSL=true (TrustAll).</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(AIProperties.class)
public class AIConfiguration {

    @Bean
    public AIService aiService(AIProperties properties) {
        if (properties.isSimulateFailure()) {
            return (text, documentType) -> {
                throw new AIUnavailableException(
                        "Симулированная недоступность ИИ (AI_SIMULATE_FAILURE=true)");
            };
        }
        if ("openai".equalsIgnoreCase(properties.getProvider()) && properties.isOpenAiConfigured()) {
            RestClient restClient = RestClient.builder()
                    .baseUrl(properties.getBaseUrl())
                    .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                    .defaultHeader("Content-Type", "application/json")
                    .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory())
                    .build();
            return new OpenAICompatibleProvider(properties, restClient);
        }
        if ("gigachat".equalsIgnoreCase(properties.getProvider()) && properties.isGigachatConfigured()) {
            HttpComponentsClientHttpRequestFactory factory = properties.isGigachatInsecureSsl()
                    ? trustAllHttpComponentsFactory()
                    : new HttpComponentsClientHttpRequestFactory();
            RestClient restClient = RestClient.builder()
                    .requestFactory(factory)
                    .build();
            return new GigaChatProvider(properties, restClient);
        }
        return new MockAIProvider();
    }

    /**
     * Фабрика HttpClient5 с TrustAll SSL — ТОЛЬКО для локальной разработки
     * (сертификаты НУЦ Минцифры не распознаются за пределами РФ-инфры).
     */
    private static HttpComponentsClientHttpRequestFactory trustAllHttpComponentsFactory() {
        try {
            var sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial((X509Certificate[] chain, String authType) -> true)
                    .build();
            var sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
                    .setSslContext(sslContext)
                    .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                    .build();
            HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(sslSocketFactory)
                    .build();
            return new HttpComponentsClientHttpRequestFactory(
                    HttpClients.custom()
                            .setConnectionManager(cm)
                            .build());
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось создать TrustAll HttpClient", e);
        }
    }
}
