package proto.ui.component;

import com.formdev.flatlaf.util.CubicBezierEasing;
import proto.ui.component.TransitionHost.Dir;
import proto.ui.motion.Motion;
import proto.ui.theme.Ui;

/**
 * [경계 boundary] 화면 전환의 시간표: 나가는 화면은 제자리에서 빨리 흐려지고, 들어오는 화면은 조금 밀려 들어오며 나타난다.
 * TransitionHost가 프레임마다 흐른 시간(ms)을 넣어 투명도와 밀림 거리를 묻는다. 보기 전환은 위·아래로 10px, 이전/다음 이동은 좌우로 40px 밀린다.
 * 들어옴은 (0.23, 1, 0.32, 1) ease-out, 나감은 (0.4, 0, 1, 1) ease-in이고 오버슈트는 없다.
 */
final class TransitionPlan {

    private static final CubicBezierEasing ENTER = Motion.ENTER;
    private static final CubicBezierEasing EXIT = Motion.EXIT;

    private final int outMs;
    private final int inMs;
    private final int dx;
    private final int dy;

    TransitionPlan(int outMs, int inMs, int dx, int dy) {
        this.outMs = outMs;
        this.inMs = inMs;
        this.dx = dx;
        this.dy = dy;
    }

    /** 방향에 맞는 시간표 (화면 배율을 곱한 px). NONE에는 쓰지 않는다 */
    static TransitionPlan of(Dir dir) {
        switch (dir) {
            case UP:
                return new TransitionPlan(80, 170, 0, Ui.px(10));
            case DOWN:
                return new TransitionPlan(80, 170, 0, -Ui.px(10));
            case FROM_RIGHT:
                return new TransitionPlan(110, 160, Ui.px(40), 0);
            default:
                return new TransitionPlan(110, 160, -Ui.px(40), 0);
        }
    }

    /** 전체 길이 (ms) */
    int totalMs() {
        return Math.max(outMs, inMs);
    }

    /** 나가는 화면의 투명도 (1에서 0으로) */
    float outgoingAlpha(float ms) {
        return 1f - EXIT.interpolate(clamp(ms / outMs));
    }

    /** 들어오는 화면의 투명도 (0에서 1로) */
    float incomingAlpha(float ms) {
        return ENTER.interpolate(clamp(ms / inMs));
    }

    /** 들어오는 화면이 아직 남은 가로 밀림 (px) */
    int offsetX(float ms) {
        return Math.round(dx * (1f - ENTER.interpolate(clamp(ms / inMs))));
    }

    /** 들어오는 화면이 아직 남은 세로 밀림 (px) */
    int offsetY(float ms) {
        return Math.round(dy * (1f - ENTER.interpolate(clamp(ms / inMs))));
    }

    private static float clamp(float f) {
        return Math.max(0f, Math.min(1f, f));
    }
}
