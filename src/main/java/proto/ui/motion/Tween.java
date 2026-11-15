package proto.ui.motion;

import com.formdev.flatlaf.util.Animator;

/**
 * [경계 boundary] 숫자 하나를 목표값까지 부드럽게 움직이는 도우미 (hover 배경, 체크, 알약 이동 등).
 * 컴포넌트가 hover 같은 상태 하나마다 Tween 하나를 갖고, 그릴 때 value()를 읽는다. 값을 정하는 방법은 set(바로)과 to(애니메이션) 두 가지다.
 * 값이 바뀔 때마다 onChange를 불러 그 컴포넌트만 다시 그리게 한다.
 * 가만히 있던 값이 움직이기 시작할 때는 지정한 easing 곡선대로 가고, 움직이는 도중에 to가 다시 불리면(hover가 들어왔다 바로 나가는 경우)
 * 지금 값과 속도를 이어받는 감쇠 스프링(Follower)으로 바꿔 타서 값이 튀지 않는다. 같은 목표로 다시 부르면 아무것도 하지 않는다.
 */
public final class Tween {

    private final Runnable onChange;
    private float value;
    private Motion.Handle running;
    private Follower follower;
    private float target;
    private float lo;
    private float hi;
    private float prevValue;
    private long prevNanos;
    private long lastNanos;
    private double velocity;

    public Tween(float initial, Runnable onChange) {
        this.value = initial;
        this.target = initial;
        this.onChange = onChange;
    }

    public float value() {
        return value;
    }

    /** 애니메이션 없이 바로 값을 정한다 */
    public void set(float v) {
        stop();
        value = v;
        target = v;
        onChange.run();
    }

    /** 목표값까지 durationMs 동안 움직인다. 애니메이션이 꺼져 있으면 바로 도착한다 */
    public void to(float target, int durationMs) {
        to(target, durationMs, Motion.OUT);
    }

    /** 스프링으로 목표값까지 움직인다. 중간에 목표를 살짝 넘을 수 있으니, 읽는 쪽이 범위를 잘라 써야 한다 */
    public void springTo(float target, int durationMs) {
        to(target, durationMs, Motion.SPRING);
    }

    /** easing으로 목표값까지 움직인다. 움직이는 중에 다시 부르면 지금 값과 속도에서 새 목표로 이어 간다 */
    public void to(float newTarget, int durationMs, Animator.Interpolator easing) {
        if (!Motion.enabled() || durationMs <= 0) {
            set(newTarget);
            return;
        }
        if (running != null) {
            retarget(newTarget, durationMs, easing == Motion.SPRING);
        } else if (newTarget != value) {
            startSegment(newTarget, durationMs, easing);
        }
    }

    /** 가만히 있다가 처음 움직인다: 지정한 easing 곡선 그대로 */
    private void startSegment(float to, int durationMs, Animator.Interpolator easing) {
        float from = value;
        target = to;
        lo = Math.min(from, to);
        hi = Math.max(from, to);
        prevValue = from;
        prevNanos = System.nanoTime();
        lastNanos = prevNanos;
        velocity = 0;
        running = live(Motion.run(durationMs, easing, f -> {
            remember(from + (to - from) * f);
            onChange.run();
        }, this::arrived));
    }

    /** 끝까지 갔다: 가만히 있는 상태로 돌아간다 */
    private void arrived() {
        running = null;
        follower = null;
        velocity = 0;
    }

    /** 움직이는 중에 목표가 바뀌었다: 지금 값과 속도를 이어받는 follower로 바꿔 탄다 */
    private void retarget(float newTarget, int durationMs, boolean bouncy) {
        if (newTarget == target) {
            return;
        }
        lo = Math.min(lo, value);
        hi = Math.max(hi, value);
        Follower next;
        if (follower != null) {
            next = follower.retarget(newTarget, durationMs);
        } else {
            next = bouncy ? Follower.bouncy(value, velocity, newTarget, durationMs)
                    : Follower.critical(value, velocity, newTarget, durationMs, Math.min(lo, newTarget),
                            Math.max(hi, newTarget));
        }
        running.cancel();
        follower = next;
        target = newTarget;
        lo = Math.min(lo, newTarget);
        hi = Math.max(hi, newTarget);
        lastNanos = System.nanoTime();
        running = live(Motion.run(durationMs, this::followFrame, this::arrived));
    }

    /**
     * follower 한 프레임: 흐른 시간만큼 나아가고, 끝(f=1)이거나 시간보다 먼저 목표에 닿았으면 목표에 딱 맞추고 멈춘다.
     * 먼저 닿았을 때는 프레임을 대던 모션도 같은 자리에서 함께 멈춰서, follower가 비워진 뒤에 프레임이 또 들어오는 일이 없다.
     */
    private void followFrame(float f) {
        if (follower == null) {
            return;
        }
        if (f >= 1f || follower.settled()) {
            land();
            return;
        }
        long now = System.nanoTime();
        follower.step((now - lastNanos) / 1e9);
        remember((float) follower.x());
        lastNanos = now;
        onChange.run();
    }

    /** 목표에 닿았다: 값을 목표에 맞추고, 돌던 모션을 멈추고, 가만히 있는 상태로 돌아간다 */
    private void land() {
        Motion.Handle driver = running;
        value = target;
        arrived();
        if (driver != null) {
            driver.cancel();
        }
        onChange.run();
    }

    /** run이 돌려준 손잡이를 진행 중일 때만 들고 있는다 (애니메이션이 꺼져 있으면 이미 끝나 있다) */
    private static Motion.Handle live(Motion.Handle h) {
        return h.isDone() ? null : h;
    }

    /** 새 값을 받아 두고, 직전 값과의 차이로 속도(단위/초)를 어림한다 */
    private void remember(float v) {
        long now = System.nanoTime();
        prevValue = value;
        prevNanos = lastNanos;
        value = v;
        lastNanos = now;
        double dt = (now - prevNanos) / 1e9;
        velocity = dt > 1e-4 ? (value - prevValue) / dt : velocity;
    }

    private void stop() {
        if (running != null) {
            running.cancel();
            running = null;
        }
        follower = null;
        velocity = 0;
    }
}
