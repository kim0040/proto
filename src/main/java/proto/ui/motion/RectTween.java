package proto.ui.motion;

import com.formdev.flatlaf.util.Animator;

import java.awt.Rectangle;
import java.util.function.Consumer;

/**
 * [경계 boundary] 사각형 하나를 시작 모양에서 끝 모양으로 움직이는 도우미 (끌어 놓은 일정이 제자리에 안착할 때).
 * TimelineSettle이 쓴다. 매 프레임 지금 사각형을 onFrame에 넘긴다. 스프링이면 중간에 끝 모양을 살짝 넘을 수 있으나 너비·높이는 0 아래로 내려가지 않는다.
 * Motion이 꺼져 있으면 바로 끝 모양으로 간다.
 */
public final class RectTween {

    private final Rectangle from;
    private final Rectangle to;
    private final Consumer<Rectangle> onFrame;
    private Motion.Handle running;

    public RectTween(Rectangle from, Rectangle to, Consumer<Rectangle> onFrame) {
        this.from = new Rectangle(from);
        this.to = new Rectangle(to);
        this.onFrame = onFrame;
    }

    /** 끝 모양을 바꾼다 (창 크기가 바뀌어 칸이 달라졌을 때) */
    public void retarget(Rectangle end) {
        to.setBounds(end);
    }

    /**
     * easing으로 durationMs 동안 움직인다. done은 끝난 뒤 한 번 부른다.
     * 애니메이션이 꺼져 있으면 이 메서드가 돌아오기 전에 마지막 프레임과 done이 이미 불린다 (그 뒤에는 진행 중이 아니다).
     */
    public void start(int durationMs, Animator.Interpolator easing, Runnable done) {
        stop();
        Motion.Handle h = Motion.run(durationMs, easing, f -> onFrame.accept(at(f)), done);
        running = h.isDone() ? null : h;
    }

    public void stop() {
        if (running != null) {
            running.cancel();
            running = null;
        }
    }

    /** 진행도 f(0 시작, 1 끝)에서의 사각형 */
    Rectangle at(float f) {
        return new Rectangle(lerp(from.x, to.x, f), lerp(from.y, to.y, f),
                Math.max(0, lerp(from.width, to.width, f)), Math.max(0, lerp(from.height, to.height, f)));
    }

    private static int lerp(int a, int b, float f) {
        return Math.round(a + (b - a) * f);
    }
}
