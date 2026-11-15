package proto.ui.component;

import proto.ui.motion.Motion;
import proto.ui.theme.Ui;

import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

/**
 * [경계 boundary] 마우스 휠 "한 칸" 입력을 부드럽게 이어 주는 스크롤 휠 처리기. 트랙패드 같은 정밀 입력은 건드리지 않는다.
 * SmoothScrollPaneUI가 모든 스크롤 영역에 붙인다. 휠 한 칸(회전 값이 정수)은 한 칸에 50px씩 목표를 쌓아 ScrollChase가 따라가게 하고,
 * 소수 단위로 오는 정밀 값(macOS 트랙패드)과 가로 스크롤·애니메이션 꺼짐은 원래 처리기(FlatLaf)에 그대로 넘겨 1:1로 움직인다.
 * 정밀 값이 방금(0.25초 안) 들어왔다면 같은 손짓이 정수로 끼어든 것이므로 칸 입력으로 보지 않는다.
 */
final class NotchWheel implements MouseWheelListener {

    private static final int NOTCH_PX = 50;
    private static final long PRECISE_WINDOW_NANOS = 250_000_000L;

    private final JScrollPane pane;
    private final MouseWheelListener fallback;
    private ScrollChase chase;
    private JScrollBar chaseBar;
    private long lastPreciseNanos = Long.MIN_VALUE / 2;

    NotchWheel(JScrollPane pane, MouseWheelListener fallback) {
        this.pane = pane;
        this.fallback = fallback;
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        boolean precise = e.getPreciseWheelRotation() != e.getWheelRotation();
        if (precise) {
            lastPreciseNanos = System.nanoTime();
        }
        JScrollBar bar = pane.getVerticalScrollBar();
        if (!isNotch(e, precise) || bar == null || !bar.isVisible()) {
            stopChase();
            fallback.mouseWheelMoved(e);
            return;
        }
        e.consume();
        chaseFor(bar).push(Ui.px(NOTCH_PX) * e.getWheelRotation());
    }

    /** 부드럽게 이어 줄 칸 입력인지: 세로 휠, 정수 회전, 정밀 입력이 막 끝나지 않았고 애니메이션이 켜져 있다 */
    private boolean isNotch(MouseWheelEvent e, boolean precise) {
        return Motion.enabled() && pane.isWheelScrollingEnabled() && !precise && !e.isShiftDown()
                && e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL && e.getWheelRotation() != 0
                && System.nanoTime() - lastPreciseNanos > PRECISE_WINDOW_NANOS;
    }

    private ScrollChase chaseFor(JScrollBar bar) {
        if (chase == null || chaseBar != bar) {
            stopChase();
            chase = new ScrollChase(bar);
            chaseBar = bar;
        }
        return chase;
    }

    private void stopChase() {
        if (chase != null) {
            chase.stop();
        }
    }
}
