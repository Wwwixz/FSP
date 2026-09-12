package ru.docgen.api;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import ru.docgen.ai.AIProperties;
import ru.docgen.api.dto.Dto;
import ru.docgen.core.DocumentType;
import ru.docgen.core.RequisiteKey;
import ru.docgen.core.TemplateKind;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Справочные endpoints: здоровье сервиса, типы документов, шаблоны,
 * демонстрационные черновики из стартовых материалов.
 */
@RestController
@RequestMapping("/api")
public class MetaController {

    private final AIProperties aiProperties;

    public MetaController(AIProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    @GetMapping("/health")
    public Dto.HealthDto health() {
        boolean openAi = "openai".equalsIgnoreCase(aiProperties.getProvider())
                && aiProperties.isOpenAiConfigured();
        boolean gigachat = "gigachat".equalsIgnoreCase(aiProperties.getProvider())
                && aiProperties.isGigachatConfigured();
        String provider = openAi ? "openai" : gigachat ? "gigachat" : "mock";
        boolean configured = openAi || gigachat;
        return new Dto.HealthDto(true, "ok", provider, configured, aiProperties.isSimulateFailure());
    }

    @GetMapping("/document-types")
    public List<Dto.DocumentTypeDto> documentTypes() {
        List<Dto.DocumentTypeDto> result = new ArrayList<>();
        for (DocumentType type : DocumentType.values()) {
            result.add(new Dto.DocumentTypeDto(
                    type.getId(),
                    type.getLabel(),
                    type.getTitleLine(),
                    type.getRequiredRequisites().stream()
                            .map(k -> new Dto.RequisiteMeta(k.name().toLowerCase(), type.labelOf(k)))
                            .toList(),
                    type.getOptionalRequisites().stream()
                            .map(k -> new Dto.RequisiteMeta(k.name().toLowerCase(), type.labelOf(k)))
                            .toList()));
        }
        return result;
    }

    @GetMapping("/templates")
    public List<Dto.TemplateDto> templates() {
        return Arrays.stream(TemplateKind.values())
                .map(t -> new Dto.TemplateDto(t.getId(), t.getTitle(), t.getDescription()))
                .toList();
    }

    @GetMapping("/demo-drafts")
    public List<Dto.DemoDraftDto> demoDrafts() {
        List<Dto.DemoDraftDto> drafts = new ArrayList<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources;
        try {
            resources = resolver.getResources("classpath:demo/*/*.txt");
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Не удалось загрузить демонстрационные черновики");
        }
        for (Resource resource : resources) {
            try {
                String path = resource.getURL().getPath();
                String typeDir = path.substring(path.lastIndexOf("demo/") + 5, path.lastIndexOf('/'));
                String fileName = path.substring(path.lastIndexOf('/') + 1);
                String text = FileCopyUtils.copyToString(
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
                DocumentType type = DocumentType.fromId(typeDir);
                drafts.add(new Dto.DemoDraftDto(
                        type.getId() + "-" + fileName.replace(".txt", ""),
                        type.getId(),
                        type.getLabel() + " — пример " + fileName.replace(".txt", ""),
                        text));
            } catch (Exception e) {
                // Повреждённый демо-файл не ломает список остальных
            }
        }
        drafts.sort(Comparator.comparing(Dto.DemoDraftDto::id));
        return drafts;
    }
}
