package proto.logic;

import org.junit.jupiter.api.Test;
import proto.entity.Occurrence;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.entity.WeeklyStatistics;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static proto.TestData.d;
import static proto.TestData.t;

class StatisticsCalculatorTest {

    private static final LocalDateTime NOW = LocalDateTime.parse("2026-11-12T12:00"); // 목요일

    private static Occurrence occ(String date, String start, String end, String category, boolean done) {
        return new Occurrence("id" + date + start, d(date), "t", t(start), t(end), category, "",
                RepeatRule.NONE, ReminderOption.NONE, done);
    }

    /** 이번 주(월 11-09 ~ 일 11-15) 일정 4개 */
    private static List<Occurrence> week() {
        return List.of(
                occ("2026-11-09", "09:00", "10:00", "class", true),     // 60분, 완료
                occ("2026-11-09", "11:00", "12:30", "class", false),    // 90분, 끝이 지남 → 지연
                occ("2026-11-11", "14:00", "15:00", "team", false),     // 60분, 지연
                occ("2026-11-15", "20:00", "22:00", "personal", false)); // 120분, 아직 예정
    }

    @Test
    void countsCompletedDelayedAndRate() {
        WeeklyStatistics s = StatisticsCalculator.calculate(d("2026-11-12"), week(), NOW);
        assertEquals(d("2026-11-09"), s.weekStart());
        assertEquals(d("2026-11-15"), s.weekEnd());
        assertEquals(4, s.total());
        assertEquals(1, s.completed());
        assertEquals(2, s.delayed());
        assertEquals(0.25, s.completionRate(), 1e-9);
    }

    @Test
    void sumsPlannedMinutesPerCategory() {
        WeeklyStatistics s = StatisticsCalculator.calculate(d("2026-11-12"), week(), NOW);
        assertEquals(3, s.minutesByCategory().size());
        assertEquals(150L, s.minutesByCategory().get("class"));
        assertEquals(60L, s.minutesByCategory().get("team"));
        assertEquals(120L, s.minutesByCategory().get("personal"));
    }

    @Test
    void countByDayHasAllSevenKeysFromMondayToSunday() {
        WeeklyStatistics s = StatisticsCalculator.calculate(d("2026-11-12"), week(), NOW);
        assertEquals(List.of(DayOfWeek.values()), new ArrayList<>(s.countByDay().keySet()));
        assertEquals(2, s.countByDay().get(DayOfWeek.MONDAY));
        assertEquals(0, s.countByDay().get(DayOfWeek.TUESDAY));
        assertEquals(1, s.countByDay().get(DayOfWeek.WEDNESDAY));
        assertEquals(0, s.countByDay().get(DayOfWeek.SATURDAY));
        assertEquals(1, s.countByDay().get(DayOfWeek.SUNDAY));
    }

    @Test
    void emptyWeekHasZeroRateAndStillSevenDays() {
        WeeklyStatistics s = StatisticsCalculator.calculate(d("2026-11-15"), List.of(), NOW);
        assertEquals(d("2026-11-09"), s.weekStart());
        assertEquals(0, s.total());
        assertEquals(0.0, s.completionRate());
        assertEquals(7, s.countByDay().size());
        assertEquals(0, s.minutesByCategory().size());
    }
}
