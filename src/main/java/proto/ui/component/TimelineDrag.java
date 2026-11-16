package proto.ui.component;

import proto.entity.Occurrence;
import proto.ui.Fmt;
import proto.ui.theme.Ui;

import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.time.LocalDate;

/**
 * [경계 boundary] 시간표의 끌기 동작 하나(옮기기·크기 바꾸기·빈 곳에서 새로 만들기)를 처음부터 끝까지 이끄는 작은 상태 기계.
 * TimelineBlock과 TimelineGrid가 마우스 누르기·끌기·놓기를 여기로 넘긴다 (F1, F3). 4px 넘게 움직이면 끌기가 시작되고, 블록이 들려 올라 마우스를 1:1로 따라다니며(DragCard),
 * 놓일 15분·하루 단위 자리는 반투명한 자리 틀(DragSlot)이 80ms 동안 부드럽게 옮겨 가며 알려 준다. 시간 범위 알약은 자리 틀을 따라간다.
 * 놓으면 TimelineGrid에 결과를 넘기고(카드는 TimelineSettle이 이어받아 제자리로 내려앉히거나 원래 자리로 돌려보낸다) Esc를 누르면 아무 것도 바꾸지 않고 카드가 돌아간다.
 * 끌지 않고 놓으면 눌림으로 본다 (블록은 열기, 빈 곳 두 번 누르기는 1시간짜리 새 일정). 가장자리 근처에서는 TimelineGrid가 자동으로 스크롤한다.
 * 끌기는 누른 순간의 주(week)에 묶인다. 끄는 도중 주가 바뀌거나 일정이 다시 채워지거나 시간표가 화면에서 떨어지면 abort로 아무것도 바꾸지 않고 거둔다.
 */
public final class TimelineDrag {

    private static final int THRESHOLD = 4;

    private final TimelineGrid grid;
    private final KeyEventDispatcher escape = this::onKey;
    private Mode mode = Mode.NONE;
    private boolean active;
    private TimelineBlock block;
    private Point origin;
    private LocalDate period;
    private int pressedDay;
    private int anchorMinute;
    private int day;
    private TimeSnap.Range range;
    private DragCard card;
    private DragSlot slot;
    private float resizeFrom;

    TimelineDrag(TimelineGrid grid) {
        this.grid = grid;
    }

    /** 지금 끄는 중인지 (눌렀다가 문턱을 넘어 실제로 움직이기 시작한 뒤) */
    boolean isActive() {
        return active;
    }

    /** 일정 블록을 눌렀다. 아래 가장자리면 크기 바꾸기, 아니면 옮기기 후보 */
    public void pressBlock(TimelineBlock b, Point p, TimelineBlock.Zone zone) {
        clear();
        block = b;
        begin(zone == TimelineBlock.Zone.RESIZE ? Mode.RESIZE : Mode.MOVE, p);
    }

    /** 빈 곳을 눌렀다 (시각 글자 영역은 제외). 끌면 새 일정 범위를 고르는 후보 */
    public void pressEmpty(Point p) {
        clear();
        if (p.x >= grid.metrics().gutter()) {
            anchorMinute = grid.metrics().minuteAt(p.y);
            begin(Mode.CREATE, p);
        }
    }

    /** 마우스를 끌었다. 4px를 넘으면 끌기가 시작되고, 그 뒤로는 놓일 자리 틀과 카드가 따라간다 */
    public void dragged(Point p) {
        if (mode == Mode.NONE) {
            return;
        }
        if (!active && origin.distance(p) < Ui.px(THRESHOLD)) {
            return;
        }
        if (!active) {
            activate();
        }
        update(p);
        grid.autoscroll(p);
        grid.repaint();
    }

    /** 마우스를 놓았다. 끌었으면 결과를 넘기고, 아니면 눌림으로 처리한다 */
    public void released(Point p, int clickCount) {
        if (mode == Mode.NONE) {
            return;
        }
        if (!grid.dateOf(0).equals(period)) {
            abort();
            return;
        }
        if (active) {
            update(p);
        }
        Handoff h = new Handoff(mode, block, day, range, card, slot == null ? null : slot.target());
        boolean dragged = active;
        int minute = grid.metrics().minuteAt(origin.y);
        clear(dragged && h.block() != null);
        if (dragged) {
            commit(h);
        } else {
            click(h.mode(), h.block(), clickCount, minute);
        }
    }

    /**
     * 진행 중인 누름·끌기를 아무것도 바꾸지 않고 바로 거둔다 (카드는 날아가지 않고 사라진다). 이어지는 끌기·놓기 이벤트는 무시된다.
     * 주가 바뀌거나 일정이 다시 채워지거나 시간표가 화면에서 떨어질 때 TimelineGrid가 부른다. 진행 중인 것이 없으면 아무 일도 하지 않는다.
     */
    void abort() {
        if (mode != Mode.NONE) {
            clear();
        }
    }

    /** 끄는 동안의 자리 틀·들린 카드·시간 범위 알약을 시간표 위에 그린다 */
    void paint(Graphics2D g2) {
        if (!active || range == null || slot == null || slot.shown() == null) {
            return;
        }
        Rectangle box = slot.shown();
        TimelineGhost.paintFrame(g2, box, card == null ? 1f : Math.min(1f, card.liftValue()));
        if (card != null) {
            card.paint(g2);
        }
        String label = Fmt.weekdayShort(grid.dateOf(day).getDayOfWeek()) + " "
                + Fmt.range(TimeSnap.timeOf(range.start()), TimeSnap.timeOf(range.end()));
        TimelineGhost.paintLabel(g2, box, new Rectangle(0, 0, grid.getWidth(), grid.getHeight()), label);
    }

    private void begin(Mode m, Point p) {
        mode = m;
        origin = p;
        period = grid.dateOf(0);
        pressedDay = grid.metrics().dayAt(p.x);
        day = pressedDay;
        grid.selectDay(pressedDay);
    }

    private void commit(Handoff h) {
        if (h.mode() == Mode.CREATE) {
            grid.fadeSlot(h.slot());
            grid.commitCreate(h.day(), h.range());
        } else {
            grid.commitMove(h.block(), h.day(), h.range(), h.card(), h.slot());
        }
    }

    private void click(Mode m, TimelineBlock b, int clickCount, int minute) {
        if (m == Mode.CREATE) {
            if (clickCount >= 2) {
                grid.commitCreate(pressedDay, TimeSnap.hourAt(minute));
            }
        } else if (clickCount < 2) {
            grid.openBlock(b);
        }
    }

    private void activate() {
        active = true;
        slot = new DragSlot(grid::repaint);
        if (block != null) {
            card = grid.cardFor(block, origin);
            card.lift();
            block.setDimmed(true);
            resizeFrom = card.rest().height;
        }
        grid.setDragCursor(mode == Mode.MOVE ? Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
                : mode == Mode.RESIZE ? Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR) : null);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(escape);
    }

    private void update(Point p) {
        TimelineMetrics m = grid.metrics();
        Occurrence o = block == null ? null : block.occurrence();
        switch (mode) {
            case MOVE -> {
                day = m.dayAt(p.x);
                range = TimeSnap.moved(startOf(o), endOf(o), m.minutesOf(p.y - origin.y));
                card.follow(p.x, p.y);
            }
            case RESIZE -> {
                range = TimeSnap.resized(startOf(o), endOf(o), m.minutesOf(p.y - origin.y));
                card.setHeight(resizedHeight(p));
            }
            default -> range = TimeSnap.created(anchorMinute, m.minuteAt(p.y));
        }
        slot.moveTo(grid.boxOf(day, range));
    }

    /** 크기를 바꾸는 카드의 높이: 아래 모서리가 마우스를 1:1로 따라가되 너무 낮거나 하루 끝을 넘지 않게 한다 */
    private float resizedHeight(Point p) {
        float limit = grid.metrics().yOf(TimeSnap.DAY) - card.rest().y;
        return Math.max(Ui.px(12), Math.min(limit, resizeFrom + p.y - origin.y));
    }

    private static int startOf(Occurrence o) {
        return TimeSnap.minuteOf(o.startTime());
    }

    private static int endOf(Occurrence o) {
        return TimeSnap.endMinuteOf(o.endTime());
    }

    /** Esc: 끌고 있으면 취소하고 키를 먹는다. 들고 있던 카드는 원래 자리로 돌아간다 */
    private boolean onKey(KeyEvent e) {
        if (active && e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            TimelineBlock b = block;
            DragCard c = card;
            Rectangle s = slot.shown();
            clear(c != null);
            grid.cancelDrag(b, c, s);
            return true;
        }
        return false;
    }

    /** 끌기를 끝내고(취소 포함) 모든 상태를 처음으로 돌린다. 원래 블록의 흐림도 푼다 */
    private void clear() {
        clear(false);
    }

    /** keepDim이 true면 원래 블록을 흐린 채 둔다 (카드가 돌아오거나 새 블록으로 바뀔 때까지 TimelineSettle이 맡는다) */
    private void clear(boolean keepDim) {
        boolean was = active;
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(escape);
        grid.stopAutoscroll();
        grid.setDragCursor(null);
        if (block != null && !keepDim) {
            block.setDimmed(false);
        }
        if (slot != null) {
            slot.stop();
        }
        mode = Mode.NONE;
        active = false;
        period = null;
        block = null;
        range = null;
        card = null;
        slot = null;
        if (was) {
            grid.repaint();
        }
    }

    private enum Mode { NONE, MOVE, RESIZE, CREATE }

    /** 놓는 순간의 끌기 결과 (clear가 상태를 지운 뒤에도 넘길 수 있게 따로 담아 둔다) */
    private record Handoff(Mode mode, TimelineBlock block, int day, TimeSnap.Range range, DragCard card,
                           Rectangle slot) {
    }
}
