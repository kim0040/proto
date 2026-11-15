package proto.ui.component;

import proto.ui.theme.Ui;

import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Point;
import java.util.function.Consumer;

/**
 * [경계 boundary] 끌고 있을 때 마우스가 보이는 영역의 위·아래 가장자리(약 40px)에 가까워지면 매 프레임 조금씩 부드럽게 스크롤하는 도우미.
 * TimelineGrid가 하나 갖고, 마우스가 움직일 때마다 update로 위치를 알린다 (F3). 가장자리 안쪽으로 들어간 깊이에 비례해 빨라지고(최대 초당 약 600px), 마우스를 가만히 두어도 계속 스크롤된다.
 * 스크롤하면 마우스 밑의 시간표 좌표가 바뀌므로, 매 프레임 새 좌표를 onMoved로 돌려줘서 끌기 상태(TimelineDrag)가 따라 갱신되게 한다. 시간(ns)으로 속도를 계산해 프레임이 흔들려도 일정하게 움직인다.
 * 스크롤 영역(JViewport) 안에 있지 않으면 아무 일도 하지 않는다. 애니메이션 설정과 상관없이 동작한다(끌기에 꼭 필요한 기능이라서).
 */
final class DragAutoScroll {

    private static final int EDGE = 40;
    private static final int MAX_SPEED = 600;
    private static final int FRAME_MS = 10;

    private final JComponent view;
    private final Consumer<Point> onMoved;
    private final Timer timer = new Timer(FRAME_MS, e -> tick());
    private Point inViewport;
    private long lastNanos;
    private double remainder;

    DragAutoScroll(JComponent view, Consumer<Point> onMoved) {
        this.view = view;
        this.onMoved = onMoved;
    }

    /**
     * 마우스 세로 위치가 가장자리에서 얼마나 깊은지에 비례한 속도(초당 px, 위로는 음수). 가장자리 밖이면 0이고, 영역 밖으로 더 나가도 1.5배까지만 빨라진다.
     *
     * @param y      보이는 영역 안에서 본 마우스 y
     * @param height 보이는 영역의 높이
     * @param edge   가장자리 폭
     * @param max    가장 빠른 속도
     */
    static double speed(int y, int height, int edge, int max) {
        int zone = Math.min(edge, height / 3);
        if (zone <= 0) {
            return 0;
        }
        if (y < zone) {
            return -max * Math.min(1.5, (zone - y) / (double) zone);
        }
        if (y > height - zone) {
            return max * Math.min(1.5, (y - (height - zone)) / (double) zone);
        }
        return 0;
    }

    /** 마우스가 (시간표 좌표) p로 움직였다. 가장자리 근처면 스크롤을 시작한다 */
    void update(Point p) {
        JViewport vp = viewport();
        if (vp == null) {
            return;
        }
        inViewport = SwingUtilities.convertPoint(view, p, vp);
        if (speed(inViewport.y, vp.getHeight(), Ui.px(EDGE), Ui.px(MAX_SPEED)) != 0) {
            if (!timer.isRunning()) {
                lastNanos = System.nanoTime();
                remainder = 0;
                timer.start();
            }
        } else {
            timer.stop();
        }
    }

    /** 끌기가 끝났다 */
    void stop() {
        timer.stop();
        inViewport = null;
    }

    private JViewport viewport() {
        return SwingUtilities.getAncestorOfClass(JViewport.class, view) instanceof JViewport vp ? vp : null;
    }

    private void tick() {
        JViewport vp = viewport();
        if (vp == null || inViewport == null) {
            timer.stop();
            return;
        }
        long now = System.nanoTime();
        double dt = (now - lastNanos) / 1e9;
        lastNanos = now;
        double v = speed(inViewport.y, vp.getHeight(), Ui.px(EDGE), Ui.px(MAX_SPEED));
        if (v == 0) {
            timer.stop();
            return;
        }
        scrollBy(vp, v * dt);
    }

    /** 소수 픽셀은 모아 두었다가 1px이 되면 한꺼번에 스크롤하고, 새 마우스 좌표를 알린다 */
    private void scrollBy(JViewport vp, double dy) {
        remainder += dy;
        int step = (int) remainder;
        if (step == 0) {
            return;
        }
        remainder -= step;
        Point pos = vp.getViewPosition();
        int max = Math.max(0, view.getHeight() - vp.getHeight());
        int next = Math.max(0, Math.min(max, pos.y + step));
        if (next != pos.y) {
            vp.setViewPosition(new Point(pos.x, next));
            onMoved.accept(new Point(inViewport.x + pos.x, inViewport.y + next));
        }
    }
}
