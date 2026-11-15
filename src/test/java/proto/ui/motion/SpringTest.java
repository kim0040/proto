package proto.ui.motion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringTest {

    private boolean before;

    @BeforeEach
    void rememberSetting() {
        before = Motion.enabled();
    }

    @AfterEach
    void restoreSetting() {
        Motion.setEnabledTransient(before);
    }

    @Test
    void startsAtZeroAndEndsAtOne() {
        Spring s = Spring.of(0.06);
        assertEquals(0f, s.interpolate(0f), 1e-4);
        assertEquals(1f, s.interpolate(1f), 1e-4);
    }

    @Test
    void peakOvershootMatchesConfiguredBound() {
        for (double os : new double[] {0.05, 0.06, 0.08, 0.12}) {
            Spring s = Spring.of(os);
            double peak = 0;
            for (int i = 0; i <= 1000; i++) {
                peak = Math.max(peak, s.interpolate(i / 1000f) - 1.0);
            }
            assertEquals(os, peak, os * 0.10,"overshoot " + os);
        }
    }

    @Test
    void overshootsOnceAndNeverGoesBelowZeroOrRebounds() {
        Spring s = Spring.of(0.06);
        int crossings = 0;
        boolean above = false;
        for (int i = 1; i <= 1000; i++) {
            float v = s.interpolate(i / 1000f);
            assertTrue(v >= 0f, "never negative");
            boolean now = v > 1.0005f;
            if (now && !above) {
                crossings++;
            }
            above = now;
        }
        assertEquals(1, crossings, "one visible overshoot, the rebound below 1 stays under 0.5%");
        for (int i = 0; i <= 1000; i++) {
            assertTrue(s.interpolate(i / 1000f) > 0.995f || i < 400 || i > 1000, "dip after first peak stays tiny");
        }
    }

    @Test
    void isSettledByNinetyPercent() {
        Spring s = Spring.of(0.08);
        for (int i = 900; i <= 1000; i++) {
            assertTrue(Math.abs(s.interpolate(i / 1000f) - 1f) < 0.01f, "t=" + i / 1000f);
        }
    }

    @Test
    void rejectsInvalidOvershoot() {
        assertThrows(IllegalArgumentException.class, () -> Spring.of(0));
        assertThrows(IllegalArgumentException.class, () -> Spring.of(0.9));
    }

    @Test
    void disabledMotionJumpsToEnd() {
        Motion.setEnabledTransient(false);
        List<Float> frames = new ArrayList<>();
        boolean[] done = {false};
        Motion.run(300, Motion.SPRING, frames::add, () -> done[0] = true);
        assertEquals(List.of(1f), frames);
        assertTrue(done[0]);
    }

    @Test
    void disabledTweenArrivesAtTargetWithSpring() {
        Motion.setEnabledTransient(false);
        Tween t = new Tween(0f, () -> { });
        t.springTo(1f, 320);
        assertEquals(1f, t.value(), 0f);
    }

    @Test
    void rectTweenInterpolatesAndNeverGetsNegativeSize() {
        RectTween rt = new RectTween(new Rectangle(0, 0, 10, 10), new Rectangle(100, 40, 2, 2), r -> { });
        assertEquals(new Rectangle(0, 0, 10, 10), rt.at(0f));
        assertEquals(new Rectangle(100, 40, 2, 2), rt.at(1f));
        assertTrue(rt.at(3f).width >= 0 && rt.at(3f).height >= 0);
    }
}
