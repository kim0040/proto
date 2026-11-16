package proto.ui.motion;

import com.formdev.flatlaf.util.Animator;

/**
 * [경계 boundary] 통통 튀는 모션용 감쇠 스프링 보간기: 0에서 1로 가며 목표를 살짝 넘었다가 한 번 돌아와 멈춘다.
 * Motion#SPRING으로 쓰고, Motion#run과 Tween#to에 보간기로 넘긴다 (체크 톡, 스위치·선택 표시 이동, 카드 등장, 끌어 놓기 안착).
 * 닫힌 식의 감쇠 진동(과소감쇠 스프링)을 쓴다. 첫 정점이 (지속시간의 PEAK_AT 지점)에 오고 그 높이가 overshoot(예: 0.06 = 6%)가 되도록 감쇠비를 정한다.
 * 끝에서 약간 남는 진동을 g(1)로 나눠 f(0)=0, f(1)=1이 정확히 맞는다. 값은 중간에 1을 넘을 수 있으므로 색·투명도·크기에 쓸 때는 쓰는 쪽에서 범위를 자른다.
 */
public final class Spring implements Animator.Interpolator {

    /** 첫 정점이 오는 시점 (지속시간 대비) */
    private static final double PEAK_AT = 0.42;

    private final double overshoot;
    private final double sigma;
    private final double omegaD;
    private final double norm;

    private Spring(double overshoot) {
        this.overshoot = overshoot;
        double ln = Math.log(overshoot);
        double zeta = -ln / Math.sqrt(Math.PI * Math.PI + ln * ln);
        this.omegaD = Math.PI / PEAK_AT;
        this.sigma = zeta * omegaD / Math.sqrt(1 - zeta * zeta);
        this.norm = raw(1);
    }

    /** 첫 정점이 목표를 overshoot(0 초과 0.5 이하, 0.06 = 6%)만큼 넘는 스프링 */
    public static Spring of(double overshoot) {
        if (overshoot <= 0 || overshoot > 0.5) {
            throw new IllegalArgumentException("overshoot must be in (0, 0.5]: " + overshoot);
        }
        return new Spring(overshoot);
    }

    public double overshoot() {
        return overshoot;
    }

    @Override
    public float interpolate(float t) {
        if (t <= 0f) {
            return 0f;
        }
        if (t >= 1f) {
            return 1f;
        }
        return (float) (raw(t) / norm);
    }

    /** 감쇠 진동 그대로의 값 (t=1에서 1이 아니다) */
    private double raw(double t) {
        double decay = Math.exp(-sigma * t);
        return 1 - decay * (Math.cos(omegaD * t) + sigma / omegaD * Math.sin(omegaD * t));
    }
}
