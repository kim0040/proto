package proto.ui;

import proto.control.EditScope;
import proto.control.OperationResult;
import proto.control.ScheduleDraft;
import proto.entity.Occurrence;
import proto.entity.ScheduleState;
import proto.ui.command.ShortcutHelpDialog;
import proto.ui.component.RowHandler;
import proto.ui.dialog.CategoryDialog;
import proto.ui.dialog.ColorMenu;
import proto.ui.dialog.Dialogs;
import proto.ui.dialog.FreeSlotDialog;
import proto.ui.dialog.ScheduleDialog;
import proto.ui.dialog.SettingsSheet;
import proto.ui.theme.CategoryColors;
import proto.ui.theme.Icons;
import proto.ui.theme.Tokens;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import java.awt.Component;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.time.LocalDate;
import java.util.Optional;

/**
 * [경계 boundary] 화면에서 일어나는 동작 모음: 새 일정, 수정, 삭제, 완료, 복제·복사·붙여넣기, 미루기, 되돌리기, 메뉴, 카테고리·빈 시간·설정 시트 열기.
 * 일정 줄, 알림 카드, 사이드바 어디서 동작을 시작하든 여기를 거친다. 모든 요청은 ScheduleController를 거치고, 결과는 토스트나 대화상자로 알린다.
 * 동작 직전에 항상 findOccurrence로 최신 값을 다시 확인해서(fresh), 이미 지워진 일정을 다루지 않게 한다 (F3, F4, F5, F10, F15).
 */
public final class UiActions implements RowHandler {

    private static final String GONE = "이미 지워진 일정이에요";
    private static final String GONE_OR_DONE = "이미 지워졌거나 완료한 일정이에요";
    private static final String ALL_DAY_NO_REMINDER = "하루 종일 일정은 알림 대상이 아니에요";

    private final UiContext ctx;
    private final ScheduleMover mover;
    private final HistoryActions history;
    private final ScheduleClipboard clipboard = new ScheduleClipboard();
    private boolean busy;

    UiActions(UiContext ctx) {
        this.ctx = ctx;
        this.mover = new ScheduleMover(ctx);
        this.history = new HistoryActions(ctx);
    }

    /** 일정 옮기기 (끌기·키보드·미루기가 함께 쓴다) */
    public ScheduleMover mover() {
        return mover;
    }

    /** 성공한 일정 변경의 확인 토스트. "되돌리기" 버튼이 붙고, 그 사이 다른 변경이 있으면 버튼이 꺼진다 */
    public void toastUndoable(String message) {
        history.toastUndoable(message);
    }

    /** 마지막 일정 변경을 되돌린다 (Ctrl/⌘+Z) */
    public void undo() {
        history.undo();
    }

    /** 되돌린 변경을 다시 실행한다 (Ctrl/⌘+Shift+Z, Ctrl+Y) */
    public void redo() {
        history.redo();
    }

    /** 짧은 확인 토스트 */
    public void toast(String message) {
        ctx.overlay().showToast(message, false);
    }

    /** 닫을 때까지 남는 오류 토스트 */
    public void error(String message) {
        ctx.overlay().showToast(message, true);
    }

    /** 저장소 오류: 닫을 때까지 남는 알림에 "자세히" 버튼으로 전체 문장을 보여 준다 */
    public void storageError(String message) {
        String detail = message == null || message.isBlank() ? "알 수 없는 오류예요" : message;
        ctx.overlay().showDetailToast("저장하는 중 문제가 생겼어요", "자세히",
                () -> Dialogs.inform(ctx.window(), "저장 오류", detail));
    }

    /**
     * 일정을 다루기 전에 최신 상태를 다시 읽는다. 다른 곳(알림 카드, 다른 보기)에서 이미 지워졌으면
     * 안내 토스트를 띄우고 화면을 새로 고친 뒤 빈 값을 돌려준다.
     */
    public Optional<Occurrence> fresh(Occurrence o) {
        Optional<Occurrence> latest = ctx.schedules().findOccurrence(o.scheduleId(), o.date());
        if (latest.isEmpty()) {
            toast(GONE);
            ctx.refreshViews();
        }
        return latest;
    }

    /**
     * 알림 카드를 계속 보여 줘도 되는 일정인지. 지워졌거나 완료됐거나 "하루 종일"로 바뀌었으면(하루 종일은 알림 대상이 아니다) false.
     * MainFrame이 일정이 바뀔 때마다 이 값으로 열려 있는 알림 카드를 치운다.
     */
    public boolean reminderStillActive(Occurrence o) {
        return ctx.schedules().findOccurrence(o.scheduleId(), o.date())
                .filter(f -> !f.completed() && !f.allDay()).isPresent();
    }

    /** 알림 카드의 [5분 뒤 다시]. 카드를 닫아도 되면 true (지워졌거나 완료된 일정, 하루 종일로 바뀐 일정이면 안내 후 true) */
    public boolean snoozeReminder(Occurrence o, int minutes) {
        Optional<Occurrence> latest = ctx.schedules().findOccurrence(o.scheduleId(), o.date());
        if (latest.isEmpty() || latest.get().completed() || latest.get().allDay()) {
            toast(latest.isPresent() && latest.get().allDay() ? ALL_DAY_NO_REMINDER : GONE_OR_DONE);
            ctx.refreshViews();
            return true;
        }
        OperationResult r = ctx.reminders().snooze(latest.get(), minutes);
        if (r.success()) {
            toast(minutes + "분 뒤에 다시 알려 드려요");
        } else {
            error(r.message());
        }
        return r.success();
    }

    /** 알림 카드의 [완료 처리]. 카드를 닫아도 되면 true */
    public boolean completeReminder(Occurrence o) {
        Optional<Occurrence> latest = ctx.schedules().findOccurrence(o.scheduleId(), o.date());
        if (latest.isEmpty() || latest.get().completed()) {
            toast(GONE_OR_DONE);
            ctx.refreshViews();
            return true;
        }
        OperationResult r = ctx.schedules().setCompleted(latest.get(), true);
        if (r.success()) {
            toastUndoable("일정을 완료했어요");
        } else {
            error(r.message());
        }
        return r.success();
    }

    /** 지금 보고 있는 화면에 알맞은 날짜로 새 일정 창을 연다 (N, C, 새 일정 버튼) */
    public void newScheduleHere() {
        newSchedule(ctx.state().newScheduleDay(ctx.schedules().today()));
    }

    /** 새 일정 창 (날짜를 미리 채움) */
    public void newSchedule(LocalDate date) {
        openNew(ctx.schedules().newDraft(date));
    }

    /** 이미 채워진 값으로 새 일정 창을 연다 (빈 시간 선택 등) */
    public void openNew(ScheduleDraft draft) {
        new ScheduleDialog(ctx, null, draft).showDialog();
    }

    @Override
    public void open(Occurrence o) {
        Optional<Occurrence> latest = fresh(o);
        latest.ifPresent(f -> new ScheduleDialog(ctx, f, ctx.schedules().draftOf(f)).showDialog());
    }

    /**
     * (반복이면 범위 선택) → 삭제 → "되돌리기" 토스트. 실제로 지웠으면 true.
     * 반복 없는 일정은 묻지 않고 바로 지운다: 토스트의 [되돌리기]나 Ctrl/⌘+Z로 언제든 되살릴 수 있어서, 확인 창은 손만 한 번 더 쓰게 한다.
     */
    @Override
    public boolean delete(Occurrence o) {
        // 범위 선택 시트가 열려 있는 동안 같은 삭제가 다시 시작되지 않게 막는다 (키를 연달아 눌렀을 때).
        if (busy) {
            return false;
        }
        busy = true;
        try {
            return deleteFresh(o);
        } finally {
            busy = false;
        }
    }

    @Override
    public void toggleDone(Occurrence o) {
        Optional<Occurrence> latest = fresh(o);
        if (latest.isEmpty()) {
            return;
        }
        boolean next = !latest.get().completed();
        OperationResult r = ctx.schedules().setCompleted(latest.get(), next);
        if (r.success()) {
            toastUndoable(next ? "일정을 완료했어요" : "완료를 취소했어요");
        } else {
            error(r.message());
        }
    }

    @Override
    public boolean nudge(Occurrence o, int days, int minutes) {
        return mover.nudge(o, days, minutes);
    }

    /** 내일로·다음 주로 미루기. 반복 일정이면 범위를 먼저 묻는다 */
    public void postpone(Occurrence o, int days, String done) {
        mover.nudge(o, days, 0, done);
    }

    /**
     * 일정만의 색을 바꾼다 (key가 null이면 카테고리 색으로 되돌린다). 반복 일정이면 시리즈 전체가 바뀐다 (색은 시리즈 하나에 하나).
     * 되돌리기로 이전 색을 돌려받을 수 있다. 이미 그 색이면 아무것도 바뀌지 않으므로 토스트도 띄우지 않는다 (앞선 다른 변경을 되돌리는 버튼이 생기지 않게).
     */
    public void changeColor(Occurrence o, String key) {
        fresh(o).ifPresent(f -> {
            long mark = history.mark();
            OperationResult r = ctx.schedules().setColor(f, key);
            if (r.success()) {
                history.toastIfRecorded(key == null ? "카테고리 색을 따르도록 했어요"
                        : "색을 " + ColorMenu.nameOf(key) + "(으)로 바꿨어요", mark);
            } else {
                error(r.message());
            }
        });
    }

    /** 같은 날 같은 시간에 반복 없는 복사본을 하나 더 만든다 */
    public void duplicate(Occurrence o) {
        fresh(o).ifPresent(f -> createCopy(f, f.date(), "일정을 복제했어요"));
    }

    /** 앱 안 복사 보관함에 일정을 담는다 (Ctrl/⌘+C) */
    public void copy(Occurrence o) {
        fresh(o).ifPresent(f -> {
            clipboard.set(f);
            toast("일정을 복사했어요");
        });
    }

    /** 복사해 둔 일정을 지금 보기의 날짜에 붙여 넣는다 (Ctrl/⌘+V). 복사한 게 없거나 통계 보기면 아무것도 하지 않는다 */
    public void paste() {
        AppState s = ctx.state();
        Optional<LocalDate> target = ScheduleClipboard.pasteTarget(s.mode(), ctx.schedules().today(),
                s.selectedDay(), s.weekStart());
        if (clipboard.content().isEmpty() || target.isEmpty()) {
            return;
        }
        Occurrence source = clipboard.content().get();
        createCopy(source, target.get(), "‘" + Dialogs.shorten(source.title()) + "’을(를) "
                + Fmt.monthDay(target.get()) + "에 붙여 넣었어요");
    }

    @Override
    public void showMenu(Component source, Point p, Occurrence o) {
        JPopupMenu menu = new JPopupMenu();
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        menu.add(item(o.completed() ? "완료 취소" : "완료", "check", null, () -> toggleDone(o)));
        menu.add(item("수정", "pencil", null, () -> open(o)));
        menu.add(item("복제", "copy-plus", null, () -> duplicate(o)));
        menu.add(item("복사", "copy", KeyStroke.getKeyStroke(KeyEvent.VK_C, mask), () -> copy(o)));
        menu.add(ColorMenu.submenu(o.colorKey(), () -> CategoryColors.of(o.categoryId()), key -> changeColor(o, key)));
        menu.addSeparator();
        menu.add(item("내일로 미루기", "arrow-right", null, () -> postpone(o, 1, "내일로 미뤘어요")));
        menu.add(item("다음 주로 미루기", "chevrons-right", null, () -> postpone(o, 7, "다음 주로 미뤘어요")));
        menu.addSeparator();
        menu.add(item("삭제", "trash", KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), () -> delete(o)));
        menu.show(source, p.x, p.y);
    }

    /** 단축키 안내 시트 (? 키, 명령 메뉴의 "단축키 보기") */
    public void openShortcutHelp() {
        new ShortcutHelpDialog(ctx).showDialog();
    }

    /** 설정 시트. 지금 보기 위에 뜨고, 닫으면 보기는 그대로이다 */
    public void openSettings() {
        new SettingsSheet(ctx).showDialog();
    }

    /** 카테고리 관리 창 */
    public void openCategories() {
        new CategoryDialog(ctx).showDialog();
    }

    /** 빈 시간 찾기 창. 구간을 고르면 그 시간으로 새 일정 창을 이어서 연다 */
    public void openFreeSlots() {
        FreeSlotDialog d = new FreeSlotDialog(ctx);
        d.showDialog();
        if (d.chosenSlot() != null) {
            openNew(d.draft());
        }
    }

    @Override
    public ScheduleState stateOf(Occurrence o) {
        return ctx.schedules().stateOf(o);
    }

    @Override
    public String categoryName(String categoryId) {
        return ctx.categories().nameOf(categoryId);
    }

    private static JMenuItem item(String text, String icon, KeyStroke accelerator, Runnable action) {
        JMenuItem menuItem = new JMenuItem(text, Icons.of(icon, 16, Tokens.MUTED));
        menuItem.setIconTextGap(10);
        menuItem.setAccelerator(accelerator);
        menuItem.addActionListener(e -> action.run());
        return menuItem;
    }

    private boolean deleteFresh(Occurrence o) {
        Optional<Occurrence> latest = fresh(o);
        if (latest.isEmpty()) {
            return true;
        }
        Occurrence target = latest.get();
        EditScope scope = EditScope.THIS_AND_FOLLOWING;
        if (target.isRepeating()) {
            Optional<EditScope> chosen = Dialogs.chooseScope(ctx.window(), "반복 일정 삭제", "삭제", true);
            if (chosen.isEmpty()) {
                return false;
            }
            scope = chosen.get();
        }
        OperationResult r = ctx.schedules().delete(target, scope);
        if (r.success()) {
            toastUndoable("일정을 삭제했어요");
        } else {
            error(r.message());
        }
        return r.success();
    }

    private void createCopy(Occurrence source, LocalDate date, String done) {
        OperationResult r = ctx.schedules().create(ctx.schedules().copyDraft(source, date));
        if (r.success()) {
            toastUndoable(done);
        } else {
            error(r.errors().isEmpty() ? r.message() : r.errors().get(0));
        }
    }
}
