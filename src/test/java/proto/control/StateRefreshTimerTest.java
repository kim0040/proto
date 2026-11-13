package proto.control;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static proto.control.ControllerFixture.draft;

class StateRefreshTimerTest {

    @TempDir
    Path dir;

    @Test
    void firesStatesChangedOnlyWhenSomeStateDiffersFromThePreviousTick() {
        ControllerFixture f = new ControllerFixture(dir, "2026-11-09T09:59");
        f.schedules.create(draft("회의", "2026-11-09", "10:00", "11:00"));
        StateRefreshTimer timer = new StateRefreshTimer(f.schedules);

        timer.tick();
        assertEquals(0, f.recorder.statesChanged); // 예정 그대로

        f.clock.set("2026-11-09T10:00");
        timer.tick();
        assertEquals(1, f.recorder.statesChanged); // 예정 → 진행 중

        f.clock.set("2026-11-09T10:30");
        timer.tick();
        assertEquals(1, f.recorder.statesChanged); // 진행 중 그대로

        f.clock.set("2026-11-09T11:00");
        timer.tick();
        assertEquals(2, f.recorder.statesChanged); // 진행 중 → 지연
    }

    @Test
    void scheduleAddedAfterTheTimerStillReportsItsFirstStateChange() {
        ControllerFixture f = new ControllerFixture(dir, "2026-11-09T09:59");
        StateRefreshTimer timer = new StateRefreshTimer(f.schedules);
        f.schedules.create(draft("회의", "2026-11-09", "10:00", "11:00"));

        // 중간 tick 없이 바로 시작 시각으로 넘긴다. 등록 때 기준을 다시 잡아야 첫 전환을 놓치지 않는다
        f.clock.set("2026-11-09T10:00");
        timer.tick();
        assertEquals(1, f.recorder.statesChanged); // 예정 → 진행 중

        f.clock.set("2026-11-09T10:01");
        timer.tick();
        assertEquals(1, f.recorder.statesChanged);
    }

    @Test
    void addingAScheduleThenTickingWithNoTimeChangeDoesNotFire() {
        ControllerFixture f = new ControllerFixture(dir, "2026-11-09T09:59");
        StateRefreshTimer timer = new StateRefreshTimer(f.schedules);
        f.schedules.create(draft("회의", "2026-11-09", "10:00", "11:00"));

        timer.tick();
        assertEquals(0, f.recorder.statesChanged);
    }

    @Test
    void ownStateNotificationDoesNotHideTheNextTransition() {
        ControllerFixture f = new ControllerFixture(dir, "2026-11-09T09:59");
        f.schedules.create(draft("회의", "2026-11-09", "10:00", "11:00"));
        f.schedules.addListener(new ScheduleListener() {
            @Override
            public void schedulesChanged() {
            }

            @Override
            public void statesChanged() {
                f.clock.set("2026-11-09T11:00");
            }
        });
        StateRefreshTimer timer = new StateRefreshTimer(f.schedules);

        f.clock.set("2026-11-09T10:00");
        timer.tick();
        assertEquals(1, f.recorder.statesChanged);

        timer.tick();
        assertEquals(2, f.recorder.statesChanged); // 알림 도중 11:00이 되어 지연으로 바뀜
    }

    @Test
    void aKeyLeavingTheWindowCountsAsAChange() {
        ControllerFixture f = new ControllerFixture(dir, "2026-11-09T12:00");
        f.schedules.create(draft("어제", "2026-11-08", "08:00", "09:00"));
        StateRefreshTimer timer = new StateRefreshTimer(f.schedules);

        timer.tick();
        assertEquals(0, f.recorder.statesChanged);

        f.clock.set("2026-11-10T12:00"); // 11-08은 보이는 범위(어제~7일) 밖으로 나간다
        timer.tick();
        assertEquals(1, f.recorder.statesChanged);
    }

    @Test
    void aKeyEnteringTheWindowCountsAsAChange() {
        ControllerFixture f = new ControllerFixture(dir, "2026-11-09T12:00");
        f.schedules.create(draft("다음주", "2026-11-17", "15:00", "16:00"));
        StateRefreshTimer timer = new StateRefreshTimer(f.schedules);

        timer.tick();
        assertEquals(0, f.recorder.statesChanged); // 11-17은 아직 범위(11-16까지) 밖

        f.clock.set("2026-11-10T12:00");
        timer.tick();
        assertEquals(1, f.recorder.statesChanged);
    }
}
