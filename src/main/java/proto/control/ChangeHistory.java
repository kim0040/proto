package proto.control;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * [제어 control] 일정 변경의 되돌리기·다시 실행 기록을 들고 있다.
 * ScheduleController가 등록·수정·삭제·완료에 성공할 때마다 "바뀌기 전"과 "바뀐 뒤" 일정 목록(ScheduleSnapshot)을 이름표와 함께 넣고,
 * 되돌리기·다시 실행 때 돌아갈 목록을 꺼내 간다. 저장소를 직접 고치거나 저장하지는 않는다 (그것은 controller의 몫).
 * 되돌리기 기록은 최근 {@value #MAX_STEPS}개까지만 두고 가장 오래된 것부터 버린다. 새 변경이 들어오면 다시 실행 기록은 지운다.
 */
final class ChangeHistory {

    /** 되돌리기 기록을 몇 단계까지 둘지 */
    static final int MAX_STEPS = 30;

    /** 변경 한 단계: 화면에 보일 이름표와 바뀌기 전·뒤 일정 목록 */
    private record Step(long id, String label, ScheduleSnapshot before, ScheduleSnapshot after) {
    }

    private final Deque<Step> undoSteps = new ArrayDeque<>();
    private final Deque<Step> redoSteps = new ArrayDeque<>();
    private long lastId;

    /** 성공한 변경 한 단계를 기록한다. 다시 실행 기록은 지우고, 되돌리기 기록이 넘치면 가장 오래된 것을 버린다. */
    void record(String label, ScheduleSnapshot before, ScheduleSnapshot after) {
        undoSteps.push(new Step(++lastId, label, before, after));
        while (undoSteps.size() > MAX_STEPS) {
            undoSteps.removeLast();
        }
        redoSteps.clear();
    }

    boolean canUndo() {
        return !undoSteps.isEmpty();
    }

    /**
     * 다음 되돌리기가 되돌릴 단계의 번호. 단계마다 기록될 때 한 번 붙고 바뀌지 않는다 (1부터, 되돌릴 것이 없으면 0).
     * 화면의 토스트가 "내가 알린 그 단계"만 되돌리도록 확인하는 데 쓴다.
     */
    long topId() {
        Step top = undoSteps.peek();
        return top == null ? 0 : top.id();
    }

    boolean canRedo() {
        return !redoSteps.isEmpty();
    }

    /** 다음 되돌리기의 이름표 ("일정 추가" 등). 되돌릴 것이 없으면 빈 값 */
    Optional<String> undoLabel() {
        return Optional.ofNullable(undoSteps.peek()).map(Step::label);
    }

    /** 다음 다시 실행의 이름표. 다시 실행할 것이 없으면 빈 값 */
    Optional<String> redoLabel() {
        return Optional.ofNullable(redoSteps.peek()).map(Step::label);
    }

    /** 마지막 단계를 다시 실행 쪽으로 옮기고, 돌아갈 "바뀌기 전" 목록을 돌려준다. 되돌릴 것이 없으면 빈 값 */
    Optional<ScheduleSnapshot> undo() {
        Step step = undoSteps.poll();
        if (step == null) {
            return Optional.empty();
        }
        redoSteps.push(step);
        return Optional.of(step.before());
    }

    /** 마지막으로 되돌린 단계를 되돌리기 쪽으로 옮기고, 돌아갈 "바뀐 뒤" 목록을 돌려준다. 다시 실행할 것이 없으면 빈 값 */
    Optional<ScheduleSnapshot> redo() {
        Step step = redoSteps.poll();
        if (step == null) {
            return Optional.empty();
        }
        undoSteps.push(step);
        return Optional.of(step.after());
    }

    /** 기록을 모두 지운다. 카테고리 삭제처럼 일정이 기록 밖에서 바뀌었을 때 쓴다. */
    void clear() {
        undoSteps.clear();
        redoSteps.clear();
    }
}
