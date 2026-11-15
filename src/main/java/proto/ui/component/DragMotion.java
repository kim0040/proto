package proto.ui.component;

import com.formdev.flatlaf.util.Animator;
import com.formdev.flatlaf.util.CubicBezierEasing;
import proto.ui.motion.Spring;

/**
 * [경계 boundary] 끌어서 옮기기 모션의 시간·곡선 값을 한 곳에 모은 상수 모음 (그리지도 상태를 갖지도 않는다).
 * 시간표(TimelineDrag·TimelineSettle)와 달력(MonthDrag·MonthSettle)의 끌기가 같은 값을 써서 "들림 → 따라감 → 놓임" 느낌이 두 화면에서 같게 한다 (F3).
 * 들리는 시간 110ms, 자리 틀 이동 80ms, 놓일 때 200ms(스프링 오버슈트 1.5% 이하), 되돌아갈 때 160ms, 호버는 40ms에 나타나 110ms에 사라진다.
 */
final class DragMotion {

    /** 끌기를 시작할 때 들리는 시간 */
    static final int LIFT_MS = 110;
    /** 자리 틀이 칸 사이를 옮겨 가는 시간 */
    static final int SLOT_MS = 80;
    /** 놓은 카드가 제자리로 내려앉는 시간 */
    static final int DROP_MS = 200;
    /** 취소된 카드가 원래 자리로 돌아가는 시간 */
    static final int BACK_MS = 160;
    /** 자리 틀이 사라지는 시간 */
    static final int FADE_MS = 110;
    /** 호버 강조가 나타나는 시간과 사라지는 시간 */
    static final int HOVER_IN_MS = 40;
    static final int HOVER_OUT_MS = 110;

    /** 들렸을 때의 확대(2%)와 투명도 감소(5%) */
    static final float LIFT_SCALE = 0.02f;
    static final float LIFT_FADE = 0.05f;
    /** 그림자 번짐(논리 px): 쉴 때와 다 들렸을 때 */
    static final float BLUR_REST = 4f;
    static final float BLUR_LIFT = 14f;

    /** 나타날 때·움직일 때 곡선: 빠르게 시작해 부드럽게 멈춘다 (0.23, 1, 0.32, 1) */
    static final Animator.Interpolator OUT = new CubicBezierEasing(0.23f, 1f, 0.32f, 1f);
    /** 사라질 때 곡선: 천천히 시작해 빨리 끝난다 (오버슈트 없음) */
    static final Animator.Interpolator IN = new CubicBezierEasing(0.4f, 0f, 1f, 1f);
    /** 놓일 때 스프링: 목표를 1.5%만 넘는다 (옆 일정을 덮지 않는다) */
    static final Spring LAND = Spring.of(0.015);

    private DragMotion() {
    }
}
