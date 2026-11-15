package proto.ui.component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 화면 전환 시간표: 나가는 화면은 빨리 사라지고 들어오는 화면은 밀리며 나타난다 */
class TransitionPlanTest {

    private final TransitionPlan page = new TransitionPlan(80, 170, 0, 10);

    @Test
    void 시작과_끝의_값이_맞는다() {
        assertEquals(1f, page.outgoingAlpha(0f), 1e-4);
        assertEquals(0f, page.outgoingAlpha(80f), 1e-4);
        assertEquals(0f, page.incomingAlpha(0f), 1e-4);
        assertEquals(1f, page.incomingAlpha(170f), 1e-4);
        assertEquals(10, page.offsetY(0f));
        assertEquals(0, page.offsetY(170f));
        assertEquals(0, page.offsetX(50f));
    }

    @Test
    void 전체_길이는_둘_중_긴_쪽이다() {
        assertEquals(170, page.totalMs());
    }

    @Test
    void 나가는_화면은_들어오는_화면보다_먼저_사라진다() {
        assertEquals(0f, page.outgoingAlpha(120f), 1e-4);
        assertTrue(page.incomingAlpha(120f) < 1f || page.incomingAlpha(120f) > 0.9f);
    }

    @Test
    void 투명도는_한쪽으로만_움직이고_범위를_넘지_않는다() {
        float prevIn = 0f;
        float prevOut = 1f;
        for (int ms = 0; ms <= 200; ms += 5) {
            float in = page.incomingAlpha(ms);
            float out = page.outgoingAlpha(ms);
            assertTrue(in >= prevIn - 1e-6 && in <= 1f, "들어옴: " + in);
            assertTrue(out <= prevOut + 1e-6 && out >= 0f, "나감: " + out);
            prevIn = in;
            prevOut = out;
        }
    }

    @Test
    void 밀림은_줄어들기만_하고_반대편으로_넘지_않는다() {
        TransitionPlan slide = new TransitionPlan(110, 160, -40, 0);
        int prev = slide.offsetX(0f);
        assertEquals(-40, prev);
        for (int ms = 0; ms <= 200; ms += 5) {
            int x = slide.offsetX(ms);
            assertTrue(x >= prev && x <= 0, "x=" + x);
            prev = x;
        }
    }
}
