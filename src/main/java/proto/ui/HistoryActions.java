package proto.ui;

import proto.control.OperationResult;
import proto.control.ScheduleListener;
import proto.ui.component.ActionToastCard;

import java.util.Optional;

/**
 * [경계 boundary] 되돌리기·다시 실행 동작과 "되돌리기" 버튼이 달린 토스트.
 * UiActions가 맡긴다. 토스트는 자기가 알린 변경 단계의 번호(ScheduleController#undoStepId)를 기억해 둔다.
 * 규칙: 토스트의 [되돌리기]는 "지금 맨 위 되돌리기 단계가 그 토스트가 알린 단계일 때"만 동작한다. 그래서 버튼이 엉뚱한 다른 변경을 되돌리는 일이 없다.
 * 색 바꾸기처럼 화면만 다시 그리는 알림(schedulesChanged)이 와도 맨 위 단계가 그대로면 토스트는 계속 쓸 수 있다.
 * 요청은 모두 ScheduleController#undo, #redo로 하고 결과는 토스트로 알린다.
 */
final class HistoryActions implements ScheduleListener {

    private static final String UNDO_LABEL = "되돌리기";

    private final UiContext ctx;
    private ActionToastCard live;
    private long liveStep;

    HistoryActions(UiContext ctx) {
        this.ctx = ctx;
        ctx.schedules().addListener(this);
    }

    /** 알림 뒤에 맨 위 단계가 토스트가 알린 단계가 아니게 되었으면 그 토스트의 버튼을 끈다 */
    @Override
    public void schedulesChanged() {
        if (live != null && ctx.schedules().undoStepId() != liveStep) {
            live.setActionEnabled(false);
            live = null;
        }
    }

    /** 1분마다의 상태 재계산은 일정을 바꾸지 않으므로 변경으로 세지 않는다 */
    @Override
    public void statesChanged() {
    }

    /** 지금 맨 위 되돌리기 단계의 번호. 동작 직전에 찍어 두었다가 {@link #toastIfRecorded}에 넘긴다 */
    long mark() {
        return ctx.schedules().undoStepId();
    }

    /**
     * 동작이 새 변경 단계를 남겼을 때만(mark와 번호가 다를 때) [되돌리기] 토스트를 띄운다.
     * 아무것도 바뀌지 않은 동작(이미 그 색을 고름 등)은 토스트도 띄우지 않아서, 앞선 다른 변경을 되돌리는 버튼이 생기지 않는다.
     */
    void toastIfRecorded(String message, long mark) {
        if (ctx.schedules().undoStepId() != mark) {
            toastUndoable(message);
        }
    }

    /** 방금 성공한 일정 변경의 확인 토스트에 [되돌리기] 버튼을 붙여 띄운다. 되돌릴 기록이 없으면 일반 토스트 */
    void toastUndoable(String message) {
        long step = ctx.schedules().undoStepId();
        if (step == 0) {
            ctx.actions().toast(message);
            return;
        }
        liveStep = step;
        live = ctx.overlay().showActionToast(message, UNDO_LABEL, () -> undoStep(step));
    }

    private void undoStep(long step) {
        if (step != ctx.schedules().undoStepId()) {
            ctx.overlay().showNotice("그 뒤에 다른 변경이 있어 되돌릴 수 없어요");
            return;
        }
        undo();
    }

    /** 마지막 변경을 되돌린다 (Ctrl/⌘+Z) */
    void undo() {
        Optional<String> label = ctx.schedules().undoLabel();
        OperationResult r = ctx.schedules().undo();
        report(r, "되돌렸어요", label);
    }

    /** 되돌린 변경을 다시 실행한다 (Ctrl/⌘+Shift+Z, Windows는 Ctrl+Y도) */
    void redo() {
        Optional<String> label = ctx.schedules().redoLabel();
        OperationResult r = ctx.schedules().redo();
        report(r, "다시 실행했어요", label);
    }

    private void report(OperationResult r, String done, Optional<String> label) {
        if (r.success()) {
            ctx.actions().toast(label.map(l -> done + " · " + l).orElse(done));
        } else {
            ctx.overlay().showNotice(r.message());
        }
    }
}
