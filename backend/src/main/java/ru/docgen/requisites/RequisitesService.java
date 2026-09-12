package ru.docgen.requisites;

import org.springframework.stereotype.Service;
import ru.docgen.core.DocumentType;
import ru.docgen.core.ProcessedDocument;
import ru.docgen.core.RequisiteKey;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Модуль валидации реквизитов (раздел 1.3 постановки задачи).
 *
 * Правила (из стартового материала «Перечень обязательных реквизитов»):
 * <ul>
 *   <li>обязательный реквизит, отсутствующий в тексте, никогда не придумывается;</li>
 *   <li>автозаполнение допускается только для даты (текущая дата формирования)
 *       и номера (порядковый номер) — и только если пользователь не указал их сам;</li>
 *   <li>если пользователь не заполнил отсутствующий реквизит, в итоговом
 *       документе остаётся явная пометка вида «[Заполнить]» / «[Адресат]».</li>
 * </ul>
 */
@Service
public class RequisitesService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /** Формирует статус проверки реквизитов (без автозаполнения — только диагностика). */
    public RequisitesValidation validate(DocumentType type, Map<RequisiteKey, String> requisites) {
        List<RequisiteKey> missing = new ArrayList<>();
        List<RequisitesValidation.RequisiteCheck> checks = new ArrayList<>();
        for (RequisiteKey key : type.getRequiredRequisites()) {
            String label = type.labelOf(key);
            if (isFilled(requisites.get(key))) {
                checks.add(RequisitesValidation.RequisiteCheck.done(key, label));
            } else {
                checks.add(RequisitesValidation.RequisiteCheck.missing(key, label));
                missing.add(key);
            }
        }
        return new RequisitesValidation(missing, checks);
    }

    /**
     * Подготавливает реквизиты к формированию документа:
     * автозаполняет дату (текущей датой) и номер (порядковым) там, где это
     * разрешено, остальное оставляет как есть — незаполненное ляжет в DOCX
     * явной пометкой.
     */
    public Map<RequisiteKey, String> prepareForGeneration(ProcessedDocument session) {
        DocumentType type = session.getDocumentType();
        Map<RequisiteKey, String> requisites = new EnumMap<>(RequisiteKey.class);
        requisites.putAll(session.getRequisites());

        if (type.getRequiredRequisites().contains(RequisiteKey.DATE)
                && !isFilled(requisites.get(RequisiteKey.DATE))) {
            requisites.put(RequisiteKey.DATE, LocalDate.now().format(DATE_FORMAT));
        }
        if (!type.getNumberSuffix().isEmpty()
                && type.getRequiredRequisites().contains(RequisiteKey.NUMBER)
                && !isFilled(requisites.get(RequisiteKey.NUMBER))) {
            requisites.put(RequisiteKey.NUMBER, session.nextNumber() + "-" + type.getNumberSuffix());
        }
        return requisites;
    }

    /** Явная пометка незаполненного реквизита для итогового документа. */
    public String displayValue(RequisiteKey key, String value) {
        if (isFilled(value)) {
            return value;
        }
        return key.getPlaceholder();
    }

    /** Валидация значения даты, введённого пользователем вручную. */
    public boolean isValidDateFormat(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            LocalDate.parse(value.trim(), DATE_FORMAT);
            return true;
        } catch (Exception e) {
            // Допускаем даты, распознаваемые MockAIProvider (например «12 марта 2025»)
            return ru.docgen.ai.MockAIProvider.normalizeDate(value) != null;
        }
    }

    /**
     * Валидация формата номера документа:
     * - не может быть пустым текстом без цифр
     * - допустимые символы: цифры, буквы (рус/лат), дефис, слэш, обратный слэш
     * Примеры: 47-СЗ, 12/23, 123
     */
    public boolean isValidNumberFormat(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String trimmed = value.trim();
        if (!trimmed.chars().anyMatch(Character::isDigit)) {
            return false;
        }
        return trimmed.matches("[\\dА-Яа-яA-Za-z\\-/\\\\]+");
    }

    public static boolean isFilled(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        return !v.isEmpty() && !v.equals("null") && !v.equals("[заполнить]") && !v.equals("н/д");
    }
}
