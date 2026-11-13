package proto.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * [엔티티 entity] 반복이 펼쳐진 "하루치" 일정 (읽기 전용 스냅샷).
 * RepeatExpander가 Schedule을 기간 안의 날짜마다 하나씩 만들어 준다 (F2, F9).
 * 화면은 이 객체를 보여 주기만 하고, 바꿀 때는 controller에 이 객체를 넘긴다.
 *
 * @param scheduleId 원래 Schedule의 id
 * @param date       이 하루치 일정의 날짜
 * @param completed  이 날짜에 완료 처리됐는지 (F5)
 */
public record Occurrence(
        String scheduleId,
        LocalDate date,
        String title,
        LocalTime startTime,
        LocalTime endTime,
        String categoryId,
        String memo,
        RepeatRule repeatRule,
        ReminderOption reminder,
        boolean completed) {

    /** 원래 일정과 날짜로 하루치 일정을 만든다 */
    public static Occurrence of(Schedule s, LocalDate date) {
        return new Occurrence(s.getId(), date, s.getTitle(), s.getStartTime(), s.getEndTime(),
                s.getCategoryId(), s.getMemo(), s.getRepeatRule(), s.getReminder(),
                s.isCompletedOn(date));
    }

    public LocalDateTime startDateTime() {
        return LocalDateTime.of(date, startTime);
    }

    public LocalDateTime endDateTime() {
        return LocalDateTime.of(date, endTime);
    }

    public boolean isRepeating() {
        return repeatRule != RepeatRule.NONE;
    }

    /** 같은 일정의 같은 날짜인지 (알림 중복 방지 키 등에 사용) */
    public String key() {
        return scheduleId + "@" + date;
    }
}
