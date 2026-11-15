package proto.logic;

import org.junit.jupiter.api.Test;
import proto.entity.Occurrence;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.entity.Schedule;
import proto.entity.WeeklyStatistics;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static proto.TestData.d;
import static proto.TestData.occ;
import static proto.TestData.t;

class AllDayLogicTest {

    private static Occurrence allDayOcc(String date) {
        return new Occurrence("all", d(date), "학회", t("09:00"), t("10:00"), "personal", "", RepeatRule.NONE,
                ReminderOption.THIRTY_MINUTES, false, true);
    }

    @Test
    void allDayNeverOverlaps() {
        assertFalse(ConflictDetector.overlaps(allDayOcc("2026-11-10"), occ("2026-11-10", "09:00", "10:00")));
        assertFalse(ConflictDetector.overlaps(occ("2026-11-10", "09:00", "10:00"), allDayOcc("2026-11-10")));
        Schedule existing = new Schedule("e", "학회", d("2026-11-10"), null, null, "class", "",
                RepeatRule.DAILY, null, ReminderOption.NONE, true);
        Schedule candidate = new Schedule("c", "수업", d("2026-11-10"), t("09:00"), t("10:00"), "class", "",
                RepeatRule.WEEKLY, null, ReminderOption.NONE);
        assertTrue(ConflictDetector.findConflicts(candidate, List.of(existing), o -> false).isEmpty());
    }

    @Test
    void allDayNeverReminds() {
        Occurrence o = allDayOcc("2026-11-10");
        LocalDateTime start = LocalDateTime.of(2026, 11, 9, 0, 0);
        assertTrue(ReminderChecker.findDue(List.of(o), LocalDateTime.of(2026, 11, 10, 12, 0), start, Set.of())
                .isEmpty());
    }

    @Test
    void freeSlotsIgnoreAllDay() {
        LocalDate day = d("2026-11-10");
        assertEquals(1, FreeSlotFinder.find(List.of(allDayOcc("2026-11-10")), day, 60, t("09:00"), t("12:00"),
                LocalDateTime.of(2026, 11, 1, 0, 0)).size());
    }

    @Test
    void statisticsCountAllDayWithoutMinutes() {
        WeeklyStatistics stats = StatisticsCalculator.calculate(d("2026-11-10"),
                List.of(allDayOcc("2026-11-10"), occ("2026-11-11", "09:00", "10:30")),
                LocalDateTime.of(2026, 11, 12, 0, 0));
        assertEquals(2, stats.total());
        assertEquals(2, stats.delayed());
        assertEquals(Map.of("class", 90L), stats.minutesByCategory());
    }

    @Test
    void seriesEditCopiesAllDay() {
        Schedule target = new Schedule("t", "수업", d("2026-11-10"), t("09:00"), t("10:00"), "class", "",
                RepeatRule.NONE, null, ReminderOption.FIVE_MINUTES);
        Schedule candidate = new Schedule("c", "학회", d("2026-11-10"), null, null, "class", "",
                RepeatRule.NONE, null, ReminderOption.NONE, true);
        SeriesEditor.updateInPlace(target, candidate);
        assertTrue(target.isAllDay());
        assertEquals(LocalTime.MIDNIGHT, target.getStartTime());
        Schedule timedAgain = new Schedule("c2", "수업", d("2026-11-10"), t("13:00"), t("14:00"), "class", "",
                RepeatRule.NONE, null, ReminderOption.NONE);
        SeriesEditor.updateInPlace(target, timedAgain);
        assertFalse(target.isAllDay());
        assertEquals(t("13:00"), target.getStartTime());
    }
}
