package proto.ui.view;

import com.formdev.flatlaf.util.UIScale;
import proto.entity.Occurrence;
import proto.entity.ScheduleState;
import proto.entity.TimeSlot;
import proto.ui.AppState;
import proto.ui.Fmt;
import proto.ui.ScheduleMover;
import proto.ui.UiContext;
import proto.ui.UiSettings;
import proto.ui.component.AllDayPane;
import proto.ui.component.AllDayRow;
import proto.ui.component.DayHeader;
import proto.ui.component.Hairline;
import proto.ui.component.ScrollGlide;
import proto.ui.component.ScrollPanel;
import proto.ui.component.TimeSnap;
import proto.ui.component.TimelineGrid;
import proto.ui.component.TimelineHandler;
import proto.ui.component.TimelineHeader;
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
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * [경계 boundary] 이번 주 보기 (F2): 월~일 7칸의 시간표. 일정을 시작~끝 길이로 놓고, 끌어서 옮기거나 크기를 바꾸고, 빈 곳을 끌어 새 일정을 만든다.
 * MainFrame이 만들고, refresh 때 ScheduleController#getOccurrences로 7일치를 읽어 TimelineGrid(본문), TimelineHeader(요일 줄), AllDayRow(하루 종일 줄)에 채운다.
 * 옮기기는 ScheduleMover, 새 일정은 UiActions#openNew로 요청한다. 하루 칸이 너무 좁아지면(약 110px 미만) 날짜별로 묶은 세로 목록(아젠다)으로 바꿔 WeekChip을 보여 준다.
 * 처음 보일 때와 주가 바뀔 때는 기본 활동 시작 시각(UiSettings#freeFrom) 30분 전이 맨 위에 오게 스크롤한다 (그 주 가장 이른 일정이 더 이르면 그 일정부터).
 * 하루 종일 줄은 시간표 스크롤 영역 높이의 40%까지만 차지하고, 더 길면 그 안에서 따로 스크롤된다(AllDayPane). 시간 본문이 늘 보이게 하려는 것이다.
 */
public class WeekView implements ContentView {

    /** 펼친 하루 종일 줄이 차지할 수 있는 시간표 스크롤 영역 높이의 비율 (나머지는 시간 본문이 쓴다) */
    private static final double ALL_DAY_SHARE = 0.4;
    /** 하루 칸이 이보다 좁으면 아젠다로 바꾼다 (논리 px) */
    private static final int MIN_COLUMN = 110;
    /** 아젠다에서 다시 칸 보기로 돌아가는 폭 — 경계에서 깜박이지 않게 조금 더 넉넉하게 */
    private static final int BACK_TO_COLUMNS = 122;
    private static final String COLUMNS = "columns";
    private static final String AGENDA = "agenda";
    private static final String[] ORDINALS = {"첫째", "둘째", "셋째", "넷째", "다섯째"};

    private final UiContext ctx;
    private final JPanel root = new JPanel(new CardLayout());
    private final ScrollPanel agenda = new ScrollPanel(null);
    private final ScheduleMover mover;
    private final TimelineGrid grid;
    private final TimelineHeader header;
    private final AllDayRow allDay;
    private final JScrollPane columnsScroll;
    private final JScrollPane agendaScroll;
    private final ScrollGlide glide;
    private final List<WeekChip> agendaChips = new ArrayList<>();
    private List<Occurrence> data = List.of();
    private boolean pendingScroll = true;
    private boolean glideNext;
    private boolean arrived;
    private boolean agendaMode;
    private SizeClass size;

    public WeekView(UiContext ctx) {
        this.ctx = ctx;
        this.size = ctx.size();
        this.mover = new ScheduleMover(ctx);
        root.setOpaque(false);
        agenda.setLayout(new BoxLayout(agenda, BoxLayout.Y_AXIS));
        TimelineHandler handler = new Handler();
        grid = new TimelineGrid(handler, ctx.schedules()::now);
        header = new TimelineHeader(TimelineGrid.gutterWidth(), handler::selectDay);
        allDay = new AllDayRow(handler, TimelineGrid.gutterWidth());
        columnsScroll = scroll(grid);
        allDay.setHeightCap(this::allDayCap);
        columnsScroll.setColumnHeaderView(topBar());
        columnsScroll.getColumnHeader().setOpaque(false);
        glide = new ScrollGlide(columnsScroll.getViewport());
        agendaScroll = scroll(agenda);
        root.add(columnsScroll, COLUMNS);
        root.add(agendaScroll, AGENDA);
        applyPadding();
        listenResize();
        listenArrival();
    }

    @Override
    public void applySize(SizeClass sc) {
        size = sc;
        applyPadding();
        chooseMode();
    }

    @Override
    public JComponent component() {
        return root;
    }

    /** 이번 주가 아니면 "11월 둘째 주"처럼 쓴다. 한 주가 두 달에 걸칠 수 있어서 그 주의 목요일이 속한 달과 주차로 정한다. */
    @Override
    public String title() {
        LocalDate start = ctx.state().weekStart();
        if (start.equals(thisWeekStart())) {
            return "이번 주";
        }
        LocalDate thursday = start.plusDays(3);
        return thursday.getMonthValue() + "월 " + ORDINALS[(thursday.getDayOfMonth() - 1) / 7] + " 주";
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
        if (grid.isDragging(start) || allDay.isDragging(start)) {
            // 같은 주를 그대로 보는 중에 일정을 끌고 있으면 새로 그리지 않는다 (1분 상태 새로 고침이 끌기를 끊지 않게).
            // 놓으면 controller가 바뀐 일정을 알려 와 그때 다시 그린다
            return;
        }
        data = ctx.schedules().getOccurrences(start, start.plusDays(6), s.categoryId(), s.keyword());
        chooseMode();
        render();
    }

    /** 이 보기가 화면에 다시 나타났을 때(사용자가 이동해 왔을 때만) 처음 위치로 부드럽게 스크롤한다. 주를 넘길 때는 보던 위치를 그대로 둔다 */
    @Override
    public void shown() {
        if (!arrived) {
            return;
        }
        arrived = false;
        pendingScroll = true;
        glideNext = true;
        SwingUtilities.invokeLater(this::scrollToWork);
    }

    /** 스크린샷·점검용: 지금 아젠다 모양인지 */
    public boolean isAgenda() {
        return agendaMode;
    }

    /** 스크롤해도 남는 위쪽 줄: 요일 머리글 아래에 하루 종일 줄을 붙인다 */
    private JPanel topBar() {
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(header, BorderLayout.NORTH);
        top.add(new AllDayPane(allDay), BorderLayout.CENTER);
        return top;
    }

    /** 하루 종일 줄이 보일 수 있는 높이의 한도. 아직 크기가 정해지지 않았으면 한도 없음 */
    private int allDayCap() {
        int h = columnsScroll.getHeight();
        return h <= 0 ? Integer.MAX_VALUE : (int) Math.round(h * ALL_DAY_SHARE);
    }

    /** 이 보기가 본문에 새로 붙을 때(다른 보기에서 이동해 올 때)만 표시해 둔다. 주를 넘기거나 새로 고칠 때는 붙는 일이 없다 */
    private void listenArrival() {
        root.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0 && e.getChanged() == root
                    && root.getParent() != null) {
                arrived = true;
            }
        });
    }

    /** 보기 폭이 바뀌면 칸/아젠다를 다시 고르고, 시간표 보이는 영역이 정해지면 처음 스크롤 위치를 잡는다 */
    private void listenResize() {
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
        columnsScroll.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                allDay.capChanged();
            }
        });
        columnsScroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                SwingUtilities.invokeLater(WeekView.this::scrollToWork);
            }
        });
    }

    private static JScrollPane scroll(JComponent view) {
        JScrollPane pane = new JScrollPane(view);
        pane.setOpaque(false);
        pane.getViewport().setOpaque(false);
        return pane;
    }

    private void applyPadding() {
        int side = Ui.px(size.gutter() - 8);
        int bottom = Ui.px(24 - size.verticalTrim());
        columnsScroll.setBorder(BorderFactory.createEmptyBorder(Ui.px(4), side, bottom, side));
        agendaScroll.setBorder(BorderFactory.createEmptyBorder(Ui.px(4), side, bottom, side));
    }

    /**
     * 칸 너비로 칸 보기/아젠다를 고른다. 바뀌었으면 true. 왜 두 개의 기준(110/122)인가: 한 기준으로만 바꾸면 창 너비가 경계 근처에서 조금씩 변할 때마다 보기가 칸↔아젠다로 깜박인다.
     * 아젠다에서 칸으로 돌아갈 때는 좀 더 넓어야 돌아가게 해서 이를 막는다.
     */
    private boolean chooseMode() {
        int viewport = root.getWidth() - Ui.px(size.gutter() - 8) * 2 - Ui.px(12);
        if (root.getWidth() <= 0) {
            return false;
        }
        int colWidth = UIScale.unscale((viewport - TimelineGrid.gutterWidth()) / 7);
        boolean next = agendaMode ? colWidth < BACK_TO_COLUMNS : colWidth < MIN_COLUMN;
        boolean changed = next != agendaMode;
        agendaMode = next;
        return changed;
    }

    private LocalDate thisWeekStart() {
        LocalDate today = ctx.schedules().today();
        return today.minusDays(today.getDayOfWeek().getValue() - 1L);
    }

    /**
     * 기본 활동 시작 시각 30분 전이 맨 위에 오게 스크롤한다. 그 주에 더 이른 일정이 있으면 그 일정(15분 전)부터 보인다.
     * 아직 화면 크기가 정해지지 않았으면 pendingScroll을 남겨 두었다가 크기가 정해진 뒤 한 번 한다.
     */
    private void scrollToWork() {
        JViewport vp = columnsScroll.getViewport();
        if (!pendingScroll || agendaMode || vp.getHeight() <= 0) {
            return;
        }
        int minute = Math.max(0, TimeSnap.minuteOf(UiSettings.freeFrom()) - 30);
        for (Occurrence o : data) {
            if (!o.allDay()) {
                minute = Math.min(minute, Math.max(0, TimeSnap.minuteOf(o.startTime()) - 15));
            }
        }
        int maxY = Math.max(0, grid.getPreferredSize().height - vp.getHeight());
        int y = Math.min(maxY, grid.yOfMinute(minute) - Ui.px(8));
        pendingScroll = false;
        if (glideNext) {
            glideNext = false;
            glide.to(y, 260);
        } else {
            vp.setViewPosition(new Point(0, y));
        }
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
        SwingUtilities.invokeLater(this::scrollToWork);
    }

    private void renderColumns(LocalDate start) {
        grid.setWeek(start, data);
        allDay.setWeek(start, ctx.schedules().today(), data);
        header.setWeek(start, ctx.schedules().today(), ctx.state().selectedDay());
    }

    private List<Occurrence> itemsOf(LocalDate day) {
        return data.stream().filter(o -> o.date().equals(day)).toList();
    }

    private void applyChipWidth() {
        int wrapWidth = Math.max(Ui.px(120), root.getWidth() - Ui.px(size.gutter() - 8) * 2 - Ui.px(16));
        agendaChips.forEach(c -> c.setWrapWidth(wrapWidth));
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
        head.setMaximumSize(new Dimension(Integer.MAX_VALUE, head.getPreferredSize().height));
        return head;
    }

    /** 시간표가 화면 요청(옮기기·새 일정·날짜 고르기)을 UiActions·ScheduleMover로 이어 주는 연결 */
    private final class Handler implements TimelineHandler {
        @Override
        public void open(Occurrence o) {
            ctx.actions().open(o);
        }

        @Override
        public boolean delete(Occurrence o) {
            return ctx.actions().delete(o);
        }

        @Override
        public void toggleDone(Occurrence o) {
            ctx.actions().toggleDone(o);
        }

        @Override
        public void showMenu(Component source, Point p, Occurrence o) {
            ctx.actions().showMenu(source, p, o);
        }

        @Override
        public ScheduleState stateOf(Occurrence o) {
            return ctx.actions().stateOf(o);
        }

        @Override
        public String categoryName(String categoryId) {
            return ctx.actions().categoryName(categoryId);
        }

        @Override
        public boolean move(Occurrence o, LocalDate date, LocalTime start, LocalTime end) {
            return mover.move(o, date, start, end);
        }

        @Override
        public boolean nudge(Occurrence o, int days, int minutes) {
            return mover.nudge(o, days, minutes);
        }

        @Override
        public void create(LocalDate date, LocalTime start, LocalTime end) {
            ctx.actions().openNew(ctx.schedules().draftForSlot(new TimeSlot(date, start, end)));
        }

        @Override
        public void selectDay(LocalDate day) {
            ctx.state().setSelectedDay(day);
            header.setSelected(day);
        }
    }
}
