package proto.ui.motion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tween: 기존 사용법 유지, 애니메이션 끄기, 움직이는 도중 목표가 바뀌어도 값이 튀지 않는지 */
class TweenTest {

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
    void 애니메이션이_꺼져_있으면_바로_도착한다() {
        Motion.setEnabledTransient(false);
        List<Float> seen = new ArrayList<>();
        Tween t = new Tween(0f, () -> { });
        t.to(1f, 200);
        assertEquals(1f, t.value(), 0f);
        t.springTo(0f, 300);
        assertEquals(0f, t.value(), 0f);
        t.to(0.5f, 100, Motion.OUT);
        assertEquals(0.5f, t.value(), 0f);
        assertTrue(seen.isEmpty());
    }

    @Test
    void 같은_목표로_다시_불러도_아무것도_하지_않는다() throws Exception {
        Motion.setEnabledTransient(true);
        int[] changes = {0};
        Tween t = new Tween(0f, () -> changes[0]++);
        SwingUtilities.invokeAndWait(() -> {
            t.to(0f, 200);
            assertEquals(0, changes[0]);
            t.to(1f, 200);
            t.to(1f, 200);
        });
        Thread.sleep(400);
        assertEquals(1f, t.value(), 0f);
    }

    @Test
    void 움직이다_목표가_바뀌어도_값이_튀지_않고_새_목표로_간다() throws Exception {
        Motion.setEnabledTransient(true);
        List<Float> seen = new ArrayList<>();
        Tween[] holder = new Tween[1];
        holder[0] = new Tween(0f, () -> seen.add(holder[0].value()));
        Tween t = holder[0];
        SwingUtilities.invokeAndWait(() -> t.to(1f, 300));
        Thread.sleep(110);
        float[] atCut = new float[2];
        SwingUtilities.invokeAndWait(() -> {
            atCut[0] = t.value();
            t.to(0f, 300);
            atCut[1] = t.value();
        });
        Thread.sleep(600);
        assertTrue(atCut[0] > 0.2f && atCut[0] < 0.98f, "도중이었다: " + atCut[0]);
        assertEquals(atCut[0], atCut[1], 0f);
        assertEquals(0f, t.value(), 0f);
        float maxStep = 0f;
        float max = 0f;
        float min = 1f;
        for (int i = 1; i < seen.size(); i++) {
            maxStep = Math.max(maxStep, Math.abs(seen.get(i) - seen.get(i - 1)));
            max = Math.max(max, seen.get(i));
            min = Math.min(min, seen.get(i));
        }
        // 값은 흐른 시간으로 정해지므로, 테스트를 여럿 함께 돌려 CPU가 바쁘면 프레임 사이가 벌어져 한 걸음이 커진다.
        // 목표를 바꾼 순간에 튀지 않는지는 위의 atCut 비교가 확인하고, 여기서는 "통째로 건너뛰지 않음"만 넉넉하게 본다.
        // 느린 CI 러너(macOS)에서는 프레임 사이가 100ms 넘게 벌어져 0.6쯤 뛰기도 했다. 여기서는 "끝까지 한 번에 건너뛰지 않음"만 본다
        assertTrue(maxStep < 0.9f, "한 프레임에 크게 뛰었다: " + maxStep);
        assertTrue(max <= 1f && min >= 0f, "범위를 넘었다: " + min + " ~ " + max);
    }

    @Test
    void set은_진행_중인_움직임을_멈추고_바로_정한다() throws Exception {
        Motion.setEnabledTransient(true);
        Tween t = new Tween(0f, () -> { });
        SwingUtilities.invokeAndWait(() -> {
            t.to(1f, 300);
            t.set(0.25f);
        });
        Thread.sleep(400);
        assertEquals(0.25f, t.value(), 0f);
    }

    @Test
    void 스프링으로_가다_되돌려도_이어지고_끝에는_목표에_딱_맞는다() throws Exception {
        Motion.setEnabledTransient(true);
        Tween t = new Tween(0f, () -> { });
        SwingUtilities.invokeAndWait(() -> t.springTo(1f, 340));
        Thread.sleep(120);
        SwingUtilities.invokeAndWait(() -> t.springTo(0f, 340));
        Thread.sleep(700);
        assertEquals(0f, t.value(), 0f);
    }
    @Test
    void 같은_차례에_목표를_되돌려도_EDT에서_예외가_나지_않는다() throws Exception {
        Motion.setEnabledTransient(true);
        try (EdtErrors edt = new EdtErrors()) {
            Tween t = new Tween(0f, () -> { });
            SwingUtilities.invokeAndWait(() -> {
                t.to(1f, 200);
                t.to(0f, 200);
            });
            Thread.sleep(400);
            EdtErrors.flush();
            assertEquals(List.of(), edt.errors());
            assertEquals(0f, t.value(), 0f);
        }
    }

    @Test
    void 시간보다_먼저_목표에_닿으면_프레임을_대던_모션도_멈추고_다음_움직임이_처음부터_시작된다() throws Exception {
        Motion.setEnabledTransient(true);
        try (EdtErrors edt = new EdtErrors()) {
            int[] changes = {0};
            Tween t = new Tween(0f, () -> changes[0]++);
            SwingUtilities.invokeAndWait(() -> {
                t.to(1f, 400);
                t.to(0f, 400);
            });
            Thread.sleep(120);
            int[] settled = new int[1];
            SwingUtilities.invokeAndWait(() -> settled[0] = changes[0]);
            Thread.sleep(150);
            SwingUtilities.invokeAndWait(() -> assertEquals(settled[0], changes[0], "닿은 뒤에도 프레임이 계속 왔다"));
            SwingUtilities.invokeAndWait(() -> t.to(1f, 120));
            Thread.sleep(400);
            EdtErrors.flush();
            assertEquals(List.of(), edt.errors());
            assertEquals(1f, t.value(), 0f);
        }
    }
}
