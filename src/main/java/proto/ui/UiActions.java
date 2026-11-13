package proto.ui;

import proto.control.EditScope;
import proto.control.OperationResult;
import proto.control.ScheduleDraft;
import proto.entity.Occurrence;
import proto.entity.ScheduleState;
import proto.ui.component.RowHandler;
import proto.ui.dialog.CategoryDialog;
import proto.ui.dialog.Dialogs;
import proto.ui.dialog.FreeSlotDialog;
import proto.ui.dialog.ScheduleDialog;
import proto.ui.theme.Icons;
import proto.ui.theme.Tokens;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.Component;
import java.awt.Point;
import java.time.LocalDate;
import java.util.Optional;

/**
 * [경계 boundary] 화면에서 일어나는 동작 모음: 새 일정, 수정, 삭제, 완료, 메뉴, 카테고리·빈 시간 창 열기.
 * 모든 요청은 controller를 거치고, 결과는 토스트나 대화상자로 알린다.
 */
public final class UiActions implements RowHandler {

    private final UiContext ctx;

    UiActions(UiContext ctx) {
        this.ctx = ctx;
    }

    /** 짧은 확인 토스트 */
    public void toast(String message) {
        ctx.overlay().showToast(message, false);
    }

    /** 닫을 때까지 남는 오류 토스트 */
    public void error(String message) {
        ctx.overlay().showToast(message, true);
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
        new ScheduleDialog(ctx, o, ctx.schedules().draftOf(o)).showDialog();
    }

    /** 삭제 확인 → (반복이면 범위 선택) → 삭제. 실제로 지웠으면 true */
    @Override
    public boolean delete(Occurrence o) {
        EditScope scope = EditScope.THIS_AND_FOLLOWING;
        if (o.isRepeating()) {
            Optional<EditScope> chosen = Dialogs.chooseScope(ctx.window(), "반복 일정 삭제", "삭제", true);
            if (chosen.isEmpty()) {
                return false;
            }
            scope = chosen.get();
        } else if (!Dialogs.confirmDelete(ctx.window(), o.title())) {
            return false;
        }
        OperationResult r = ctx.schedules().delete(o, scope);
        if (r.success()) {
            toast("일정을 삭제했어요");
        } else {
            error(r.message());
        }
        return r.success();
    }

    @Override
    public void toggleDone(Occurrence o) {
        boolean next = !o.completed();
        OperationResult r = ctx.schedules().setCompleted(o, next);
        if (r.success()) {
            toast(next ? "일정을 완료했어요" : "완료를 취소했어요");
        } else {
            error(r.message());
        }
    }

    @Override
    public void showMenu(Component source, Point p, Occurrence o) {
        JPopupMenu menu = new JPopupMenu();
        menu.add(item(o.completed() ? "완료 취소" : "완료", "check", () -> toggleDone(o)));
        menu.add(item("수정", "pencil", () -> open(o)));
        menu.addSeparator();
        menu.add(item("삭제", "trash", () -> delete(o)));
        menu.show(source, p.x, p.y);
    }

    private static JMenuItem item(String text, String icon, Runnable action) {
        JMenuItem i = new JMenuItem(text, Icons.of(icon, 16, Tokens.MUTED));
        i.setIconTextGap(10);
        i.addActionListener(e -> action.run());
        return i;
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
}
