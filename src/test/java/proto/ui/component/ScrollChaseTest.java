package proto.ui.component;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import proto.ui.motion.Motion;

import javax.swing.JScrollBar;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 휠 한 칸이 목표로 부드럽게 모이는지: 목표 도착, 누적, 한계 안, 직접 움직이면 중단 */
class ScrollChaseTest {

    private boolean before;

    @BeforeEach
    void rememberSetting() {
        before = Motion.enabled();
        Motion.setEnabledTransient(true);
    }

    @AfterEach
    void restoreSetting() {
        Motion.setEnabledTransient(before);
    }

    private static JScrollBar bar() {
        return new JScrollBar(JScrollBar.VERTICAL, 0, 100, 0, 2000);
    }

    @Test
    void 한_칸은_여러_걸음에_걸쳐_목표에_정확히_도착한다() throws Exception {
        JScrollBar bar = bar();
        List<Integer> seen = new ArrayList<>();
        bar.getModel().addChangeListener(e -> seen.add(bar.getValue()));
        ScrollChase chase = new ScrollChase(bar);
        SwingUtilities.invokeAndWait(() -> chase.push(50));
        Thread.sleep(500);
        assertEquals(50, bar.getValue());
        // 걸음 수는 타이머가 실제로 몇 번 돌았는지에 달려 있어 느린 CI 러너에서는 적다. "한 번에 뛰지 않았다"(2걸음 넘게)만 본다
        assertTrue(seen.size() > 2, "여러 걸음으로 갔다: " + seen.size());
        for (int i = 1; i < seen.size(); i++) {
            assertTrue(seen.get(i) >= seen.get(i - 1), "되돌아가지 않는다");
            assertTrue(seen.get(i) <= 50, "목표를 넘지 않는다");
        }
    }

    @Test
    void 연달아_돌리면_목표가_쌓인다() throws Exception {
        JScrollBar bar = bar();
        ScrollChase chase = new ScrollChase(bar);
        SwingUtilities.invokeAndWait(() -> {
            chase.push(50);
            chase.push(50);
            chase.push(50);
        });
        Thread.sleep(700);
        assertEquals(150, bar.getValue());
    }

    @Test
    void 끝을_넘어서는_가지_않는다() throws Exception {
        JScrollBar bar = bar();
        ScrollChase chase = new ScrollChase(bar);
        SwingUtilities.invokeAndWait(() -> chase.push(100000));
        Thread.sleep(900);
        assertEquals(1900, bar.getValue());
        SwingUtilities.invokeAndWait(() -> chase.push(-100000));
        Thread.sleep(900);
        assertEquals(0, bar.getValue());
    }

    @Test
    void 스크롤바를_직접_움직이면_따라가기를_그만둔다() throws Exception {
        JScrollBar bar = bar();
        ScrollChase chase = new ScrollChase(bar);
        SwingUtilities.invokeAndWait(() -> chase.push(400));
        Thread.sleep(60);
        SwingUtilities.invokeAndWait(() -> bar.setValue(1000));
        Thread.sleep(500);
        assertEquals(1000, bar.getValue());
    }

    @Test
    void 반대로_돌리면_바로_되돌아간다() throws Exception {
        JScrollBar bar = bar();
        ScrollChase chase = new ScrollChase(bar);
        SwingUtilities.invokeAndWait(() -> {
            bar.setValue(500);
            chase.push(50);
        });
        Thread.sleep(40);
        SwingUtilities.invokeAndWait(() -> chase.push(-100));
        Thread.sleep(700);
        assertEquals(450, bar.getValue());
    }
}
