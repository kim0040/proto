package proto.logic;

import proto.entity.RepeatRule;
import proto.entity.Schedule;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * [논리 application logic] 반복 일정을 "이 일정만 / 앞으로 전부" 범위로 수정·삭제하는 규칙을 적용한다 (F3, F4).
 * 넘겨받은 Schedule을 직접 고치고, 저장소에 새로 넣거나 빼야 할 일정만 {@link Change}로 알려 준다.
 * 저장·알림은 하지 않는다 (control의 몫).
 */
public final class SeriesEditor {

    /** 저장소에 추가해야 할 일정과 제거해야 할 일정 */
    public record Change(List<Schedule> added, List<Schedule> removed) {

        static Change none() {
            return new Change(List.of(), List.of());
        }
    }

    private SeriesEditor() {
    }

    /** 반복이 없는 일정 수정: 그 자리에서 바꾼다. 완료했던 일정이면 새 날짜에서도 완료로 둔다 */
    public static Change updateInPlace(Schedule target, Schedule candidate) {
        boolean wasDone = !target.isRepeating() && target.isCompletedOn(target.getDate());
        copyFields(target, candidate);
        if (wasDone) {
            target.replaceDateSets(Set.of(target.getDate()), Set.of());
        } else {
            keepValidDates(target);
        }
        return Change.none();
    }

    /** 이 일정만 수정: 그 날짜를 반복에서 빼고, 반복 없는 새 일정을 만든다 */
    public static Change updateThisOnly(Schedule series, LocalDate day, Schedule candidate) {
        boolean wasDone = series.isCompletedOn(day);
        series.excludeDate(day);
        series.setCompletedOn(day, false);
        candidate.setRepeatRule(RepeatRule.NONE);
        candidate.setRepeatEndDate(null);
        if (wasDone) {
            candidate.setCompletedOn(candidate.getDate(), true);
        }
        return new Change(List.of(candidate), removedIfEmpty(series));
    }

    /**
     * 앞으로 전부 수정: 첫 날짜면 반복 규칙 자체를 바꾸고(id 유지),
     * 아니면 원래 반복을 전날로 끝내고 그 날짜부터 새 반복(candidate)을 만든다.
     */
    public static Change updateFollowing(Schedule series, LocalDate day, Schedule candidate) {
        if (day.equals(series.getDate())) {
            copyFields(series, candidate);
            keepValidDates(series);
            return Change.none();
        }
        Set<LocalDate> doneLater = filter(series.getCompletedDates(), d -> !d.isBefore(day));
        Set<LocalDate> excludedLater = filter(series.getExcludedDates(), d -> !d.isBefore(day));
        trimSeriesBefore(series, day);
        candidate.replaceDateSets(filter(doneLater, d -> RepeatExpander.matchesRule(candidate, d)),
                filter(excludedLater, d -> RepeatExpander.matchesRule(candidate, d)));
        return new Change(List.of(candidate), removedIfEmpty(series));
    }

    /** 이 일정만 삭제: 그 날짜를 반복에서 뺀다. 남는 날짜가 없으면 일정 전체를 지운다 */
    public static Change deleteThisOnly(Schedule series, LocalDate day) {
        series.excludeDate(day);
        series.setCompletedOn(day, false);
        return new Change(List.of(), removedIfEmpty(series));
    }

    /** 앞으로 전부 삭제: 첫 날짜면 전체 삭제, 아니면 전날까지만 반복한다 */
    public static Change deleteFollowing(Schedule series, LocalDate day) {
        if (day.equals(series.getDate())) {
            return new Change(List.of(), List.of(series));
        }
        trimSeriesBefore(series, day);
        return new Change(List.of(), removedIfEmpty(series));
    }

    /** 반복 종료일을 day의 전날로 줄이고, day 이후의 완료/제외 기록은 지운다 */
    private static void trimSeriesBefore(Schedule series, LocalDate day) {
        series.setRepeatEndDate(day.minusDays(1));
        series.replaceDateSets(filter(series.getCompletedDates(), d -> d.isBefore(day)),
                filter(series.getExcludedDates(), d -> d.isBefore(day)));
    }

    private static void copyFields(Schedule target, Schedule from) {
        target.setTitle(from.getTitle());
        target.setDate(from.getDate());
        target.setStartTime(from.getStartTime());
        target.setEndTime(from.getEndTime());
        target.setCategoryId(from.getCategoryId());
        target.setMemo(from.getMemo());
        target.setRepeatRule(from.getRepeatRule());
        target.setRepeatEndDate(from.getRepeatEndDate());
        target.setReminder(from.getReminder());
    }

    /** 바뀐 규칙에서도 여전히 일정 날짜인 완료/제외 기록만 남긴다 */
    private static void keepValidDates(Schedule s) {
        s.replaceDateSets(filter(s.getCompletedDates(), d -> RepeatExpander.matchesRule(s, d)),
                filter(s.getExcludedDates(), d -> RepeatExpander.matchesRule(s, d)));
    }

    private static List<Schedule> removedIfEmpty(Schedule series) {
        return RepeatExpander.hasOccurrences(series) ? List.of() : List.of(series);
    }

    private static Set<LocalDate> filter(Set<LocalDate> dates, Predicate<LocalDate> keep) {
        Set<LocalDate> kept = new TreeSet<>();
        for (LocalDate d : dates) {
            if (keep.test(d)) {
                kept.add(d);
            }
        }
        return kept;
    }
}
