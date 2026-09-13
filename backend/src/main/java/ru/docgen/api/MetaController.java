package ru.docgen.api;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    /**
     * Лёгкий список демо-черновиков: без полных текстов (только анонсы).
     * Полный текст — /api/demo-drafts/{id}. Экономит трафик на публичном туннеле.
     */
    @GetMapping("/demo-drafts")
    public List<Dto.DemoDraftDto> demoDrafts() {
        List<Dto.DemoDraftDto> drafts = new ArrayList<>();
        for (DemoResource resource : findDemoResources()) {
            String shortPreview = resource.text.replace("\r\n", " ").replace('\n', ' ')
                    .replaceAll("\\s{2,}", " ").trim();
            if (shortPreview.length() > 140) {
                shortPreview = shortPreview.substring(0, 140) + "…";
            }
            String fileName = resource.fileName.replace(".txt", "");
            drafts.add(new Dto.DemoDraftDto(
                    resource.typeId + "-" + fileName,
                    resource.typeId,
                    resource.typeLabel + " — пример " + fileName,
                    null,
                    shortPreview));
        }
        drafts.sort(Comparator.comparing(Dto.DemoDraftDto::id));
        return drafts;
    }

    /** Полный текст одного демо-черновика по id вида «memo-1». */
    @GetMapping("/demo-drafts/{id}")
    public Dto.DemoDraftDto demoDraft(@PathVariable String id) {
        return findDemoResources().stream()
                .filter(r -> (r.typeId + "-" + r.fileName.replace(".txt", "")).equals(id))
                .findFirst()
                .map(r -> new Dto.DemoDraftDto(id, r.typeId, r.typeLabel + " — пример "
                        + r.fileName.replace(".txt", ""), r.text, null))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Демо-черновик не найден: " + id));
    }

    private record DemoResource(String typeId, String typeLabel, String fileName, String text) {
    }

    private List<DemoResource> findDemoResources() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources;
        try {
            resources = resolver.getResources("classpath:demo/*/*.txt");
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Не удалось загрузить демонстрационные черновики");
        }
        List<DemoResource> result = new ArrayList<>();
        for (Resource resource : resources) {
            try {
                String path = resource.getURL().getPath();
                String typeId = path.substring(path.lastIndexOf("demo/") + 5, path.lastIndexOf('/'));
                String fileName = path.substring(path.lastIndexOf('/') + 1);
                String text = FileCopyUtils.copyToString(
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
                DocumentType type = DocumentType.fromId(typeId);
                result.add(new DemoResource(typeId, type.getLabel(), fileName, text));
            } catch (Exception e) {
                // Повреждённый демо-файл не ломает список остальных
            }
        }
        return result;
    }
}
