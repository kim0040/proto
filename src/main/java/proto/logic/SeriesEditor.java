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
 * ScheduleController가 범위(EditScope)에 따라 알맞은 메서드를 고른다.
 * 넘겨받은 Schedule을 직접 고치고, 저장소에 새로 넣거나 빼야 할 일정만 {@link Change}로 알려 준다.
 * 저장·알림은 하지 않는다 (control의 몫).
 * 고친 값을 옮길 때 하루 종일 여부도 같이 옮기므로, 어느 범위든 입력값의 하루 종일 여부를 따른다.
 */
public final class SeriesEditor {

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

    /**
     * 이 일정만 수정: 그 날짜를 반복에서 빼고, 고친 내용은 반복 없는 새 일정(candidate)이 된다.
     * 그 날 완료해 둔 일정이면 새 일정도 완료로 이어 간다.
     */
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
     * "이 일정과 앞으로 전부"를 고친다. 이 클래스에서 가장 헷갈리는 부분이다.
     * 자르는 날(cut)은 min(고른 날, 새 시작일)이다. 더 이른 날에서 잘라야 원래 반복과 새 반복이 같은 날짜를 함께 만들어
     * 일정이 두 번 나오는 일이 없다. 새 시작일이 이르면 그 사이 날짜는 새 반복으로만 채워지고,
     * 새 시작일이 늦으면 고른 날부터 새 시작일 전까지는 일정이 없는 것이 된다.
     * cut이 반복의 첫 날짜 이전이거나 같으면 자를 앞부분이 없으므로 같은 일정(id 그대로)을 그 자리에서 고친다.
     * 아니면 원래 반복을 전날로 끝내고, 새 반복(candidate)을 저장소에 더하라고 Change에 담는다.
     *
     * @param series    고칠 반복 일정 (이 객체가 직접 바뀐다)
     * @param day       사용자가 고른 하루치 일정의 날짜
     * @param candidate 입력값으로 만든 새 일정 (반복의 새 모양)
     * @return 저장소에 더할 일정과 뺄 일정
     */
    public static Change updateFollowing(Schedule series, LocalDate day, Schedule candidate) {
        LocalDate cut = earlier(day, candidate.getDate());
        if (!cut.isAfter(series.getDate())) {
            copyFields(series, candidate);
            keepValidDates(series);
            return Change.none();
        }
        moveTail(series, candidate, cut);
        return new Change(List.of(candidate), removedIfEmpty(series));
    }

    /** 이 날짜만 반복에서 뺀다. 남는 날짜가 하나도 없으면(반복이 그 하루뿐이었다면) 일정 전체를 지운다고 알린다 */
    public static Change deleteThisOnly(Schedule series, LocalDate day) {
        series.excludeDate(day);
        series.setCompletedOn(day, false);
        return new Change(List.of(), removedIfEmpty(series));
    }

    /** 반복의 첫 날짜를 골랐으면 전부 지운다. 아니면 반복 종료일을 전날로 줄여서 앞부분만 남긴다 */
    public static Change deleteFollowing(Schedule series, LocalDate day) {
        if (day.equals(series.getDate())) {
            return new Change(List.of(), List.of(series));
        }
        trimSeriesBefore(series, day);
        return new Change(List.of(), removedIfEmpty(series));
    }

    private static LocalDate earlier(LocalDate first, LocalDate second) {
        return first.isBefore(second) ? first : second;
    }

    /**
     * 자르는 날 이후의 완료·제외 기록은 원래 반복에서 지우고, 새 반복에서도 날짜가 규칙에 맞는 것만 옮겨 준다.
     * 요일을 바꾸면 옛 완료 날짜가 새 규칙에 안 맞아 저절로 버려진다.
     */
    private static void moveTail(Schedule series, Schedule candidate, LocalDate cut) {
        Set<LocalDate> doneLater = filter(series.getCompletedDates(), date -> !date.isBefore(cut));
        Set<LocalDate> excludedLater = filter(series.getExcludedDates(), date -> !date.isBefore(cut));
        trimSeriesBefore(series, cut);
        candidate.replaceDateSets(matching(candidate, doneLater), matching(candidate, excludedLater));
    }

    private static Set<LocalDate> matching(Schedule candidate, Set<LocalDate> dates) {
        return filter(dates, date -> RepeatExpander.matchesRule(candidate, date));
    }

    /** 반복 종료일을 day의 전날로 줄인다. 잘려 나간 날짜의 완료·제외 기록은 의미가 없으므로 같이 지운다 */
    private static void trimSeriesBefore(Schedule series, LocalDate day) {
        series.setRepeatEndDate(day.minusDays(1));
        series.replaceDateSets(filter(series.getCompletedDates(), date -> date.isBefore(day)),
                filter(series.getExcludedDates(), date -> date.isBefore(day)));
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
        target.setAllDay(from.isAllDay());
        target.setColorKey(from.getColorKey());
    }

    /** 바뀐 규칙에서도 여전히 일정 날짜인 완료/제외 기록만 남긴다 */
    private static void keepValidDates(Schedule schedule) {
        schedule.replaceDateSets(filter(schedule.getCompletedDates(), date -> RepeatExpander.matchesRule(schedule, date)),
                filter(schedule.getExcludedDates(), date -> RepeatExpander.matchesRule(schedule, date)));
    }

    private static List<Schedule> removedIfEmpty(Schedule series) {
        return RepeatExpander.hasOccurrences(series) ? List.of() : List.of(series);
    }

    private static Set<LocalDate> filter(Set<LocalDate> dates, Predicate<LocalDate> keep) {
        Set<LocalDate> kept = new TreeSet<>();
        for (LocalDate date : dates) {
            if (keep.test(date)) {
                kept.add(date);
            }
        }
        return kept;
    }

    /** 저장소에 추가해야 할 일정과 제거해야 할 일정 */
    public record Change(List<Schedule> added, List<Schedule> removed) {

        static Change none() {
            return new Change(List.of(), List.of());
        }
    }
}
