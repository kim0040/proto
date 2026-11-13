package proto.server;

import org.junit.jupiter.api.Test;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.entity.Schedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleJsonMapperTest {

    @Test
    void roundTripsEveryFieldIncludingNullRepeatEnd() {
        Schedule full = new Schedule("id-1", "제목, \"쉼표\"와 한글", LocalDate.of(2026, 11, 9),
                LocalTime.of(9, 30), LocalTime.of(10, 45), "team", "메모 1줄\n둘째, 쉼표",
                RepeatRule.WEEKLY, LocalDate.of(2026, 12, 31), ReminderOption.THIRTY_MINUTES);
        full.setCompletedOn(LocalDate.of(2026, 11, 9), true);
        full.setCompletedOn(LocalDate.of(2026, 11, 16), true);
        full.excludeDate(LocalDate.of(2026, 11, 23));
        Schedule plain = new Schedule("id-2", "단순", LocalDate.of(2026, 11, 10),
                LocalTime.of(0, 0), LocalTime.of(23, 59), "class", "",
                RepeatRule.NONE, null, ReminderOption.NONE);

        List<Schedule> back = ScheduleJsonMapper.readSnapshot(
                ScheduleJsonMapper.writeSnapshot(List.of(full, plain)));

        assertEquals(2, back.size());
        assertSame(full, back.get(0));
        assertSame(plain, back.get(1));
        assertNull(back.get(1).getRepeatEndDate());
        assertTrue(back.get(1).getCompletedDates().isEmpty());
        assertTrue(back.get(1).getExcludedDates().isEmpty());
    }

    private static void assertSame(Schedule expected, Schedule back) {
        assertEquals(expected.getId(), back.getId());
        assertEquals(expected.getTitle(), back.getTitle());
        assertEquals(expected.getMemo(), back.getMemo());
        assertEquals(expected.getDate(), back.getDate());
        assertEquals(expected.getStartTime(), back.getStartTime());
        assertEquals(expected.getEndTime(), back.getEndTime());
        assertEquals(expected.getCategoryId(), back.getCategoryId());
        assertEquals(expected.getRepeatRule(), back.getRepeatRule());
        assertEquals(expected.getRepeatEndDate(), back.getRepeatEndDate());
        assertEquals(expected.getReminder(), back.getReminder());
        assertEquals(expected.getCompletedDates(), back.getCompletedDates());
        assertEquals(expected.getExcludedDates(), back.getExcludedDates());
    }
}
