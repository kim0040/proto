package proto.ui;

import proto.control.EditScope;
import proto.control.OperationResult;
import proto.control.ScheduleDraft;
import proto.entity.Occurrence;
import proto.ui.component.TimeSnap;
import proto.ui.dialog.Dialogs;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

/**
 * [경계 boundary] 일정의 날짜·시간만 바꿔 옮기는 동작 (끌어서 옮기기, 크기 바꾸기, 키보드 이동, 미루기). 시간표 말고 다른 보기에서도 쓰려고 따로 두었다 (F3).
 * 최신 값을 다시 읽어(fresh) 날짜·시작·끝만 갈아 끼운 입력값을 만들어 ScheduleController#update에 맡긴다. 반복 일정이면 범위(이 일정만/앞으로 전부)를 먼저 묻는다.
 * 겹치는 일정이 있어도 막지 않고 알림 글에만 덧붙인다. 성공 알림에는 "되돌리기" 버튼이 붙고, 바뀐 게 없으면 아무것도 하지 않는다.
 * 하루 종일 일정은 날짜만 바뀐다: 넘어온 시작·끝은 무시하고 시간 방향 미세 이동(Alt+↑/↓)은 하지 않으며, 하루 종일 표시(allDay)는 그대로 남는다.
 */
public final class ScheduleMover {

    private final UiContext ctx;

    public ScheduleMover(UiContext ctx) {
        this.ctx = ctx;
    }

    /**
     * 일정을 새 날짜·시간으로 옮긴다. 실제로 바뀌었으면 true (취소하거나 바뀐 게 없거나 실패하면 false).
     * 날짜와 시작이 그대로이고 끝만 달라졌으면 "크기 바꾸기"로 보고 알림 글을 달리한다.
     */
    public boolean move(Occurrence o, LocalDate date, LocalTime start, LocalTime end) {
        return move(o, date, start, end, null);
    }

    /**
     * 날짜를 days일, 시간을 minutes분 밀어 옮긴다 (Alt+방향키). 시간은 하루 안(00:00~24:00)에서 멈춘다.
     * 같은 방법(move)으로 옮기므로 확인·알림도 같다.
     */
    public boolean nudge(Occurrence o, int days, int minutes) {
        return nudge(o, days, minutes, null);
    }

    /** nudge와 같지만 성공 알림 글을 done으로 바꾼다 (내일로·다음 주로 미루기) */
    public boolean nudge(Occurrence o, int days, int minutes, String done) {
        Optional<Occurrence> fresh = ctx.actions().fresh(o);
        if (fresh.isEmpty()) {
            return false;
        }
        Occurrence latest = fresh.get();
        if (latest.allDay()) {
            return days != 0 && move(latest, latest.date().plusDays(days), latest.startTime(), latest.endTime(), done);
        }
        TimeSnap.Range r = TimeSnap.shifted(TimeSnap.minuteOf(latest.startTime()),
                TimeSnap.endMinuteOf(latest.endTime()), minutes);
        return move(latest, latest.date().plusDays(days), TimeSnap.timeOf(r.start()), TimeSnap.timeOf(r.end()), done);
    }

    private static boolean isResize(Occurrence latest, LocalDate date, LocalTime start) {
        return latest.date().equals(date) && latest.startTime().equals(start);
    }

    private static ScheduleDraft withSlot(ScheduleDraft d, LocalDate date, LocalTime start, LocalTime end) {
        return new ScheduleDraft(d.title(), date, start, end, d.categoryId(), d.memo(), d.repeatRule(),
                d.repeatEndDate(), d.reminder(), d.allDay(), d.colorKey());
    }

    private Optional<EditScope> chooseScope(Occurrence latest) {
        if (latest.isRepeating()) {
            return Dialogs.chooseScope(ctx.window(), "반복 일정 이동", "이동", false);
        }
        return Optional.of(EditScope.THIS_AND_FOLLOWING);
    }

    private boolean save(Occurrence latest, ScheduleDraft draft, EditScope scope, boolean overlaps, String done) {
        OperationResult r = ctx.schedules().update(latest, draft, scope);
        if (!r.success()) {
            ctx.actions().error(r.errors().isEmpty() ? r.message() : r.errors().get(0));
            return false;
        }
        ctx.actions().toastUndoable(overlaps ? done + " · 다른 일정과 시간이 겹쳐요" : done);
        return true;
    }

    /** move와 같지만 성공 알림 글을 done으로 바꾼다 (done이 null이면 기본 글). 미루기처럼 "내일로 미뤘어요"를 알릴 때 쓴다 */
    private boolean move(Occurrence o, LocalDate date, LocalTime start, LocalTime end, String done) {
        Optional<Occurrence> fresh = ctx.actions().fresh(o);
        if (fresh.isEmpty()) {
            return false;
        }
        Occurrence latest = fresh.get();
        if (latest.allDay()) {
            // 하루 종일: 날짜만 본다. 시작·끝으로 넘어온 값은 쓰지 않는다
            start = latest.startTime();
            end = latest.endTime();
        }
        if (latest.date().equals(date) && latest.startTime().equals(start) && latest.endTime().equals(end)) {
            return false;
        }
        ScheduleDraft draft = withSlot(ctx.schedules().draftOf(latest), date, start, end);
        Optional<EditScope> scope = chooseScope(latest);
        if (scope.isEmpty()) {
            return false;
        }
        boolean overlaps = !ctx.schedules().findConflicts(draft, latest, scope.get()).isEmpty();
        String text = done != null ? done : isResize(latest, date, start) ? "시간을 바꿨어요" : "일정을 옮겼어요";
        return save(latest, draft, scope.get(), overlaps, text);
    }
}
