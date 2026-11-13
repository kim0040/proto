package proto.logic;

import org.junit.jupiter.api.Test;
import proto.entity.Occurrence;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static proto.TestData.d;
import static proto.TestData.t;

class ReminderCheckerTest {

    private static final LocalDateTime STARTED = LocalDateTime.parse("2026-11-09T09:00");

    /** 10:00 시작 일정이고 알림은 10분 전이라 알림 시각은 09:50 */
    private static Occurrence occ(ReminderOption reminder, boolean completed) {
        return new Occurrence("a", d("2026-11-09"), "x", t("10:00"), t("11:00"), "class", "",
                RepeatRule.NONE, reminder, completed);
    }

    private static List<Occurrence> due(Occurrence o, String now, Set<String> notified) {
        return ReminderChecker.findDue(List.of(o), LocalDateTime.parse("2026-11-09T" + now), STARTED, notified);
    }

    @Test
    void notDueBeforeReminderTime() {
        assertTrue(due(occ(ReminderOption.TEN_MINUTES, false), "09:49:59", new HashSet<>()).isEmpty());
    }

    @Test
    void dueExactlyAtReminderTimeAndAfter() {
        Occurrence o = occ(ReminderOption.TEN_MINUTES, false);
        assertEquals(List.of(o), due(o, "09:50:00", new HashSet<>()));
        assertEquals(List.of(o), due(o, "10:30:00", new HashSet<>()));
    }

    @Test
    void notDueTwiceOnceItsKeyWasNotified() {
        Occurrence o = occ(ReminderOption.TEN_MINUTES, false);
        assertTrue(due(o, "09:55", Set.of(o.key())).isEmpty());
    }

    @Test
    void notDueWhenCompleted() {
        assertTrue(due(occ(ReminderOption.TEN_MINUTES, true), "09:55", new HashSet<>()).isEmpty());
    }

    @Test
    void notDueWhenReminderTimePassedBeforeAppStarted() {
        // 알림 시각은 09:50인데 앱을 09:51에 켰다면, 꺼져 있던 동안 지난 알림이라 보내지 않는다
        Occurrence o = occ(ReminderOption.TEN_MINUTES, false);
        List<Occurrence> result = ReminderChecker.findDue(List.of(o), LocalDateTime.parse("2026-11-09T09:55"),
                LocalDateTime.parse("2026-11-09T09:51"), new HashSet<>());
        assertTrue(result.isEmpty());
    }

    @Test
    void reminderTimeEqualToAppStartIsStillDue() {
        Occurrence o = occ(ReminderOption.TEN_MINUTES, false);
        List<Occurrence> result = ReminderChecker.findDue(List.of(o), LocalDateTime.parse("2026-11-09T09:55"),
                LocalDateTime.parse("2026-11-09T09:50"), new HashSet<>());
        assertEquals(List.of(o), result);
    }

    @Test
    void neverDueWithoutReminder() {
        assertTrue(due(occ(ReminderOption.NONE, false), "10:30", new HashSet<>()).isEmpty());
    }

    @Test
    void reminderOptionDecidesTheReminderTime() {
        Occurrence five = occ(ReminderOption.FIVE_MINUTES, false);   // 09:55
        Occurrence thirty = occ(ReminderOption.THIRTY_MINUTES, false); // 09:30
        List<Occurrence> result = ReminderChecker.findDue(List.of(five, thirty),
                LocalDateTime.parse("2026-11-09T09:40"), STARTED, new HashSet<>());
        assertEquals(List.of(thirty), result);
    }
}
