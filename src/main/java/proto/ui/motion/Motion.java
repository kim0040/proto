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
 * 모든 애니메이션(Tween, ColorTween, TransitionHost, FadePanel)이 이 클래스를 거쳐 실행된다. 설정 시트의 "애니메이션" 스위치(SettingsView)가
 * setEnabled를 부른다.
 * 꺼져 있으면 시간을 기다리지 않고 모든 모션이 끝 상태로 바로 넘어가므로, 화면 코드는 켜짐/꺼짐을 따로 나누지 않아도 된다 (Motion gating).
 */
public final class Motion {

    /** 감속이 부드러운 ease-out (대부분의 모션) */
    public static final CubicBezierEasing OUT = new CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f);

    /** 나타날 때 쓰는 빠르게 시작해 길게 가라앉는 ease-out (0.23, 1, 0.32, 1): 화면 전환·목록 행이 들어올 때 */
    public static final CubicBezierEasing ENTER = new CubicBezierEasing(0.23f, 1f, 0.32f, 1f);

    /** 사라질 때 쓰는 ease-in (0.4, 0, 1, 1): 나가는 화면·지워지는 행. 들어올 때보다 35~45% 짧게 쓴다 */
    public static final CubicBezierEasing EXIT = new CubicBezierEasing(0.4f, 0f, 1f, 1f);

    /** 살짝 튀는 스프링 (오버슈트 6%). 사용자가 직접 누르거나 놓은 것에만 쓴다 (hover·스크롤·화면 전환·대화상자에는 쓰지 않는다) */
    public static final Spring SPRING = Spring.of(0.06);

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

    /** 스크린샷 도구가 쓴다. 캡처 때문에 사용자의 저장된 설정이 바뀌지 않게 한다. */
    public static void setEnabledTransient(boolean on) {
        enabled = on;
    }

    /**
     * 0→1로 진행하는 모션을 실행한다. frame에는 보정된(ease-out) 진행도가 들어온다.
     * 꺼져 있으면 frame(1)과 done을 바로 부르고 끝낸다.
     */
    public static Handle run(int durationMs, Consumer<Float> frame, Runnable done) {
        return run(durationMs, OUT, frame, done);
    }

    /** easing으로 진행도를 보정하는 run. 스프링이면 frame에 1을 넘는 값이 들어올 수 있다 */
    public static Handle run(int durationMs, Animator.Interpolator easing, Consumer<Float> frame, Runnable done) {
        Handle handle = new Handle(frame, done);
        if (!enabled || durationMs <= 0) {
            handle.finish();
            return handle;
        }
        Animator animator = new Animator(durationMs, handle::tick, handle::finish);
        animator.setInterpolator(easing);
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

        /**
         * 이미 끝났거나 멈췄는지. 애니메이션이 꺼져 있으면 run이 돌려주기 전에 끝나 있으므로,
         * 받은 손잡이를 "진행 중"으로 들고 있지 않도록 확인할 때 쓴다.
         */
        public boolean isDone() {
            return finished;
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
