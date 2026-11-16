package proto.ui;

import proto.control.ScheduleDraft;
import proto.entity.Category;
import proto.entity.Occurrence;
import proto.ui.AppState.Mode;
import proto.ui.command.CommandHost;
import proto.ui.component.OccurrenceHolder;
import proto.ui.theme.ThemeManager;
import proto.ui.theme.ThemeManager.Choice;

import java.awt.KeyboardFocusManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * [경계 boundary] 명령 메뉴(CommandHost)의 실제 구현: 이동은 MainFrame이 넘겨 준 동작으로, 나머지는 UiActions·controller·테마 관리자로 처리한다.
 * MainFrame이 만들어 CommandCatalog에 쓰게 한다. 일정 데이터는 UiActions와 controller만 바꾸고 이 클래스는 요청만 넘긴다 (F1, F2, F7, F12).
 */
final class FrameCommandHost implements CommandHost {

    private final UiContext ctx;
    private final Consumer<Mode> showMode;
    private final Runnable goToday;
    private final Consumer<LocalDate> goToDate;
    private final Consumer<String> filterCategory;
    private Occurrence target;

    FrameCommandHost(UiContext ctx, Consumer<Mode> showMode, Runnable goToday, Consumer<LocalDate> goToDate,
                     Consumer<String> filterCategory) {
        this.ctx = ctx;
        this.showMode = showMode;
        this.goToday = goToday;
        this.goToDate = goToDate;
        this.filterCategory = filterCategory;
    }

    /** 명령 메뉴를 열기 직전에 불러, 그때 포커스가 놓여 있던 일정을 "선택한 일정"으로 기억한다 (메뉴가 열리면 포커스가 검색 칸으로 옮겨가므로) */
    void captureTarget() {
        target = OccurrenceHolder.find(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
    }

    @Override
    public Optional<Occurrence> targetSchedule() {
        return Optional.ofNullable(target);
    }

    @Override
    public void changeColor(Occurrence o, String colorKey) {
        ctx.actions().changeColor(o, colorKey);
    }

    @Override
    public Optional<LocalDate> resolveDate(String text) {
        return ctx.schedules().resolveDate(text);
    }

    @Override
    public LocalDate today() {
        return ctx.schedules().today();
    }

    @Override
    public void showMode(Mode mode) {
        showMode.accept(mode);
    }

    @Override
    public void goToday() {
        goToday.run();
    }

    @Override
    public void goToDate(LocalDate date) {
        goToDate.accept(date);
    }

    /** 새 일정 창. 제목이 있으면 미리 채워서 연다 (날짜는 지금 보이는 날) */
    @Override
    public void newSchedule(String title) {
        if (title == null || title.isBlank()) {
            ctx.actions().newScheduleHere();
            return;
        }
        ScheduleDraft d = ctx.schedules().newDraft(ctx.state().newScheduleDay(today()));
        ctx.actions().openNew(new ScheduleDraft(title.strip(), d.date(), d.startTime(), d.endTime(), d.categoryId(),
                d.memo(), d.repeatRule(), d.repeatEndDate(), d.reminder(), d.allDay(), d.colorKey()));
    }

    @Override
    public void filterCategory(String categoryId) {
        filterCategory.accept(categoryId);
    }

    @Override
    public List<Category> categories() {
        return ctx.categories().getCategories();
    }

    @Override
    public String currentCategory() {
        return ctx.state().categoryId();
    }

    @Override
    public void openFreeSlots() {
        ctx.actions().openFreeSlots();
    }

    @Override
    public void setTheme(Choice choice) {
        ThemeManager.setChoice(choice);
    }

    @Override
    public Choice theme() {
        return ThemeManager.choice();
    }

    @Override
    public void openSettings() {
        ctx.actions().openSettings();
    }

    @Override
    public void showShortcuts() {
        ctx.actions().openShortcutHelp();
    }

    @Override
    public boolean canUndo() {
        return ctx.schedules().canUndo();
    }

    @Override
    public boolean canRedo() {
        return ctx.schedules().canRedo();
    }

    @Override
    public String undoLabel() {
        return ctx.schedules().undoLabel().orElse("");
    }

    @Override
    public String redoLabel() {
        return ctx.schedules().redoLabel().orElse("");
    }

    @Override
    public void undo() {
        ctx.actions().undo();
    }

    @Override
    public void redo() {
        ctx.actions().redo();
    }
}
