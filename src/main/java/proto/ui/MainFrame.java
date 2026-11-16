package proto.ui;

import proto.control.CategoryListener;
import proto.control.ReminderListener;
import proto.control.ScheduleListener;
import proto.entity.Category;
import proto.entity.Occurrence;
import proto.ui.AppState.Mode;
import proto.ui.command.CommandCatalog;
import proto.ui.command.PaletteLayer;
import proto.ui.command.PlainKey;
import proto.ui.command.PlainKeys;
import proto.ui.component.Overlay;
import proto.ui.component.ReminderCard;
import proto.ui.component.TokenPanel;
import proto.ui.component.TransitionHost;
import proto.ui.component.TransitionHost.Dir;
import proto.ui.layout.SizeClass;
import proto.ui.layout.WindowState;
import proto.ui.sheet.SheetHost;
import proto.ui.theme.AppIcon;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;
import proto.ui.view.ContentView;
import proto.ui.view.MonthView;
import proto.ui.view.StatsView;
import proto.ui.view.TodayView;
import proto.ui.view.WeekView;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.Taskbar;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
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
 * [경계 boundary] 메인 창: 사이드바 + 제목줄 + 보기 영역을 한 창에 조립하고, controller의 변화 알림(일정·카테고리·알림)에 맞춰 화면을 다시 그린다.
 * UiLauncher가 만들고 attachListeners로 controller에 연결한다. 화면에서 일어난 일은 ScheduleController 등에 요청하고,
 * 결과는 ScheduleListener·CategoryListener·ReminderListener 메서드로 돌아온다 (F2, F6, F10).
 * "ui는 controller만 호출하고, controller는 리스너로 ui에 알린다"는 규칙이 이 클래스에서 만난다.
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
    private SheetHost sheets;
    private PaletteLayer palette;
    private FrameCommandHost commandHost;
    private PlainKeys plainKeys;
    private final CommandCatalog commands;
    private final WindowState windowState = new WindowState();
    private final Timer saveBounds = new Timer(600, e -> windowState.save(this));
    private final PropertyChangeListener lafListener = e -> {
        if ("lookAndFeel".equals(e.getPropertyName())) {
            refreshHiddenViews();
        }
    };
    // 지금 보이는 보기. 보기 4개는 처음에 다 만들어 두고 전환할 때 current만 바꾼다.
    private ContentView current;
    private LocalDate knownToday;
    private boolean disposed;

    public MainFrame(UiContext ctx) {
        super("일정 관리");
        this.ctx = ctx;
        ctx.setWindow(() -> this);
        ctx.setOverlay(overlay);
        ctx.setRefresher(() -> refreshCurrent(false));
        createViews();
        configureWindow();
        // 창 크기 등급(SizeClass)은 사이드바·헤더·보기를 만들기 전에 정해 두어야 처음부터 알맞은 모양으로 그려진다.
        ctx.breakpoints().prime(getWidth(), getHeight());
        drawer = new SidebarDrawer(() -> rail().focusMenu());
        sidebar = new Sidebar(ctx, this::navigateFromSidebar, this::selectCategory, this::goToDate, drawer::closeNow,
                this::toggleSidebar);
        rail = new SidebarRail(ctx, this::navigate, this::openDrawer, this::toggleSidebar);
        sidebarHost = new SidebarHost(sidebar, rail, ctx.size());
        header = new HeaderBar(() -> shift(-1), this::goToday, () -> shift(1), this::search, this::openCommandMenu,
                this::toggleSidebar);
        commandHost = new FrameCommandHost(ctx, this::navigate, this::goToday, this::goToDate, this::selectCategory,
                this::toggleSidebar);
        commands = new CommandCatalog(commandHost);
        buildLayout();
        installShortcuts();
        knownToday = ctx.schedules().today();
        ctx.breakpoints().attach(getRootPane());
        ctx.breakpoints().onChange(sc -> applySize(sc, isShowing()));
        applySize(ctx.size(), false);
        restoreSidebarHidden();
        switchTo(UiSettings.startMode(), false);
        UIManager.addPropertyChangeListener(lafListener);
        saveBounds.setRepeats(false);
    }

    private void createViews() {
        views.put(Mode.TODAY, new TodayView(ctx));
        views.put(Mode.WEEK, new WeekView(ctx));
        views.put(Mode.MONTH, new MonthView(ctx));
        views.put(Mode.STATS, new StatsView(ctx));
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
            UIManager.removePropertyChangeListener(lafListener);
            if (plainKeys != null) {
                plainKeys.uninstall();
            }
            saveBounds.stop();
            header.dispose();
            drawer.closeNow();
            overlay.clearAll();
        }
        super.dispose();
    }

    /**
     * 날짜가 바뀌었으면(자정을 넘기거나 절전에서 깨어남) "오늘" 표시들을 새 날짜로 맞춘다.
     * 왜 필요한가: 창을 열어 둔 채 자정을 넘기거나 노트북이 절전에서 깨어나면 "오늘"이 어제 날짜로 남는다. 창이 다시 활성화될 때와 화면이 갱신될 때 확인한다.
     */
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

    @Override
    public void schedulesChanged() {
        if (disposed) {
            return;
        }
        checkDate();
        pruneReminders();
        sidebar.refreshMonth();
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

    @Override
    public void categoriesChanged() {
        String selected = ctx.state().categoryId();
        boolean stillThere = selected == null
                || ctx.categories().getCategories().stream().map(Category::getId).anyMatch(selected::equals);
        if (!stillThere) {
            ctx.state().setCategoryId(null);
        }
        sidebar.refreshCategories();
        sidebar.refreshMonth();
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

    /** 스크린샷 도구가 쓰는 사이드바 숨기기/보이기. 사용자 설정(UiSettings)에는 남기지 않는다 */
    public void setSidebarHiddenForCapture(boolean hidden) {
        sidebarHost.setHidden(hidden, false);
        header.setSidebarHidden(hidden);
    }

    public UiContext context() {
        return ctx;
    }

    public Overlay overlay() {
        return overlay;
    }

    // 서랍은 띠(rail)보다 먼저 만들어서, 생성 중에는 rail 필드를 직접 쓸 수 없다. 나중에 불릴 때 읽도록 메서드로 감쌌다.
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

    /** 저장해 둔 "사이드바 숨김"을 처음 화면에 맞춘다 (애니메이션 없이) */
    private void restoreSidebarHidden() {
        boolean hidden = UiSettings.sidebarHidden();
        sidebarHost.setHidden(hidden, false);
        header.setSidebarHidden(hidden);
    }

    /**
     * 사이드바를 숨기거나 다시 보인다 (⌘\, 사이드바의 숨기기 버튼, 위쪽 줄의 보이기 버튼, 명령 메뉴). 다음 실행 때도 기억한다.
     * 사이드바 안에 포커스가 있었으면 위쪽 줄의 보이기 버튼으로 옮겨서 키보드 사용자가 길을 잃지 않게 한다
     */
    private void toggleSidebar() {
        boolean hide = !sidebarHost.isHidden();
        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        boolean focusInside = focus != null && (SwingUtilities.isDescendingFrom(focus, sidebarHost)
                || SwingUtilities.isDescendingFrom(focus, sidebar));
        drawer.closeNow();
        UiSettings.setSidebarHidden(hide);
        sidebarHost.setHidden(hide, true);
        header.setSidebarHidden(hide);
        if (hide && focusInside) {
            header.focusSidebarButton();
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
                .forEach(v -> SwingUtilities.updateComponentTreeUI(v.component()));
        if (sidebar.getParent() == null) {
            SwingUtilities.updateComponentTreeUI(sidebar);
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
        // macOS는 제목 줄을 투명하게 하고 내용이 창 전체를 쓰게 한다 (신호등 버튼 아래까지 사이드바 배경이 이어진다). 그래서 위쪽 여백이 더 크다.
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
        // 시트(대화상자·설정)는 서랍 위, 토스트·알림 카드 아래에 뜬다. 그래서 시트가 열려 있어도 알림 카드는 보이고 누를 수 있다
        sheets = SheetHost.install(layers);
        palette = PaletteLayer.install(layers);
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
        installBoundsListener();
        installFocusAndCloseListener();
    }

    private void installBoundsListener() {
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
    }

    private void installFocusAndCloseListener() {
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

    // ---- 이동 ----

    /** 다른 보기로 이동한다. 이동하면 기준 날짜와 선택 날짜는 오늘로 돌아온다. 같은 보기를 다시 누르면 아무것도 하지 않는다. */
    private void navigate(Mode mode) {
        checkDate();
        if (mode == ctx.state().mode()) {
            return;
        }
        LocalDate today = ctx.schedules().today();
        ctx.state().setAnchor(today);
        ctx.state().setSelectedDay(today);
        host.transition(Dir.toward(ctx.state().mode().ordinal(), mode.ordinal()), () -> switchTo(mode, true),
                () -> current.shown());
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

    /** 위쪽 줄(제목·버튼)과 사이드바 달력(오늘·고른 날·주 띠)을 지금 보기에 맞춘다 */
    private void updateHeader() {
        sidebar.syncMonth();
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
        if (today.equals(s.anchor()) && today.equals(s.selectedDay())) {
            return;
        }
        s.setSelectedDay(today);
        moveAnchor(today, today.isAfter(s.anchor()) ? Dir.FROM_RIGHT : Dir.FROM_LEFT);
    }

    /** 이전/다음/오늘 버튼으로 기준 날짜를 옮긴다. 달력(MONTH)에서 달이 바뀌면 선택한 날도 새 달 안으로 옮겨야 한다. 이번 달이면 오늘, 다른 달이면 그 달 1일을 고른다. */
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

    /**
     * 그 날짜로 이동한다 (사이드바 달력, 명령 메뉴의 "날짜로 이동"). 오늘 보기는 하루만 보여 주므로 이번 주(주 보기)로 바꿔서 그 날을 고르고,
     * 주·달력·통계 보기는 보기를 바꾸지 않고 그 날이 든 주/달로 옮긴다(통계는 그 주의 통계). 같은 주/달 안이면 미끄러지지 않고 고른 날만 바뀐다.
     */
    private void goToDate(LocalDate day) {
        checkDate();
        AppState s = ctx.state();
        Mode from = s.mode();
        Mode target = from == Mode.TODAY ? Mode.WEEK : from;
        if (from == Mode.TODAY && day.equals(ctx.schedules().today())) {
            return;
        }
        LocalDate old = s.anchor();
        boolean samePeriod = target == from && samePeriod(target, old, day);
        s.setAnchor(day);
        s.setSelectedDay(day);
        if (target != from) {
            host.transition(Dir.toward(from.ordinal(), target.ordinal()), () -> switchTo(target, true),
                    () -> current.shown());
        } else if (samePeriod) {
            refreshCurrent(false);
        } else {
            host.transition(day.isAfter(old) ? Dir.FROM_RIGHT : Dir.FROM_LEFT, () -> {
                current.refresh(false);
                updateHeader();
            }, () -> current.shown());
        }
    }

    /** 두 날짜가 같은 달(달력) 또는 같은 주(주·통계)에 드는지 */
    private static boolean samePeriod(Mode mode, LocalDate a, LocalDate b) {
        if (mode == Mode.MONTH) {
            return YearMonth.from(a).equals(YearMonth.from(b));
        }
        return a.minusDays(a.getDayOfWeek().getValue() - 1L).equals(b.minusDays(b.getDayOfWeek().getValue() - 1L));
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
        bind(KeyEvent.VK_N, mask, () -> ctx.actions().newScheduleHere());
        bind(KeyEvent.VK_K, mask, this::openCommandMenu);
        bind(KeyEvent.VK_F, mask, header::focusSearch);
        bind(KeyEvent.VK_1, mask, () -> navigate(Mode.TODAY));
        bind(KeyEvent.VK_2, mask, () -> navigate(Mode.WEEK));
        bind(KeyEvent.VK_3, mask, () -> navigate(Mode.MONTH));
        bind(KeyEvent.VK_4, mask, () -> navigate(Mode.STATS));
        bind(KeyEvent.VK_COMMA, mask, () -> ctx.actions().openSettings());
        bind(KeyEvent.VK_BACK_SLASH, mask, this::toggleSidebar);
        new EditShortcuts(ctx, getRootPane(), this::keysFree).install();
        installPlainKeys();
    }

    /** 한 글자 단축키 (C 새 일정, T 오늘, W/M/D 보기, ←/→ 이전·다음, ? 단축키 안내). 시트·명령 메뉴가 열려 있거나 글을 입력하는 중에는 꺼진다 */
    private void installPlainKeys() {
        Map<PlainKey, Runnable> actions = new EnumMap<>(PlainKey.class);
        actions.put(PlainKey.NEW, () -> ctx.actions().newScheduleHere());
        actions.put(PlainKey.TODAY, () -> {
            if (current.hasNavigator()) {
                goToday();
            }
        });
        actions.put(PlainKey.WEEK, () -> navigate(Mode.WEEK));
        actions.put(PlainKey.MONTH, () -> navigate(Mode.MONTH));
        actions.put(PlainKey.DAY, () -> navigate(Mode.TODAY));
        actions.put(PlainKey.PREVIOUS, () -> shiftIfPossible(-1));
        actions.put(PlainKey.NEXT, () -> shiftIfPossible(1));
        actions.put(PlainKey.HELP, () -> ctx.actions().openShortcutHelp());
        plainKeys = new PlainKeys(this, () -> isActive() && keysFree(), actions);
        plainKeys.install();
    }

    private void shiftIfPossible(int direction) {
        if (current.hasNavigator()) {
            shift(direction);
        }
    }

    /** 시트도 명령 메뉴도 열려 있지 않아 창 단축키가 동작해도 되는지 */
    private boolean keysFree() {
        return !sheets.isOpen() && !palette.isOpen();
    }

    /** 명령 메뉴(⌘K)를 열거나 닫는다. 시트가 열려 있으면 열지 않는다 */
    private void openCommandMenu() {
        if (palette.isOpen()) {
            palette.close();
        } else if (!sheets.isOpen()) {
            drawer.closeNow();
            commandHost.captureTarget();
            palette.open(commands);
        }
    }

    /** 창 단축키를 단다. 시트가 열려 있는 동안에는 꺼져서(isEnabled) 뒤 화면에 닿지 않고 키는 시트 안의 칸이 그대로 받는다 */
    private void bind(int key, int modifiers, Runnable action) {
        String name = "proto-" + key;
        JComponent root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, modifiers), name);
        root.getActionMap().put(name, new AbstractAction() {
            @Override
            public boolean isEnabled() {
                return keysFree();
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    /** 일정이 바뀐 뒤, 지워졌거나 완료됐거나 하루 종일로 바뀐(알림 제외 대상) 일정의 알림 카드는 치운다 */
    private void pruneReminders() {
        overlay.pruneReminders(ctx.actions()::reminderStillActive);
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
}
