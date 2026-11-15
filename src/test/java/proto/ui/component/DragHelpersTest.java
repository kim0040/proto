package proto.ui.component;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import proto.ui.motion.Motion;

import javax.swing.JPanel;
import java.awt.Point;
import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 끌기 도우미: 자동 스크롤 속도, 마우스를 1:1로 따라가는 카드, 자리 틀 이동, 호버 강조, 스프링 오버슈트 한도 (Motion을 꺼서 바로 끝 상태로) */
class DragHelpersTest {

    private boolean motionBefore;

    @BeforeEach
    void setUp() {
        motionBefore = Motion.enabled();
        Motion.setEnabledTransient(false);
    }

    @AfterEach
    void tearDown() {
        Motion.setEnabledTransient(motionBefore);
    }

    @Test
    void 자동_스크롤은_가장자리_깊이에_비례하고_안쪽에서는_멈춘다() {
        assertEquals(0, DragAutoScroll.speed(300, 600, 40, 600), 0.001);
        assertEquals(0, DragAutoScroll.speed(560, 600, 40, 600), 0.001);
        assertEquals(300, DragAutoScroll.speed(580, 600, 40, 600), 0.001);
        assertEquals(570, DragAutoScroll.speed(598, 600, 40, 600), 0.001);
        assertEquals(-300, DragAutoScroll.speed(20, 600, 40, 600), 0.001);
    }

    @Test
    void 자동_스크롤은_영역_밖으로_나가도_1점5배까지만_빨라진다() {
        assertEquals(900, DragAutoScroll.speed(900, 600, 40, 600), 0.001);
        assertEquals(-900, DragAutoScroll.speed(-500, 600, 40, 600), 0.001);
        assertEquals(0, DragAutoScroll.speed(5, 0, 40, 600), 0.001);
    }

    @Test
    void 카드는_잡은_자리를_지키며_마우스를_그대로_따라간다() {
        JPanel host = new JPanel();
        host.setSize(800, 600);
        DragCard card = new DragCard(host, (g, w, h) -> { }, new Rectangle(100, 200, 120, 60), new Point(130, 210), 6);
        card.follow(400.5, 300.25);
        assertEquals(370.5f, card.rest().x, 0.001);
        assertEquals(290.25f, card.rest().y, 0.001);
    }

    @Test
    void 카드는_호스트_밖으로_나가지_않는다() {
        JPanel host = new JPanel();
        host.setSize(800, 600);
        DragCard card = new DragCard(host, (g, w, h) -> { }, new Rectangle(100, 200, 120, 60), new Point(100, 200), 6);
        card.follow(-50, 9000);
        assertEquals(0f, card.rest().x, 0.001);
        assertEquals(540f, card.rest().y, 0.001);
    }

    @Test
    void 들리면_2퍼센트_커지고_모션이_꺼져_있으면_바로_다_들린다() {
        JPanel host = new JPanel();
        host.setSize(800, 600);
        DragCard card = new DragCard(host, (g, w, h) -> { }, new Rectangle(100, 200, 100, 50), new Point(110, 210), 6);
        assertEquals(new Rectangle(100, 200, 100, 50), card.shown());
        card.lift();
        assertEquals(1f, card.liftValue(), 0.001);
        assertEquals(new Rectangle(99, 200, 102, 51), card.shown());
    }

    @Test
    void 날아간_카드는_끝_자리에_서고_들림이_풀리며_도착_동작을_부른다() {
        JPanel host = new JPanel();
        host.setSize(800, 600);
        DragCard card = new DragCard(host, (g, w, h) -> { }, new Rectangle(100, 200, 100, 50), new Point(110, 210), 6);
        card.lift();
        boolean[] done = new boolean[1];
        card.flyTo(new Rectangle(300, 400, 90, 40), DragMotion.BACK_MS, DragMotion.OUT, () -> done[0] = true);
        assertTrue(done[0]);
        assertEquals(new Rectangle(300, 400, 90, 40), card.shown());
        assertEquals(0f, card.liftValue(), 0.001);
    }

    @Test
    void 자리_틀은_처음엔_그_자리에_서고_이후_목표를_따라간다() {
        DragSlot slot = new DragSlot(() -> { });
        assertNull(slot.shown());
        slot.moveTo(new Rectangle(0, 0, 50, 12));
        assertEquals(new Rectangle(0, 0, 50, 12), slot.shown());
        slot.moveTo(new Rectangle(60, 24, 50, 12));
        assertEquals(new Rectangle(60, 24, 50, 12), slot.shown());
        assertEquals(new Rectangle(60, 24, 50, 12), slot.target());
    }

    @Test
    void 호버_강조는_새_칸을_알리면_나타나고_null이면_사라진다() {
        HoverGlow glow = new HoverGlow(() -> { }, 40, 110);
        assertFalse(glow.visible());
        glow.moveTo(new Rectangle(0, 0, 10, 10));
        assertTrue(glow.visible());
        assertEquals(new Rectangle(0, 0, 10, 10), glow.current());
        glow.moveTo(new Rectangle(0, 0, 10, 10));
        assertEquals(new Rectangle(0, 0, 10, 10), glow.current());
        glow.moveTo(null);
        assertNull(glow.current());
        assertFalse(glow.visible());
    }

    @Test
    void 놓일_때_스프링의_오버슈트는_1점5퍼센트_이하다() {
        float peak = 0f;
        for (int i = 0; i <= 1000; i++) {
            peak = Math.max(peak, DragMotion.LAND.interpolate(i / 1000f));
        }
        assertTrue(peak > 1f, "살짝은 넘어야 스프링이다");
        assertTrue(peak <= 1.016f, "오버슈트 " + peak);
        assertEquals(1f, DragMotion.LAND.interpolate(1f), 0.0001);
    }
}
