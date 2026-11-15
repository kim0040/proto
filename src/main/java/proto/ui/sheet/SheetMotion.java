package proto.ui.sheet;

import com.formdev.flatlaf.util.Animator;
import com.formdev.flatlaf.util.CubicBezierEasing;
import proto.ui.motion.Motion;

/**
 * [경계 boundary] 시트 하나의 움직임 값(스크림 진하기, 카드 투명도·크기·세로 위치, 흔들림)을 시간에 따라 계산한다.
 * SheetLayer가 하나씩 갖고, 그릴 때 값만 읽는다. 열릴 때 스크림은 150ms 동안 고르게 짙어지고, 카드는 200ms 동안 96%→100% 크기, 아래 8px→제자리,
 * 투명→불투명으로 감속하며 나타난다(튀지 않음). 닫힐 때는 110ms 동안 가속하며 98% 크기로 줄고 흐려지며, 스크림은 100ms에 걷힌다.
 * 모든 모션은 Motion을 거치므로 설정의 "애니메이션"을 끄면 바로 끝 상태가 된다.
 */
final class SheetMotion {

    /** 시트·서랍용 감속 곡선 */
    private static final CubicBezierEasing ENTER = new CubicBezierEasing(0.32f, 0.72f, 0f, 1f);
    /** 닫힐 때의 가속 곡선 (튀지 않음) */
    private static final CubicBezierEasing EXIT = new CubicBezierEasing(0.3f, 0f, 1f, 1f);
    /** 시간 그대로 (곡선은 frame 안에서 따로 적용한다) */
    private static final Animator.Interpolator LINEAR = f -> f;

    static final int ENTER_MS = 200;
    static final int SCRIM_IN_MS = 150;
    static final int EXIT_MS = 110;
    static final int SCRIM_OUT_MS = 100;
    static final int SHAKE_MS = 180;
    static final float START_SCALE = 0.96f;
    static final float END_SCALE = 0.98f;
    static final int RISE_PX = 8;
    static final int SHAKE_PX = 5;

    /** 값이 바뀔 때 불린다. 인자는 스크림도 바뀌었는지 (아니면 카드 둘레만 다시 그리면 된다) */
    interface Listener {
        void changed(boolean scrimChanged);
    }

    private final Listener listener;
    private float scrim;
    private float opacity;
    private float scale = START_SCALE;
    private float rise = 1f;
    private float shake;
    private Motion.Handle running;
    private Motion.Handle shaking;

    SheetMotion(Listener listener) {
        this.listener = listener;
    }

    /** 나타나기 시작한다 */
    void enter() {
        stop();
        running = Motion.run(ENTER_MS, LINEAR, this::enterFrame, () -> { });
    }

    private void enterFrame(float t) {
        float before = scrim;
        scrim = Math.min(1f, t * ENTER_MS / SCRIM_IN_MS);
        float e = ENTER.interpolate(t);
        opacity = e;
        scale = START_SCALE + (1f - START_SCALE) * e;
        rise = 1f - e;
        listener.changed(before != scrim);
    }

    /** 사라지기 시작한다. 나타나는 중이었으면 지금 값에서 이어서 사라진다. 다 사라지면 done을 부른다 */
    void exit(Runnable done) {
        stop();
        float scrim0 = scrim;
        float opacity0 = opacity;
        float scale0 = scale;
        running = Motion.run(EXIT_MS, LINEAR, t -> {
            scrim = scrim0 * (1f - Math.min(1f, t * EXIT_MS / SCRIM_OUT_MS));
            float e = EXIT.interpolate(t);
            opacity = opacity0 * (1f - e);
            scale = scale0 + (END_SCALE - scale0) * e;
            listener.changed(true);
        }, done);
    }

    /** 좌우로 짧게 흔든다 (±5px, 3번, 점점 작게). 닫을 수 없다는 신호이다 */
    void shake() {
        if (shaking != null) {
            shaking.cancel();
        }
        shaking = Motion.run(SHAKE_MS, LINEAR, t -> {
            shake = (float) (Math.sin(t * Math.PI * 6) * (1f - t));
            listener.changed(false);
        }, () -> {
            shake = 0f;
            listener.changed(false);
        });
    }

    private void stop() {
        if (running != null) {
            running.cancel();
            running = null;
        }
    }

    /** 스크림 진하기 (0~1) */
    float scrim() {
        return scrim;
    }

    /** 카드 투명도 (0~1) */
    float opacity() {
        return Math.max(0f, Math.min(1f, opacity));
    }

    /** 카드 크기 배율 */
    float scale() {
        return scale;
    }

    /** 카드가 제자리보다 아래로 내려가 있는 정도 (0~1, 곱하기 RISE_PX) */
    float rise() {
        return rise;
    }

    /** 흔들림 (-1~1, 곱하기 SHAKE_PX) */
    float shakeOffset() {
        return shake;
    }

    /** 움직임이 끝나 평소 모습 그대로인지 (변환 없이 그려도 되는지) */
    boolean atRest() {
        return opacity >= 0.999f && scale >= 0.9995f && rise <= 0.0005f && shake == 0f;
    }
}
