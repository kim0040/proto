package proto.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllDayScheduleTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 16);

    private static Schedule allDay() {
        return new Schedule("a", "학회", DAY, LocalTime.of(14, 0), LocalTime.of(9, 0), "class", "",
                RepeatRule.NONE, null, ReminderOption.THIRTY_MINUTES, true);
    }

    @Test
    void allDayScheduleReadsAsMidnightTo2359WithoutReminder() {
        Schedule s = allDay();
        assertTrue(s.isAllDay());
        assertEquals(LocalTime.MIDNIGHT, s.getStartTime());
        assertEquals(LocalTime.of(23, 59), s.getEndTime());
        assertEquals(ReminderOption.NONE, s.getReminder());
        s.setStartTime(LocalTime.of(8, 0));
        s.setReminder(ReminderOption.FIVE_MINUTES);
        assertEquals(LocalTime.MIDNIGHT, s.getStartTime());
        assertEquals(ReminderOption.NONE, s.getReminder());
    }

    @Test
    void oldConstructorIsNotAllDayAndTurningAllDayOffKeepsNormalisedTimes() {
        Schedule timed = new Schedule("t", "수업", DAY, LocalTime.of(9, 0), LocalTime.of(10, 0), "class", "",
                RepeatRule.NONE, null, ReminderOption.TEN_MINUTES);
        assertFalse(timed.isAllDay());
        timed.setAllDay(true);
        timed.setAllDay(false);
        assertEquals(LocalTime.MIDNIGHT, timed.getStartTime());
        assertEquals(LocalTime.of(23, 59), timed.getEndTime());
        assertEquals(ReminderOption.NONE, timed.getReminder());
    }

    @Test
    void copiesKeepAllDay() {
        assertTrue(allDay().copy().isAllDay());
        assertTrue(allDay().copyWithId("b").isAllDay());
    }

    @Test
    void occurrenceOfAllDayCoversTheWholeDay() {
        Occurrence o = Occurrence.of(allDay(), DAY);
        assertTrue(o.allDay());
        assertEquals(LocalDateTime.of(2026, 11, 16, 0, 0), o.startDateTime());
        assertEquals(LocalDateTime.of(2026, 11, 17, 0, 0), o.endDateTime());
    }

    @Test
    void occurrenceRecordNormalisesAndOldConstructorMeansTimed() {
        Occurrence o = new Occurrence("a", DAY, "t", LocalTime.of(9, 0), LocalTime.of(10, 0), "class", "",
                RepeatRule.NONE, ReminderOption.FIVE_MINUTES, false, true);
        assertEquals(LocalTime.MIDNIGHT, o.startTime());
        assertEquals(LocalTime.of(23, 59), o.endTime());
        assertEquals(ReminderOption.NONE, o.reminder());
        Occurrence old = new Occurrence("a", DAY, "t", LocalTime.of(9, 0), LocalTime.of(10, 0), "class", "",
                RepeatRule.NONE, ReminderOption.FIVE_MINUTES, false);
        assertFalse(old.allDay());
        assertEquals(LocalDateTime.of(2026, 11, 16, 10, 0), old.endDateTime());
    }
}
