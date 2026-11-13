package proto.ui.motion;

import proto.ui.theme.Tokens;

import java.awt.Color;

/**
 * [경계 boundary] 색 하나를 다른 색으로 부드럽게 바꾸는 도우미 (상태 알약 색 전환 등).
 */
public final class ColorTween {

    private final Runnable onChange;
    private Color from;
    private Color to;
    private float progress = 1f;
    private Motion.Handle running;

    public ColorTween(Color initial, Runnable onChange) {
        this.from = initial;
        this.to = initial;
        this.onChange = onChange;
    }

    /** 지금 그려야 할 색 */
    public Color current() {
        return progress >= 1f ? to : Tokens.blend(from, to, progress);
    }

    public Color target() {
        return to;
    }

    /** 색을 바로 바꾼다 */
    public void set(Color c) {
        stop();
        from = c;
        to = c;
        progress = 1f;
    }

    /** 지금 보이는 색에서 c로 durationMs 동안 바뀐다 */
    public void to(Color c, int durationMs) {
        if (c.equals(to)) {
            return;
        }
        stop();
        from = current();
        to = c;
        progress = 0f;
        running = Motion.run(durationMs, f -> {
            progress = f;
            onChange.run();
        });
    }

    private void stop() {
        if (running != null) {
            running.cancel();
            running = null;
        }
    }
}
