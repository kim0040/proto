package proto.entity;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * [엔티티 entity] 비어 있는 시간 구간 하나 (F12 빈 시간 찾기 결과).
 */
public record TimeSlot(LocalDate date, LocalTime start, LocalTime end) {

    public long minutes() {
        return Duration.between(start, end).toMinutes();
    }
}
