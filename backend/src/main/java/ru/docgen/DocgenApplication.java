package ru.docgen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import ru.docgen.config.DotEnv;

@SpringBootApplication
public class DocgenApplication {

    public static void main(String[] args) {
        // Локальные секреты из .env (см. DotEnv). Устанавливаем до старта Spring,
        // чтобы плейсхолдеры ${AI_*} в application.yml могли их увидеть.
        DotEnv.loadFromWorkingDirectory();
        SpringApplication.run(DocgenApplication.class, args);
    }
}
