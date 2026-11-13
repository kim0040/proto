package proto.entity;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

/**
 * [엔티티 entity] 한 주(월~일) 통계 결과 (F14).
 *
 * @param weekStart           그 주 월요일
 * @param total               그 주 하루치 일정 수 (반복은 펼친 개수)
 * @param completed           완료한 수
 * @param delayed             지금 시각 기준 지연 상태인 수
 * @param completionRate      완료율 0.0~1.0 (total이 0이면 0.0)
 * @param minutesByCategory   카테고리 id → 계획된 시간(분) 합. 일정이 있는 카테고리만 들어감
 * @param countByDay          요일 → 일정 수. 월~일 7개 키가 모두 들어감 (0 포함)
 */
public record WeeklyStatistics(
        LocalDate weekStart,
        int total,
        int completed,
        int delayed,
        double completionRate,
        Map<String, Long> minutesByCategory,
        Map<DayOfWeek, Integer> countByDay) {

    public LocalDate weekEnd() {
        return weekStart.plusDays(6);
    }
}
