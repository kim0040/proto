package proto.logic;

import proto.entity.Occurrence;
import proto.entity.ScheduleState;

import java.time.LocalDateTime;

/**
 * [논리 application logic] "지금 시각 + 완료 여부"로 일정 상태를 매번 계산한다 (F6).
 * 상태는 어디에도 저장하지 않는다.
 */
public final class StateCalculator {

    private StateCalculator() {
    }

    /** 완료 → 완료 / 시작 전 → 예정 / 시작~끝 사이(시작 포함, 끝 제외) → 진행 중 / 끝 이후 미완료 → 지연 */
    public static ScheduleState stateOf(Occurrence occurrence, LocalDateTime now) {
        if (occurrence.completed()) {
            return ScheduleState.DONE;
        }
        if (now.isBefore(occurrence.startDateTime())) {
            return ScheduleState.SCHEDULED;
        }
        if (now.isBefore(occurrence.endDateTime())) {
            return ScheduleState.IN_PROGRESS;
        }
        return ScheduleState.DELAYED;
    }
}
