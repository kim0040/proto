package proto.ui.motion;

import com.formdev.flatlaf.util.Animator;
import com.formdev.flatlaf.util.CubicBezierEasing;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * [경계 boundary] 애니메이션 공통 진입점. 켜고 끄는 설정(저장됨)과 짧은 ease-out 모션 실행을 맡는다.
 * 꺼져 있으면 모든 모션이 즉시 끝 상태로 건너뛴다.
 */
public final class Motion {

    /** 감속이 부드러운 ease-out (대부분의 모션) */
    public static final CubicBezierEasing OUT = new CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f);

    private static final String PREF_KEY = "animations";
    private static final Preferences PREFS = Preferences.userNodeForPackage(Motion.class);
    private static final Set<Handle> ACTIVE = new LinkedHashSet<>();
    private static final List<Runnable> LISTENERS = new ArrayList<>();

    private static boolean enabled = PREFS.getBoolean(PREF_KEY, true);

    private Motion() {
    }

    public static boolean enabled() {
        return enabled;
    }

    /** 애니메이션을 켜거나 끈다. 끄면 진행 중이던 모션도 바로 끝낸다 */
    public static void setEnabled(boolean on) {
        enabled = on;
        PREFS.putBoolean(PREF_KEY, on);
        if (!on) {
            for (Handle h : new ArrayList<>(ACTIVE)) {
                h.finish();
            }
        }
        LISTENERS.forEach(Runnable::run);
    }

    /** 설정이 바뀔 때 알려 달라고 등록한다 */
    public static void onToggle(Runnable listener) {
        LISTENERS.add(listener);
    }

    /** 저장하지 않고 이번 실행에서만 켜고 끈다 (스크린샷 도구용) */
    public static void setEnabledTransient(boolean on) {
        enabled = on;
    }

    /**
     * 0→1로 진행하는 모션을 실행한다. frame에는 보정된(ease-out) 진행도가 들어온다.
     * 꺼져 있으면 frame(1)과 done을 바로 부르고 끝낸다.
     */
    public static Handle run(int durationMs, Consumer<Float> frame, Runnable done) {
        Handle handle = new Handle(frame, done);
        if (!enabled || durationMs <= 0) {
            handle.finish();
            return handle;
        }
        Animator animator = new Animator(durationMs, handle::tick, handle::finish);
        animator.setInterpolator(OUT);
        animator.setResolution(8);
        handle.animator = animator;
        ACTIVE.add(handle);
        animator.start();
        return handle;
    }

    public static Handle run(int durationMs, Consumer<Float> frame) {
        return run(durationMs, frame, () -> { });
    }

    /** 진행 중인 모션 하나를 가리키는 손잡이 */
    public static final class Handle {
        private final Consumer<Float> frame;
        private final Runnable done;
        private Animator animator;
        private boolean finished;

        private Handle(Consumer<Float> frame, Runnable done) {
            this.frame = frame;
            this.done = done;
        }

        private void tick(float fraction) {
            if (!finished) {
                frame.accept(fraction);
            }
        }

        /** 끝 상태로 바로 건너뛰고 마무리 동작을 부른다 */
        public void finish() {
            if (finished) {
                return;
            }
            finished = true;
            ACTIVE.remove(this);
            if (animator != null) {
                animator.stop();
            }
            frame.accept(1f);
            done.run();
        }

        /** 마무리 동작 없이 멈춘다 (다른 모션으로 이어 갈 때) */
        public void cancel() {
            if (finished) {
                return;
            }
            finished = true;
            ACTIVE.remove(this);
            if (animator != null) {
                animator.cancel();
            }
        }
    }
}
