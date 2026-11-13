package proto.ui;

import proto.entity.Occurrence;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * [경계 boundary] 날짜·시간·길이를 화면에 보여 줄 한국어 문장으로 바꾼다. (예: "11월 14일 (금)", "14:00", "2시간 30분")
 */
public final class Fmt {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final Locale KO = Locale.KOREAN;

    private Fmt() {
    }

    /** "14:00" */
    public static String time(LocalTime t) {
        return t.format(TIME);
    }

    /** "09:00 – 10:30" */
    public static String range(LocalTime start, LocalTime end) {
        return time(start) + " – " + time(end);
    }

    public static String range(Occurrence o) {
        return range(o.startTime(), o.endTime());
    }

    /** "금" */
    public static String weekdayShort(DayOfWeek d) {
        return d.getDisplayName(TextStyle.NARROW, KO);
    }

    /** "금요일" */
    public static String weekdayLong(DayOfWeek d) {
        return d.getDisplayName(TextStyle.FULL, KO);
    }

    /** "11월 14일" */
    public static String monthDay(LocalDate d) {
        return d.getMonthValue() + "월 " + d.getDayOfMonth() + "일";
    }

    /** "11월 14일 (금)" */
    public static String dateShort(LocalDate d) {
        return monthDay(d) + " (" + weekdayShort(d.getDayOfWeek()) + ")";
    }

    /** "11월 14일 금요일" */
    public static String dateLong(LocalDate d) {
        return monthDay(d) + " " + weekdayLong(d.getDayOfWeek());
    }

    /** "2026년 11월" */
    public static String yearMonth(LocalDate d) {
        return d.getYear() + "년 " + d.getMonthValue() + "월";
    }

    /** "2026년 11월 14일 (금)" */
    public static String dateFull(LocalDate d) {
        return d.getYear() + "년 " + dateShort(d);
    }

    /** 분을 "1시간 30분", "45분", "2시간" 처럼 */
    public static String duration(long minutes) {
        long h = minutes / 60;
        long m = minutes % 60;
        if (h == 0) {
            return m + "분";
        }
        return m == 0 ? h + "시간" : h + "시간 " + m + "분";
    }

    /** 같은 해면 연도를 빼고, 다르면 연도를 붙인 짧은 날짜 범위 "11월 10일 – 11월 16일" */
    public static String dateRange(LocalDate from, LocalDate to) {
        return monthDay(from) + " – " + monthDay(to);
    }
}
