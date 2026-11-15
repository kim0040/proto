package proto.logic;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * [애플리케이션 로직 application logic] 사람이 친 글("내일", "11월 20일", "3일 뒤", "다음 주 금요일", "2026-11-20")을 날짜 하나로 읽는다.
 * 명령 메뉴(⌘K)의 "날짜로 이동"이 쓴다 (F2 조회 이동). 화면 상태를 모르고, 기준이 되는 "오늘"만 받아서 계산하는 순수 계산이다.
 * 읽는 규칙(띄어쓰기는 무시): 오늘·내일·모레·글피·어제·그저께 / N일·N주·N개월 뒤(후)·전 / 다음 주·지난 주·다음 달·지난 달 /
 * (다음 주|이번 주|지난 주) X요일, X요일(오늘 포함 앞으로 처음 오는 그 요일) / 2026-11-20, 2026.11.20, 2026년 11월 20일 /
 * 11월 20일, 11/20, 11-20 (올해) / 20일 (이번 달). 없는 날짜(2월 30일)나 읽을 수 없는 글은 빈 값이다.
 */
public final class DateQueryParser {

    private static final int MAX_AMOUNT = 3650;
    private static final Map<String, Integer> WORDS = Map.of("오늘", 0, "내일", 1, "모레", 2, "글피", 3,
            "어제", -1, "그저께", -2, "그제", -2);
    private static final String WEEKDAYS = "월화수목금토일";

    private static final Pattern RELATIVE = Pattern.compile("(\\d{1,4})(일|주일|주|개월|달)(뒤|후|전)");
    private static final Pattern WEEKDAY = Pattern.compile("(다음주|이번주|지난주)?([월화수목금토일])요일");
    private static final Pattern FULL = Pattern.compile("(\\d{4})[-./년](\\d{1,2})[-./월](\\d{1,2})일?");
    private static final Pattern MONTH_DAY = Pattern.compile("(\\d{1,2})(?:월|[-./])(\\d{1,2})일?");
    private static final Pattern DAY_ONLY = Pattern.compile("(\\d{1,2})일");
    private static final Pattern COMPACT = Pattern.compile("(\\d{4})(\\d{2})(\\d{2})");

    private DateQueryParser() {
    }

    /** text를 today 기준 날짜로 읽는다. 읽을 수 없으면 빈 값 */
    public static Optional<LocalDate> parse(String text, LocalDate today) {
        if (text == null || today == null) {
            return Optional.empty();
        }
        String q = text.strip().replaceAll("\\s+", "");
        if (q.isEmpty()) {
            return Optional.empty();
        }
        try {
            return read(q, today);
        } catch (DateTimeException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<LocalDate> read(String q, LocalDate today) {
        Optional<LocalDate> relative = relative(q, today);
        if (relative.isPresent()) {
            return relative;
        }
        Optional<LocalDate> weekday = weekday(q, today);
        if (weekday.isPresent()) {
            return weekday;
        }
        return written(q, today);
    }

    /** 오늘·내일, N일 뒤, 다음 주처럼 오늘에서 얼마나 떨어졌는지로 말한 날짜 */
    private static Optional<LocalDate> relative(String q, LocalDate today) {
        if (WORDS.containsKey(q)) {
            return Optional.of(today.plusDays(WORDS.get(q)));
        }
        switch (q) {
            case "다음주":
                return Optional.of(today.plusWeeks(1));
            case "지난주":
                return Optional.of(today.minusWeeks(1));
            case "다음달":
                return Optional.of(today.plusMonths(1));
            case "지난달":
                return Optional.of(today.minusMonths(1));
            case "일주일뒤":
            case "일주일후":
                return Optional.of(today.plusWeeks(1));
            case "일주일전":
                return Optional.of(today.minusWeeks(1));
            default:
                return amount(q, today);
        }
    }

    private static Optional<LocalDate> amount(String q, LocalDate today) {
        Matcher m = RELATIVE.matcher(q);
        if (!m.matches()) {
            return Optional.empty();
        }
        int n = Integer.parseInt(m.group(1));
        if (n > MAX_AMOUNT) {
            return Optional.empty();
        }
        int signed = "전".equals(m.group(3)) ? -n : n;
        return Optional.of(switch (m.group(2)) {
            case "일" -> today.plusDays(signed);
            case "개월", "달" -> today.plusMonths(signed);
            default -> today.plusWeeks(signed);
        });
    }

    /** "금요일"은 오늘 포함 앞으로 처음 오는 금요일, "다음 주 금요일"처럼 주를 붙이면 그 주(월~일)의 금요일 */
    private static Optional<LocalDate> weekday(String q, LocalDate today) {
        Matcher m = WEEKDAY.matcher(q);
        if (!m.matches()) {
            return Optional.empty();
        }
        DayOfWeek target = DayOfWeek.of(WEEKDAYS.indexOf(m.group(2)) + 1);
        String week = m.group(1);
        if (week == null) {
            int ahead = (target.getValue() - today.getDayOfWeek().getValue() + 7) % 7;
            return Optional.of(today.plusDays(ahead));
        }
        LocalDate monday = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        int shift = "다음주".equals(week) ? 7 : "지난주".equals(week) ? -7 : 0;
        return Optional.of(monday.plusDays(shift + target.getValue() - 1L));
    }

    /** 숫자로 적은 날짜: 연월일 / 월일(올해) / 일(이번 달) */
    private static Optional<LocalDate> written(String q, LocalDate today) {
        Matcher full = FULL.matcher(q);
        if (full.matches()) {
            return Optional.of(LocalDate.of(num(full, 1), num(full, 2), num(full, 3)));
        }
        Matcher compact = COMPACT.matcher(q);
        if (compact.matches()) {
            return Optional.of(LocalDate.of(num(compact, 1), num(compact, 2), num(compact, 3)));
        }
        Matcher md = MONTH_DAY.matcher(q);
        if (md.matches()) {
            return Optional.of(LocalDate.of(today.getYear(), num(md, 1), num(md, 2)));
        }
        Matcher day = DAY_ONLY.matcher(q);
        if (day.matches()) {
            return Optional.of(today.withDayOfMonth(num(day, 1)));
        }
        return Optional.empty();
    }

    private static int num(Matcher m, int group) {
        return Integer.parseInt(m.group(group));
    }
}
