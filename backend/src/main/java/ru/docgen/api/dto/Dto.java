package ru.docgen.api.dto;

import java.util.List;

public final class Dto {

    private Dto() {
    }

    /** Единый формат ошибки для фронтенда. */
    public record ApiError(boolean success, ErrorBody error) {
        public record ErrorBody(String code, String message) {
        }

        public static ApiError of(String code, String message) {
            return new ApiError(false, new ErrorBody(code, message));
        }
    }

    public record DocumentTypeDto(String id,
                                  String label,
                                  String titleLine,
                                  List<RequisiteMeta> required,
                                  List<RequisiteMeta> optional) {
    }

    public record RequisiteMeta(String key, String label) {
    }

    public record TemplateDto(String id, String title, String description) {
    }

    public record RequisiteCheckDto(String key, String label, String status, String hint) {
        public static RequisiteCheckDto from(ru.docgen.requisites.RequisitesValidation.RequisiteCheck c) {
            return new RequisiteCheckDto(c.key(), c.label(), c.status(), c.hint());
        }
    }

    public record ProcessRequest(String text, String documentType, String templateId) {
    }

    public record ProcessResponse(String documentId,
                                  String improvedText,
                                  String documentType,
                                  String templateId,
                                  RequisitesDto requisites,
                                  List<RequisiteCheckDto> checks,
                                  List<String> missing) {
    }

    /** Реквизиты в JSON-виде (строчные ключи), null для отсутствующих. */
    public record RequisitesDto(String recipient, String author, String subject, String date,
                                String number, String signature, String salutation,
                                String executor, String organization) {
    }

    /**
     * @param values         обновлённые значения реквизитов
     * @param signatureImage опционально — base64-dataURL изображения подписи
     * @param photoImage     опционально — base64-dataURL фото автора
     */
    public record RequisitesUpdateRequest(java.util.Map<String, String> values,
                                          String signatureImage,
                                          String photoImage) {
    }

    /**
     * @param finalText      опционально — отредактированный пользователем текст
     * @param signatureImage опционально — base64-dataURL изображения подписи
     *                       (например "data:image/png;base64,iVBORw0KGgo...")
     * @param photoImage     опционально — base64-dataURL фото автора
     */
    public record GenerateRequest(String finalText, String signatureImage, String photoImage) {
    }

    public record GenerateResponse(boolean success,
                                   String documentId,
                                   String fileName,
                                   String downloadUrl,
                                   List<String> warnings) {
    }

    public record SedSendRequest(String apiUrl, String apiKey, String systemName) {
    }

    public record SedSendResponse(boolean success, String message) {
    }

    public record ValidateRequest(String text, String documentType) {
    }

    public record ValidateResponse(RequisitesDto requisites,
                                   List<RequisiteCheckDto> checks,
                                   List<String> missing) {
    }

    public record DemoDraftDto(String id, String documentType, String title, String text) {
    }

    public record HealthDto(boolean success, String status, String aiProvider,
                            boolean aiConfigured, boolean simulateFailure) {
    }

    public record GeneratedDocumentDto(String id, String fileName, String documentType,
                                       String templateId, String createdAt) {
    }
}
