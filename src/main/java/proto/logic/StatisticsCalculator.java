package proto.logic;

import proto.entity.Occurrence;
import proto.entity.ScheduleState;
import proto.entity.WeeklyStatistics;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * [논리 application logic] 한 주(월~일)의 하루치 일정으로 완료율·지연 수·카테고리별 시간·요일별 개수를 센다 (F14).
 */
public final class StatisticsCalculator {

    private StatisticsCalculator() {
    }

    /**
     * @param anyDayInWeek 통계를 낼 주에 들어 있는 아무 날짜
     * @param occurrences  그 주의 하루치 일정들 (주 밖의 것은 무시)
     * @param now          지연 판단에 쓰는 지금 시각
     */
    public static WeeklyStatistics calculate(LocalDate anyDayInWeek, List<Occurrence> occurrences,
                                             LocalDateTime now) {
        LocalDate monday = anyDayInWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);
        Map<DayOfWeek, Integer> countByDay = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek d : DayOfWeek.values()) {
            countByDay.put(d, 0);
        }
        Map<String, Long> minutesByCategory = new LinkedHashMap<>();
        int total = 0;
        int completed = 0;
        int delayed = 0;
        for (Occurrence o : occurrences) {
            if (o.date().isBefore(monday) || o.date().isAfter(sunday)) {
                continue;
            }
            total++;
            completed += o.completed() ? 1 : 0;
            delayed += StateCalculator.stateOf(o, now) == ScheduleState.DELAYED ? 1 : 0;
            countByDay.merge(o.date().getDayOfWeek(), 1, Integer::sum);
            minutesByCategory.merge(o.categoryId(),
                    Duration.between(o.startTime(), o.endTime()).toMinutes(), Long::sum);
        }
        double rate = total == 0 ? 0.0 : (double) completed / total;
        return new WeeklyStatistics(monday, total, completed, delayed, rate, minutesByCategory, countByDay);
    }
}
