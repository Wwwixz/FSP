package ru.docgen.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки ИИ-модуля (см. application.yml и .env).
 */
@ConfigurationProperties(prefix = "ai")
public class AIProperties {

    /** mock | openai | gigachat */
    private String provider = "mock";

    private String apiKey = "";

    private String baseUrl = "https://api.openai.com/v1";

    private String model = "gpt-4o-mini";

    private long timeoutMs = 60_000;

    private boolean jsonMode = true;

    // --- GigaChat (Сбер, OpenAI-совместимый) ---
    /** Authorization key из кабинета Сбера (base64 client_id:client_secret). */
    private String gigachatAuthKey = "";
    /** scope: GIGACHAT_API_PERS / GIGACHAT_API_CORP / GIGACHAT_API_B2B. */
    private String gigachatScope = "GIGACHAT_API_PERS";
    /** OAuth-эндпоинт получения access_token. */
    private String gigachatOauthUrl = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth";
    /** Базовый URL API GigaChat. */
    private String gigachatBaseUrl = "https://api.giga.chat";
    private String gigachatModel = "GigaChat";
    /**
     * Dev-флаг: отключить строгую проверку TLS (сертификаты НУЦ Минцифры
     * не доверены из-пределов РФ-инфраструктуры). НЕ включать в продакшене!
     */
    private boolean gigachatInsecureSsl = false;

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

    public String getGigachatAuthKey() {
        return gigachatAuthKey;
    }

    public void setGigachatAuthKey(String gigachatAuthKey) {
        this.gigachatAuthKey = gigachatAuthKey;
    }

    public String getGigachatScope() {
        return gigachatScope;
    }

    public void setGigachatScope(String gigachatScope) {
        this.gigachatScope = gigachatScope;
    }

    public String getGigachatOauthUrl() {
        return gigachatOauthUrl;
    }

    public void setGigachatOauthUrl(String gigachatOauthUrl) {
        this.gigachatOauthUrl = gigachatOauthUrl;
    }

    public String getGigachatBaseUrl() {
        return gigachatBaseUrl;
    }

    public void setGigachatBaseUrl(String gigachatBaseUrl) {
        this.gigachatBaseUrl = gigachatBaseUrl;
    }

    public String getGigachatModel() {
        return gigachatModel;
    }

    public void setGigachatModel(String gigachatModel) {
        this.gigachatModel = gigachatModel;
    }

    public boolean isGigachatInsecureSsl() {
        return gigachatInsecureSsl;
    }

    public void setGigachatInsecureSsl(boolean gigachatInsecureSsl) {
        this.gigachatInsecureSsl = gigachatInsecureSsl;
    }

    public boolean isGigachatConfigured() {
        return gigachatAuthKey != null && !gigachatAuthKey.isBlank();
    }
}
