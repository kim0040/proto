package proto.logic;

import proto.entity.Occurrence;
import proto.entity.ScheduleState;

import java.time.LocalDateTime;

/**
 * [논리 application logic] "지금 시각 + 완료 여부"로 일정 상태를 매번 계산한다 (F6).
 * F6(상태 자동 계산)에서 쓴다. 화면이 일정 줄을 그릴 때(ScheduleController#stateOf 경유)와 StateRefreshTimer가 1분마다,
 * StatisticsCalculator가 지연 수를 셀 때 부른다. 상태를 저장하지 않고 매번 계산하므로 시간이 지나면 값이 저절로 바뀐다.
 * 상태는 어디에도 저장하지 않는다.
 */
public final class StateCalculator {

    private StateCalculator() {
    }

    /**
     * 하루치 일정의 상태를 지금 시각(now)으로 계산한다. 위에서부터 차례로 따진다: 완료했으면 완료, 시작 전이면 예정,
     * 끝 전이면 진행 중, 나머지(끝났는데 미완료)는 지연이다. 진행 중은 시작 시각을 포함하고 끝 시각은 포함하지 않는다
     * (끝 시각 정각부터는 지연). 완료 여부는 Occurrence가 들고 있으므로 이 클래스는 시계 말고는 아무것도 기억하지 않는다.
     */
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
