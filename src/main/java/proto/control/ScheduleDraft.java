package proto.control;

import proto.entity.ReminderOption;
import proto.entity.RepeatRule;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * [제어 control] 등록/수정 화면이 입력값을 controller에 넘길 때 쓰는 값 묶음 (F1, F3).
 * 검증 전 값이라 비어 있거나 틀린 값이 들어올 수 있다. 검증은 controller가 한다.
 *
 * @param repeatEndDate 반복 종료일 (없으면 null)
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
        ReminderOption reminder) {
}
