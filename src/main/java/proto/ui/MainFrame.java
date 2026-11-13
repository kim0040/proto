package proto.ui;

import proto.control.CategoryListener;
import proto.control.ReminderListener;
import proto.control.ScheduleListener;
import proto.entity.Category;
import proto.entity.Occurrence;
import proto.ui.AppState.Mode;
import proto.ui.component.Overlay;
import proto.ui.component.ReminderCard;
import proto.ui.component.TokenPanel;
import proto.ui.layout.SizeClass;
import proto.ui.layout.WindowState;
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
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Taskbar;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeListener;
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
    private final SidebarRail rail;
    private final SidebarHost sidebarHost;
    private final SidebarDrawer drawer;
    private final HeaderBar header;
    private final Overlay overlay = new Overlay();
    private final WindowState windowState = new WindowState();
    private final Timer saveBounds = new Timer(600, e -> windowState.save(this));
    private final PropertyChangeListener lafListener = e -> {
        if ("lookAndFeel".equals(e.getPropertyName())) {
            refreshHiddenViews();
        }
    };
    private ContentView current;
    private LocalDate knownToday;
    private boolean disposed;

    public MainFrame(UiContext ctx) {
        super("일정 관리");
        this.ctx = ctx;
        ctx.setWindow(() -> this);
        ctx.setOverlay(overlay);
        ctx.setRefresher(() -> refreshCurrent(false));
        views.put(Mode.TODAY, new TodayView(ctx));
        views.put(Mode.WEEK, new WeekView(ctx));
        views.put(Mode.MONTH, new MonthView(ctx));
        views.put(Mode.STATS, new StatsView(ctx));
        views.put(Mode.SETTINGS, new SettingsView(ctx));
        configureWindow();
        ctx.breakpoints().prime(getWidth(), getHeight());
        drawer = new SidebarDrawer(() -> rail().focusMenu());
        sidebar = new Sidebar(ctx, this::navigateFromSidebar, this::selectCategory, drawer::closeNow);
        rail = new SidebarRail(ctx, this::navigate, this::openDrawer);
        sidebarHost = new SidebarHost(sidebar, rail, ctx.size());
        header = new HeaderBar(() -> shift(-1), this::goToday, () -> shift(1), this::search);
        buildLayout();
        installShortcuts();
        knownToday = ctx.schedules().today();
        ctx.breakpoints().attach(getRootPane());
        ctx.breakpoints().onChange(sc -> applySize(sc, isShowing()));
        applySize(ctx.size(), false);
        switchTo(UiSettings.startMode(), false);
        javax.swing.UIManager.addPropertyChangeListener(lafListener);
        saveBounds.setRepeats(false);
    }

    private SidebarRail rail() {
        return rail;
    }

    /** 크기 등급이 바뀌었을 때: 사이드바(띠/전체), 위쪽 줄, 보기들의 배치를 맞춘다 */
    private void applySize(SizeClass sc, boolean animate) {
        if (!sc.compact()) {
            drawer.closeNow();
        }
        header.apply(sc);
        sidebarHost.apply(sc, animate);
        views.values().forEach(v -> v.applySize(sc));
        if (current != null) {
            refreshCurrent(false);
        }
    }

    private void openDrawer() {
        if (!ctx.size().compact() || drawer.isOpened()) {
            return;
        }
        sidebar.setMode(ctx.state().mode(), false);
        drawer.open(sidebar);
    }

    private void navigateFromSidebar(Mode mode) {
        drawer.closeNow();
        navigate(mode);
    }

    /** 화면에 붙어 있지 않은 보기들도 테마가 바뀌면 새 스타일로 맞춘다 */
    private void refreshHiddenViews() {
        views.values().stream().filter(v -> v != current)
                .forEach(v -> javax.swing.SwingUtilities.updateComponentTreeUI(v.component()));
        if (sidebar.getParent() == null) {
            javax.swing.SwingUtilities.updateComponentTreeUI(sidebar);
        }
    }

    private void configureWindow() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        Dimension minimum = new Dimension(Ui.px(760), Ui.px(540));
        setMinimumSize(minimum);
        boolean maximized = windowState.restore(this, minimum);
        if (maximized) {
            setExtendedState(MAXIMIZED_BOTH);
        }
        AppIcon.apply(this);
        installWindowListeners();
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
        root.add(sidebarHost, BorderLayout.WEST);
        root.add(content, BorderLayout.CENTER);
        setContentPane(root);
        JLayeredPane layers = getLayeredPane();
        layers.add(drawer, JLayeredPane.PALETTE_LAYER);
        layers.add(overlay, JLayeredPane.POPUP_LAYER);
        layers.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                overlay.setBounds(0, 0, layers.getWidth(), layers.getHeight());
                drawer.setBounds(0, 0, layers.getWidth(), layers.getHeight());
            }
        });
    }

    /** 창 위치·크기 저장, 닫기, 다시 앞으로 왔을 때 날짜 확인 */
    private void installWindowListeners() {
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                boundsChanged();
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                boundsChanged();
            }
        });
        WindowAdapter listener = new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                windowState.save(MainFrame.this);
            }

            @Override
            public void windowStateChanged(WindowEvent e) {
                saveBounds.restart();
            }

            @Override
            public void windowGainedFocus(WindowEvent e) {
                checkDate();
            }
        };
        addWindowListener(listener);
        addWindowFocusListener(listener);
    }

    private void boundsChanged() {
        windowState.remember(this);
        saveBounds.restart();
    }

    /** controller 리스너로 등록한다 (Main이 만든 controller에 한 번만) */
    public void attachListeners() {
        ctx.schedules().addListener(this);
        ctx.categories().addListener(this);
        ctx.reminders().addListener(this);
    }

    /** 창을 정리할 때 controller·전역 리스너와 타이머를 떼어 낸다 */
    @Override
    public void dispose() {
        if (!disposed) {
            disposed = true;
            ctx.schedules().removeListener(this);
            ctx.categories().removeListener(this);
            ctx.reminders().removeListener(this);
            javax.swing.UIManager.removePropertyChangeListener(lafListener);
            saveBounds.stop();
            header.dispose();
            drawer.closeNow();
            overlay.clearAll();
        }
        super.dispose();
    }

    /** 날짜가 바뀌었으면(자정을 넘기거나 절전에서 깨어남) "오늘" 표시들을 새 날짜로 맞춘다 */
    public void checkDate() {
        LocalDate today = ctx.schedules().today();
        if (today.equals(knownToday)) {
            return;
        }
        LocalDate old = knownToday;
        knownToday = today;
        AppState s = ctx.state();
        if (old.equals(s.anchor())) {
            s.setAnchor(today);
        }
        if (old.equals(s.selectedDay())) {
            s.setSelectedDay(today);
        }
        refreshCurrent(false);
    }

    // ---- 이동 ----

    private void navigate(Mode mode) {
        checkDate();
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
        rail.setMode(mode, animateSidebar);
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
        drawer.closeNow();
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
        if (disposed) {
            return;
        }
        checkDate();
        pruneReminders();
        refreshCurrent(true);
    }

    @Override
    public void statesChanged() {
        if (disposed) {
            return;
        }
        checkDate();
        refreshCurrent(true);
    }

    @Override
    public void storageFailed(String message) {
        ctx.actions().storageError(message);
    }

    /** 일정이 바뀐 뒤, 지워졌거나 완료된 일정의 알림 카드는 치운다 */
    private void pruneReminders() {
        overlay.pruneReminders(o -> ctx.schedules().findOccurrence(o.scheduleId(), o.date())
                .filter(f -> !f.completed()).isPresent());
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
        if (disposed) {
            return;
        }
        overlay.addReminder(occurrence, snoozed, ctx.categories().nameOf(occurrence.categoryId()),
                new ReminderCard.Actions() {
                    @Override
                    public boolean snooze(Occurrence o) {
                        return ctx.actions().snoozeReminder(o, REMINDER_SNOOZE_MINUTES);
                    }

                    @Override
                    public boolean complete(Occurrence o) {
                        return ctx.actions().completeReminder(o);
                    }
                });
        drawAttention();
    }

    /**
     * 알림이 와도 다른 앱의 포커스를 빼앗거나 앞으로 튀어나오지 않는다. 창이 활성 상태가 아니면
     * 독 아이콘/작업 표시줄만 깜박이게 하고, 카드는 창 안에 보여 둔다. 카드는 키보드 포커스를 가져가지 않는다.
     */
    private void drawAttention() {
        try {
            if (UiSettings.reminderSound()) {
                Toolkit.getDefaultToolkit().beep();
            }
            if (!isActive() && Taskbar.isTaskbarSupported()) {
                Taskbar tb = Taskbar.getTaskbar();
                if (tb.isSupported(Taskbar.Feature.USER_ATTENTION)) {
                    tb.requestUserAttention(true, false);
                }
                if (tb.isSupported(Taskbar.Feature.USER_ATTENTION_WINDOW)) {
                    tb.requestWindowUserAttention(this);
                }
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

    /** 스크린샷 도구가 쓰는 서랍 열기/닫기 (애니메이션 없이) */
    public void openDrawerForCapture() {
        openDrawer();
    }

    public void closeDrawerForCapture() {
        drawer.closeNow();
    }

    public UiContext context() {
        return ctx;
    }

    public Overlay overlay() {
        return overlay;
    }
}
