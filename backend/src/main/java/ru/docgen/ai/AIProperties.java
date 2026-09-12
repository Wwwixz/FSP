package ru.docgen.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки ИИ-модуля (см. application.yml и .env).
 */
@ConfigurationProperties(prefix = "ai")
public class AIProperties {

    /** mock | openai */
    private String provider = "mock";

    private String apiKey = "";

    private String baseUrl = "https://api.openai.com/v1";

    private String model = "gpt-4o-mini";

    private long timeoutMs = 60_000;

    private boolean jsonMode = true;

    /** Имитация недоступности ИИ для демонстрации сценария 6. */
    private boolean simulateFailure = false;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public boolean isJsonMode() {
        return jsonMode;
    }

    public void setJsonMode(boolean jsonMode) {
        this.jsonMode = jsonMode;
    }

    public boolean isSimulateFailure() {
        return simulateFailure;
    }

    public void setSimulateFailure(boolean simulateFailure) {
        this.simulateFailure = simulateFailure;
    }

    public boolean isOpenAiConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
