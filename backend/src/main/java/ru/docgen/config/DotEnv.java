package ru.docgen.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Минимальный загрузчик {@code .env} для локального запуска.
 *
 * <p>Рекомендуемый способ конфигурации — переменные окружения ИИ/контейнера
 * (очевидный и безопасный). Но при запуске из IDE или {@code mvn spring-boot:run}
 * удобно держать секреты в gitignored {@code .env}: файл ищется в текущем каталоге
 * (backend/), в backend/.env и в корне проекта (родитель рабочего каталога).
 *
 * <p>Строки вида {@code AI_GIGACHAT_AUTH_KEY=...} устанавливаются системными
 * свойствами в двух формах: оригинальной ({@code AI_GIGACHAT_AUTH_KEY}, чтобы
 * плейсхолдеры {@code ${...}} в application.yml резолвились) и relaxed-формой
 * ({@code ai.gigachat-auth-key}, чтобы биндились в {@code @ConfigurationProperties}).
 * Существующие системные свойства и реальные переменные окружения имеют приоритет.
 */
public final class DotEnv {

    private static final Logger log = LoggerFactory.getLogger(DotEnv.class);

    private DotEnv() {
    }

    public static void loadFromWorkingDirectory() {
        String userDir = System.getProperty("user.dir");
        if (userDir == null || userDir.isBlank()) {
            return;
        }
        Path cwd = Paths.get(userDir).toAbsolutePath();
        load(cwd.resolve(".env"));
        load(cwd.resolve("backend").resolve(".env"));
        Path parent = cwd.getParent();
        if (parent != null) {
            load(parent.resolve(".env"));
        }
    }

    private static void load(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        int loaded = 0;
        try {
            for (String rawLine : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                    continue;
                }
                int eq = line.indexOf('=');
                String rawKey = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                if (rawKey.isEmpty() || value.isEmpty()) {
                    continue;
                }
                if (value.length() >= 2
                        && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                setIfAbsent(file, rawKey, value);
                loaded++;
            }
            log.info("Загружен файл настроек {}", file);
        } catch (IOException e) {
            log.warn("Не удалось прочитать {}: {}", file, e.getMessage());
        }
        if (loaded == 0) {
            log.info("Файл {} прочитан, переменных нет", file);
        }
    }

    private static void setIfAbsent(Path file, String rawKey, String value) {
        if (System.getProperty(rawKey) != null) {
            return;
        }
        System.setProperty(rawKey, value);
        // AI_GIGACHAT_AUTH_KEY -> ai.gigachat-auth-key
        String relaxed = rawKey.toLowerCase(Locale.ROOT).replace('_', '-');
        int dash = relaxed.indexOf('-');
        if (dash > 0) {
            relaxed = relaxed.substring(0, dash) + "." + relaxed.substring(dash + 1);
            if (System.getProperty(relaxed) == null) {
                System.setProperty(relaxed, value);
            }
        }
    }
}