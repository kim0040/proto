package proto.ui.motion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 목표를 따라가는 감쇠 스프링: 이어짐(값·속도), 시간 쪼개도 같은 곡선, 오버슈트 없음, 도착 시각 */
class FollowerTest {

    @Test
    void 목표를_바꿔도_값과_속도가_그대로_이어진다() {
        Follower f = Follower.critical(0, 0, 1, 200, 0, 1);
        f.step(0.05);
        double x = f.x();
        double v = f.v();
        Follower g = f.retarget(0, 200);
        assertEquals(x, g.x(), 0.0);
        assertEquals(v, g.v(), 0.0);
        assertEquals(0, g.target(), 0.0);
    }

    @Test
    void 시간을_쪼개어_나아가도_한_번에_나아간_것과_같다() {
        Follower a = Follower.critical(0.2, 1.5, 1, 180, 0, 1);
        Follower b = Follower.critical(0.2, 1.5, 1, 180, 0, 1);
        for (int i = 0; i < 100; i++) {
            a.step(0.001);
        }
        b.step(0.1);
        assertEquals(b.x(), a.x(), 1e-9);
        assertEquals(b.v(), a.v(), 1e-9);
    }

    @Test
    void 과소감쇠도_시간을_쪼개어도_같은_곡선이다() {
        Follower a = Follower.bouncy(0, 0, 1, 340);
        Follower b = Follower.bouncy(0, 0, 1, 340);
        for (int i = 0; i < 80; i++) {
            a.step(0.001);
        }
        b.step(0.08);
        assertEquals(b.x(), a.x(), 1e-9);
        assertEquals(b.v(), a.v(), 1e-9);
    }

    @Test
    void 정해진_시간_안에_목표에_도착한다() {
        Follower f = Follower.critical(0, 0, 1, 180, 0, 1);
        f.step(0.180);
        assertEquals(1.0, f.x(), 0.006);
        Follower s = Follower.bouncy(0, 0, 1, 340);
        s.step(0.340);
        assertEquals(1.0, s.x(), 0.006);
    }

    @Test
    void 임계감쇠는_범위를_넘지_않는다_세게_달리다_되돌려도() {
        Follower f = Follower.critical(0, 0, 1, 160, 0, 1);
        double max = 0;
        for (int i = 0; i < 12; i++) {
            f.step(0.008);
            max = Math.max(max, f.x());
        }
        Follower back = f.retarget(0, 160);
        double min = 1;
        double top = back.x();
        for (int i = 0; i < 80; i++) {
            back.step(0.008);
            min = Math.min(min, back.x());
            top = Math.max(top, back.x());
        }
        assertTrue(max <= 1.0 && top <= 1.0, "위로 넘치지 않는다: " + top);
        assertTrue(min >= 0.0, "아래로 넘치지 않는다: " + min);
        assertEquals(0.0, back.x(), 0.01);
    }

    @Test
    void 스프링의_오버슈트는_육_퍼센트_안팎이다() {
        Follower f = Follower.bouncy(0, 0, 1, 340);
        double peak = 0;
        for (int i = 0; i < 400; i++) {
            f.step(0.001);
            peak = Math.max(peak, f.x());
        }
        assertTrue(peak > 1.02 && peak < 1.09, "peak=" + peak);
    }

    @Test
    void 도착하면_멈춘_것으로_본다() {
        Follower f = Follower.critical(0, 0, 1, 100, 0, 1);
        assertFalse(f.settled());
        f.step(1.0);
        assertTrue(f.settled());
    }

    @Test
    void 아주_긴_프레임_간격에도_안정적이다() {
        Follower f = Follower.critical(0, 3, 1, 160, 0, 1);
        f.step(5.0);
        assertEquals(1.0, f.x(), 1e-6);
        assertEquals(0.0, f.v(), 1e-6);
    }
}
