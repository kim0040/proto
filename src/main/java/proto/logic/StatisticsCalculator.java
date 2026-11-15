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
 * F14(주간 통계)에서 쓴다. ScheduleController#weeklyStatistics가 그 주의 하루치 일정을 모아 이 클래스에 맡기고, 결과를 StatsView가 보여 준다.
 * 주(週)는 월요일 시작이다. 지연 판단은 StateCalculator에 맡겨서 화면의 상태 표시와 숫자가 어긋나지 않게 한다.
 * 하루 종일 일정은 일정 수·완료 수·지연 수·완료율·요일별 개수에는 세지만, 정해진 시간이 없으므로 카테고리별 시간(분)에는 넣지 않는다.
 */
public final class StatisticsCalculator {

    private StatisticsCalculator() {
    }

    /**
     * 한 주의 일정 수·완료 수·지연 수·완료율, 카테고리별 계획 시간(분), 요일별 일정 수를 한 번에 센다.
     * 일정이 하나도 없으면 완료율은 0.0이다 (0으로 나누지 않으려고 따로 처리). 요일별 개수는 일정이 없는 요일도 0으로 채워 넣는다.
     *
     * @param anyDayInWeek 통계를 낼 주에 들어 있는 아무 날짜 (그 주 월요일을 스스로 찾는다)
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
            addMinutes(minutesByCategory, o);
        }
        double rate = total == 0 ? 0.0 : (double) completed / total;
        return new WeeklyStatistics(monday, total, completed, delayed, rate, minutesByCategory, countByDay);
    }

    /** 카테고리별 시간에 이 일정의 길이(분)를 더한다. 하루 종일 일정은 더하지 않는다(카테고리 키도 만들지 않는다). */
    private static void addMinutes(Map<String, Long> minutesByCategory, Occurrence o) {
        if (o.allDay()) {
            return;
        }
        minutesByCategory.merge(o.categoryId(), Duration.between(o.startTime(), o.endTime()).toMinutes(), Long::sum);
    }
}
