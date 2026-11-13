package proto.logic;

import org.junit.jupiter.api.Test;
import proto.entity.Occurrence;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.entity.ScheduleState;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static proto.TestData.d;
import static proto.TestData.t;

class StateCalculatorTest {

    private static Occurrence occ(boolean completed) {
        return new Occurrence("id", d("2026-11-09"), "a", t("10:00"), t("11:00"), "class", "",
                RepeatRule.NONE, ReminderOption.NONE, completed);
    }

    private static ScheduleState at(String time, boolean completed) {
        return StateCalculator.stateOf(occ(completed), LocalDateTime.parse("2026-11-09T" + time));
    }

    @Test
    void beforeStartIsScheduled() {
        assertEquals(ScheduleState.SCHEDULED, at("09:59:59", false));
    }

    @Test
    void startInstantIsInProgress() {
        assertEquals(ScheduleState.IN_PROGRESS, at("10:00:00", false));
        assertEquals(ScheduleState.IN_PROGRESS, at("10:59:59", false));
    }

    @Test
    void endInstantIsDelayedWhenNotCompleted() {
        assertEquals(ScheduleState.DELAYED, at("11:00:00", false));
        assertEquals(ScheduleState.DELAYED, at("23:00:00", false));
    }

    @Test
    void completedIsDoneInEveryTimePosition() {
        assertEquals(ScheduleState.DONE, at("09:00:00", true));
        assertEquals(ScheduleState.DONE, at("10:30:00", true));
        assertEquals(ScheduleState.DONE, at("12:00:00", true));
    }

    @Test
    void previousDayScheduleNotCompletedIsDelayed() {
        Occurrence o = occ(false);
        assertEquals(ScheduleState.DELAYED, StateCalculator.stateOf(o, LocalDateTime.parse("2026-11-10T00:00")));
    }
}
