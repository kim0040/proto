package proto.ui.view;

import com.formdev.flatlaf.util.UIScale;
import proto.entity.Occurrence;
import proto.ui.AppState;
import proto.ui.Fmt;
import proto.ui.UiContext;
import proto.ui.component.DayHeader;
import proto.ui.component.Hairline;
import proto.ui.component.ScrollPanel;
import proto.ui.component.TokenLabel;
import proto.ui.component.WeekChip;
import proto.ui.layout.SizeClass;
import proto.ui.theme.Fonts;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * [경계 boundary] 이번 주 보기 (F2): 월~일 7칸에 하루치 일정을 작은 칩으로 보여 준다. 오늘은 숫자 뒤에 강조 동그라미.
 * 하루 칸이 너무 좁아지면(약 110px 미만) 7칸 대신 날짜별로 묶은 세로 목록(아젠다)으로 바꿔 같은 칩을 보여 준다.
 */
public class WeekView implements ContentView {

    private static final int GAP = 10;
    /** 하루 칸이 이보다 좁으면 아젠다로 바꾼다 (논리 px) */
    private static final int MIN_COLUMN = 110;
    /** 아젠다에서 다시 칸 보기로 돌아가는 폭 — 경계에서 깜박이지 않게 조금 더 넉넉하게 */
    private static final int BACK_TO_COLUMNS = 122;
    private static final String COLUMNS = "columns";
    private static final String AGENDA = "agenda";
    private static final String[] ORDINALS = {"첫째", "둘째", "셋째", "넷째", "다섯째"};

    private final UiContext ctx;
    private final JPanel root = new JPanel(new CardLayout());
    private final JPanel columns = new JPanel(new GridLayout(1, 7, Ui.px(GAP), 0));
    private final ScrollPanel agenda = new ScrollPanel(null);
    private final JScrollPane columnsScroll;
    private final JScrollPane agendaScroll;
    private final List<WeekChip> chips = new ArrayList<>();
    private final List<WeekChip> agendaChips = new ArrayList<>();
    private List<Occurrence> data = List.of();
    private boolean agendaMode;
    private SizeClass size;

    public WeekView(UiContext ctx) {
        this.ctx = ctx;
        this.size = ctx.size();
        root.setOpaque(false);
        columns.setOpaque(false);
        agenda.setLayout(new BoxLayout(agenda, BoxLayout.Y_AXIS));
        ScrollPanel holder = new ScrollPanel(new BorderLayout());
        holder.add(columns, BorderLayout.NORTH);
        columnsScroll = scroll(holder);
        agendaScroll = scroll(agenda);
        root.add(columnsScroll, COLUMNS);
        root.add(agendaScroll, AGENDA);
        applyPadding();
        root.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (chooseMode()) {
                    render();
                } else {
                    applyChipWidth();
                }
            }
        });
    }

    private static JScrollPane scroll(JComponent view) {
        JScrollPane s = new JScrollPane(view);
        s.setOpaque(false);
        s.getViewport().setOpaque(false);
        return s;
    }

    private void applyPadding() {
        int g = Ui.px(size.gutter() - 8);
        int bottom = Ui.px(24 - size.verticalTrim());
        columnsScroll.setBorder(BorderFactory.createEmptyBorder(Ui.px(4), g, bottom, g));
        agendaScroll.setBorder(BorderFactory.createEmptyBorder(Ui.px(4), g, bottom, g));
    }

    @Override
    public void applySize(SizeClass sc) {
        size = sc;
        applyPadding();
        chooseMode();
    }

    /** 칸 너비로 칸 보기/아젠다를 고른다. 바뀌었으면 true */
    private boolean chooseMode() {
        int viewport = root.getWidth() - Ui.px(size.gutter() - 8) * 2 - Ui.px(12);
        if (root.getWidth() <= 0) {
            return false;
        }
        int colWidth = UIScale.unscale((viewport - Ui.px(GAP) * 6) / 7);
        boolean next = agendaMode ? colWidth < BACK_TO_COLUMNS : colWidth < MIN_COLUMN;
        boolean changed = next != agendaMode;
        agendaMode = next;
        return changed;
    }

    @Override
    public JComponent component() {
        return root;
    }

    @Override
    public String title() {
        LocalDate start = ctx.state().weekStart();
        if (start.equals(thisWeekStart())) {
            return "이번 주";
        }
        LocalDate thursday = start.plusDays(3);
        return thursday.getMonthValue() + "월 " + ORDINALS[(thursday.getDayOfMonth() - 1) / 7] + " 주";
    }

    private LocalDate thisWeekStart() {
        LocalDate t = ctx.schedules().today();
        return t.minusDays(t.getDayOfWeek().getValue() - 1L);
    }

    @Override
    public String subtitle() {
        LocalDate start = ctx.state().weekStart();
        return Fmt.dateRange(start, start.plusDays(6));
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
        LocalDate start = s.weekStart();
        data = ctx.schedules().getOccurrences(start, start.plusDays(6), s.categoryId(), s.keyword());
        chooseMode();
        render();
    }

    /** 지금 고른 모양(칸/아젠다)으로 데이터를 다시 그린다 */
    private void render() {
        LocalDate start = ctx.state().weekStart();
        if (agendaMode) {
            renderAgenda(start);
        } else {
            renderColumns(start);
        }
        ((CardLayout) root.getLayout()).show(root, agendaMode ? AGENDA : COLUMNS);
        applyChipWidth();
    }

    private void renderColumns(LocalDate start) {
        columns.removeAll();
        chips.clear();
        for (int i = 0; i < 7; i++) {
            LocalDate day = start.plusDays(i);
            columns.add(column(day, itemsOf(day)));
        }
        columns.revalidate();
        columns.repaint();
    }

    private List<Occurrence> itemsOf(LocalDate day) {
        return data.stream().filter(o -> o.date().equals(day)).toList();
    }

    private void applyChipWidth() {
        int w = Math.max(Ui.px(60), (columns.getWidth() - Ui.px(GAP) * 6) / 7);
        chips.forEach(c -> c.setWrapWidth(w));
        int aw = Math.max(Ui.px(120), root.getWidth() - Ui.px(size.gutter() - 8) * 2 - Ui.px(16));
        agendaChips.forEach(c -> c.setWrapWidth(aw));
    }

    private JPanel column(LocalDate day, List<Occurrence> items) {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.add(new DayHeader(day, day.equals(ctx.schedules().today())));
        col.add(Box.createVerticalStrut(Ui.px(8)));
        for (Occurrence o : items) {
            WeekChip chip = new WeekChip(ctx.actions(), o);
            chip.setAlignmentX(0f);
            chips.add(chip);
            col.add(chip);
            col.add(Box.createVerticalStrut(Ui.px(6)));
        }
        col.add(Box.createVerticalGlue());
        return col;
    }

    // ---- 아젠다 (칸이 좁을 때) ----

    private void renderAgenda(LocalDate start) {
        agenda.removeAll();
        agendaChips.clear();
        for (int i = 0; i < 7; i++) {
            LocalDate day = start.plusDays(i);
            agenda.add(agendaDay(day, itemsOf(day)));
        }
        agenda.revalidate();
        agenda.repaint();
    }

    private JPanel agendaDay(LocalDate day, List<Occurrence> items) {
        JPanel section = new JPanel();
        section.setOpaque(false);
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setAlignmentX(0f);
        section.add(agendaHeader(day, items.size()));
        if (items.isEmpty()) {
            TokenLabel none = new TokenLabel("일정 없음", Fonts.regular(Fonts.SMALL), Tokens.FAINT);
            none.setBorder(BorderFactory.createEmptyBorder(Ui.px(2), Ui.px(8), Ui.px(8), 0));
            none.setAlignmentX(0f);
            section.add(none);
        }
        for (Occurrence o : items) {
            WeekChip chip = new WeekChip(ctx.actions(), o);
            chip.setAlignmentX(0f);
            agendaChips.add(chip);
            section.add(chip);
            section.add(Box.createVerticalStrut(Ui.px(6)));
        }
        section.add(Box.createVerticalStrut(Ui.px(8)));
        return section;
    }

    /** 날짜 머리글: 요일·날짜와 일정 개수, 아래에 가는 선. 칩 목록과 구분되도록 굵게 보인다 */
    private JPanel agendaHeader(LocalDate day, int count) {
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setAlignmentX(0f);
        head.setBorder(BorderFactory.createCompoundBorder(new Hairline(),
                BorderFactory.createEmptyBorder(Ui.px(4), 0, Ui.px(4), 0)));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(new DayHeader(day, day.equals(ctx.schedules().today())));
        head.add(left, BorderLayout.WEST);
        if (count > 0) {
            head.add(new TokenLabel(count + "개", Fonts.medium(Fonts.SMALL), Tokens.MUTED), BorderLayout.EAST);
        }
        head.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, head.getPreferredSize().height));
        return head;
    }

    /** 스크린샷·점검용: 지금 아젠다 모양인지 */
    public boolean isAgenda() {
        return agendaMode;
    }
}
