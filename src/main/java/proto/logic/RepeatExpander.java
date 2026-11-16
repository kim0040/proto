package proto.logic;

import proto.entity.Occurrence;
import proto.entity.Schedule;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * [논리 application logic] 일정 하나 + 반복 규칙을 기간 안의 하루치 일정(Occurrence)으로 펼친다 (F9).
 * 조회·겹침 검사·빈 시간·통계·알림이 모두 이 클래스로 반복 일정을 날짜별로 펼친다. ScheduleController, ConflictDetector, SeriesEditor가 부른다.
 * 제외 날짜(excludedDates)는 펼칠 때 빼고, 반복 종료일이 없는 일정은 요청한 기간까지만 계산한다 (끝없이 만들 수 없다).
 * 매월은 시작 날짜의 일(day)이 없는 달(31일의 30일 달, 2월 등)을 건너뛴다. 시작 날짜 이전은 만들지 않는다.
 */
public final class RepeatExpander {

    private RepeatExpander() {
    }

    /**
     * from~to(둘 다 포함) 사이의 하루치 일정. 제외된 날짜는 빠지고, 날짜순이다.
     * 기간 안에 해당하는 날이 없으면 빈 목록이다.
     */
    public static List<Occurrence> expand(Schedule schedule, LocalDate from, LocalDate to) {
        List<Occurrence> result = new ArrayList<>();
        for (LocalDate day : ruleDates(schedule, from, to)) {
            if (!schedule.isExcludedOn(day)) {
                result.add(Occurrence.of(schedule, day));
            }
        }
        return result;
    }

    /** 이 날짜에 실제로 일정이 나타나는지 (규칙에 맞고 제외되지 않음) */
    public static boolean occursOn(Schedule schedule, LocalDate day) {
        return !schedule.isExcludedOn(day) && matchesRule(schedule, day);
    }

    /**
     * 제외 여부와 상관없이 반복 규칙(시작일, 종료일, 요일/일)에 맞는 날짜인지.
     * 수정 뒤에 "원래 있던 완료·제외 기록이 아직 의미가 있는지" 판단할 때 쓴다 (SeriesEditor).
     */
    public static boolean matchesRule(Schedule schedule, LocalDate day) {
        return !ruleDates(schedule, day, day).isEmpty();
    }

    /**
     * 남은 하루치 일정이 하나라도 있는지. 종료일 없는 반복은 항상 있다. 있으면 그 날짜에서 바로 끝낸다.
     * SeriesEditor가 "남은 날이 없으면 일정 전체를 지운다"를 판단할 때 쓴다.
     */
    public static boolean hasOccurrences(Schedule schedule) {
        if (schedule.isRepeating() && schedule.getRepeatEndDate() == null) {
            return true;
        }
        LocalDate last = schedule.isRepeating() ? schedule.getRepeatEndDate() : schedule.getDate();
        return hasOpenDate(schedule, schedule.getDate(), last);
    }

    /**
     * 이 클래스의 중심 메서드다. 시작 날짜 이전은 버리고(lo), 반복 종료일이 있으면 거기서 자른다(hi). 그다음 규칙별로 날짜를 만든다.
     * 매주는 시작 날짜와 요일이 같은 첫 날로 건너뛴 뒤 7일씩, 매월은 시작 날짜의 일(day)이 있는 달만 고른다.
     * 기간이 길어도 시작 날짜부터 하나씩 세지 않고 첫 후보로 바로 건너뛰므로 빠르다.
     */
    public static List<LocalDate> ruleDates(Schedule schedule, LocalDate from, LocalDate to) {
        LocalDate lo = from.isBefore(schedule.getDate()) ? schedule.getDate() : from;
        LocalDate end = schedule.getRepeatEndDate();
        LocalDate hi = schedule.isRepeating() && end != null && end.isBefore(to) ? end : to;
        if (lo.isAfter(hi)) {
            return List.of();
        }
        return switch (schedule.getRepeatRule()) {
            case NONE -> lo.equals(schedule.getDate()) ? List.of(lo) : List.of();
            case DAILY -> stepDates(lo, hi, 1);
            case WEEKLY -> stepDates(lo.plusDays(daysUntilSameWeekday(schedule.getDate(), lo)), hi, 7);
            case MONTHLY -> monthlyDates(schedule.getDate().getDayOfMonth(), lo, hi);
        };
    }

    /** 1년씩 끊어서 보고, 제외되지 않은 첫 날짜에서 바로 true */
    private static boolean hasOpenDate(Schedule schedule, LocalDate from, LocalDate last) {
        LocalDate windowStart = from;
        while (!windowStart.isAfter(last)) {
            LocalDate windowEnd = windowStart.plusYears(1).minusDays(1);
            if (windowEnd.isAfter(last)) {
                windowEnd = last;
            }
            if (hasUnexcludedDate(schedule, windowStart, windowEnd)) {
                return true;
            }
            windowStart = windowEnd.plusDays(1);
        }
        return false;
    }

    private static boolean hasUnexcludedDate(Schedule schedule, LocalDate from, LocalDate to) {
        for (LocalDate day : ruleDates(schedule, from, to)) {
            if (!schedule.isExcludedOn(day)) {
                return true;
            }
        }
        return false;
    }

    private static int daysUntilSameWeekday(LocalDate anchor, LocalDate from) {
        return Math.floorMod(anchor.getDayOfWeek().getValue() - from.getDayOfWeek().getValue(), 7);
    }

    private static List<LocalDate> stepDates(LocalDate first, LocalDate last, int stepDays) {
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(stepDays)) {
            dates.add(date);
        }
        return dates;
    }

    private static List<LocalDate> monthlyDates(int dayOfMonth, LocalDate lo, LocalDate hi) {
        List<LocalDate> dates = new ArrayList<>();
        YearMonth last = YearMonth.from(hi);
        for (YearMonth month = YearMonth.from(lo); !month.isAfter(last); month = month.plusMonths(1)) {
            if (dayOfMonth > month.lengthOfMonth()) {
                continue;
            }
            LocalDate date = month.atDay(dayOfMonth);
            if (!date.isBefore(lo) && !date.isAfter(hi)) {
                dates.add(date);
            }
        }
        return dates;
    }
}
