package proto.control;

import proto.entity.Schedule;
import proto.repository.ScheduleStore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * [제어 control] 어느 한 순간의 일정 목록 전체를 깊은 복사본으로 담아 두고, 나중에 저장소를 그 모양으로 되돌린다.
 * ChangeHistory가 되돌리기·다시 실행 단계마다 "바뀌기 전"과 "바뀐 뒤"를 이것으로 보관하고, ScheduleController가 되돌릴 때 restoreInto를 부른다.
 * Schedule은 고칠 수 있는 객체(setCompletedOn 등)라서 담을 때와 되돌릴 때 모두 copy()로 복사한다. 그래야 되돌린 뒤 일정을 고쳐도 기록이 바뀌지 않는다.
 * 되돌릴 때는 목록을 통째로 비우고 다시 넣지 않고, 달라진 일정만 저장소에서 빼고 더한다 (저장소가 바뀐 일정만 다루게 하려는 것이다).
 */
final class ScheduleSnapshot {

    private final Map<String, Schedule> byId = new LinkedHashMap<>();

    private ScheduleSnapshot(List<Schedule> schedules) {
        schedules.forEach(s -> byId.put(s.getId(), s.copy()));
    }

    /** 저장소에 지금 있는 일정 전체의 복사본을 담는다. 이 뒤에 저장소의 일정을 고쳐도 담아 둔 값은 그대로다. */
    static ScheduleSnapshot of(ScheduleStore store) {
        return new ScheduleSnapshot(store.getAll());
    }

    /**
     * 저장소를 이 순간의 모양으로 되돌린다. 여기 없는 id는 빼고, 값이 달라진 일정은 빼고 복사본을 넣고, 없어진 일정은 복사본을 더한다.
     * 값이 같은 일정은 건드리지 않는다. 같은 id로 되살리므로 지웠던 일정을 되돌려도 id가 그대로이다. 저장(save)은 하지 않는다.
     */
    void restoreInto(ScheduleStore store) {
        Set<String> kept = new HashSet<>();
        for (Schedule current : new ArrayList<>(store.getAll())) {
            Schedule wanted = byId.get(current.getId());
            if (wanted != null && sameValues(current, wanted)) {
                kept.add(current.getId());
            } else {
                store.remove(current);
            }
        }
        byId.values().stream()
                .filter(s -> !kept.contains(s.getId()))
                .forEach(s -> store.add(s.copy()));
    }

    /** id뿐 아니라 저장되는 모든 값(하루 종일 여부, 완료·제외 날짜 포함)이 같은지. Schedule#equals는 id만 보므로 따로 비교한다. */
    static boolean sameValues(Schedule a, Schedule b) {
        return a.getId().equals(b.getId())
                && Objects.equals(a.getTitle(), b.getTitle())
                && Objects.equals(a.getDate(), b.getDate())
                && Objects.equals(a.getStartTime(), b.getStartTime())
                && Objects.equals(a.getEndTime(), b.getEndTime())
                && Objects.equals(a.getCategoryId(), b.getCategoryId())
                && Objects.equals(a.getMemo(), b.getMemo())
                && a.getRepeatRule() == b.getRepeatRule()
                && Objects.equals(a.getRepeatEndDate(), b.getRepeatEndDate())
                && a.getReminder() == b.getReminder()
                && a.isAllDay() == b.isAllDay()
                && Objects.equals(a.getColorKey(), b.getColorKey())
                && a.getCompletedDates().equals(b.getCompletedDates())
                && a.getExcludedDates().equals(b.getExcludedDates());
    }
}
