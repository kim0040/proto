package proto.ui.component;

import proto.entity.Occurrence;
import proto.ui.theme.Fonts;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.JComponent;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * [경계 boundary] 이번 주 시간표 본문: 왼쪽 시각 글자 영역, 7개 날짜 칸, 시간 줄, 일정 블록, 지금 시각 선을 한 판에 그린다.
 * WeekView가 스크롤 영역 안에 넣고 refresh마다 setWeek로 일정을 다시 채운다 (F2, F3). 하루 안에서 겹치는 일정은 TimelineLayout이 나눈 줄에 따라 나란히 놓는다.
 * 블록 위의 끌기는 TimelineBlock이, 빈 곳의 끌기는 이 클래스가 받아 TimelineDrag로 넘기고, 놓은 결과(옮기기·새로 만들기)는 TimelineHandler로 요청한다.
 * 지금 시각 선은 오늘 칸에 얇게 블록 밑으로 그리고(블록 위에서는 1px 반투명, 점은 맨 위), 화면에 보이는 동안 30초마다 다시 그려 따라 움직인다.
 * 놓기에 성공한 일정은 TimelineSettle이 놓은 틀에서 새 자리로 스프링으로 안착시킨다.
 */
public class TimelineGrid extends JComponent implements Scrollable {

    private static final int HOUR = 48;
    private static final int GUTTER = 56;
    private static final int PAD = 12;
    private static final float HOUR_SIZE = 11f;
    /** 블록의 가장 낮은 높이(논리 px)와, 겹침을 따질 때 그만큼 차지한다고 보는 분 */
    private static final int MIN_HEIGHT = 22;
    private static final int MIN_MINUTES = 28;

    /** 일정 블록 하나와 놓일 칸·줄 */
    private record Cell(TimelineBlock block, int day, TimelineLayout.Placement place) {
    }

    private final TimelineHandler handler;
    private final Supplier<LocalDateTime> clock;
    private final TimelineDrag drag = new TimelineDrag(this);
    private final TimelineSettle settle = new TimelineSettle(this);
    private final DragAutoScroll scroller = new DragAutoScroll(this, p -> drag.dragged(p));
    private final HoverGlow slotHover = new HoverGlow(this::repaint, DragMotion.HOVER_IN_MS, DragMotion.HOVER_OUT_MS);
    private final List<Cell> cells = new ArrayList<>();
    private final Timer tick = new Timer(30_000, e -> repaint());
    private LocalDate weekStart = LocalDate.now();

    public TimelineGrid(TimelineHandler handler, Supplier<LocalDateTime> clock) {
        this.handler = handler;
        this.clock = clock;
        setOpaque(false);
        GridMouse mouse = new GridMouse();
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    /** 시각 글자 영역의 너비 (요일 머리글이 같은 폭을 비워 두려고 읽는다) */
    public static int gutterWidth() {
        return Ui.px(GUTTER);
    }

    /**
     * 이 주를 보여 주는 동안 일정을 끄는 중인지. 1분마다 오는 상태 새로 고침이 끌기를 끊지 않도록 WeekView가 묻는다.
     * 다른 주로 넘어갈 때는 이 값과 상관없이 setWeek가 끌기를 거둔다.
     */
    public boolean isDragging(LocalDate start) {
        return drag.isActive() && start.equals(weekStart);
    }

    public TimelineDrag drag() {
        return drag;
    }

    public TimelineMetrics metrics() {
        return new TimelineMetrics(Ui.px(GUTTER), getWidth(), Ui.px(HOUR), Ui.px(PAD));
    }

    public LocalDate dateOf(int day) {
        return weekStart.plusDays(day);
    }

    /** 분(0~1440)이 놓이는 세로 위치 (처음 스크롤 위치를 잡을 때 쓴다) */
    public int yOfMinute(int minute) {
        return metrics().yOf(minute);
    }

    // ---- 일정 채우기·배치 ----

    /**
     * 이번 주 일정으로 블록을 다시 만든다 (하루 종일 일정은 AllDayRow가 맡으므로 건너뛴다). 키보드 포커스가 있던 블록은 같은 일정에 다시 준다.
     * 끌고 있던 동작이 있으면 먼저 아무것도 바꾸지 않고 거둔다: 끌던 블록이 사라지고, 주가 바뀌면 같은 칸 번호가 다른 날짜가 되기 때문이다.
     */
    public void setWeek(LocalDate start, List<Occurrence> items) {
        drag.abort();
        String focused = cells.stream().filter(c -> c.block().isFocusOwner())
                .map(c -> c.block().occurrence().key()).findFirst().orElse(null);
        removeAll();
        cells.clear();
        weekStart = start;
        for (int d = 0; d < TimelineMetrics.DAYS; d++) {
            LocalDate date = dateOf(d);
            addDay(d, items.stream().filter(o -> !o.allDay() && o.date().equals(date)).toList());
        }
        settle.attach(cells.stream().map(Cell::block).toList());
        revalidate();
        repaint();
        if (focused != null) {
            focusBlock(o -> o.key().equals(focused));
        }
    }

    private void addDay(int day, List<Occurrence> items) {
        List<TimelineLayout.Span> spans = items.stream().map(o -> new TimelineLayout.Span(
                TimeSnap.minuteOf(o.startTime()), TimeSnap.endMinuteOf(o.endTime()))).toList();
        List<TimelineLayout.Placement> places = TimelineLayout.pack(spans, MIN_MINUTES);
        for (int i = 0; i < items.size(); i++) {
            TimelineBlock block = new TimelineBlock(handler, this, items.get(i));
            cells.add(new Cell(block, day, places.get(i)));
            add(block);
        }
    }

    @Override
    public void doLayout() {
        TimelineMetrics m = metrics();
        for (Cell c : cells) {
            c.block().setBounds(settle.place(c.block(), boundsOf(c, m)));
        }
    }

    /** 블록이 놓일 최종 자리 (겹치는 일정은 줄에 따라 나눈 폭) */
    private Rectangle boundsOf(Cell c, TimelineMetrics m) {
        Occurrence o = c.block().occurrence();
        int top = m.yOf(TimeSnap.minuteOf(o.startTime()));
        int bottom = m.yOf(TimeSnap.endMinuteOf(o.endTime()));
        int sub = (m.columnWidth(c.day()) - Ui.px(8)) / c.place().columns();
        return new Rectangle(m.columnX(c.day()) + Ui.px(2) + c.place().column() * sub, top + Ui.px(1),
                Math.max(Ui.px(8), sub - Ui.px(2)), Math.max(bottom - top, Ui.px(MIN_HEIGHT)) - Ui.px(2));
    }

    /** 놓일 칸과 시간 범위의 틀 (끌기 미리보기용) */
    Rectangle boxOf(int day, TimeSnap.Range r) {
        TimelineMetrics m = metrics();
        int top = m.yOf(r.start());
        return new Rectangle(m.columnX(day) + Ui.px(2), top + Ui.px(1), m.columnWidth(day) - Ui.px(8),
                Math.max(m.yOf(r.end()) - top, Ui.px(MIN_HEIGHT) / 2) - Ui.px(2));
    }

    // ---- 끌기·키보드 결과 ----

    void selectDay(int day) {
        handler.selectDay(dateOf(day));
    }

    void openBlock(TimelineBlock b) {
        handler.open(b.occurrence());
    }

    /**
     * 놓은 자리로 옮긴다. 결과를 기다리는 동안 카드는 놓은 자리에 들린 채 머문다. 옮겨졌으면 그 일정에 다시 키보드 포커스를 주고
     * 카드가 새 자리로 내려앉게 하고, 취소·실패·바뀐 것 없음이면 카드를 원래 블록 자리로 돌려보낸다.
     */
    void commitMove(TimelineBlock b, int day, TimeSnap.Range r, DragCard card, Rectangle slot) {
        Occurrence o = b.occurrence();
        LocalTime start = TimeSnap.timeOf(r.start());
        LocalTime end = TimeSnap.timeOf(r.end());
        MovedItem moved = new MovedItem(o, dateOf(day), start, occurrences());
        settle.expect(card, slot, moved);
        if (handler.move(o, dateOf(day), start, end)) {
            settle.confirm();
            focusMoved(moved);
        } else {
            settle.revert(b);
        }
    }

    /** Esc로 끌기를 취소했다: 카드를 원래 블록 자리로 돌려보낸다 (카드가 없는 빈 곳 끌기는 자리 틀만 지운다) */
    void cancelDrag(TimelineBlock b, DragCard card, Rectangle slot) {
        if (card != null && b != null) {
            settle.cancelDrag(card, slot, b);
        } else if (slot != null) {
            settle.fadeSlot(slot);
        }
    }

    /** 끌기 카드를 만든다: 블록과 같은 모양의 복사본이 press(누른 자리)를 쥔 채 따라다닌다 */
    DragCard cardFor(TimelineBlock b, Point press) {
        TimelineBlock copy = b.floating();
        return new DragCard(this, (g2, w, h) -> {
            copy.setSize(w, h);
            copy.paintCard(g2);
        }, b.getBounds(), press, Ui.px(5));
    }

    /** 빈 곳 끌기를 놓았다: 자리 틀을 서서히 지운다 */
    void fadeSlot(Rectangle box) {
        settle.fadeSlot(box);
    }

    /** 끌기 동안 모든 블록과 시간표의 마우스 모양 (null이면 평소 모양) */
    void setDragCursor(Cursor c) {
        setCursor(c);
        cells.forEach(cell -> cell.block().setDragCursor(c));
    }

    void commitCreate(int day, TimeSnap.Range r) {
        handler.create(dateOf(day), TimeSnap.timeOf(r.start()), TimeSnap.timeOf(r.end()));
    }

    /** Alt+방향키 이동. 옮겨졌으면 그 일정에 다시 포커스를 준다 */
    void nudge(Occurrence o, int days, int minutes) {
        TimeSnap.Range r = TimeSnap.shifted(TimeSnap.minuteOf(o.startTime()), TimeSnap.endMinuteOf(o.endTime()), minutes);
        MovedItem moved = new MovedItem(o, o.date().plusDays(days), TimeSnap.timeOf(r.start()), occurrences());
        if (handler.nudge(o, days, minutes)) {
            focusMoved(moved);
        }
    }

    /** 지금 시간표에 놓인 일정들 */
    private List<Occurrence> occurrences() {
        return cells.stream().map(c -> c.block().occurrence()).toList();
    }

    /** 옮겨진 일정의 블록에 포커스를 준다 (일정 번호·날짜로 찾는다. 다시 그린 직후라 한 박자 늦춘다) */
    private void focusMoved(MovedItem moved) {
        SwingUtilities.invokeLater(() -> {
            TimelineBlock b = movedBlock(moved);
            if (b != null) {
                b.requestFocusInWindow();
            }
        });
    }

    /** 다시 만든 블록 중 옮겨진 일정의 블록 (없으면 null) */
    TimelineBlock movedBlock(MovedItem moved) {
        Occurrence landed = moved.pick(occurrences());
        return cells.stream().map(Cell::block).filter(b -> b.occurrence() == landed).findFirst().orElse(null);
    }

    /** 점검용: 놓은 카드가 내려앉을 블록의 일정 (없으면 null) */
    Occurrence landingItem() {
        return settle.landing();
    }

    /** 조건에 맞는 블록에 포커스를 준다 (다시 그린 직후라 한 박자 늦춘다) */
    public void focusBlock(Predicate<Occurrence> match) {
        SwingUtilities.invokeLater(() -> cells.stream().map(Cell::block)
                .filter(b -> match.test(b.occurrence())).findFirst().ifPresent(TimelineBlock::requestFocusInWindow));
    }

    /** 끌다가 화면 위·아래 가장자리에 가까워지면 부드럽게 스크롤한다 (가장자리 40px 안쪽 깊이에 비례해 빨라진다) */
    void autoscroll(Point p) {
        scroller.update(p);
    }

    void stopAutoscroll() {
        scroller.stop();
    }

    /** 마우스가 가리키는 빈 15분 칸 (시각 글자 영역이면 null) */
    private Rectangle slotAt(Point p) {
        TimelineMetrics m = metrics();
        if (p.x < m.gutter() || p.x >= getWidth()) {
            return null;
        }
        int day = m.dayAt(p.x);
        int minute = Math.min(TimeSnap.DAY - TimeSnap.STEP, TimeSnap.floorStep(m.minuteAt(p.y)));
        int top = m.yOf(minute);
        return new Rectangle(m.columnX(day) + Ui.px(2), top, m.columnWidth(day) - Ui.px(4),
                m.yOf(minute + TimeSnap.STEP) - top);
    }

    /** 빈 곳의 마우스: 누르기·끌기·놓기를 TimelineDrag로 넘기고, 가리키는 빈 칸을 옅게 밝힌다 */
    private final class GridMouse extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            slotHover.moveTo(null);
            if (SwingUtilities.isLeftMouseButton(e) && !e.isPopupTrigger()) {
                drag.pressEmpty(e.getPoint());
            }
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            slotHover.moveTo(slotAt(e.getPoint()));
        }

        @Override
        public void mouseExited(MouseEvent e) {
            slotHover.moveTo(null);
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            drag.dragged(e.getPoint());
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (SwingUtilities.isLeftMouseButton(e)) {
                drag.released(e.getPoint(), e.getClickCount());
            }
        }
    }

    // ---- 그리기 ----

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        TimelineMetrics m = metrics();
        ColumnTints.paint(g2, m, getHeight(), todayIndex());
        slotHover.paint(g2, Tokens.alpha(Tokens.get(Tokens.SUBTLE), 0.9f), null, Ui.px(4));
        paintLines(g2, m);
        paintHours(g2, m);
        paintNowUnder(g2);
        g2.dispose();
    }

    /** 오늘 칸 번호 (이번 주가 아니면 -1) */
    private int todayIndex() {
        long d = java.time.temporal.ChronoUnit.DAYS.between(weekStart, clock.get().toLocalDate());
        return d >= 0 && d < TimelineMetrics.DAYS ? (int) d : -1;
    }

    /** 한 시간마다 가로줄, 날짜 칸 사이에 세로줄 */
    private void paintLines(Graphics2D g2, TimelineMetrics m) {
        g2.setColor(Tokens.alpha(Tokens.get(Tokens.BORDER), 0.8f));
        for (int h = 0; h <= 24; h++) {
            g2.fillRect(m.gutter(), m.yOf(h * 60), getWidth() - m.gutter(), 1);
        }
        g2.setColor(Tokens.alpha(Tokens.get(Tokens.BORDER), 0.5f));
        for (int d = 0; d <= TimelineMetrics.DAYS; d++) {
            g2.fillRect(Math.min(m.columnX(d), getWidth() - 1), m.yOf(0), 1, m.yOf(TimeSnap.DAY) - m.yOf(0));
        }
    }

    /** 시각 글자: 11px 옅은 색, 정시 줄 높이에 가운데를 맞춰 왼쪽 영역 오른쪽 끝에 붙인다 (0시·24시는 생략) */
    private void paintHours(Graphics2D g2, TimelineMetrics m) {
        Ui.smooth(g2);
        g2.setFont(Fonts.regular(HOUR_SIZE));
        g2.setColor(Tokens.get(Tokens.FAINT));
        FontMetrics fm = g2.getFontMetrics();
        for (int h = 1; h < 24; h++) {
            String text = String.format("%02d:00", h);
            g2.drawString(text, m.gutter() - Ui.px(10) - fm.stringWidth(text),
                    m.yOf(h * 60) - fm.getHeight() / 2 + fm.getAscent());
        }
    }

    /** 블록 밑에 내려앉는 블록의 그림자를, 블록 위에 지금 시각 선의 점·사라지는 자리 틀·돌아가는 카드·끌기 미리보기를 겹쳐 그린다 */
    @Override
    protected void paintChildren(Graphics g) {
        Graphics2D under = (Graphics2D) g.create();
        Ui.smooth(under);
        settle.paintUnder(under);
        under.dispose();
        super.paintChildren(g);
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.smooth(g2);
        paintNowOverBlocks(g2);
        settle.paintOver(g2);
        drag.paint(g2);
        g2.dispose();
    }

    /** 지금 시각 선의 위치: {칸 왼쪽 x, 칸 너비, 선 가운데 y}. 이번 주가 아니면 null */
    private int[] nowSpot() {
        LocalDateTime now = clock.get();
        int d = (int) java.time.temporal.ChronoUnit.DAYS.between(weekStart, now.toLocalDate());
        if (d < 0 || d >= TimelineMetrics.DAYS) {
            return null;
        }
        TimelineMetrics m = metrics();
        return new int[] {m.columnX(d), m.columnWidth(d), m.yOf(TimeSnap.minuteOf(now.toLocalTime()))};
    }

    /** 블록 밑에 지금 시각 선을 그린다. 블록은 색이 차 있어서 선이 글자 위를 가로지르지 않는다 */
    private void paintNowUnder(Graphics2D g2) {
        int[] s = nowSpot();
        if (s != null) {
            g2.setColor(Tokens.get(Tokens.ACCENT));
            g2.fillRect(s[0], s[2] - Ui.px(1), s[1], Math.max(1, Ui.px(2)));
        }
    }

    /**
     * 블록 위에서는 선을 1px 반투명으로만 비춰 글자를 덜 가리게 하고, 선 왼쪽 끝의 점은 맨 위에 그린다.
     * 점은 시간 글자 영역 쪽 칸 경계에 걸려 있어 블록과 거의 겹치지 않는다.
     */
    private void paintNowOverBlocks(Graphics2D g2) {
        int[] s = nowSpot();
        if (s == null) {
            return;
        }
        g2.setColor(Tokens.alpha(Tokens.get(Tokens.ACCENT), 0.35f));
        for (Cell c : cells) {
            Rectangle r = c.block().getBounds();
            boolean today = r.x >= s[0] && r.x < s[0] + s[1];
            if (today && s[2] >= r.y && s[2] < r.y + r.height) {
                g2.fillRect(r.x, s[2], r.width, Math.max(1, Ui.px(1)));
            }
        }
        int dot = Ui.px(8);
        g2.setColor(Tokens.get(Tokens.ACCENT));
        g2.fillOval(s[0] - dot / 2, s[2] - dot / 2, dot, dot);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        tick.start();
    }

    /** 화면에서 떨어지면 시계를 멈추고, 끌던 동작(자동 스크롤 포함)이 있으면 아무것도 바꾸지 않고 거둔다 */
    @Override
    public void removeNotify() {
        tick.stop();
        drag.abort();
        super.removeNotify();
    }

    // ---- 스크롤 ----

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Ui.px(GUTTER) + Ui.px(100) * TimelineMetrics.DAYS, metrics().totalHeight());
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(getPreferredSize().width, Ui.px(HOUR) * 8);
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return Ui.px(24);
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return Math.max(visible.height - Ui.px(48), Ui.px(48));
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
