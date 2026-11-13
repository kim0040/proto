package proto.logic;

import org.junit.jupiter.api.Test;
import proto.entity.Occurrence;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.entity.Schedule;
import proto.entity.ScheduleState;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SampleScheduleFactoryTest {

    @Test
    void daytimeNowHasAFinishedItemADelayedItemAndALaterItem() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 9, 10, 30);
        List<Schedule> samples = SampleScheduleFactory.create(now);

        assertEquals(5, samples.size());
        assertEquals(LocalTime.of(8, 30), startOf(samples, "알고리즘 수업"));
        assertEquals(LocalTime.of(9, 30), startOf(samples, "도서관 책 반납"));
        assertEquals(LocalTime.of(12, 30), startOf(samples, "팀플 회의"));
        assertEquals(1, samples.stream().filter(s -> finishedBefore(s, now)).count());
        assertEquals(1, samples.stream().filter(s -> delayedBefore(s, now)).count());
        assertTrue(samples.stream().anyMatch(s -> laterToday(s, now)));
        assertKeepsTheRequiredSamples(samples, LocalDate.of(2026, 11, 9));
    }

    @Test
    void theWindowIsInclusiveAtEightAndExclusiveAtEightPm() {
        assertEquals(LocalTime.of(6, 0), startOf(SampleScheduleFactory.create(at(8, 0)), "알고리즘 수업"));
        assertEquals(LocalTime.of(17, 50), startOf(SampleScheduleFactory.create(at(19, 59)), "알고리즘 수업"));
        assertEquals(LocalTime.of(9, 0), startOf(SampleScheduleFactory.create(at(20, 0)), "알고리즘 수업"));
    }

    @Test
    void earlyMorningAndLateNightUseTheFixedTimes() {
        List<Schedule> night = SampleScheduleFactory.create(at(23, 0));
        List<Schedule> early = SampleScheduleFactory.create(at(0, 30));
        assertEquals(LocalTime.of(9, 0), startOf(night, "알고리즘 수업"));
        assertEquals(LocalTime.of(7, 0), startOf(night, "도서관 책 반납"));
        assertEquals(LocalTime.of(18, 0), startOf(night, "팀플 회의"));
        assertEquals(LocalTime.of(21, 0), startOf(night, "운동"));
        assertEquals(LocalTime.of(9, 0), startOf(early, "알고리즘 수업"));
        assertKeepsTheRequiredSamples(night, LocalDate.of(2026, 11, 9));
    }

    private static void assertKeepsTheRequiredSamples(List<Schedule> samples, LocalDate today) {
        Schedule team = samples.stream().filter(s -> s.getTitle().equals("팀플 회의")).findFirst().orElseThrow();
        assertEquals(RepeatRule.WEEKLY, team.getRepeatRule());
        assertEquals(ReminderOption.TEN_MINUTES, team.getReminder());
        assertTrue(samples.stream().anyMatch(s -> s.getMemo().contains(",") && s.getMemo().contains("\n")));
        assertTrue(samples.stream().anyMatch(s -> s.getDate().equals(today.plusDays(1))));
    }

    private static boolean finishedBefore(Schedule s, LocalDateTime now) {
        return s.getDate().equals(now.toLocalDate()) && s.isCompletedOn(s.getDate())
                && endOf(s).isBefore(now);
    }

    private static boolean delayedBefore(Schedule s, LocalDateTime now) {
        return s.getDate().equals(now.toLocalDate()) && !s.isCompletedOn(s.getDate())
                && !now.isBefore(endOf(s))
                && StateCalculator.stateOf(Occurrence.of(s, s.getDate()), now) == ScheduleState.DELAYED;
    }

    private static boolean laterToday(Schedule s, LocalDateTime now) {
        return s.getDate().equals(now.toLocalDate()) && now.isBefore(LocalDateTime.of(s.getDate(), s.getStartTime()));
    }

    private static LocalDateTime endOf(Schedule s) {
        return LocalDateTime.of(s.getDate(), s.getEndTime());
    }

    private static LocalTime startOf(List<Schedule> samples, String title) {
        return samples.stream().filter(s -> s.getTitle().equals(title)).findFirst().orElseThrow().getStartTime();
    }

    private static LocalDateTime at(int hour, int minute) {
        return LocalDateTime.of(2026, 11, 9, hour, minute);
    }
}
