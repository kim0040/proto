package proto.entity;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * [엔티티 entity] 비어 있는 시간 구간 하나 (F12 빈 시간 찾기 결과).
 * F12(빈 시간 찾기)에서 쓴다. FreeSlotFinder가 만들고 ScheduleController가 목록으로 돌려주며, FreeSlotDialog가 보여 준다.
 * 시간 구간은 start 이상 end 미만(반쯤 열린 구간)으로 본다. 그래서 10:00에 끝난 일정과 10:00에 시작하는 일정은 겹치지 않는다.
 */
public record TimeSlot(LocalDate date, LocalTime start, LocalTime end) {

    /** 구간 길이(분). start가 end보다 늦지 않다고 가정한다. */
    public long minutes() {
        return Duration.between(start, end).toMinutes();
    }
}
