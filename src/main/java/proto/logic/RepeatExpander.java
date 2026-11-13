package proto.logic;

import proto.entity.Occurrence;
import proto.entity.Schedule;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * [논리 application logic] 일정 하나 + 반복 규칙을 기간 안의 하루치 일정(Occurrence)으로 펼친다 (F9).
 * 매월은 시작 날짜의 일(day)이 없는 달(31일의 30일 달, 2월 등)을 건너뛴다.
 * 시작 날짜 이전은 만들지 않고, 긴 기간도 첫 후보로 바로 건너뛰어 계산한다.
 */
public final class RepeatExpander {

    private RepeatExpander() {
    }

    /** from~to(둘 다 포함) 사이의 하루치 일정. 제외된 날짜는 빠지고, 날짜순이다 */
    public static List<Occurrence> expand(Schedule s, LocalDate from, LocalDate to) {
        List<Occurrence> result = new ArrayList<>();
        for (LocalDate day : ruleDates(s, from, to)) {
            if (!s.isExcludedOn(day)) {
                result.add(Occurrence.of(s, day));
            }
        }
        return result;
    }

    /** 이 날짜에 실제로 일정이 나타나는지 (규칙에 맞고 제외되지 않음) */
    public static boolean occursOn(Schedule s, LocalDate day) {
        return !s.isExcludedOn(day) && matchesRule(s, day);
    }

    /** 제외 여부와 상관없이 반복 규칙(시작일, 종료일, 요일/일)에 맞는 날짜인지 */
    public static boolean matchesRule(Schedule s, LocalDate day) {
        return !ruleDates(s, day, day).isEmpty();
    }

    /** 남은 하루치 일정이 하나라도 있는지. 종료일 없는 반복은 항상 있다 */
    public static boolean hasOccurrences(Schedule s) {
        if (s.isRepeating() && s.getRepeatEndDate() == null) {
            return true;
        }
        LocalDate last = s.isRepeating() ? s.getRepeatEndDate() : s.getDate();
        return !expand(s, s.getDate(), last).isEmpty();
    }

    /** 제외 날짜를 무시하고 규칙만으로 정해지는 from~to 사이 날짜들 */
    public static List<LocalDate> ruleDates(Schedule s, LocalDate from, LocalDate to) {
        LocalDate lo = from.isBefore(s.getDate()) ? s.getDate() : from;
        LocalDate end = s.getRepeatEndDate();
        LocalDate hi = s.isRepeating() && end != null && end.isBefore(to) ? end : to;
        if (lo.isAfter(hi)) {
            return List.of();
        }
        return switch (s.getRepeatRule()) {
            case NONE -> lo.equals(s.getDate()) ? List.of(lo) : List.of();
            case DAILY -> stepDates(lo, hi, 1);
            case WEEKLY -> stepDates(lo.plusDays(daysUntilSameWeekday(s.getDate(), lo)), hi, 7);
            case MONTHLY -> monthlyDates(s.getDate().getDayOfMonth(), lo, hi);
        };
    }

    private static int daysUntilSameWeekday(LocalDate anchor, LocalDate from) {
        return Math.floorMod(anchor.getDayOfWeek().getValue() - from.getDayOfWeek().getValue(), 7);
    }

    private static List<LocalDate> stepDates(LocalDate first, LocalDate last, int stepDays) {
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(stepDays)) {
            dates.add(d);
        }
        return dates;
    }

    private static List<LocalDate> monthlyDates(int dayOfMonth, LocalDate lo, LocalDate hi) {
        List<LocalDate> dates = new ArrayList<>();
        YearMonth last = YearMonth.from(hi);
        for (YearMonth ym = YearMonth.from(lo); !ym.isAfter(last); ym = ym.plusMonths(1)) {
            if (dayOfMonth > ym.lengthOfMonth()) {
                continue;
            }
            LocalDate d = ym.atDay(dayOfMonth);
            if (!d.isBefore(lo) && !d.isAfter(hi)) {
                dates.add(d);
            }
        }
        return dates;
    }
}
