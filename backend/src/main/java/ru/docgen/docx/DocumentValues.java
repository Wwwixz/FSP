package ru.docgen.docx;

import ru.docgen.core.DocumentType;
import ru.docgen.core.RequisiteKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Готовые к отображению значения реквизитов для построителя документа.
 *
 * Незаполненные обязательные реквизиты превращаются в явные пометки
 * («[Адресат]», «[Заполнить]» …), опциональные — просто опускаются.
 */
public class DocumentValues {

    private static final Pattern FIO = Pattern.compile(
            "([А-ЯЁ][а-яё-]+)\\s+([А-ЯЁ]\\.\\s?[А-ЯЁ]\\.)|((?:[А-ЯЁ]\\.\\s?){2}\\s?[А-ЯЁ][а-яё-]+)");
    private static final Pattern ORG = Pattern.compile(
            "(ООО|ОАО|ЗАО|АО|ПАО|ИП)\\s*[«\"]?([^»\"\\n,.;]{0,40})[»\"]?");

    private final DocumentType type;
    private final Map<RequisiteKey, String> requisites;
    private final String bodyText;
    private final String signatureImage;
    private final String photoImage;

    public DocumentValues(DocumentType type, Map<RequisiteKey, String> requisites, String bodyText,
                          String signatureImage, String photoImage) {
        this.type = type;
        this.requisites = requisites;
        this.bodyText = bodyText == null ? "" : bodyText;
        this.signatureImage = signatureImage;
        this.photoImage = photoImage;
    }

    public String signatureImageDataUrl() {
        return signatureImage;
    }

    public String photoImageDataUrl() {
        return photoImage;
    }

    /** Улучшенный содержательный текст документа. */
    public String bodyText() {
        return bodyText;
    }

    public DocumentType getType() {
        return type;
    }

    private String filled(RequisiteKey key) {
        String value = requisites.get(key);
        return RequisitesBridge.isFilled(value) ? value.trim() : null;
    }

    /** Адресат многострочным блоком (должность / организация / ФИО). */
    public List<String> recipientLines() {
        String value = filled(RequisiteKey.RECIPIENT);
        if (value == null) {
            return List.of(RequisiteKey.RECIPIENT.getPlaceholder());
        }
        if (value.contains("\n")) {
            return value.lines().map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        return smartSplitRecipient(value);
    }

    /** Значение «От кого» / «Отправитель» / «Составитель» одной строкой. */
    public String authorLine() {
        String value = filled(RequisiteKey.AUTHOR);
        return value != null ? value : RequisiteKey.AUTHOR.getPlaceholder();
    }

    /** Тема/заголовок документа. */
    public String subject() {
        String value = filled(RequisiteKey.SUBJECT);
        return value != null ? value : RequisiteKey.SUBJECT.getPlaceholder();
    }

    public String date() {
        String value = filled(RequisiteKey.DATE);
        return value != null ? value : RequisiteKey.DATE.getPlaceholder();
    }

    public String number() {
        String value = filled(RequisiteKey.NUMBER);
        return value != null ? value : RequisiteKey.NUMBER.getPlaceholder();
    }

    /** Обращение — только если задано (опциональный реквизит). */
    public String salutationOrNull() {
        return filled(RequisiteKey.SALUTATION);
    }

    /** Исполнитель — только если задан (опциональный реквизит). */
    public String executorOrNull() {
        return filled(RequisiteKey.EXECUTOR);
    }

    /** Должность подписанта, выделенная из значения «От кого». */
    public String positionLine() {
        String author = filled(RequisiteKey.AUTHOR);
        if (author == null) {
            return "[Заполнить]";
        }
        Matcher m = FIO.matcher(author);
        if (m.find()) {
            String position = author.replace(m.group().trim(), "").trim();
            if (!position.isEmpty()) {
                return position.replaceAll("[,;]$", "");
            }
        }
        return author;
    }

    /** И.О. Фамилия подписанта: из реквизита «Подпись» либо из «От кого». */
    public String signatureNameLine() {
        String signature = filled(RequisiteKey.SIGNATURE);
        if (signature != null) {
            return signature;
        }
        String author = filled(RequisiteKey.AUTHOR);
        if (author != null) {
            Matcher m = FIO.matcher(author);
            if (m.find()) {
                String g1 = m.group(1), g2 = m.group(2), g3 = m.group(3);
                if (g1 != null && g2 != null) {
                    return g1 + " " + g2.replace(" ", "");
                }
                if (g3 != null) {
                    return g3;
                }
            }
        }
        return RequisiteKey.SIGNATURE.getPlaceholder();
    }

    /** Организация отправителя (для подписи письма и колонтитулов). */
    public String organizationOrNull() {
        String org = filled(RequisiteKey.ORGANIZATION);
        if (org != null) {
            return org;
        }
        for (RequisiteKey key : new RequisiteKey[]{RequisiteKey.AUTHOR, RequisiteKey.RECIPIENT}) {
            String value = filled(key);
            if (value == null) {
                continue;
            }
            Matcher m = ORG.matcher(value);
            if (m.find() && !m.group(2).isBlank()) {
                return m.group(1) + " «" + m.group(2).trim() + "»";
            }
        }
        return null;
    }

    /**
     * Делит однострочный адресат на блок «должность / организация / ФИО»
     * (как в эталонных примерах). Если разобрать не удалось — возвращает
     * исходную строку одной строкой.
     */
    static List<String> smartSplitRecipient(String value) {
        String rest = value.trim();
        String org = null;
        Matcher orgMatcher = ORG.matcher(rest);
        if (orgMatcher.find() && !orgMatcher.group(2).isBlank()) {
            org = orgMatcher.group(1) + " «" + orgMatcher.group(2).trim() + "»";
            rest = (rest.substring(0, orgMatcher.start()) + " " + rest.substring(orgMatcher.end()))
                    .trim().replaceAll("\\s{2,}", " ");
        }
        String fio = null;
        Matcher fioMatcher = FIO.matcher(rest);
        if (fioMatcher.find()) {
            String g1 = fioMatcher.group(1), g2 = fioMatcher.group(2), g3 = fioMatcher.group(3);
            fio = (g1 != null && g2 != null) ? g1 + " " + g2.replace(" ", "") : g3;
            if (fio != null) {
                rest = (rest.substring(0, fioMatcher.start()) + " " + rest.substring(fioMatcher.end()))
                        .trim().replaceAll("\\s{2,}", " ");
            }
        }
        List<String> lines = new ArrayList<>();
        if (!rest.isEmpty()) {
            lines.add(rest);
        }
        if (org != null) {
            lines.add(org);
        }
        if (fio != null) {
            lines.add(fio);
        }
        return lines.isEmpty() ? List.of(value) : lines;
    }
}
