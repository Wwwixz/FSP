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
                                  List<String> missing,
                                  List<String> warnings) {
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
     * @param stampImage     опционально — base64-dataURL печати организации
     */
    public record RequisitesUpdateRequest(java.util.Map<String, String> values,
                                          String signatureImage,
                                          String photoImage,
                                          String stampImage) {
    }

    /**
     * @param finalText       опционально — отредактированный пользователем текст
     * @param signatureImage  опционально — base64-dataURL изображения подписи
     *                        (например "data:image/png;base64,iVBORw0KGgo...")
     * @param photoImage      опционально — base64-dataURL фото автора
     * @param stampImage      опционально — base64-dataURL печати организации
     * @param templateOptions опционально — параметры «своего шаблона»
     *                        (font, fontSize, lineSpacing, indent, align,
     *                        header, headerAlign, footer)
     * @param format          "docx" (по умолчанию) или "pdf"
     * @param origin          адрес сайта (window.location.origin) — из него
     *                        строится ссылка в QR-коде проверки подлинности
     */
    public record GenerateRequest(String finalText, String signatureImage, String photoImage,
                                  String stampImage,
                                  java.util.Map<String, String> templateOptions,
                                  String format,
                                  String origin,
                                  String uploadedTemplate) {
    }

    public record GenerateResponse(boolean success,
                                   String documentId,
                                   String fileName,
                                   String downloadUrl,
                                   List<String> warnings,
                                   String previewUrl,
                                   String qrDataUrl) {
    }

    /** Запрос отправки готового документа по почте. */
    public record EmailRequest(String to) {
    }

    public record EmailResponse(boolean success, String message) {
    }

    /** Инструкция ИИ на доработку улучшенного текста. */
    public record RefineRequest(String instruction) {
    }

    public record RefineResponse(boolean success, String improvedText, String warning) {
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

    /**
     * Результат разбора готового файла пользователя (DOCX/PDF/TXT).
     *
     * @param text         извлечённый текст черновика
     * @param documentType определённый по тексту тип документа или null
     * @param warning      предупреждение (например, тип не удалось определить)
     */
    public record ExtractResponse(boolean success, String text, String documentType, String warning) {
    }

    public record HealthDto(boolean success, String status, String aiProvider,
                            boolean aiConfigured, boolean simulateFailure) {
    }

    public record GeneratedDocumentDto(String id, String fileName, String documentType,
                                       String templateId, String createdAt, String downloadUrl) {
    }
}
