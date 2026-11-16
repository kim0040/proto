package proto.entity;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

/**
 * [엔티티 entity] 한 주(월~일) 통계 결과 (F14).
 * StatisticsCalculator가 계산해서 ScheduleController를 거쳐 StatsView에 전달한다.
 * 값을 담기만 하는 레코드이고, 퍼센트로 바꾸는 일 등은 화면이 한다.
 *
 * @param weekStart           그 주 월요일
 * @param total               그 주 하루치 일정 수 (반복은 펼친 개수)
 * @param completed           완료한 수
 * @param delayed             지금 시각 기준 지연 상태인 수
 * @param completionRate      완료율 0.0~1.0 (total이 0이면 0.0)
 * @param minutesByCategory   카테고리 id → 계획된 시간(분) 합. 시간이 있는 일정이 있는 카테고리만 들어감
 *                            (하루 종일 일정은 시간이 없으므로 여기서 빠지고, total·completed·delayed·countByDay에는 들어감)
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

    /** 그 주 일요일 (weekStart + 6일) */
    public LocalDate weekEnd() {
        return weekStart.plusDays(6);
    }
}
