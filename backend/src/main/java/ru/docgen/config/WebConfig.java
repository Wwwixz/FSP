package ru.docgen.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS для локальной разработки (frontend на Astro dev-сервере).
 * В docker-сборке запросы идут через reverse-proxy того же origin.
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties properties;

    public WebConfig(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(properties.getCors().getAllowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*");
    }

    /**
     * SPA-fallback: в docker/облачной сборке фронтенд лежит в static/ этого же jar,
     * и любой путь без точки (/, /settings, /documents…) отдаёт index.html.
     * Точные маппинги контроллеров (/api/**) всегда имеют приоритет.
     */
    @Override
    public void addViewControllers(@NonNull org.springframework.web.servlet.config.annotation.ViewControllerRegistry registry) {
        registry.addViewController("/{path:[^\\.]*}").setViewName("forward:/index.html");
    }
}
