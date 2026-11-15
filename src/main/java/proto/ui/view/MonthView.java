package proto.ui.view;

import proto.entity.Occurrence;
import proto.ui.AppState;
import proto.ui.Fmt;
import proto.ui.UiContext;
import proto.ui.component.Buttons;
import proto.ui.component.MonthGrid;
import proto.ui.component.OccurrenceList;
import proto.ui.component.TokenLabel;
import proto.ui.component.TokenPanel;
import proto.ui.layout.SizeClass;
import proto.ui.theme.Fonts;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * [경계 boundary] 달력 보기 (F2): 월간 격자와, 고른 날의 일정 목록 패널.
 * MainFrame이 만들고, refresh 때마다 ScheduleController#getOccurrences로 보이는 6주(42일)치를 한 번에 읽어 날짜별로 나눈다. 날짜를 고르면 AppState의
 * selectedDay가 바뀐다. 일정 이름 줄을 다른 날 칸으로 끌어 그 날로 옮길 수 있다(MonthGrid, 시간은 그대로).
 * 넓은 화면에서는 패널이 오른쪽에, 좁거나 세로로 긴 화면에서는 격자 아래에 놓인다.
 */
public class MonthView implements ContentView {

    private final UiContext ctx;
    private final JPanel root = new JPanel(new BorderLayout());
    private final JPanel gridHolder = new JPanel(new BorderLayout());
    private final MonthGrid grid;
    private final TokenLabel dayTitle = new TokenLabel("", Fonts.semibold(Fonts.TITLE - 2), Tokens.TEXT);
    private final TokenLabel dayCount = new TokenLabel("", Fonts.regular(Fonts.SMALL), Tokens.MUTED);
    private final OccurrenceList list;
    private final JPanel listCards = new JPanel(new CardLayout());
    private final JPanel side = new TokenPanel(new BorderLayout(0, Ui.px(12)), Tokens.SIDEBAR);
    private final JPanel head = new JPanel(new BorderLayout(Ui.px(12), 0));
    private final JButton add = Buttons.secondary("이 날에 일정 추가");
    private Map<LocalDate, List<Occurrence>> byDay = new HashMap<>();
    private int monthCount;
    private SizeClass size;

    public MonthView(UiContext ctx) {
        this.ctx = ctx;
        this.size = ctx.size();
        this.grid = new MonthGrid(this::select, d -> {
            select(d);
            ctx.actions().newSchedule(d);
        }, (o, day) -> ctx.actions().mover().move(o, day, o.startTime(), o.endTime()));
        this.list = new OccurrenceList(ctx.actions(), true);
        root.setOpaque(false);
        gridHolder.setOpaque(false);
        gridHolder.add(grid, BorderLayout.CENTER);
        buildSide();
        root.add(gridHolder, BorderLayout.CENTER);
        layoutFor(size);
    }

    private void buildSide() {
        JPanel titles = new JPanel(new BorderLayout(0, Ui.px(2)));
        titles.setOpaque(false);
        titles.add(dayTitle, BorderLayout.NORTH);
        titles.add(dayCount, BorderLayout.CENTER);
        head.setOpaque(false);
        head.setBorder(BorderFactory.createEmptyBorder(0, Ui.px(8), 0, 0));
        head.add(titles, BorderLayout.CENTER);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        TokenLabel none = new TokenLabel("이 날은 일정이 없어요", Fonts.medium(Fonts.BODY), Tokens.MUTED);
        none.setHorizontalAlignment(TokenLabel.CENTER);
        listCards.setOpaque(false);
        listCards.add(scroll, "list");
        listCards.add(none, "empty");
        add.addActionListener(e -> ctx.actions().newSchedule(ctx.state().selectedDay()));
        side.add(head, BorderLayout.NORTH);
        side.add(listCards, BorderLayout.CENTER);
    }

    @Override
    public void applySize(SizeClass sc) {
        size = sc;
        layoutFor(sc);
    }

    /** 패널을 오른쪽(넓은 화면) 또는 아래(좁거나 세로로 긴 화면)에 놓고 여백·점 모드를 맞춘다 */
    private void layoutFor(SizeClass sc) {
        boolean below = sc.dayPanelBelow();
        int g = sc.gutter();
        int bottom = Ui.px(below ? 12 : 24 - sc.verticalTrim());
        gridHolder.setBorder(BorderFactory.createEmptyBorder(0, Ui.px(g - 8), bottom, Ui.px(below ? g - 8 : 16)));
        root.remove(side);
        side.remove(add);
        head.remove(add);
        int pad = Ui.px(below ? 12 : 16);
        side.setBorder(BorderFactory.createEmptyBorder(Ui.px(below ? 12 : 20), Ui.px(below ? g - 8 : 16), pad,
                Ui.px(below ? g - 8 : 16)));
        if (below) {
            side.setPreferredSize(new Dimension(10, Ui.px(sc.tall() ? 280 : 200)));
            head.add(add, BorderLayout.EAST);
            root.add(side, BorderLayout.SOUTH);
        } else {
            side.setPreferredSize(new Dimension(Ui.px(sc.dayPanelWidth()), 10));
            side.add(add, BorderLayout.SOUTH);
            root.add(side, BorderLayout.EAST);
        }
        grid.setDotMode(sc.monthDots());
        root.revalidate();
        root.repaint();
    }

    /** 날짜를 고른다. 키보드로 지금 보이는 6주 밖까지 가면 그 달로 넘어간다 */
    private void select(LocalDate day) {
        AppState s = ctx.state();
        s.setSelectedDay(day);
        LocalDate first = grid.gridStart();
        if (day.isBefore(first) || day.isAfter(first.plusDays(41))) {
            s.setAnchor(day);
            ctx.refreshViews();
            return;
        }
        grid.setSelected(day);
        refreshSide(true);
    }

    @Override
    public JComponent component() {
        return root;
    }

    @Override
    public String title() {
        return Fmt.yearMonth(ctx.state().anchor());
    }

    @Override
    public String subtitle() {
        return monthCount == 0 ? "이 달에는 일정이 없어요" : "이 달 일정 " + monthCount + "개";
    }

    @Override
    public boolean hasNavigator() {
        return true;
    }

    @Override
    public boolean usesFilters() {
        return true;
    }

    @Override
    public void refresh(boolean animate) {
        AppState s = ctx.state();
        LocalDate month = s.anchor().withDayOfMonth(1);
        if (grid.isDragging(month)) {
            return; // 같은 달에서 끄는 중이면 새로 그리지 않는다 (WeekView#refresh와 같은 이유)
        }
        // 먼저 빈 데이터로 격자를 맞춰야 gridStart(보이는 첫 날)를 알 수 있고, 그 날짜 범위로 일정을 읽는다.
        grid.setData(month, ctx.schedules().today(), s.selectedDay(), Map.of());
        LocalDate from = grid.gridStart();
        List<Occurrence> all = ctx.schedules().getOccurrences(from, from.plusDays(41), s.categoryId(), s.keyword());
        byDay = new HashMap<>();
        all.forEach(o -> byDay.computeIfAbsent(o.date(), k -> new java.util.ArrayList<>()).add(o));
        monthCount = (int) all.stream().filter(o -> o.date().getMonth() == month.getMonth()).count();
        grid.setData(month, ctx.schedules().today(), s.selectedDay(), byDay);
        refreshSide(animate);
    }

    private void refreshSide(boolean animate) {
        LocalDate day = ctx.state().selectedDay();
        List<Occurrence> items = byDay.getOrDefault(day, List.of());
        dayTitle.setText(Fmt.dateShort(day));
        dayCount.setText(items.isEmpty() ? "일정 없음" : "일정 " + items.size() + "개");
        list.setItems(items, animate);
        ((CardLayout) listCards.getLayout()).show(listCards, items.isEmpty() ? "empty" : "list");
    }
}
