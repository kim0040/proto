package proto.ui.motion;

/**
 * [경계 boundary] 숫자 하나를 목표값까지 부드럽게 움직이는 도우미 (hover 배경, 체크, 알약 이동 등).
 * 컴포넌트가 hover 같은 상태 하나마다 Tween 하나를 갖고, 그릴 때 value()를 읽는다. 값을 정하는 방법은 set(바로)과 to(애니메이션) 두 가지다.
 * 값이 바뀔 때마다 onChange를 불러 그 컴포넌트만 다시 그리게 한다.
 */
public final class Tween {

    private final Runnable onChange;
    private float value;
    private Motion.Handle running;

    public Tween(float initial, Runnable onChange) {
        this.value = initial;
        this.onChange = onChange;
    }

    public float value() {
        return value;
    }

    /** 애니메이션 없이 바로 값을 정한다 */
    public void set(float v) {
        stop();
        value = v;
        onChange.run();
    }

    /** 목표값까지 durationMs 동안 움직인다. 애니메이션이 꺼져 있으면 바로 도착한다 */
    public void to(float target, int durationMs) {
        stop();
        if (target == value) {
            return;
        }
        float from = value;
        running = Motion.run(durationMs, f -> {
            value = from + (target - from) * f;
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
