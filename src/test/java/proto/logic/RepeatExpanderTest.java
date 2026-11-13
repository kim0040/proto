package proto.logic;

import org.junit.jupiter.api.Test;
import proto.entity.Occurrence;
import proto.entity.RepeatRule;
import proto.entity.Schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static proto.TestData.d;
import static proto.TestData.repeating;
import static proto.TestData.single;

class RepeatExpanderTest {

    private static List<LocalDate> dates(Schedule s, String from, String to) {
        return RepeatExpander.expand(s, d(from), d(to)).stream().map(Occurrence::date).toList();
    }

    @Test
    void noneAppearsOnlyOnItsDateWhenInRange() {
        Schedule s = single("a", "x", "2026-11-10", "09:00", "10:00");
        assertEquals(List.of(d("2026-11-10")), dates(s, "2026-11-01", "2026-11-30"));
        assertEquals(List.of(d("2026-11-10")), dates(s, "2026-11-10", "2026-11-10"));
        assertTrue(dates(s, "2026-11-11", "2026-11-30").isEmpty());
        assertTrue(dates(s, "2026-11-01", "2026-11-09").isEmpty());
    }

    @Test
    void dailyCoversRangeButNeverBeforeStart() {
        Schedule s = repeating("a", "x", "2026-11-10", "09:00", "10:00", RepeatRule.DAILY, null);
        assertEquals(List.of(d("2026-11-10"), d("2026-11-11"), d("2026-11-12"), d("2026-11-13")),
                dates(s, "2026-11-08", "2026-11-13"));
    }

    @Test
    void weeklyKeepsTheWeekdayOfTheStartDate() {
        // 2026-11-11 은 수요일
        Schedule s = repeating("a", "x", "2026-11-11", "09:00", "10:00", RepeatRule.WEEKLY, null);
        assertEquals(List.of(d("2026-11-11"), d("2026-11-18"), d("2026-11-25")),
                dates(s, "2026-11-01", "2026-11-30"));
        assertEquals(List.of(d("2026-11-18"), d("2026-11-25"), d("2026-12-02")),
                dates(s, "2026-11-12", "2026-12-02"));
    }

    @Test
    void weeklyJumpsStraightToFirstCandidateInLongRanges() {
        // 2100-01-01 은 금요일이라 1월의 수요일은 6, 13, 20, 27일
        Schedule s = repeating("a", "x", "2026-11-11", "09:00", "10:00", RepeatRule.WEEKLY, null);
        List<LocalDate> result = dates(s, "2100-01-01", "2100-01-31");
        assertEquals(List.of(d("2100-01-06"), d("2100-01-13"), d("2100-01-20"), d("2100-01-27")), result);
        assertTrue(result.stream().allMatch(x -> x.getDayOfWeek() == DayOfWeek.WEDNESDAY));
    }

    @Test
    void monthlyOnThe31stSkipsShortMonths() {
        Schedule s = repeating("a", "x", "2027-01-31", "09:00", "10:00", RepeatRule.MONTHLY, null);
        assertEquals(List.of(d("2027-01-31"), d("2027-03-31"), d("2027-05-31"), d("2027-07-31")),
                dates(s, "2027-01-01", "2027-07-31"));
    }

    @Test
    void monthlyOnFeb29OnlyAppearsInMonthsWith29DaysAcrossYears() {
        Schedule s = repeating("a", "x", "2028-02-29", "09:00", "10:00", RepeatRule.MONTHLY, null);
        // 2028은 윤년이라 2월 29일이 있고, 2029년 2월에는 29일이 없어서 건너뜀
        assertEquals(List.of(d("2028-02-29"), d("2028-03-29"), d("2028-04-29"), d("2028-05-29"),
                        d("2028-06-29"), d("2028-07-29"), d("2028-08-29"), d("2028-09-29"),
                        d("2028-10-29"), d("2028-11-29"), d("2028-12-29"), d("2029-01-29"),
                        d("2029-03-29")),
                dates(s, "2028-02-01", "2029-03-31"));
    }

    @Test
    void repeatEndDateIsInclusive() {
        Schedule s = repeating("a", "x", "2026-11-10", "09:00", "10:00", RepeatRule.DAILY, "2026-11-12");
        assertEquals(List.of(d("2026-11-10"), d("2026-11-11"), d("2026-11-12")),
                dates(s, "2026-11-01", "2026-12-31"));
    }

    @Test
    void excludedDatesAreSkipped() {
        Schedule s = repeating("a", "x", "2026-11-10", "09:00", "10:00", RepeatRule.DAILY, null);
        s.excludeDate(d("2026-11-11"));
        assertEquals(List.of(d("2026-11-10"), d("2026-11-12")), dates(s, "2026-11-10", "2026-11-12"));
        assertFalse(RepeatExpander.occursOn(s, d("2026-11-11")));
        assertTrue(RepeatExpander.matchesRule(s, d("2026-11-11")));
    }

    @Test
    void rangeBeforeStartIsEmpty() {
        Schedule s = repeating("a", "x", "2026-11-10", "09:00", "10:00", RepeatRule.WEEKLY, null);
        assertTrue(dates(s, "2026-10-01", "2026-11-09").isEmpty());
        assertTrue(dates(s, "2026-11-30", "2026-11-01").isEmpty());
    }

    @Test
    void completionIsPerDate() {
        Schedule s = repeating("a", "x", "2026-11-11", "09:00", "10:00", RepeatRule.WEEKLY, null);
        s.setCompletedOn(d("2026-11-18"), true);
        List<Boolean> flags = RepeatExpander.expand(s, d("2026-11-11"), d("2026-11-25")).stream()
                .map(Occurrence::completed).toList();
        assertEquals(List.of(false, true, false), flags);
    }

    @Test
    void hasOccurrencesIsFalseOnlyWhenEverythingIsGone() {
        Schedule open = repeating("a", "x", "2026-11-10", "09:00", "10:00", RepeatRule.DAILY, null);
        assertTrue(RepeatExpander.hasOccurrences(open));
        Schedule ending = repeating("b", "x", "2026-11-10", "09:00", "10:00", RepeatRule.DAILY, "2026-11-10");
        assertTrue(RepeatExpander.hasOccurrences(ending));
        ending.excludeDate(d("2026-11-10"));
        assertFalse(RepeatExpander.hasOccurrences(ending));
    }
}
