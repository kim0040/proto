package proto.control;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import proto.entity.Occurrence;
import proto.entity.ReminderOption;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static proto.control.ControllerFixture.draft;
import static proto.control.ControllerFixture.withReminder;

/** 시계는 2026-11-09 09:00 에서 시작. 10:00~11:00 "회의"는 10분 전 알림이라 알림 시각이 09:50 */
class ReminderTimerTest {

    @TempDir
    Path dir;
    ControllerFixture f;
    ReminderTimer timer;
    final List<String> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        f = new ControllerFixture(dir, "2026-11-09T09:00");
        f.schedules.create(withReminder(draft("회의", "2026-11-09", "10:00", "11:00"), ReminderOption.TEN_MINUTES));
        timer = new ReminderTimer(f.schedules, f.clock);
        timer.addListener((o, snoozed) -> events.add(o.title() + (snoozed ? " (다시)" : "")));
    }

    private void checkAt(String time) {
        f.clock.set("2026-11-09T" + time);
        timer.checkNow();
    }

    @Test
    void firesOnceWhenReminderTimeArrives() {
        checkAt("09:49:59");
        assertTrue(events.isEmpty());
        checkAt("09:50");
        assertEquals(List.of("회의"), events);
        checkAt("09:51");
        checkAt("10:30");
        assertEquals(List.of("회의"), events);
    }

    @Test
    void snoozeFiresAgainAfterTheGivenMinutesAndOnlyOnce() {
        checkAt("09:50");
        Occurrence o = f.occurrenceOn("회의", "2026-11-09");
        f.clock.set("2026-11-09T09:52");
        assertTrue(timer.snooze(o, 5).success()); // 09:57에 다시

        checkAt("09:56:59");
        assertEquals(List.of("회의"), events);
        checkAt("09:57");
        assertEquals(List.of("회의", "회의 (다시)"), events);
        checkAt("10:30");
        assertEquals(List.of("회의", "회의 (다시)"), events);
    }

    @Test
    void snoozeIsDroppedWhenTheOccurrenceWasCompletedMeanwhile() {
        checkAt("09:50");
        Occurrence o = f.occurrenceOn("회의", "2026-11-09");
        f.clock.set("2026-11-09T09:52");
        timer.snooze(o, 5);
        f.schedules.setCompleted(o, true);
        checkAt("09:57");
        checkAt("10:30");
        assertEquals(List.of("회의"), events);
    }

    @Test
    void snoozeIsDroppedWhenTheOccurrenceWasDeletedMeanwhile() {
        checkAt("09:50");
        Occurrence o = f.occurrenceOn("회의", "2026-11-09");
        timer.snooze(o, 1);
        f.schedules.delete(o, EditScope.THIS_ONLY);
        checkAt("09:55");
        assertEquals(List.of("회의"), events);
    }

    @Test
    void snoozeMinutesMustBeBetween1And60() {
        Occurrence o = f.occurrenceOn("회의", "2026-11-09");
        assertFalse(timer.snooze(o, 0).success());
        assertFalse(timer.snooze(o, 61).success());
        assertFalse(timer.snooze(o, -5).success());
        assertTrue(timer.snooze(o, 1).success());
        assertTrue(timer.snooze(o, 60).success());
    }

    @Test
    void completedScheduleIsNotNotified() {
        f.schedules.setCompleted(f.occurrenceOn("회의", "2026-11-09"), true);
        checkAt("09:55");
        assertTrue(events.isEmpty());
    }

    @Test
    void reminderThatPassedBeforeTheAppStartedIsNeverSent() {
        f.clock.set("2026-11-09T09:55");
        ReminderTimer late = new ReminderTimer(f.schedules, f.clock); // 앱을 알림 시각(09:50) 뒤에 켬
        List<String> lateEvents = new ArrayList<>();
        late.addListener((o, s) -> lateEvents.add(o.title()));
        late.checkNow();
        f.clock.set("2026-11-09T10:30");
        late.checkNow();
        assertTrue(lateEvents.isEmpty());
    }

    @Test
    void reminderForTomorrowEarlyMorningFiresTheEveningBefore() {
        f.clock.set("2026-11-09T23:30");
        ReminderTimer night = new ReminderTimer(f.schedules, f.clock);
        List<String> nightEvents = new ArrayList<>();
        night.addListener((o, s) -> nightEvents.add(o.title() + "@" + o.date()));
        f.schedules.create(withReminder(draft("새벽 일정", "2026-11-10", "00:10", "01:00"), ReminderOption.THIRTY_MINUTES));

        f.clock.set("2026-11-09T23:39");
        night.checkNow();
        assertTrue(nightEvents.isEmpty());
        f.clock.set("2026-11-09T23:41"); // 알림 시각 23:40
        night.checkNow();
        assertEquals(List.of("새벽 일정@2026-11-10"), nightEvents);
        f.clock.set("2026-11-10T00:05");
        night.checkNow();
        assertEquals(1, nightEvents.size());
    }
}
