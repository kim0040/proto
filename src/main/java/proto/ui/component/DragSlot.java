package proto.ui.component;

import proto.ui.motion.RectTween;

import java.awt.Rectangle;

/**
 * [경계 boundary] 끌고 있는 동안 "여기에 놓여요" 하고 보여 주는 자리 틀의 위치. 15분·하루 단위 칸 사이를 80ms 동안 부드럽게 옮겨 간다 (순간이동하지 않는다).
 * TimelineDrag가 마우스가 새 칸으로 넘어갈 때마다 moveTo로 목표를 알리고, 그릴 때 shown()을 읽는다 (F3). 이동 중에 목표가 또 바뀌면 지금 위치에서 새 목표로 이어 간다.
 * Motion을 끄면 바로 목표 자리에 선다. 처음 한 번은 모션 없이 그 자리에 나타난다.
 */
final class DragSlot {

    private final Runnable onChange;
    private Rectangle shown;
    private Rectangle target;
    private RectTween tween;

    DragSlot(Runnable onChange) {
        this.onChange = onChange;
    }

    /** 틀이 놓일 자리를 알린다. 같은 자리면 아무 일도 하지 않는다 */
    void moveTo(Rectangle next) {
        if (next.equals(target)) {
            return;
        }
        target = new Rectangle(next);
        if (shown == null) {
            shown = new Rectangle(next);
            return;
        }
        if (tween != null) {
            tween.stop();
        }
        tween = new RectTween(shown, target, r -> {
            shown = r;
            onChange.run();
        });
        tween.start(DragMotion.SLOT_MS, DragMotion.OUT, () -> { });
    }

    /** 지금 보이는 자리 (이동 중이면 중간 자리). 아직 정해지지 않았으면 null */
    Rectangle shown() {
        return shown == null ? null : new Rectangle(shown);
    }

    /** 틀이 가기로 한 자리 */
    Rectangle target() {
        return target == null ? null : new Rectangle(target);
    }

    /** 움직이던 모션을 멈춘다 (끌기가 끝났을 때) */
    void stop() {
        if (tween != null) {
            tween.stop();
            tween = null;
        }
    }
}
