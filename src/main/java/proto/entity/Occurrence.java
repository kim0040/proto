package proto.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * [엔티티 entity] 반복이 펼쳐진 "하루치" 일정 (읽기 전용 스냅샷).
 * RepeatExpander가 Schedule을 기간 안의 날짜마다 하나씩 만들어 준다 (F2, F9). ScheduleController가 이것을 화면에 넘긴다.
 * 화면은 이 객체를 보여 주기만 하고, 바꿀 때는 controller에 이 객체를 넘긴다.
 * 레코드라 만든 뒤에는 바뀌지 않는다. 그래서 반복 일정을 날짜마다 따로 보여 줘도 원본 Schedule은 안전하다.
 * 상태(F6)와 알림(F10) 계산(StateCalculator, ReminderChecker)도 이 값을 읽기만 한다.
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

    /**
     * 원래 일정과 날짜로 하루치 일정을 만든다.
     * 완료 여부는 원본의 completedDates에서 그 날짜가 있는지로 정해진다.
     */
    public static Occurrence of(Schedule s, LocalDate date) {
        return new Occurrence(s.getId(), date, s.getTitle(), s.getStartTime(), s.getEndTime(),
                s.getCategoryId(), s.getMemo(), s.getRepeatRule(), s.getReminder(),
                s.isCompletedOn(date));
    }

    /** 날짜와 시작 시간을 합친 시작 시각. 상태 계산(F6)과 알림(F10)이 "지금"과 비교할 때 쓴다. */
    public LocalDateTime startDateTime() {
        return LocalDateTime.of(date, startTime);
    }

    /** 날짜와 끝 시간을 합친 끝 시각. 끝 시간은 항상 시작보다 늦으므로(자정을 넘기는 일정은 없다) 같은 날짜로 합쳐도 된다. */
    public LocalDateTime endDateTime() {
        return LocalDateTime.of(date, endTime);
    }

    /** 반복 규칙이 NONE이 아니면 true. */
    public boolean isRepeating() {
        return repeatRule != RepeatRule.NONE;
    }

    /**
     * 같은 일정의 같은 날짜인지 (알림 중복 방지 키 등에 사용)
     * "일정id@날짜" 모양이다. 반복 일정은 id가 같아도 날짜가 다르면 다른 키가 된다.
     */
    public String key() {
        return scheduleId + "@" + date;
    }
}
