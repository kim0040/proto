package proto.control;

import proto.entity.Category;
import proto.entity.Occurrence;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.entity.Schedule;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * [제어 control] 화면 입력값(ScheduleDraft)과 엔티티(Schedule, Occurrence) 사이를 바꿔 주는 도우미.
 * ScheduleController만 부른다 (같은 패키지에서만 보인다). 입력값 ↔ 엔티티 변환을 여기 모아 두어
 * controller 안에서 필드를 하나하나 옮기는 코드가 퍼지지 않게 한다.
 * 하루 종일 입력값은 시간을 00:00~23:59로, 알림을 NONE으로 바꾼다. 알릴 시작 시각이 없으므로
 * 하루 종일 일정에 알림을 골라도 검증에서 막지 않고 조용히 "알림 없음"으로 저장한다.
 */
final class DraftMapper {

    private static final LocalTime DEFAULT_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_END = LocalTime.of(10, 0);

    private DraftMapper() {
    }

    /**
     * 검증과 저장에 쓸 Schedule을 만든다. 제목의 앞뒤 공백은 지우고, 하루 종일이면 화면이 넣은 시간·알림을 버린다.
     * 반복이 없으면 반복 종료일은 비운다. "반복 안 함"으로 바꿨는데 예전 종료일이 남아 있으면 검증에서 엉뚱한 오류가 나기 때문이다.
     *
     * @param id 새 일정의 id. 검증만 할 때는 아무 글이나 넣는다 ("draft" 등).
     */
    static Schedule toSchedule(ScheduleDraft draft, String id) {
        String title = draft.title() == null ? null : draft.title().trim();
        RepeatRule repeat = draft.repeatRule() == null ? RepeatRule.NONE : draft.repeatRule();
        LocalDate repeatEnd = repeat == RepeatRule.NONE ? null : draft.repeatEndDate();
        LocalTime start = draft.allDay() ? Schedule.ALL_DAY_START : draft.startTime();
        LocalTime end = draft.allDay() ? Schedule.ALL_DAY_END : draft.endTime();
        ReminderOption reminder = draft.allDay() ? ReminderOption.NONE : draft.reminder();
        Schedule schedule = new Schedule(id, title, draft.date(), start, end, draft.categoryId(), draft.memo(),
                repeat, repeatEnd, reminder, draft.allDay());
        schedule.setColorKey(draft.colorKey());
        return schedule;
    }

    /**
     * 기존 하루치 일정으로 수정 화면의 입력값을 만든다.
     *
     * @param repeatEndDate Occurrence에는 없어서 따로 받는 반복 종료일 (없으면 null)
     */
    static ScheduleDraft toDraft(Occurrence occurrence, LocalDate repeatEndDate) {
        return new ScheduleDraft(occurrence.title(), occurrence.date(), occurrence.startTime(), occurrence.endTime(),
                occurrence.categoryId(), occurrence.memo(), occurrence.repeatRule(), repeatEndDate,
                occurrence.reminder(), occurrence.allDay(), occurrence.colorKey());
    }

    /** 새 일정의 기본값: 제목 없음, 카테고리 "개인", 반복·알림 없음, 하루 종일 아님, 주어진 날짜와 시간 */
    static ScheduleDraft emptyDraft(LocalDate date, LocalTime start, LocalTime end) {
        return new ScheduleDraft("", date, start, end, Category.PERSONAL_ID, "", RepeatRule.NONE, null,
                ReminderOption.NONE);
    }

    /** 새 일정의 기본값에 기본 시간(09:00~10:00)을 넣는다 */
    static ScheduleDraft emptyDraft(LocalDate date) {
        return emptyDraft(date, DEFAULT_START, DEFAULT_END);
    }
}
