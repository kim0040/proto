package proto.control;

import proto.entity.ReminderOption;
import proto.entity.RepeatRule;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * [제어 control] 등록/수정 화면이 입력값을 controller에 넘길 때 쓰는 값 묶음 (F1, F3).
 * ScheduleDialog가 채워서 ScheduleController#create, #update, #validate에 넘기고, DraftMapper가 이것을 Schedule로 바꾼다.
 * 수정할 때는 반대로 ScheduleController#draftOf가 기존 일정으로 이 값을 만들어 화면을 채워 준다.
 * 아직 검증 전 값이라 비어 있거나 틀린 값이 들어올 수 있고, 검증은 controller가 한다.
 * allDay가 true면 시작·끝은 00:00~23:59, 알림은 NONE으로 바뀌어 쓰이므로 화면이 넣은 시간·알림 값은 무시된다
 * (null이어도 검증 오류가 나지 않는다).
 *
 * @param repeatEndDate 반복 종료일 (없으면 null)
 * @param allDay        하루 종일 일정인지
 * @param colorKey      이 일정만의 색 키 (null이면 카테고리 색을 따른다)
 */
public record ScheduleDraft(
        String title,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        String categoryId,
        String memo,
        RepeatRule repeatRule,
        LocalDate repeatEndDate,
        ReminderOption reminder,
        boolean allDay,
        String colorKey) {

    /** 하루 종일이 아닌 입력값 (allDay를 넣기 전의 인자 목록. 예전 호출 코드가 그대로 돈다) */
    public ScheduleDraft(String title, LocalDate date, LocalTime startTime, LocalTime endTime, String categoryId,
                         String memo, RepeatRule repeatRule, LocalDate repeatEndDate, ReminderOption reminder) {
        this(title, date, startTime, endTime, categoryId, memo, repeatRule, repeatEndDate, reminder, false);
    }

    /** 일정만의 색이 없는 입력값 (colorKey를 넣기 전의 인자 목록. 예전 호출 코드가 그대로 돈다) */
    public ScheduleDraft(String title, LocalDate date, LocalTime startTime, LocalTime endTime, String categoryId,
                         String memo, RepeatRule repeatRule, LocalDate repeatEndDate, ReminderOption reminder,
                         boolean allDay) {
        this(title, date, startTime, endTime, categoryId, memo, repeatRule, repeatEndDate, reminder, allDay, null);
    }

    /** 색만 바꾼 같은 입력값 (null이면 카테고리 색을 따른다) */
    public ScheduleDraft withColorKey(String key) {
        return new ScheduleDraft(title, date, startTime, endTime, categoryId, memo, repeatRule, repeatEndDate,
                reminder, allDay, key);
    }

    /** 반복만 없앤 같은 입력값 (반복 없음, 반복 종료일 없음). 복제·"이 일정만" 수정에 쓴다 */
    public ScheduleDraft withoutRepeat() {
        return new ScheduleDraft(title, date, startTime, endTime, categoryId, memo, RepeatRule.NONE, null,
                reminder, allDay, colorKey);
    }
}
