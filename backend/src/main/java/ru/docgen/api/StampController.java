package ru.docgen.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.docgen.docx.StampGenerator;

/**
 * Генерация печати организации по наименованию и городу.
 */
@RestController
@RequestMapping("/api/stamp")
public class StampController {

    private final StampGenerator stampGenerator;

    public StampController(StampGenerator stampGenerator) {
        this.stampGenerator = stampGenerator;
    }

    public record StampRequest(String organization, String city) {
    }

    public record StampResponse(boolean success, String dataUrl) {
    }

    @PostMapping("/preview")
    public StampResponse preview(@RequestBody StampRequest request) {
        if (request == null || request.organization() == null || request.organization().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "EMPTY_ORGANIZATION: Введите наименование организации для печати");
        }
        String dataUrl = stampGenerator.generate(request.organization(), request.city());
        if (dataUrl == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Не удалось сгенерировать печать");
        }
        return new StampResponse(true, dataUrl);
    }
}
