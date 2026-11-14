package proto.ui;

import proto.control.CategoryController;
import proto.control.ReminderTimer;
import proto.control.ScheduleController;
import proto.ui.component.Overlay;
import proto.ui.layout.Breakpoints;
import proto.ui.layout.SizeClass;

import java.awt.Window;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * [경계 boundary] 화면 전체가 함께 쓰는 묶음: 세 controller, 화면 상태, 동작 모음, 알림 층.
 * MainFrame과 모든 보기·대화상자가 생성자로 받아 쓰는 "공용 가방"이다. controller 세 개(ScheduleController, CategoryController, ReminderTimer)와
 * 화면 상태(AppState), 동작 모음(UiActions), 알림 층(Overlay)을 한데 묶어서, 클래스마다 인자를 여러 개 넘기지 않게 한다. 화면은 이것을 통해서만 controller에 닿는다.
 */
public final class UiContext {

    private final ScheduleController schedules;
    private final CategoryController categories;
    private final ReminderTimer reminders;
    private final Path dataDir;
    private final AppState state;
    private Overlay overlay = new Overlay();
    private Supplier<Window> window = () -> null;
    private final Breakpoints breakpoints = new Breakpoints();
    private Runnable refresher = () -> { };
    private UiActions actions;

    public UiContext(ScheduleController schedules, CategoryController categories, ReminderTimer reminders,
                     Path dataDir) {
        this.dataDir = dataDir;
        this.schedules = schedules;
        this.categories = categories;
        this.reminders = reminders;
        this.state = new AppState(schedules.today());
        this.actions = new UiActions(this);
    }

    public ScheduleController schedules() {
        return schedules;
    }

    public CategoryController categories() {
        return categories;
    }

    public ReminderTimer reminders() {
        return reminders;
    }

    /** 데이터가 저장되는 폴더 (설정 화면이 보여 주고 연다) */
    public Path dataDir() {
        return dataDir;
    }

    public AppState state() {
        return state;
    }

    public UiActions actions() {
        return actions;
    }

    /** 화면 크기 등급 계산기. 보기들은 여기서 지금 등급을 읽고 바뀔 때 알림을 받는다 */
    public Breakpoints breakpoints() {
        return breakpoints;
    }

    /** 지금의 크기 등급 */
    public SizeClass size() {
        return breakpoints.current();
    }

    /** 다른 곳에서 일정이 바뀌었을 수 있을 때 현재 보기를 다시 그리게 한다 */
    public void refreshViews() {
        refresher.run();
    }

    public void setRefresher(Runnable refresher) {
        this.refresher = refresher;
    }

    public Overlay overlay() {
        return overlay;
    }

    public void setOverlay(Overlay overlay) {
        this.overlay = overlay;
    }

    /** 대화상자의 부모가 될 메인 창 */
    public Window window() {
        return window.get();
    }

    public void setWindow(Supplier<Window> window) {
        this.window = window;
    }
}
