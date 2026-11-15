package proto.ui.component;

import proto.entity.Occurrence;
import proto.ui.motion.Motion;
import proto.ui.theme.Fonts;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.JComponent;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

/**
 * [경계 boundary] 주 시간표 위쪽(요일 머리글 아래)에 붙는 하루 종일 줄: 날짜 칸마다 하루 종일 일정 칩(AllDayChip)을 세로로 쌓고, 왼쪽 시각 글자 영역에 "하루 종일" 이름표를 둔다.
 * WeekView가 TimelineHeader와 함께 스크롤 영역의 열 머리글로 달아 둔다 (F2, F3). 이번 주에 하루 종일 일정이 하나도 없으면 높이가 0이라 보이지 않는다.
 * 한 날에 3개 이상이면 접힌 상태에서는 칩 1개 + "+N"(AllDayButton)만 보이고, 왼쪽 칸의 화살표나 "+N"을 누르면 140ms 동안 펼쳐진다 (노션 캘린더의 접히는 하루 종일 구역).
 * 칩을 가로로 끌면 다른 날로 옮긴다(AllDayDrag, 시간은 그대로). 시간 블록과 칩을 서로의 영역에 끌어 놓는 것은 지원하지 않는다.
 * 칸 위치는 TimelineMetrics로 계산해 시간 본문과 어긋나지 않는다.
 * 보이는 높이는 setHeightCap으로 받은 한도(예: 시간표 스크롤 영역의 40%, 적어도 한 줄)를 넘지 않는다. 내용이 더 길면 AllDayPane 안에서 따로 스크롤되고,
 * 왼쪽 칸의 이름표와 화살표는 스크롤해도 제자리에 남는다. 끄는 도중 주가 바뀌거나 일정이 다시 채워지거나 화면에서 떨어지면 끌기를 거둔다.
 */
public class AllDayRow extends JComponent implements AllDayChip.Host, Scrollable {

    private static final int PAD_Y = 3;
    private static final int GAP = 2;
    private static final int FOLD_AT = 2;
    private static final int TOGGLE_MS = 140;
    /** 왼쪽 칸의 화살표가 놓이는 줄 (이름표 바로 아래) */
    private static final int TOGGLE_ROW = 1;

    /** 칩 하나와 놓일 날짜 칸·칸 안의 줄 번호 */
    private record Cell(AllDayChip chip, int day, int index) {
    }

    /** "+N" 버튼과 놓일 날짜 칸·줄 번호 */
    private record Extra(AllDayButton button, int day, int index) {
    }

    private final TimelineHandler handler;
    private final int gutter;
    private final AllDayDrag drag = new AllDayDrag(this);
    private final List<Cell> cells = new ArrayList<>();
    private final List<Extra> extras = new ArrayList<>();
    private final List<List<Occurrence>> days = new ArrayList<>();
    private LocalDate weekStart = LocalDate.now();

    /** 이 주를 보여 주는 동안 하루 종일 칩을 끄는 중인지 (TimelineGrid#isDragging과 같은 쓰임) */
    public boolean isDragging(LocalDate start) {
        return drag.isActive() && start.equals(weekStart);
    }
    private int today = -1;
    private boolean expanded;
    private boolean showAll;
    private float height;
    private Motion.Handle toggling;
    private AllDayButton toggle;
    private IntSupplier heightCap = () -> Integer.MAX_VALUE;

    public AllDayRow(TimelineHandler handler, int gutter) {
        this.handler = handler;
        this.gutter = gutter;
        setOpaque(false);
        for (int i = 0; i < TimelineMetrics.DAYS; i++) {
            days.add(List.of());
        }
    }

    /** 이번에 보여 줄 한 주와 오늘, 그 주의 일정 (하루 종일이 아닌 일정은 건너뛴다) */
    public void setWeek(LocalDate start, LocalDate todayDate, List<Occurrence> items) {
        drag.abort();
        String focused = focusedKey();
        weekStart = start;
        today = (int) ChronoUnit.DAYS.between(start, todayDate);
        for (int d = 0; d < TimelineMetrics.DAYS; d++) {
            LocalDate date = start.plusDays(d);
            days.set(d, items.stream().filter(o -> o.allDay() && o.date().equals(date)).toList());
        }
        stopToggle();
        showAll = expanded;
        rebuild();
        height = targetHeight();
        revalidate();
        repaint();
        if (focused != null) {
            focusChip(o -> o.key().equals(focused));
        }
    }

    private String focusedKey() {
        return cells.stream().filter(c -> c.chip().isFocusOwner()).map(c -> c.chip().occurrence().key())
                .findFirst().orElse(null);
    }

    // ---- 구성·배치 ----

    /** 칩과 "+N"·화살표 버튼을 지금 펼침 상태에 맞게 다시 만든다 */
    private void rebuild() {
        removeAll();
        cells.clear();
        extras.clear();
        toggle = null;
        for (int d = 0; d < TimelineMetrics.DAYS; d++) {
            List<Occurrence> list = days.get(d);
            int shown = visibleCount(list.size());
            for (int i = 0; i < shown; i++) {
                AllDayChip chip = new AllDayChip(handler, list.get(i), this);
                chip.addFocusListener(REVEAL);
                cells.add(new Cell(chip, d, i));
                add(chip);
            }
            if (shown < list.size()) {
                addMore(d, shown, list.size() - shown);
            }
        }
        if (days.stream().anyMatch(l -> l.size() > FOLD_AT)) {
            toggle = AllDayButton.chevron(expanded, this::toggle);
            add(toggle);
        }
    }

    private int visibleCount(int total) {
        return showAll || total <= FOLD_AT ? total : 1;
    }

    private void addMore(int day, int index, int hidden) {
        AllDayButton more = AllDayButton.more(hidden, this::toggle);
        extras.add(new Extra(more, day, index));
        add(more);
    }

    private int rowsOf(List<Occurrence> list) {
        int shown = visibleCount(list.size());
        return shown + (shown < list.size() ? 1 : 0);
    }

    /** 칩을 다 놓는 데 필요한 높이 (한도 없이). 하루 종일 일정이 없으면 0 */
    private int contentHeight() {
        int rows = days.stream().mapToInt(this::rowsOf).max().orElse(0);
        return rows == 0 ? 0 : heightOf(rows);
    }

    private static int heightOf(int rows) {
        return Ui.px(PAD_Y) * 2 + rows * Ui.px(AllDayChip.HEIGHT) + (rows - 1) * Ui.px(GAP) + 1;
    }

    /** 보여 줄 높이: 내용 높이를 한도(적어도 한 줄)까지만 */
    private float targetHeight() {
        int content = contentHeight();
        return content == 0 ? 0f : Math.min(content, Math.max(heightOf(1), heightCap.getAsInt()));
    }

    /**
     * 보이는 높이의 한도를 정한다 (WeekView가 시간표 스크롤 영역 높이의 40%를 넘긴다). 한도가 한 줄보다 작으면 한 줄까지는 보인다.
     * 한도는 읽을 때마다 다시 물어보므로, 영역 크기가 바뀌면 capChanged를 불러 다시 맞춘다.
     */
    public void setHeightCap(IntSupplier cap) {
        heightCap = cap;
        capChanged();
    }

    /** 한도가 바뀌었다 (창 크기 변화). 펼치거나 접는 중이 아니면 보이는 높이를 다시 맞춘다 */
    public void capChanged() {
        if (toggling == null && Math.round(height) != Math.round(targetHeight())) {
            height = targetHeight();
            revalidate();
            repaint();
        }
    }

    /** 지금 보이는 높이 (펼치고 접는 중이면 그 중간). AllDayPane이 자기 높이로 쓴다 */
    public int shownHeight() {
        return Math.round(height);
    }

    /** 왼쪽 시각 글자 영역의 폭 */
    int gutter() {
        return gutter;
    }

    TimelineMetrics metrics() {
        return new TimelineMetrics(gutter, getWidth(), Ui.px(48), 0);
    }

    private int rowY(int index) {
        return Ui.px(PAD_Y) + index * (Ui.px(AllDayChip.HEIGHT) + Ui.px(GAP));
    }

    @Override
    public void doLayout() {
        TimelineMetrics m = metrics();
        for (Cell c : cells) {
            c.chip().setBounds(m.columnX(c.day()) + Ui.px(2), rowY(c.index()),
                    Math.max(Ui.px(8), m.columnWidth(c.day()) - Ui.px(10)), Ui.px(AllDayChip.HEIGHT));
        }
        for (Extra e : extras) {
            e.button().setBounds(m.columnX(e.day()) + Ui.px(2), rowY(e.index()), e.button().getPreferredSize().width,
                    Ui.px(AllDayChip.HEIGHT));
        }
        placeToggle();
    }

    /** 왼쪽 칸의 화살표: 스크롤해도 보이는 영역 맨 위 기준 같은 자리에 둔다 */
    private void placeToggle() {
        if (toggle != null) {
            Dimension p = toggle.getPreferredSize();
            toggle.setBounds(gutter - Ui.px(8) - p.width, scrollTop() + rowY(TOGGLE_ROW), p.width, p.height);
        }
    }

    /** AllDayPane 안에서 스크롤된 만큼 (스크롤되지 않았거나 패널 밖이면 0) */
    private int scrollTop() {
        return Math.max(0, -getY());
    }

    /** AllDayPane이 스크롤했다: 이름표와 화살표를 보이는 자리로 옮긴다 */
    void viewMoved() {
        placeToggle();
        repaint();
    }

    /** 칩을 다 놓는 높이 (보이는 높이보다 클 수 있다. 펼치고 접는 동안에는 둘 중 큰 쪽) */
    @Override
    public Dimension getPreferredSize() {
        return new Dimension(gutter + Ui.px(100) * TimelineMetrics.DAYS, Math.max(contentHeight(), shownHeight()));
    }

    // ---- 스크롤 (AllDayPane 안에서) ----

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(getPreferredSize().width, shownHeight());
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return Ui.px(AllDayChip.HEIGHT) + Ui.px(GAP);
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return Math.max(visible.height - Ui.px(AllDayChip.HEIGHT), Ui.px(AllDayChip.HEIGHT));
    }

    /** 폭은 늘 보이는 영역에 맞춰 시간 본문의 칸과 어긋나지 않게 한다 */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() != null && getParent().getHeight() >= getPreferredSize().height;
    }

    /** 키보드로 칩에 포커스가 오면 그 칩이 보이게 스크롤한다 */
    private static final FocusAdapter REVEAL = new FocusAdapter() {
        @Override
        public void focusGained(FocusEvent e) {
            JComponent c = (JComponent) e.getComponent();
            c.scrollRectToVisible(new Rectangle(0, 0, c.getWidth(), c.getHeight()));
        }
    };

    // ---- 펼치기·접기 ----

    /** 접힌 줄을 펼치거나 펼친 줄을 접는다. 펼칠 때는 칩을 먼저 모두 놓고 높이를 키우고, 접을 때는 높이를 줄인 뒤 칩을 바꾼다 */
    private void toggle() {
        stopToggle();
        expanded = !expanded;
        float from = height;
        if (expanded) {
            showAll = true;
            rebuild();
        }
        float to = expanded ? targetHeightFor(true) : targetHeightFor(false);
        toggling = Motion.run(TOGGLE_MS, Motion.OUT, f -> {
            height = from + (to - from) * f;
            revalidate();
            repaint();
        }, () -> settleToggle(to));
    }

    /** 줄을 펼치거나 접는다 (이미 그 상태면 아무것도 하지 않는다. 점검·캡처용) */
    public void setExpanded(boolean on) {
        if (on != expanded && toggle != null) {
            toggle();
        }
    }

    private void settleToggle(float to) {
        toggling = null;
        showAll = expanded;
        rebuild();
        height = to;
        revalidate();
        repaint();
    }

    private float targetHeightFor(boolean all) {
        boolean before = showAll;
        showAll = all;
        float h = targetHeight();
        showAll = before;
        return h;
    }

    private void stopToggle() {
        if (toggling != null) {
            toggling.cancel();
            toggling = null;
        }
    }

    // ---- 끌기·키보드 (AllDayChip.Host) ----

    @Override
    public void press(AllDayChip chip, MouseEvent e) {
        drag.press(chip, e);
    }

    @Override
    public void drag(MouseEvent e) {
        drag.drag(e);
    }

    @Override
    public boolean release(MouseEvent e) {
        return drag.release(e);
    }

    /** Alt+←/→: 하루씩 옮긴다. 옮겨졌으면 그 일정 칩에 다시 포커스를 준다 (일정 번호·날짜로 찾는다) */
    @Override
    public void nudge(AllDayChip chip, int daysDelta) {
        Occurrence o = chip.occurrence();
        MovedItem moved = movedItem(o, o.date().plusDays(daysDelta));
        if (handler.nudge(o, daysDelta, 0)) {
            SwingUtilities.invokeLater(() -> {
                AllDayChip landed = chipFor(moved);
                if (landed != null) {
                    landed.requestFocusInWindow();
                }
            });
        }
    }

    /** 옮기기를 요청하기 직전에 부른다: 다시 그린 뒤 옮겨진 일정을 알아볼 표 */
    MovedItem movedItem(Occurrence o, LocalDate date) {
        List<Occurrence> prior = days.stream().flatMap(List::stream).toList();
        return new MovedItem(o, date, o.startTime(), prior);
    }

    /** 칩을 target번 날짜 칸으로 옮겨 달라고 요청한다 (시간은 그대로). 옮겨졌으면 true */
    boolean moveTo(Occurrence o, int target) {
        return handler.move(o, dateOf(target), o.startTime(), o.endTime());
    }

    LocalDate dateOf(int day) {
        return weekStart.plusDays(day);
    }

    int dayOf(Occurrence o) {
        return (int) ChronoUnit.DAYS.between(weekStart, o.date());
    }

    /** 놓일 날짜 칸의 틀 (줄 전체 높이) */
    Rectangle cellOf(int day) {
        TimelineMetrics m = metrics();
        return new Rectangle(m.columnX(day) + Ui.px(1), Ui.px(1), m.columnWidth(day) - Ui.px(2), getHeight() - Ui.px(2));
    }

    /** 옮겨진 일정의 칩 (옮긴 뒤 새로 만든 칩 찾기). 접혀 있어 보이지 않거나 없으면 null */
    AllDayChip chipFor(MovedItem moved) {
        List<Occurrence> shown = cells.stream().map(c -> c.chip().occurrence()).toList();
        Occurrence landed = moved.pick(shown);
        return cells.stream().map(Cell::chip).filter(c -> c.occurrence() == landed).findFirst().orElse(null);
    }

    /** 조건에 맞는 칩에 포커스를 준다 (다시 그린 직후라 한 박자 늦춘다) */
    public void focusChip(Predicate<Occurrence> match) {
        SwingUtilities.invokeLater(() -> cells.stream().map(Cell::chip).filter(c -> match.test(c.occurrence()))
                .findFirst().ifPresent(AllDayChip::requestFocusInWindow));
    }

    /** 화면에서 떨어지면 끌던 동작이 있으면 아무것도 바꾸지 않고 거둔다 */
    @Override
    public void removeNotify() {
        drag.abort();
        super.removeNotify();
    }

    /** 점검용: 지금 보이는 칩 개수 */
    public int chipCount() {
        return cells.size();
    }

    // ---- 그리기 ----

    @Override
    protected void paintComponent(Graphics g) {
        if (getHeight() <= 0) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.smooth(g2);
        TimelineMetrics m = metrics();
        ColumnTints.paint(g2, m, getHeight(), today);
        g2.setColor(Tokens.alpha(Tokens.get(Tokens.BORDER), 0.5f));
        for (int d = 0; d <= TimelineMetrics.DAYS; d++) {
            g2.fillRect(Math.min(m.columnX(d), getWidth() - 1), 0, 1, getHeight());
        }
        g2.setColor(Tokens.get(Tokens.BORDER));
        g2.fillRect(0, getHeight() - 1, getWidth(), 1);
        paintLabel(g2);
        g2.dispose();
    }

    /** 왼쪽 칸의 "하루 종일" 이름표: 11px 옅은 색, 첫 줄 칩과 가운데를 맞춰 오른쪽에 붙인다 */
    private void paintLabel(Graphics2D g2) {
        g2.setFont(Fonts.regular(11f));
        g2.setColor(Tokens.get(Tokens.FAINT));
        FontMetrics fm = g2.getFontMetrics();
        String text = "하루 종일";
        int y = scrollTop() + rowY(0) + (Ui.px(AllDayChip.HEIGHT) - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(text, gutter - Ui.px(10) - fm.stringWidth(text), y);
    }

    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.smooth(g2);
        drag.paint(g2);
        g2.dispose();
    }

    /** 점검용: 칩 하나가 놓인 화면 영역 (그 날짜의 index번째) */
    public Rectangle chipBounds(int day, int index) {
        return cells.stream().filter(c -> c.day() == day && c.index() == index).map(c -> c.chip().getBounds())
                .findFirst().orElse(null);
    }
}
