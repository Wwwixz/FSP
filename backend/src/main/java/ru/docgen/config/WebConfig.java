package ru.docgen.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

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
     * SPA-fallback для облачной сборки: фронтенд лежит в static/ этого же jar.
     * Настоящие файлы (ассеты с точкой) отдаются как есть; пути без точки
     * (/, /settings, /documents…) получают index.html. Маппинги контроллеров
     * (/api/**) всегда имеют приоритет над ресурсами.
     */
    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(@NonNull String resourcePath, @NonNull Resource location) throws IOException {
                        // страницы Astro — пути без расширения: отдаём index.html
                        if (resourcePath.isBlank() || !resourcePath.contains(".")) {
                            return location.createRelative("index.html");
                        }
                        Resource requested = location.createRelative(resourcePath);
                        return requested.exists() && requested.isReadable() ? requested : null;
                    }
                });
    }
}
