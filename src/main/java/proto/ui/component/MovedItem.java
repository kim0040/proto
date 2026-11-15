package proto.ui.component;

import proto.entity.Occurrence;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * [경계 boundary] 옮기기를 요청한 일정이 화면을 다시 만든 뒤 어느 일정이 되었는지 알아보는 표 (옮긴 뒤 포커스, 내려앉는 모션, 하루 종일 칩 찾기).
 * TimelineGrid·AllDayRow·MonthSettle이 옮기기를 요청하기 직전에 만들고, 다시 만든 목록에서 pick으로 하나를 고른다.
 * 제목·시각이 같은 일정이 여럿이어도 헷갈리지 않도록 일정 번호(scheduleId)와 날짜로 먼저 찾는다.
 * 반복 일정의 "이 일정만" 옮기기는 새 번호가 생겨 미리 알 수 없으므로, 그때는 새 날짜·시작·제목이 꼭 맞는 일정 중 옮기기 전에 없던 번호를 고른다.
 * 고르는 순서: (1) 같은 번호·새 날짜·새 시작이고 옮기기 전에는 없던 칸 → (2) 새 값이 꼭 맞고 옮기기 전에 없던 번호 → (3) 같은 번호·새 날짜·새 시작 → (4) 새 값이 꼭 맞는 아무 일정.
 */
final class MovedItem {

    private final Occurrence source;
    private final LocalDate date;
    private final LocalTime start;
    private final Set<String> priorKeys = new HashSet<>();
    private final Set<String> priorIds = new HashSet<>();

    /**
     * @param source 옮기기를 요청한 일정
     * @param date   옮겨 갈 날짜
     * @param start  옮겨 간 뒤의 시작 시각 (하루 종일이면 그대로)
     * @param prior  옮기기 전에 화면에 있던 일정들 (같은 기간)
     */
    MovedItem(Occurrence source, LocalDate date, LocalTime start, Collection<Occurrence> prior) {
        this.source = source;
        this.date = date;
        this.start = start;
        for (Occurrence o : prior) {
            priorKeys.add(o.key());
            priorIds.add(o.scheduleId());
        }
        priorKeys.add(source.key());
        priorIds.add(source.scheduleId());
    }

    /** 옮기기를 요청한 일정 */
    Occurrence source() {
        return source;
    }

    LocalDate date() {
        return date;
    }

    /** 다시 만든 목록에서 옮겨진 일정을 고른다. 없으면 null */
    Occurrence pick(List<Occurrence> items) {
        Occurrence best = null;
        int bestRank = Integer.MAX_VALUE;
        for (Occurrence o : items) {
            int r = rank(o);
            if (r < bestRank) {
                best = o;
                bestRank = r;
            }
        }
        return best;
    }

    /** 이 일정이 옮겨진 일정일 가능성의 순위 (작을수록 확실, 아니면 MAX_VALUE) */
    private int rank(Occurrence o) {
        if (!o.date().equals(date) || !o.startTime().equals(start)) {
            return Integer.MAX_VALUE;
        }
        boolean sameId = o.scheduleId().equals(source.scheduleId());
        if (sameId && !priorKeys.contains(o.key())) {
            return 1;
        }
        boolean sameValues = o.title().equals(source.title());
        if (sameValues && !priorIds.contains(o.scheduleId())) {
            return 2;
        }
        if (sameId) {
            return 3;
        }
        return sameValues ? 4 : Integer.MAX_VALUE;
    }
}
