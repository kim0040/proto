package proto.ui;

import proto.control.CategoryController;
import proto.control.ReminderTimer;
import proto.control.ScheduleController;
import proto.ui.component.Overlay;

import java.awt.Window;
import java.util.function.Supplier;

/**
 * [경계 boundary] 화면 전체가 함께 쓰는 묶음: 세 controller, 화면 상태, 동작 모음, 알림 층.
 */
public final class UiContext {

    private final ScheduleController schedules;
    private final CategoryController categories;
    private final ReminderTimer reminders;
    private final AppState state;
    private Overlay overlay = new Overlay();
    private Supplier<Window> window = () -> null;
    private UiActions actions;

    public UiContext(ScheduleController schedules, CategoryController categories, ReminderTimer reminders) {
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

    public AppState state() {
        return state;
    }

    public UiActions actions() {
        return actions;
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
