package proto.ui;

import proto.control.CategoryListener;
import proto.control.OperationResult;
import proto.control.ReminderListener;
import proto.control.ScheduleListener;
import proto.entity.Category;
import proto.entity.Occurrence;
import proto.ui.AppState.Mode;
import proto.ui.component.Overlay;
import proto.ui.component.ReminderCard;
import proto.ui.component.TokenPanel;
import proto.ui.component.TransitionHost;
import proto.ui.component.TransitionHost.Dir;
import proto.ui.theme.AppIcon;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;
import proto.ui.view.ContentView;
import proto.ui.view.MonthView;
import proto.ui.view.SettingsView;
import proto.ui.view.StatsView;
import proto.ui.view.TodayView;
import proto.ui.view.WeekView;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Taskbar;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.Map;

/**
 * [경계 boundary] 메인 창: 사이드바 + 제목줄 + 보기 영역을 조립하고, controller의 변화 알림(일정·카테고리·알림)을 받아 화면을 맞춘다.
 */
public final class MainFrame extends JFrame implements ScheduleListener, CategoryListener, ReminderListener {

    private static final int REMINDER_SNOOZE_MINUTES = 5;

    private final UiContext ctx;
    private final Map<Mode, ContentView> views = new EnumMap<>(Mode.class);
    private final TransitionHost host = new TransitionHost();
    private final Sidebar sidebar;
    private final HeaderBar header;
    private final Overlay overlay = new Overlay();
    private ContentView current;

    public MainFrame(UiContext ctx) {
        super("일정 관리");
        this.ctx = ctx;
        ctx.setWindow(() -> this);
        ctx.setOverlay(overlay);
        views.put(Mode.TODAY, new TodayView(ctx));
        views.put(Mode.WEEK, new WeekView(ctx));
        views.put(Mode.MONTH, new MonthView(ctx));
        views.put(Mode.STATS, new StatsView(ctx));
        views.put(Mode.SETTINGS, new SettingsView(ctx));
        sidebar = new Sidebar(ctx, this::navigate, this::selectCategory);
        header = new HeaderBar(() -> shift(-1), this::goToday, () -> shift(1), this::search);
        configureWindow();
        buildLayout();
        installShortcuts();
        switchTo(UiSettings.startMode(), false);
        javax.swing.UIManager.addPropertyChangeListener(e -> {
            if ("lookAndFeel".equals(e.getPropertyName())) {
                refreshHiddenViews();
            }
        });
    }

    /** 화면에 붙어 있지 않은 보기들도 테마가 바뀌면 새 스타일로 맞춘다 */
    private void refreshHiddenViews() {
        views.values().stream().filter(v -> v != current)
                .forEach(v -> javax.swing.SwingUtilities.updateComponentTreeUI(v.component()));
    }

    private void configureWindow() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(Ui.px(960), Ui.px(640)));
        setSize(new Dimension(Ui.px(1200), Ui.px(780)));
        setLocationRelativeTo(null);
        AppIcon.apply(this);
        if (Ui.isMac()) {
            getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
            getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
            getRootPane().putClientProperty("apple.awt.windowTitleVisible", false);
        }
    }

    private void buildLayout() {
        TokenPanel root = new TokenPanel(new BorderLayout(), Tokens.BG);
        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        content.add(header, BorderLayout.NORTH);
        content.add(host, BorderLayout.CENTER);
        root.add(sidebar, BorderLayout.WEST);
        root.add(content, BorderLayout.CENTER);
        setContentPane(root);
        JLayeredPane layers = getLayeredPane();
        layers.add(overlay, JLayeredPane.POPUP_LAYER);
        layers.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                overlay.setBounds(0, 0, layers.getWidth(), layers.getHeight());
            }
        });
    }

    /** controller 리스너로 등록한다 (Main이 만든 controller에 한 번만) */
    public void attachListeners() {
        ctx.schedules().addListener(this);
        ctx.categories().addListener(this);
        ctx.reminders().addListener(this);
    }

    // ---- 이동 ----

    private void navigate(Mode mode) {
        if (mode == ctx.state().mode()) {
            return;
        }
        LocalDate today = ctx.schedules().today();
        ctx.state().setAnchor(today);
        ctx.state().setSelectedDay(today);
        host.transition(Dir.UP, () -> switchTo(mode, true), () -> current.shown());
    }

    private void switchTo(Mode mode, boolean animateSidebar) {
        ctx.state().setMode(mode);
        current = views.get(mode);
        current.refresh(false);
        host.setContent(current.component());
        sidebar.setMode(mode, animateSidebar);
        updateHeader();
    }

    private void updateHeader() {
        header.setTitles(current.title(), current.subtitle());
        header.setNavigatorVisible(current.hasNavigator());
        header.setSearchVisible(current.hasSearch());
        header.setSearchEnabled(current.usesFilters());
    }

    private void shift(int direction) {
        AppState s = ctx.state();
        LocalDate next = s.mode() == Mode.MONTH ? s.anchor().plusMonths(direction) : s.anchor().plusWeeks(direction);
        moveAnchor(next, direction > 0 ? Dir.FROM_RIGHT : Dir.FROM_LEFT);
    }

    private void goToday() {
        AppState s = ctx.state();
        LocalDate today = ctx.schedules().today();
        s.setSelectedDay(today);
        moveAnchor(today, today.isAfter(s.anchor()) ? Dir.FROM_RIGHT : Dir.FROM_LEFT);
    }

    private void moveAnchor(LocalDate next, Dir dir) {
        AppState s = ctx.state();
        s.setAnchor(next);
        if (s.mode() == Mode.MONTH && !YearMonth.from(s.selectedDay()).equals(YearMonth.from(next))) {
            LocalDate today = ctx.schedules().today();
            s.setSelectedDay(YearMonth.from(today).equals(YearMonth.from(next)) ? today : next.withDayOfMonth(1));
        }
        host.transition(dir, () -> {
            current.refresh(false);
            updateHeader();
        }, () -> current.shown());
    }

    private void selectCategory(String id) {
        ctx.state().setCategoryId(id);
        sidebar.setCategory(id, true);
        refreshCurrent(false);
    }

    private void search(String text) {
        ctx.state().setKeyword(text);
        refreshCurrent(false);
    }

    private void refreshCurrent(boolean animate) {
        current.refresh(animate);
        updateHeader();
    }

    // ---- 단축키 ----

    private void installShortcuts() {
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        bind(KeyEvent.VK_N, mask, () -> ctx.actions().newSchedule(ctx.state().mode() == Mode.MONTH
                ? ctx.state().selectedDay() : ctx.schedules().today()));
        bind(KeyEvent.VK_F, mask, header::focusSearch);
        bind(KeyEvent.VK_1, mask, () -> navigate(Mode.TODAY));
        bind(KeyEvent.VK_2, mask, () -> navigate(Mode.WEEK));
        bind(KeyEvent.VK_3, mask, () -> navigate(Mode.MONTH));
        bind(KeyEvent.VK_4, mask, () -> navigate(Mode.STATS));
        bind(KeyEvent.VK_COMMA, mask, () -> navigate(Mode.SETTINGS));
    }

    private void bind(int key, int modifiers, Runnable action) {
        String name = "proto-" + key;
        JComponent root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, modifiers), name);
        root.getActionMap().put(name, new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                action.run();
            }
        });
    }

    // ---- controller 알림 ----

    @Override
    public void schedulesChanged() {
        refreshCurrent(true);
    }

    @Override
    public void statesChanged() {
        refreshCurrent(true);
    }

    @Override
    public void storageFailed(String message) {
        ctx.actions().error(message);
    }

    @Override
    public void categoriesChanged() {
        String selected = ctx.state().categoryId();
        boolean stillThere = selected == null
                || ctx.categories().getCategories().stream().map(Category::getId).anyMatch(selected::equals);
        if (!stillThere) {
            ctx.state().setCategoryId(null);
        }
        sidebar.refreshCategories();
        refreshCurrent(false);
    }

    @Override
    public void reminderDue(Occurrence occurrence, boolean snoozed) {
        overlay.addReminder(occurrence, snoozed, ctx.categories().nameOf(occurrence.categoryId()),
                new ReminderCard.Actions() {
                    @Override
                    public void snooze(Occurrence o) {
                        OperationResult r = ctx.reminders().snooze(o, REMINDER_SNOOZE_MINUTES);
                        report(r, REMINDER_SNOOZE_MINUTES + "분 뒤에 다시 알려 드려요");
                    }

                    @Override
                    public void complete(Occurrence o) {
                        report(ctx.schedules().setCompleted(o, true), "일정을 완료했어요");
                    }
                });
        drawAttention();
    }

    private void report(OperationResult r, String okMessage) {
        if (r.success()) {
            ctx.actions().toast(okMessage);
        } else {
            ctx.actions().error(r.message());
        }
    }

    /** 창이 가려져 있어도 알림을 알아보도록 독 아이콘/작업 표시줄에 주의를 끌고 창을 앞으로 가져온다 */
    private void drawAttention() {
        try {
            if (UiSettings.reminderSound()) {
                Toolkit.getDefaultToolkit().beep();
            }
            if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.USER_ATTENTION)) {
                Taskbar.getTaskbar().requestUserAttention(true, false);
            }
            if (!isActive()) {
                toFront();
            }
        } catch (RuntimeException e) {
            // 지원하지 않는 환경에서는 카드만 보여 준다
        }
    }

    /** 스크린샷 도구가 쓰는 현재 보기 */
    public ContentView currentView() {
        return current;
    }

    /** 스크린샷 도구가 쓰는 보기 전환 (애니메이션 없이) */
    public void showMode(Mode mode) {
        switchTo(mode, false);
        current.shown();
    }

    public UiContext context() {
        return ctx;
    }

    public Overlay overlay() {
        return overlay;
    }
}
