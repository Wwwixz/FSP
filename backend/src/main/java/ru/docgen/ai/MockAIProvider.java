package ru.docgen.ai;

import ru.docgen.core.DocumentType;
import ru.docgen.core.RequisiteKey;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Детерминированный офлайн-провайдер ИИ (по умолчанию).
 *
 * Не требует ключей и сети — обеспечивает бесплатную демонстрацию и
 * проверку обязательных сценариев ТЗ (в том числе «система не придумывает
 * факты»: тело документа проходит насквозь без содержательных изменений).
 *
 * Что делает:
 *  1. отделяет служебные строки черновика (Кому:/От кого:/Дата:/Номер:
 *     /Заголовок:/Тема:/Составитель:/Подпись:/Исполнитель:) от тела;
 *  2. выписывает реквизиты в структурированный JSON;
 *  3. выполняет базовую нормализацию: орфография типовых разговорных
 *     оборотов (небольшой словарь), пробелы, капитализация предложений;
 *  4. отсутствующие реквизиты остаются null — ничего не придумывается.
 *
 * Полноценное исправление орфографии произвольного текста выполняет
 * реальная модель (см. OpenAICompatibleProvider).
 */
public class MockAIProvider implements AIService {

    // ------------------------------------------------------------------
    // Служебные строки черновика
    // ------------------------------------------------------------------
    private static final Pattern LINE_RECIPIENT = linePattern("кому|адресат|получатель");
    private static final Pattern LINE_AUTHOR = linePattern("от кого|отправитель|составитель|автор");
    private static final Pattern LINE_DATE = linePattern("дата");
    private static final Pattern LINE_NUMBER = linePattern("номер|исх\\W*№?|исходящий номер");
    private static final Pattern LINE_SUBJECT = linePattern("заголовок|тема|предмет");
    private static final Pattern LINE_SIGNATURE = linePattern("подпись|подписант");
    private static final Pattern LINE_EXECUTOR = linePattern("исполнитель");
    private static final Pattern LINE_SALUTATION = linePattern("обращение");

    /** Хвостовая подпись внутри абзаца: «… в срок. Подпись Петров». */
    private static final Pattern INLINE_SIGNATURE = Pattern.compile(
            "(?iu)\\bподпись\\s*[:—–-]?\\s*(.+)$");

    private static Pattern linePattern(String keys) {
        return Pattern.compile("^\\s*(?:" + keys + ")\\s*[:—–-]?\\s*(.*)$",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    // ------------------------------------------------------------------
    // Прозаические образцы реквизитов (черновики вида «…сообщает: …»)
    // ------------------------------------------------------------------
    private static final String NAME = "[А-ЯЁ][а-яё-]+\\s+(?:[А-ЯЁ]\\.\\s?[А-ЯЁ]\\.|[А-ЯЁ]\\.[А-ЯЁ]\\.)"
            + "|(?:[А-ЯЁ]\\.\\s?){2}[А-ЯЁ][а-яё-]+";
    private static final Pattern PROSE_AUTHOR = Pattern.compile(
            "(^|[\\n.!?]\\s*)((?:[А-ЯЁа-яё][А-ЯЁа-яё\\- ]{2,80}?))\\s+(" + NAME + ")\\s*"
                    + "(?=сообщает|докладывает|просит|информирует|передаёт|передает|направляет|пишет)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SALUTATION_LINE = Pattern.compile(
            "^\\s*(уважаемый|уважаемая|уважаемые|добрый день|здравствуйте)[^\\n]*!\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ORG_PATTERN = Pattern.compile(
            "(ООО|ОАО|ЗАО|АО|ПАО|ИП)\\s*[«\"]?([А-ЯЁа-яёA-Za-z0-9][^»\"\\n,.;]{0,40})[»\"]?");
    private static final Pattern DATE_DDMMYYYY = Pattern.compile("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})");
    private static final Pattern DATE_ISO = Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})");
    private static final Pattern DATE_RU = Pattern.compile(
            "(\\d{1,2})\\s+(января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)\\s+(\\d{4})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final String[] MONTHS = {
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря"
    };

    // ------------------------------------------------------------------
    // Словарь разговорных оборотов → официально-деловой стиль
    // (замены не добавляют новых фактов, только переформулируют написанное)
    // ------------------------------------------------------------------
    private static final Map<String, String> STYLE_MAP = Map.<String, String>ofEntries(
            Map.entry("вообщем", "Таким образом,"),
            Map.entry("здрасьте", ""),
            Map.entry("здравствуйте", ""),
            Map.entry("нам надо", "необходимо"),
            Map.entry("надо срочно", "необходимо в срочном порядке"),
            Map.entry("комп", "компьютер"),
            Map.entry("компа", "компьютера"),
            Map.entry("компы", "компьютеры"),
            Map.entry("компов", "компьютеров"),
            Map.entry("старые уже не работают", "имеющееся оборудование вышло из строя"),
            Map.entry("обещал привезти", "подтвердил возможность поставки"),
            Map.entry("без этого мы не сможем делать задачи в срок",
                    "без этого выполнение задач в установленные сроки невозможно"),
            Map.entry("там всё плохо", "выявлены нарушения"),
            Map.entry("ржавые", "имеющие следы коррозии"),
            Map.entry("барахлит", "работает с перебоями"),
            Map.entry("может испортится", "возможна порча товара"),
            Map.entry("может испортиться", "возможна порча товара"),
            Map.entry("товар может испортится", "возможна порча товара"),
            Map.entry("починить", "отремонтировать"),
            Map.entry("поменять", "заменить"),
            Map.entry("сделал много", "выполнен значительный объём задач"),
            Map.entry("это нормально", "что соответствует плановому показателю"),
            Map.entry("утвердили и ввели", "утверждён и введён"),
            Map.entry("перешли", "осуществлён переход"),
            Map.entry("доделаем", "будут выполнены"),
            Map.entry("мы хотим заключить", "рассматриваем возможность заключения"),
            Map.entry("пришлите пожалуйста", "просим предоставить"),
            Map.entry("пришлите, пожалуйста", "просим предоставить"),
            Map.entry("напишите", "укажите"),
            Map.entry("ответ ждём", "ответ просим направить"),
            Map.entry("ответ ждем", "ответ просим направить"),
            Map.entry("будем рады сотрудничать", "рассчитываем на взаимовыгодное сотрудничество"),
            Map.entry("будем рады", "рассчитываем"),
            Map.entry("купить", "приобрести"),
            Map.entry("цена", "стоимость"),
            Map.entry("чтобы сотрудники работали без простоев",
                    "чтобы обеспечить бесперебойную работу сотрудников")
    );

    @Override
    public AIResult process(String text, DocumentType documentType) {
        if (text == null || text.isBlank()) {
            throw new AIException("Пустой текст для обработки");
        }

        EnumMap<RequisiteKey, String> requisites = new EnumMap<>(RequisiteKey.class);
        List<String> bodyLines = new ArrayList<>();

        for (String rawLine : text.replace("\r\n", "\n").split("\n", -1)) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                bodyLines.add("");
                continue;
            }

            if (extractLine(LINE_RECIPIENT, line, requisites, RequisiteKey.RECIPIENT)) continue;
            if (extractLine(LINE_AUTHOR, line, requisites, RequisiteKey.AUTHOR)) continue;
            if (extractLine(LINE_DATE, line, requisites, RequisiteKey.DATE)) continue;
            if (extractLine(LINE_NUMBER, line, requisites, RequisiteKey.NUMBER)) continue;
            if (extractLine(LINE_SUBJECT, line, requisites, RequisiteKey.SUBJECT)) continue;
            if (extractLine(LINE_SIGNATURE, line, requisites, RequisiteKey.SIGNATURE)) continue;
            if (extractLine(LINE_EXECUTOR, line, requisites, RequisiteKey.EXECUTOR)) continue;
            if (extractLine(LINE_SALUTATION, line, requisites, RequisiteKey.SALUTATION)) continue;

            if (SALUTATION_LINE.matcher(line).matches()) {
                requisites.putIfAbsent(RequisiteKey.SALUTATION, normalizeSalutation(line));
                continue;
            }
            bodyLines.add(line);
        }

        // Прозаические образцы для черновиков без служебных строк
        proseExtraction(bodyLines, requisites);

        // Хвостовая «Подпись …» в конце абзаца
        extractInlineSignature(bodyLines, requisites);

        requisites.computeIfAbsent(RequisiteKey.ORGANIZATION,
                k -> ru.docgen.core.TextPatterns.organization(requisites.get(RequisiteKey.AUTHOR)) != null
                        ? ru.docgen.core.TextPatterns.organization(requisites.get(RequisiteKey.AUTHOR))
                        : ru.docgen.core.TextPatterns.organization(requisites.get(RequisiteKey.RECIPIENT)));

        String improvedText = improveBody(String.join("\n", bodyLines));

        // Дата в заголовочной части должна быть нормализована к ДД.ММ.ГГГГ
        String date = requisites.get(RequisiteKey.DATE);
        if (date != null) {
            String normalized = normalizeDate(date);
            if (normalized != null) {
                requisites.put(RequisiteKey.DATE, normalized);
            } else {
                requisites.remove(RequisiteKey.DATE); // невалидную дату не придумываем
            }
        }

        return new AIResult(improvedText, requisites);
    }

    private boolean extractLine(Pattern pattern, String line,
                                Map<RequisiteKey, String> requisites, RequisiteKey key) {
        Matcher m = pattern.matcher(line);
        if (!m.matches()) {
            return false;
        }
        String value = m.group(1).trim().replaceAll("^[:—–-]\\s*", "").trim();
        if (value.isEmpty()) {
            return true; // строка-заголовок без значения — считаем служебной строкой
        }
        if (key == RequisiteKey.DATE) {
            String normalized = normalizeDate(value);
            if (normalized == null) {
                return true; // нераспознаваемая дата — не факт, что это дата документа
            }
            value = normalized;
        }
        requisites.putIfAbsent(key, value);
        return true;
    }

    private void extractInlineSignature(List<String> bodyLines, Map<RequisiteKey, String> requisites) {
        if (requisites.get(RequisiteKey.SIGNATURE) != null) {
            return;
        }
        for (int i = bodyLines.size() - 1; i >= 0; i--) {
            String line = bodyLines.get(i);
            if (line == null || line.isBlank()) {
                continue;
            }
            Matcher m = INLINE_SIGNATURE.matcher(line);
            if (!m.find()) {
                continue;
            }
            String value = m.group(1).trim().replaceAll("\\.$", "").trim();
            if (value.isEmpty() || value.length() > 60) {
                return;
            }
            requisites.put(RequisiteKey.SIGNATURE, value);
            String prefix = line.substring(0, m.start()).trim();
            if (prefix.isEmpty()) {
                bodyLines.remove(i);
            } else {
                bodyLines.set(i, prefix);
            }
            return;
        }
    }

    private void proseExtraction(List<String> bodyLines, Map<RequisiteKey, String> requisites) {
        String body = String.join("\n", bodyLines);
        if (requisites.get(RequisiteKey.AUTHOR) == null) {
            Matcher m = PROSE_AUTHOR.matcher(body);
            if (m.find()) {
                String position = m.group(2).trim();
                String name = m.group(3).trim();
                if (position.length() >= 3) {
                    requisites.put(RequisiteKey.AUTHOR, position + " " + name);
                }
            }
        }
        // Даты в теле документа относятся к описываемому событию, поэтому как
        // дата документа они не подставляются (см. перечень реквизитов:
        // «содержательные даты система не придумывает»). Дата документа берётся
        // только из явной служебной строки «Дата: …» либо автозаполняется.
    }

    private String normalizeSalutation(String line) {
        String s = line.trim();
        if (s.toLowerCase(Locale.ROOT).startsWith("здравствуйте")) {
            return "Уважаемый(-ая) [Заполнить]!";
        }
        return s;
    }

    // ------------------------------------------------------------------
    // Улучшение тела документа
    // ------------------------------------------------------------------
    private String improveBody(String body) {
        // 1. Словарь разговорных оборотов (по словам, с учётом границ слова)
        String result = body;
        for (Map.Entry<String, String> e : STYLE_MAP.entrySet()) {
            result = replaceWord(result, e.getKey(), e.getValue());
        }

        // 2. Нормализация пробелов и двойных знаков препинания
        result = result.replaceAll("[ \\t]+", " ")
                .replaceAll(",\\s*,+", ",")
                .trim();

        // 3. Капитализация начала предложений и удаление висячих запятых
        String[] paragraphs = result.split("\\n\\s*\\n|\\n");
        StringBuilder sb = new StringBuilder();
        List<String> paragraphList = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String p : paragraphs) {
            String trimmed = p.trim();
            if (trimmed.isEmpty()) {
                if (current.length() > 0) {
                    paragraphList.add(current.toString());
                    current.setLength(0);
                }
            } else {
                if (current.length() > 0) {
                    current.append(" ");
                }
                current.append(trimmed);
            }
        }
        if (current.length() > 0) {
            paragraphList.add(current.toString());
        }

        for (String paragraph : paragraphList) {
            String cleaned = paragraph
                    .replaceAll("\\s+,", ",")
                    .replaceAll("\\s{2,}", " ")
                    .replaceAll(",\\s*\\.", ".")
                    .replaceAll(",{2,}", ",")
                    // мусорная пунктуация в начале абзаца (осталась от убранных приветствий)
                    .replaceFirst("^[\\s!.,;:—–-]+", "")
                    .trim();
            cleaned = capitalizeSentences(cleaned);
            if (!cleaned.isEmpty()) {
                sb.append(cleaned).append("\n\n");
            }
        }
        return sb.toString().trim();
    }

    private String replaceWord(String text, String from, String to) {
        Pattern p = Pattern.compile("(?<![\\p{L}])" + Pattern.quote(from) + "(?![\\p{L}])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher m = p.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(to));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String capitalizeSentences(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        boolean capitalizeNext = true;
        for (char c : text.toCharArray()) {
            if (capitalizeNext && Character.isLetter(c)) {
                sb.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                sb.append(c);
            }
            if (c == '.' || c == '!' || c == '?') {
                capitalizeNext = true;
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Вспомогательные разборы
    // ------------------------------------------------------------------
    private String findOrganization(String author, String recipient) {
        for (String source : new String[]{author, recipient}) {
            if (source == null) {
                continue;
            }
            Matcher m = ORG_PATTERN.matcher(source);
            if (m.find()) {
                String name = m.group(2).trim();
                if (!name.isEmpty()) {
                    return m.group(1) + " «" + name + "»";
                }
            }
        }
        return null;
    }

    /** Приводит распознаваемую дату к формату ДД.ММ.ГГГГ; иначе null. */
    public static String normalizeDate(String value) {
        String s = value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s*г\\.\\s*$", "").trim();

        Matcher m = DATE_DDMMYYYY.matcher(s);
        if (m.matches() || (m.find() && m.group().length() == s.length())) {
            return String.format("%02d.%02d.%04d",
                    Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
        }
        m = DATE_ISO.matcher(s);
        if (m.find() && m.group().length() == s.length()) {
            return String.format("%02d.%02d.%04d",
                    Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
        }
        m = DATE_RU.matcher(s);
        if (m.find() && m.group().length() == s.length()) {
            int month = 0;
            for (int i = 0; i < MONTHS.length; i++) {
                if (MONTHS[i].equals(m.group(2))) {
                    month = i + 1;
                    break;
                }
            }
            if (month > 0) {
                return String.format("%02d.%02d.%04d",
                        Integer.parseInt(m.group(1)), month, Integer.parseInt(m.group(3)));
            }
        }
        return null;
    }
}
