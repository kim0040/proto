package proto.ui.component;

import proto.entity.Occurrence;

import javax.swing.Timer;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.time.LocalDate;

/**
 * [경계 boundary] 달력에서 놓은 일정 칩이 칸에 내려앉거나(성공) 원래 줄로 돌아가는(취소·실패) 모션을 맡는다 (TimelineSettle의 달력판).
 * MonthDrag가 놓는 순간 expect로 칩을 맡기고 onDrop을 부른 뒤 afterDrop을 부른다. 옮기기가 성공해 MonthGrid.setData로 새 일정이 들어오면(dataChanged)
 * 칩이 새 줄 자리로 200ms 스프링(오버슈트 1.5%)으로 내려앉고, 그동안 달력은 그 줄을 숨겨 두 번 보이지 않게 한다.
 * 결과를 기다리는 동안(범위를 묻는 창 등) 칩은 놓은 자리에 들린 채 머문다. onDrop이 끝났는데도 일정이 오지 않으면(취소·실패·바뀐 것 없음) 잠깐(120ms) 뒤 칩이 160ms 동안 원래 줄로 돌아가고,
 * 원래 줄은 그때까지 흐리게 둔다. 원래 줄과 새 줄은 제목·시각이 아니라 일정 번호·날짜로 알아본다(MovedItem). Motion을 끄면 모두 바로 끝난다.
 */
final class MonthSettle {

    /** onDrop이 끝난 뒤 새 일정이 오기를 더 기다리는 시간 */
    private static final int GRACE_MS = 120;

    private final MonthDrag.Host host;
    private final Timer grace = new Timer(GRACE_MS, e -> giveUp());
    private State state = State.IDLE;
    private DragCard card;
    private Occurrence moved;
    private MovedItem identity;
    private Occurrence landedItem;
    private LocalDate target;
    private Rectangle home;

    MonthSettle(MonthDrag.Host host) {
        this.host = host;
        grace.setRepeats(false);
    }

    /** 일정을 date로 옮겨 달라고 요청하는 순간: 칩을 맡아 들고 있는다. home은 칩이 처음 있던 자리 */
    void expect(DragCard held, Occurrence item, LocalDate date, Rectangle homeRect) {
        finishNow();
        card = held;
        moved = item;
        target = date;
        identity = new MovedItem(item, date, item.startTime(), host.shownItems());
        home = homeRect;
        state = State.HOLD;
        host.repaintGrid();
    }

    /** onDrop이 돌아왔다. 아직 새 일정이 오지 않았으면 조금만 더 기다려 본다 */
    void afterDrop() {
        if (state == State.HOLD) {
            grace.restart();
        }
    }

    /** 칩을 원래 줄로 돌려보낸다 (같은 날·칸 밖에 놓았거나 Esc) */
    void revert(DragCard held, Occurrence item, Rectangle homeRect) {
        finishNow();
        card = held;
        moved = item;
        home = homeRect;
        giveUp();
    }

    /** MonthGrid가 새 일정을 받았다. 기다리던 칩이 있고 옮겨진 줄이 보이면 그 자리로 내려앉힌다 */
    void dataChanged() {
        if (state != State.HOLD) {
            return;
        }
        Occurrence landed = identity.pick(host.shownItems());
        Rectangle row = landed == null ? null : host.landingRow(target, landed);
        if (row != null) {
            grace.stop();
            landedItem = landed;
            state = State.FLY;
            card.flyTo(MonthChip.landRect(row), DragMotion.DROP_MS, DragMotion.LAND, this::landed);
        }
    }

    /** 원래 줄이어서 흐리게 둘 일정인지 (결과를 기다리거나 돌아오는 동안) */
    boolean dims(Occurrence o) {
        return (state == State.HOLD || state == State.BACK) && moved != null && o.key().equals(moved.key());
    }

    /** 칩이 내려앉는 동안 달력이 그리지 않을 새 줄인지 */
    boolean hides(Occurrence o) {
        return state == State.FLY && o.equals(landedItem);
    }

    /** 맡고 있는 칩을 그린다 */
    void paint(Graphics2D g2) {
        if (card != null) {
            card.paint(g2);
        }
    }

    private void giveUp() {
        grace.stop();
        if (card == null) {
            return;
        }
        state = State.BACK;
        card.flyTo(home, DragMotion.BACK_MS, DragMotion.OUT, this::landed);
    }

    private void landed() {
        state = State.IDLE;
        card = null;
        moved = null;
        identity = null;
        landedItem = null;
        target = null;
        host.repaintGrid();
    }

    /** 하던 모션을 끝 자리까지 건너뛰고 비운다 (새 끌기가 시작될 때) */
    private void finishNow() {
        grace.stop();
        if (card != null) {
            card.finishFlight();
        }
        landed();
    }

    private enum State { IDLE, HOLD, FLY, BACK }
}
