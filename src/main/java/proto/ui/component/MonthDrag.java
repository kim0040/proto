package proto.ui.component;

import proto.entity.Occurrence;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.JComponent;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.time.LocalDate;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * [경계 boundary] 달력(MonthGrid)에서 일정을 다른 날 칸으로 끌어 옮기는 동작의 상태 기계: 누름 → 끌기(4px 넘으면 시작) → 놓기 또는 Esc 취소.
 * MonthGrid가 마우스 이벤트를 그대로 넘기고, 끄는 동안 칸 강조와 들린 칩을 그리게 한다 (F3). 일정 줄이 들려 올라 칩이 되어 마우스를 1:1로 따라다니고(DragCard),
 * 놓일 날짜 칸의 강조는 칸 사이를 80ms 동안 서서히 옮겨 간다(HoverGlow). 다른 날 칸에 놓으면 onDrop(일정, 날짜)을 부르고 칩은 MonthSettle이 이어받아 칸에 내려앉힌다.
 * 같은 날이나 칸 밖에서 놓거나 Esc를 누르면 아무 일도 하지 않고 칩이 원래 줄로 돌아간다. 일정을 읽거나 저장하지 않고 요청만 넘긴다.
 * 끌기가 끝난 직후 따라오는 "클릭"은 한 번 삼켜서 칸 선택·새 일정 창이 열리지 않게 한다.
 * 끌기는 누른 순간의 달(칸 맨 앞 날짜)에 묶인다. 끄는 도중 달이 바뀌거나 일정이 다시 채워지거나 달력이 화면에서 떨어지면 abort로 아무것도 바꾸지 않고 거둔다.
 */
final class MonthDrag {

    /** MonthGrid가 알려 줘야 하는 칸 정보 */
    interface Host {
        /** 이 좌표의 일정 줄. 없으면 null */
        Occurrence itemAt(int x, int y);

        /** 이 좌표에 있는 일정 줄의 화면 영역. 없으면 null */
        Rectangle itemBounds(int x, int y);

        /** 이 좌표의 칸 번호(0~41). 칸 밖이면 -1 */
        int cellAt(int x, int y);

        Rectangle cellBounds(int index);

        LocalDate dateAt(int index);

        /** 옮겨진 뒤의 일정(landed, 다시 채운 목록에 있는 것)이 date 칸의 어느 줄에 그려지는지 (보이지 않으면 null) */
        Rectangle landingRow(LocalDate date, Occurrence landed);

        /** 지금 달력에 채워진 일정 전부 (옮긴 일정을 번호·날짜로 알아볼 때 쓴다) */
        List<Occurrence> shownItems();

        /** 칩을 그리고 따라다니게 할 달력 컴포넌트 */
        JComponent view();

        /** 끌기가 시작되었거나 끝났다 (포커스·커서) */
        void dragging(boolean on);

        void repaintGrid();
    }

    private static final int THRESHOLD = 4;

    private final Host host;
    private final BiConsumer<Occurrence, LocalDate> onDrop;
    private final HoverGlow glow;
    private final MonthSettle settle;
    private Occurrence item;
    private Point pressAt;
    private LocalDate period;
    private boolean active;
    private boolean swallowClick;
    private DragCard card;
    private Rectangle home;

    MonthDrag(Host host, BiConsumer<Occurrence, LocalDate> onDrop) {
        this.host = host;
        this.onDrop = onDrop;
        this.glow = new HoverGlow(host::repaintGrid, DragMotion.SLOT_MS, DragMotion.SLOT_MS);
        this.settle = new MonthSettle(host);
    }

    /** 눌린 자리가 일정 줄이면 끌기 후보로 기억한다. 일정 줄이 아니면 false (보통 클릭으로 처리) */
    boolean press(int x, int y) {
        swallowClick = false;
        reset();
        item = host.itemAt(x, y);
        pressAt = item == null ? null : new Point(x, y);
        period = host.dateAt(0);
        return item != null;
    }

    /** 처음 눌린 곳에서 threshold(논리 px)보다 멀어졌는지 */
    static boolean beyond(Point from, int x, int y, int threshold) {
        return Math.abs(x - from.x) > threshold || Math.abs(y - from.y) > threshold;
    }

    /** 마우스를 끌었다. 4px를 넘으면 끌기가 시작되고, 그 뒤로는 칩이 마우스를 따라가고 놓일 칸 강조가 옮겨 간다 */
    void drag(int x, int y) {
        if (item == null) {
            return;
        }
        if (!active && beyond(pressAt, x, y, Ui.px(THRESHOLD))) {
            activate();
        }
        if (active) {
            card.follow(x, y);
            int cell = host.cellAt(x, y);
            // 원래 있던 날 칸은 "옮기지 않음"이라 강조하지 않는다
            boolean other = cell >= 0 && !host.dateAt(cell).equals(item.date());
            glow.moveTo(other ? inset(host.cellBounds(cell)) : null);
            host.repaintGrid();
        }
    }

    /** 일정 줄이 칩으로 들려 올라 끌기가 시작된다 */
    private void activate() {
        active = true;
        Rectangle row = host.itemBounds(pressAt.x, pressAt.y);
        MonthChip chip = new MonthChip(item);
        home = MonthChip.startRect(row);
        card = new DragCard(host.view(), chip, home, pressAt, MonthPill.arc());
        card.lift();
        host.dragging(true);
    }

    /** 마우스를 놓았다. 다른 날 칸 위였으면 onDrop을 부르고 칩을 내려앉히고, 아니면 칩이 원래 줄로 돌아간다 */
    void release(int x, int y) {
        if (item != null && !host.dateAt(0).equals(period)) {
            abort();
        }
        if (!active) {
            reset();
            return;
        }
        Occurrence moved = item;
        DragCard chip = card;
        Rectangle back = home;
        int cell = host.cellAt(x, y);
        LocalDate date = cell < 0 ? null : host.dateAt(cell);
        swallowClick = true;
        finish();
        if (date != null && !date.equals(moved.date())) {
            settle.expect(chip, moved, date, back);
            onDrop.accept(moved, date);
            settle.afterDrop();
        } else {
            settle.revert(chip, moved, back);
        }
    }

    /** Esc: 끌기를 그만둔다 (아무것도 바꾸지 않고 칩이 원래 줄로 돌아간다) */
    void cancel() {
        if (active) {
            swallowClick = true;
            Occurrence moved = item;
            DragCard chip = card;
            Rectangle back = home;
            finish();
            settle.revert(chip, moved, back);
        }
    }

    /**
     * 누르고 있거나 끌고 있는 동작을 아무것도 바꾸지 않고 바로 거둔다 (들린 칩은 돌아가지 않고 사라진다). 이어지는 클릭은 한 번 삼킨다.
     * 놓은 뒤 결과를 기다리거나 내려앉는 칩(MonthSettle)은 건드리지 않는다. 진행 중인 동작이 없으면 아무 일도 하지 않는다.
     */
    void abort() {
        if (item == null) {
            return;
        }
        swallowClick = true;
        finish();
    }

    boolean isActive() {
        return active;
    }

    /** 끌기가 막 끝났다면 따라오는 클릭 하나를 삼킨다 (한 번만 true) */
    boolean takeSwallowedClick() {
        boolean was = swallowClick;
        swallowClick = false;
        return was;
    }

    /** 새 일정이 들어왔다 (MonthGrid.setData). 기다리던 칩이 있으면 내려앉힌다 */
    void dataChanged() {
        settle.dataChanged();
    }

    /** 이 일정 줄을 흐리게 그려야 하는지 (끌려 나간 원래 줄) */
    boolean dims(Occurrence o) {
        return (active && item != null && o.equals(item)) || settle.dims(o);
    }

    /** 이 일정 줄을 그리지 않아야 하는지 (칩이 내려앉는 중인 새 줄) */
    boolean hides(Occurrence o) {
        return settle.hides(o);
    }

    private void finish() {
        reset();
        glow.moveTo(null);
        host.dragging(false);
        host.repaintGrid();
    }

    private void reset() {
        item = null;
        pressAt = null;
        period = null;
        active = false;
        card = null;
        home = null;
    }

    /** 칸에 2px 안쪽으로 들여 강조 모양으로 쓴다 */
    private static Rectangle inset(Rectangle cell) {
        int inset = Ui.px(2);
        return new Rectangle(cell.x + inset, cell.y + inset, cell.width - inset * 2, cell.height - inset * 2);
    }

    /** 놓일 칸 강조(서서히 옮겨 감 / 사라짐), 놓은 뒤 날아가는 칩, 끌고 있는 칩 */
    void paint(Graphics2D g2) {
        Ui.smooth(g2);
        glow.paint(g2, Tokens.alpha(Tokens.get(Tokens.ACCENT), 0.10f), Tokens.alpha(Tokens.get(Tokens.ACCENT), 0.85f),
                Ui.px(10));
        settle.paint(g2);
        if (active && card != null) {
            card.paint(g2);
        }
    }
}
