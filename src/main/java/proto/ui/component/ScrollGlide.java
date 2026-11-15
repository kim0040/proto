package proto.ui.component;

import proto.ui.motion.Motion;

import javax.swing.JViewport;
import java.awt.Point;

/**
 * [경계 boundary] 스크롤 영역의 보이는 위치를 정해진 시간 동안 부드럽게 옮기는 도우미 (주간 시간표를 처음 열 때 업무 시작 시각으로 가는 이동 등).
 * WeekView가 쓴다. 이동하는 동안 사용자가 휠·스크롤바로 직접 움직이면 그 자리에서 그만둔다. 애니메이션이 꺼져 있으면 바로 간다.
 */
public final class ScrollGlide {

    private final JViewport viewport;
    private Motion.Handle handle;
    private int lastSet;

    public ScrollGlide(JViewport viewport) {
        this.viewport = viewport;
    }

    /** 세로 위치 y까지 durationMs 동안 ease-out으로 옮긴다 */
    public void to(int y, int durationMs) {
        stop();
        int from = viewport.getViewPosition().y;
        if (from == y) {
            return;
        }
        lastSet = from;
        handle = Motion.run(durationMs, f -> step(from, y, f), () -> handle = null);
    }

    public void stop() {
        if (handle != null) {
            handle.cancel();
            handle = null;
        }
    }

    private void step(int from, int to, float f) {
        if (viewport.getViewPosition().y != lastSet) {
            stop();
            return;
        }
        lastSet = Math.round(from + (to - from) * Math.min(1f, f));
        viewport.setViewPosition(new Point(viewport.getViewPosition().x, lastSet));
    }
}
