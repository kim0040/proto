package proto.ui.component;

import proto.entity.Occurrence;
import proto.ui.motion.Motion;
import proto.ui.motion.RectTween;
import proto.ui.motion.Tween;
import proto.ui.theme.Ui;

import javax.swing.Timer;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;

/**
 * [경계 boundary] 놓은 카드가 제자리에 내려앉거나(성공) 원래 자리로 돌아가는(취소) 모션을 맡는다 (들린 카드 → 새 블록 / 원래 블록).
 * TimelineGrid가 놓는 순간 expect로 카드와 새 자리(날짜·시작·제목)를 알리고, 옮기기 결과에 따라 confirm(성공) 또는 revert(취소·실패·바뀐 것 없음)를 부른다.
 * 결과를 기다리는 동안(예: 반복 일정 범위를 묻는 창) 카드는 놓은 자리에 들린 채 머문다. setWeek가 블록을 다시 만들면 attach로 같은 일정의 블록을 찾고,
 * 맞는 블록이 없으면 카드를 조용히 치운다 (같은 일정은 MovedItem으로 일정 번호와 날짜부터 맞춘다).
 * 성공하면 카드가 새 블록 자리로 200ms 스프링(오버슈트 1.5%)으로 내려앉고 그림자가 옅어진다. 취소하면 160ms 동안 원래 블록 자리로 돌아가고 원래 블록이 다시 선명해진다.
 * 배치(doLayout)는 place를 거쳐 움직이는 중의 위치를 쓴다. 애니메이션이 꺼져 있으면 바로 새 자리에 놓는다. 자리 틀은 110ms 동안 서서히 사라진다.
 */
final class TimelineSettle {

    /** 놓은 뒤 이 시간 안에 시간표가 다시 만들어지지 않으면 기대를 버린다 */
    private static final int EXPIRE_MS = 1500;

    private final TimelineGrid grid;
    private final Tween fade;
    private final Tween shadow;
    private final Timer expiry;
    private DragCard card;
    private Rectangle slot;
    private MovedItem moved;
    private boolean confirmed;
    private boolean reverting;
    private TimelineBlock target;
    private RectTween tween;
    private Rectangle current;

    TimelineSettle(TimelineGrid grid) {
        this.grid = grid;
        this.fade = new Tween(0f, grid::repaint);
        this.shadow = new Tween(0f, grid::repaint);
        this.expiry = new Timer(EXPIRE_MS, e -> drop());
        expiry.setRepeats(false);
    }

    /** 옮기기를 요청하기 직전에 놓은 카드·자리 틀(시간표 좌표)과 옮겨질 일정을 기억하고, 결과가 나올 때까지 카드를 들고 있는다 */
    void expect(DragCard held, Rectangle box, MovedItem item) {
        stopFlight();
        reset();
        card = held;
        slot = new Rectangle(box);
        moved = item;
        fade.set(1f);
        grid.repaint();
    }

    /** 옮기기가 성공했다. 이 시점부터 EXPIRE_MS 안에 만들어지는 블록으로 카드를 내려앉힌다 */
    void confirm() {
        if (card != null) {
            confirmed = true;
            expiry.restart();
        }
    }

    /** 옮기기가 실패하거나 취소됐다: 카드를 원래 블록 자리로 돌려보낸다 */
    void revert(TimelineBlock origin) {
        if (card == null) {
            origin.setDimmed(false);
            return;
        }
        DragCard flying = card;
        reverting = true;
        fade.to(0f, DragMotion.FADE_MS, DragMotion.IN);
        flying.flyTo(origin.getBounds(), DragMotion.BACK_MS, DragMotion.OUT, () -> {
            origin.setDimmed(false);
            if (card == flying) {
                card = null;
                reverting = false;
            }
            grid.repaint();
        });
    }

    /** Esc로 끌기를 취소했다: 들고 있던 카드를 원래 블록 자리로 돌려보낸다 */
    void cancelDrag(DragCard held, Rectangle box, TimelineBlock origin) {
        stopFlight();
        reset();
        card = held;
        slot = new Rectangle(box);
        fade.set(1f);
        revert(origin);
    }

    /** 새로 만든 블록 중 기억해 둔 자리의 일정을 찾아 모션 대상으로 정한다. 날아가던 모션은 멈춘다 */
    void attach(List<TimelineBlock> blocks) {
        stopFlight();
        if (card == null || reverting || moved == null) {
            return;
        }
        Occurrence landed = moved.pick(blocks.stream().map(TimelineBlock::occurrence).toList());
        target = blocks.stream().filter(b -> b.occurrence() == landed).findFirst().orElse(null);
        if (target == null && confirmed) {
            drop();
        }
    }

    /**
     * 배치 때 블록이 놓일 자리. 모션 중인 블록이면 지금 위치를, 아니면 end를 돌려준다.
     * 애니메이션이 꺼져 있으면 모션을 시작하지 않고 카드를 치운 뒤 바로 end를 돌려준다 (null을 돌려주는 일이 없다).
     */
    Rectangle place(TimelineBlock b, Rectangle end) {
        if (b != target) {
            return end;
        }
        if (tween == null && !Motion.enabled()) {
            finish();
            return end;
        }
        if (tween == null) {
            begin(end);
        } else {
            tween.retarget(end);
        }
        return current != null ? current : end;
    }

    /** 점검용: 내려앉을(내려앉는 중인) 블록의 일정. 없으면 null */
    Occurrence landing() {
        return target == null ? null : target.occurrence();
    }

    /** 블록 밑에: 내려앉는 블록의 그림자 */
    void paintUnder(Graphics2D g2) {
        if (target != null && shadow.value() > 0.01f) {
            float k = Math.min(1f, shadow.value());
            float blur = DragMotion.BLUR_REST + (DragMotion.BLUR_LIFT - DragMotion.BLUR_REST) * k;
            LiftShadow.paint(g2, target.getBounds(), Ui.px(6), blur, k);
        }
    }

    /** 블록 위에: 사라지는 자리 틀과 결과를 기다리거나 돌아가는 카드 */
    void paintOver(Graphics2D g2) {
        if (slot != null && fade.value() > 0.01f) {
            TimelineGhost.paintFrame(g2, slot, Math.min(1f, fade.value()));
        }
        if (card != null) {
            card.paint(g2);
        }
    }

    /** 카드 없이 자리 틀만 서서히 지운다 (새 일정 만들기·Esc로 취소한 빈 곳 끌기) */
    void fadeSlot(Rectangle box) {
        reset();
        slot = new Rectangle(box);
        fade.set(1f);
        fade.to(0f, DragMotion.FADE_MS, DragMotion.IN);
    }

    /** 카드가 있던 모양에서 새 블록 자리로 내려앉기 시작한다 */
    private void begin(Rectangle end) {
        expiry.stop();
        Rectangle from = card.shown();
        shadow.set(card.liftValue());
        card = null;
        current = from;
        TimelineBlock moving = target;
        tween = new RectTween(from, end, r -> {
            current = r;
            moving.setBounds(r);
            grid.repaint();
        });
        shadow.to(0f, DragMotion.DROP_MS, DragMotion.OUT);
        fade.to(0f, DragMotion.FADE_MS, DragMotion.IN);
        tween.start(DragMotion.DROP_MS, DragMotion.LAND, this::finish);
    }

    private void finish() {
        target = null;
        tween = null;
        current = null;
        reset();
    }

    /** 날아가던(내려앉던) 모션만 멈춘다. 결과를 기다리는 카드는 그대로 둔다 */
    private void stopFlight() {
        if (tween != null) {
            tween.stop();
            shadow.set(0f);
            fade.set(0f);
            tween = null;
            target = null;
            current = null;
            card = null;
        } else if (card != null && target != null) {
            target = null;
        }
    }

    /** 기다리던 카드를 치운다 (맞는 블록을 못 찾았거나 시간이 지났다) */
    private void drop() {
        expiry.stop();
        reset();
        grid.repaint();
    }

    private void reset() {
        expiry.stop();
        if (card != null) {
            card.finishFlight();
        }
        card = null;
        slot = null;
        moved = null;
        confirmed = false;
        reverting = false;
        fade.set(0f);
        shadow.set(0f);
    }
}
