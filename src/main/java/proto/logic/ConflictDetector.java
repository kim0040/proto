package proto.logic;

import proto.entity.Occurrence;
import proto.entity.Schedule;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * [논리 application logic] 저장하려는 일정과 시간이 겹치는 기존 하루치 일정을 찾는다 (F13).
 * F13(일정 겹침 경고)에서 쓴다. ScheduleController가 등록·수정 직전에 부르고, 겹침이 있으면 화면이 "시간이 겹쳐요" 확인 창을 띄운다.
 * 정해진 입력만으로 결과가 나오는 정적 메서드뿐이라 화면·저장소 없이도 시험할 수 있다. 겹침은 경고일 뿐 저장을 막지 않는다.
 * 같은 날짜에서 a.start &lt; b.end 이고 b.start &lt; a.end 일 때만 겹침이며, 끝과 시작이 맞닿는 것은 겹침이 아니다.
 * 반복 일정은 시작 날짜부터 28일 안의 하루치만 검사한다.
 */
public final class ConflictDetector {

    /** 반복 일정을 검사하는 기간(일). 시작 날짜 포함 */
    public static final int REPEAT_CHECK_DAYS = 28;

    private ConflictDetector() {
    }

    /**
     * 저장하려는 일정(candidate)과 시간이 겹치는 기존 일정을 찾는다. 기간은 후보의 시작 날짜부터이고,
     * 반복이 없으면 하루, 있으면 28일이다. 반복은 끝이 없을 수 있어서 무한히 펼칠 수 없기 때문에 28일로 자른다.
     *
     * @param candidate 저장하려는 값으로 만든 일정
     * @param existing  비교할 저장된 일정들
     * @param ignored   겹침 후보에서 뺄 기존 하루치 일정 (수정 중인 자기 자신 등). 아무것도 안 뺄 때는 o -> false
     * @return 기존 쪽의 겹치는 하루치 일정. 중복 없이 날짜 → 시작 시간 → 제목 순. 겹침이 없으면 빈 목록
     */
    public static List<Occurrence> findConflicts(Schedule candidate, Collection<Schedule> existing,
                                                 Predicate<Occurrence> ignored) {
        var from = candidate.getDate();
        var to = candidate.isRepeating() ? from.plusDays(REPEAT_CHECK_DAYS - 1) : from;
        List<Occurrence> mine = RepeatExpander.expand(candidate, from, to);
        Set<Occurrence> found = new LinkedHashSet<>();
        for (Schedule other : existing) {
            for (Occurrence o : RepeatExpander.expand(other, from, to)) {
                if (!ignored.test(o) && mine.stream().anyMatch(m -> overlaps(m, o))) {
                    found.add(o);
                }
            }
        }
        return found.stream()
                .sorted(Comparator.comparing(Occurrence::date)
                        .thenComparing(Occurrence::startTime)
                        .thenComparing(Occurrence::title))
                .toList();
    }

    /**
     * 같은 날짜이고 시간 구간이 겹치는지. 시간 구간을 "시작 이상 끝 미만"으로 보기 때문에
     * 10:00에 끝나는 일정과 10:00에 시작하는 일정은 겹치지 않는다 (맞닿기만 하면 false).
     */
    public static boolean overlaps(Occurrence a, Occurrence b) {
        return a.date().equals(b.date())
                && a.startTime().isBefore(b.endTime())
                && b.startTime().isBefore(a.endTime());
    }
}
