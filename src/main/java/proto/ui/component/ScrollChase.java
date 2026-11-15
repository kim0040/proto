package proto.ui.component;

import com.formdev.flatlaf.util.Animator;
import proto.ui.motion.Motion;

import javax.swing.JScrollBar;

/**
 * [경계 boundary] 마우스 휠 한 칸씩의 이동을 모아 두었다가, 스크롤바를 목표 위치로 부드럽게 따라가게 하는 도우미.
 * NotchWheel이 휠 한 칸마다 거리를 더하면, 남은 거리의 일부씩 흐른 시간만큼 좁혀 가는(지수 감쇠, 시간 상수 TAU_MS) 방식으로 따라간다.
 * 칸을 연달아 돌리면 목표가 계속 누적되어 끊기지 않고 이어지고, 0.5px 안으로 들어오면 목표에 딱 맞춰 멈춘다.
 * 스크롤바를 끌거나 키보드로 직접 움직이면 따라가기를 그만둔다. 애니메이션이 꺼져 있으면 목표로 바로 간다.
 */
final class ScrollChase {

    /** 남은 거리가 1/e로 줄어드는 시간 (약 38ms → 칸 하나가 120~180ms 안에 도착한다) */
    static final double TAU_MS = 38.0;
    private static final double SNAP_PX = 0.5;
    private static final int MAX_MS = 700;
    private static final Animator.Interpolator LINEAR = f -> f;

    private final JScrollBar bar;
    private Motion.Handle handle;
    private double pos;
    private double target;
    private int lastSet;
    private long lastNanos;

    ScrollChase(JScrollBar bar) {
        this.bar = bar;
    }

    /** 목표를 delta(px)만큼 더 옮긴다. 아직 따라가는 중이면 이어서 누적된다 */
    void push(double delta) {
        if (handle == null || bar.getValue() != lastSet) {
            pos = bar.getValue();
            target = pos;
            lastSet = bar.getValue();
        }
        target = clamp(target + delta);
        lastNanos = System.nanoTime();
        restartDriver();
    }

    /** 따라가던 것을 그 자리에서 그만둔다 */
    void stop() {
        if (handle != null) {
            handle.cancel();
            handle = null;
        }
    }

    private double clamp(double v) {
        double max = bar.getMaximum() - bar.getModel().getExtent();
        return Math.max(bar.getMinimum(), Math.min(max, v));
    }

    private void restartDriver() {
        if (handle != null) {
            handle.cancel();
        }
        handle = Motion.run(MAX_MS, LINEAR, f -> frame(f >= 1f), () -> handle = null);
    }

    /** 한 프레임: 흐른 시간만큼 남은 거리를 좁힌다. 누가 스크롤바를 따로 움직였으면(끌기·키보드) 그만둔다 */
    private void frame(boolean last) {
        if (bar.getValue() != lastSet) {
            stop();
            return;
        }
        long now = System.nanoTime();
        double dt = (now - lastNanos) / 1e6;
        lastNanos = now;
        pos = last ? target : pos + (target - pos) * (1 - Math.exp(-dt / TAU_MS));
        if (Math.abs(target - pos) < SNAP_PX) {
            pos = target;
        }
        lastSet = (int) Math.round(pos);
        bar.setValue(lastSet);
        if (pos == target) {
            stop();
        }
    }
}
