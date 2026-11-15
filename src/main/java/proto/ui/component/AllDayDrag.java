package proto.ui.component;

import proto.entity.Occurrence;
import proto.ui.theme.Ui;

import javax.swing.SwingUtilities;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.time.LocalDate;

/**
 * [경계 boundary] 하루 종일 칩을 가로로 끌어 다른 날로 옮기는 동작 하나를 이끄는 작은 상태 기계 (날짜만 바뀌고 시간은 그대로다).
 * AllDayRow가 칩의 누르기·끌기·놓기를 넘겨 준다 (F3). 4px 넘게 움직이면 칩이 들려 올라 마우스를 가로로만 따라다니고(DragCard), 놓일 날짜 칸은 자리 틀(DragSlot)이 80ms 동안 옮겨 가며 보여 준다.
 * 다른 날 칸에 놓으면 AllDayRow#moveTo로 요청하고, 옮겨졌으면 카드가 새 칩 자리로 내려앉고(200ms), 같은 날·취소·실패면 원래 자리로 돌아간다(160ms). Esc는 취소한다.
 * 시간이 있는 블록을 이 줄에 끌어 놓거나 이 칩을 시간 칸에 끌어 놓는 것은 지원하지 않는다 (줄 안에서만 움직인다).
 * 끌기는 누른 순간의 주에 묶인다. 끄는 도중 주가 바뀌거나 일정이 다시 채워지거나 줄이 화면에서 떨어지면 abort로 아무것도 바꾸지 않고 거두고, 이어지는 놓기는 삼킨다(열기도 하지 않는다).
 */
final class AllDayDrag {

    private static final int THRESHOLD = 4;

    private final AllDayRow row;
    private final KeyEventDispatcher escape = this::onKey;
    private AllDayChip chip;
    private Point origin;
    private LocalDate period;
    private boolean active;

    /** 지금 끄는 중인지 (눌렀다가 문턱을 넘어 실제로 움직이기 시작한 뒤) */
    boolean isActive() {
        return active;
    }
    private boolean aborted;
    private int day;
    private DragCard card;
    private DragSlot slot;
    private Rectangle home;

    AllDayDrag(AllDayRow row) {
        this.row = row;
    }

    /** 칩을 눌렀다 (끌기 후보) */
    void press(AllDayChip c, MouseEvent e) {
        clear();
        aborted = false;
        chip = c;
        period = row.dateOf(0);
        origin = point(e);
        day = row.metrics().dayAt(origin.x);
    }

    void drag(MouseEvent e) {
        if (chip == null) {
            return;
        }
        Point p = point(e);
        if (!active && origin.distance(p) < Ui.px(THRESHOLD)) {
            return;
        }
        if (!active) {
            activate();
        }
        day = row.metrics().dayAt(p.x);
        card.follow(p.x, origin.y);
        slot.moveTo(row.cellOf(day));
        row.repaint();
    }

    /** 놓았다. 끌었으면 옮기기를 요청하고 true, 아니면 false (클릭으로 처리된다) */
    boolean release(MouseEvent e) {
        if (chip != null && !row.dateOf(0).equals(period)) {
            abort();
        }
        if (aborted) {
            aborted = false;
            return true;
        }
        if (chip == null) {
            return false;
        }
        boolean dragged = active;
        if (dragged) {
            drag(e);
            drop();
        } else {
            chip = null;
        }
        return dragged;
    }

    private void activate() {
        active = true;
        home = chip.getBounds();
        AllDayChip source = chip;
        card = new DragCard(row, (g2, w, h) -> source.paintBody(g2, w, h, 0.6f), home, origin, Ui.px(4));
        card.lift();
        slot = new DragSlot(row::repaint);
        chip.setDimmed(true);
        row.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(escape);
    }

    /** 놓은 자리로 옮긴다. 결과를 기다리는 동안(반복 일정 범위 묻기 등) 카드는 놓은 자리에 들린 채 머문다 */
    private void drop() {
        AllDayChip source = chip;
        DragCard held = card;
        int target = day;
        finish();
        Occurrence o = source.occurrence();
        MovedItem moved = row.movedItem(o, row.dateOf(target));
        if (row.dayOf(o) != target && row.moveTo(o, target)) {
            land(held, moved);
        } else {
            back(held, source);
        }
    }

    /** 옮겨졌다: 새로 만들어진 칩 자리로 카드가 내려앉는다. 칩을 못 찾으면 카드를 치운다 */
    private void land(DragCard held, MovedItem moved) {
        AllDayChip landing = row.chipFor(moved);
        if (landing == null) {
            card = null;
            return;
        }
        landing.setGhosted(true);
        held.flyTo(landing.getBounds(), DragMotion.DROP_MS, DragMotion.LAND, () -> {
            landing.setGhosted(false);
            if (card == held) {
                card = null;
            }
            row.repaint();
        });
    }

    /** 옮기지 않았다: 카드가 원래 칩 자리로 돌아가고 칩이 다시 선명해진다 */
    private void back(DragCard held, AllDayChip source) {
        Rectangle to = home;
        held.flyTo(to, DragMotion.BACK_MS, DragMotion.OUT, () -> {
            source.setDimmed(false);
            if (card == held) {
                card = null;
            }
            row.repaint();
        });
    }

    /** Esc: 끌고 있으면 취소하고 키를 먹는다 */
    private boolean onKey(KeyEvent e) {
        if (active && e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            AllDayChip source = chip;
            DragCard held = card;
            finish();
            back(held, source);
            return true;
        }
        return false;
    }

    /**
     * 누르고 있거나 끌고 있는 동작을 아무것도 바꾸지 않고 바로 거둔다 (들린 카드는 날아가지 않고 사라지고 원래 칩의 흐림이 풀린다).
     * 놓은 뒤 결과를 기다리거나 날아가는 카드는 건드리지 않는다. 진행 중인 동작이 없으면 아무 일도 하지 않는다.
     */
    void abort() {
        if (chip == null) {
            return;
        }
        AllDayChip source = chip;
        boolean lifted = active;
        finish();
        source.setDimmed(false);
        if (lifted) {
            card = null;
            home = null;
        }
        aborted = true;
        row.repaint();
    }

    /** 끌기 상태를 거둔다 (카드는 날아가는 동안 쓰도록 남긴다) */
    private void finish() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(escape);
        row.setCursor(null);
        if (slot != null) {
            slot.stop();
        }
        active = false;
        chip = null;
        period = null;
        slot = null;
    }

    /** 새 끌기를 시작하기 전에 남은 것을 모두 치운다 */
    private void clear() {
        if (card != null) {
            card.finishFlight();
        }
        finish();
        card = null;
        home = null;
    }

    private Point point(MouseEvent e) {
        return SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), row);
    }

    /** 끄는 동안의 자리 틀과 들린(날아가는) 카드를 줄 위에 그린다 */
    void paint(Graphics2D g2) {
        if (slot != null && slot.shown() != null && card != null) {
            TimelineGhost.paintFrame(g2, slot.shown(), Math.min(1f, card.liftValue()));
        }
        if (card != null) {
            card.paint(g2);
        }
    }
}
