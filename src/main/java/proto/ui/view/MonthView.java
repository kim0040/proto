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
 * [경계 boundary] 달력 보기 (F2): 월간 격자와, 고른 날의 일정 목록이 있는 오른쪽 패널.
 */
public class MonthView implements ContentView {

    private final UiContext ctx;
    private final JPanel root = new JPanel(new BorderLayout());
    private final MonthGrid grid;
    private final TokenLabel dayTitle = new TokenLabel("", Fonts.semibold(Fonts.TITLE - 2), Tokens.TEXT);
    private final TokenLabel dayCount = new TokenLabel("", Fonts.regular(Fonts.SMALL), Tokens.MUTED);
    private final OccurrenceList list;
    private final JPanel listCards = new JPanel(new CardLayout());
    private Map<LocalDate, List<Occurrence>> byDay = new HashMap<>();
    private int monthCount;

    public MonthView(UiContext ctx) {
        this.ctx = ctx;
        this.grid = new MonthGrid(this::select, d -> {
            select(d);
            ctx.actions().newSchedule(d);
        });
        this.list = new OccurrenceList(ctx.actions(), true);
        root.setOpaque(false);
        JPanel gridHolder = new JPanel(new BorderLayout());
        gridHolder.setOpaque(false);
        gridHolder.setBorder(BorderFactory.createEmptyBorder(0, Ui.px(24), Ui.px(24), Ui.px(16)));
        gridHolder.add(grid, BorderLayout.CENTER);
        root.add(gridHolder, BorderLayout.CENTER);
        root.add(buildSide(), BorderLayout.EAST);
    }

    private JPanel buildSide() {
        TokenPanel side = new TokenPanel(new BorderLayout(0, Ui.px(12)), Tokens.SIDEBAR);
        side.setBorder(BorderFactory.createEmptyBorder(Ui.px(20), Ui.px(16), Ui.px(20), Ui.px(16)));
        side.setPreferredSize(new Dimension(Ui.px(304), 10));
        JPanel head = new JPanel(new BorderLayout(0, Ui.px(2)));
        head.setOpaque(false);
        head.setBorder(BorderFactory.createEmptyBorder(0, Ui.px(8), 0, 0));
        head.add(dayTitle, BorderLayout.NORTH);
        head.add(dayCount, BorderLayout.CENTER);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        TokenLabel none = new TokenLabel("이 날은 일정이 없어요", Fonts.medium(Fonts.BODY), Tokens.MUTED);
        none.setHorizontalAlignment(TokenLabel.CENTER);
        listCards.setOpaque(false);
        listCards.add(scroll, "list");
        listCards.add(none, "empty");
        JButton add = Buttons.secondary("이 날에 일정 추가");
        add.addActionListener(e -> ctx.actions().newSchedule(ctx.state().selectedDay()));
        side.add(head, BorderLayout.NORTH);
        side.add(listCards, BorderLayout.CENTER);
        side.add(add, BorderLayout.SOUTH);
        return side;
    }

    private void select(LocalDate day) {
        ctx.state().setSelectedDay(day);
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
