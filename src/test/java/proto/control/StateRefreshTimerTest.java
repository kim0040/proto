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
}
