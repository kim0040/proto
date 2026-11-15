package proto.ui.motion;

/**
 * [경계 boundary] 목표값을 따라가는 감쇠 스프링: 위치(x)와 속도(v)를 함께 들고 있어서, 목표가 도중에 바뀌어도 값과 속도가 끊기지 않고 이어진다.
 * Tween이 움직이는 도중에 to()가 다시 불릴 때(hover가 들어왔다 바로 나가는 경우 등) 쓴다.
 * 프레임 수가 아니라 흐른 시간(초)으로 닫힌 식을 풀어서 프레임이 늦어져도 같은 곡선을 그린다. 임계감쇠(오버슈트 없음)와 약한 과소감쇠(6% 안팎의
 * 오버슈트) 두 가지가 있고, 임계감쇠는 지금까지 지나온 범위(lo~hi) 밖으로 나가지 않게 잘라서 투명도·색에 써도 넘치지 않는다.
 */
final class Follower {

    /** 임계감쇠가 0.3% 안으로 들어오는 데 걸리는 ω·t (지정한 시간 안에 사실상 도착한다) */
    private static final double CRITICAL_SETTLE = 7.6;
    /** 6% 오버슈트가 되는 감쇠비 */
    private static final double SPRING_ZETA = 0.67;
    private static final double SPRING_SETTLE = 5.8;
    private static final double EPS = 1e-4;

    private final double omega;
    private final double zeta;
    private double lo;
    private double hi;
    private double x;
    private double v;
    private double target;

    private Follower(double x, double v, double target, int settleMs, boolean bouncy, double lo, double hi) {
        this.x = x;
        this.v = v;
        this.target = target;
        this.zeta = bouncy ? SPRING_ZETA : 1.0;
        this.omega = (bouncy ? SPRING_SETTLE / SPRING_ZETA : CRITICAL_SETTLE) / (Math.max(1, settleMs) / 1000.0);
        this.lo = lo;
        this.hi = hi;
    }

    /** 오버슈트 없이 settleMs 안에 target에 도착하는 follower (lo~hi 밖으로 나가지 않는다) */
    static Follower critical(double x, double v, double target, int settleMs, double lo, double hi) {
        return new Follower(x, v, target, settleMs, false, lo, hi);
    }

    /** 살짝 튀는(6%) follower. 범위를 자르지 않는다 */
    static Follower bouncy(double x, double v, double target, int settleMs) {
        return new Follower(x, v, target, settleMs, true, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    double x() {
        return x;
    }

    double v() {
        return v;
    }

    double target() {
        return target;
    }

    /** 새 목표로 바꾼다. 지금의 위치와 속도는 그대로다. 오버슈트 없는 follower는 범위를 새 목표까지 넓힌다 */
    Follower retarget(double newTarget, int settleMs) {
        boolean bouncy = zeta < 1.0;
        double newLo = Math.min(lo, newTarget);
        double newHi = Math.max(hi, newTarget);
        return new Follower(x, v, newTarget, settleMs, bouncy, newLo, newHi);
    }

    /** dt초만큼 나아간다 (닫힌 식이라 dt가 커도 안정적이다) */
    void step(double dt) {
        if (dt <= 0) {
            return;
        }
        double e0 = x - target;
        double decay;
        double e;
        double ev;
        if (zeta >= 1.0) {
            decay = Math.exp(-omega * dt);
            double b = v + omega * e0;
            e = (e0 + b * dt) * decay;
            ev = (v - omega * b * dt) * decay;
        } else {
            double s = zeta * omega;
            double wd = omega * Math.sqrt(1 - zeta * zeta);
            decay = Math.exp(-s * dt);
            double cos = Math.cos(wd * dt);
            double sin = Math.sin(wd * dt);
            e = decay * (e0 * cos + (v + s * e0) / wd * sin);
            ev = decay * (v * cos - (s * v + omega * omega * e0) / wd * sin);
        }
        x = target + e;
        v = ev;
        clampToRange();
    }

    private void clampToRange() {
        if (x < lo) {
            x = lo;
            v = Math.max(0, v);
        } else if (x > hi) {
            x = hi;
            v = Math.min(0, v);
        }
    }

    /** 목표에 충분히 가깝고 거의 멈췄는지 */
    boolean settled() {
        return Math.abs(x - target) < EPS && Math.abs(v) < EPS * 10;
    }
}
