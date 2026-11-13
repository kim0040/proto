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
 */
final class DraftMapper {

    private static final LocalTime DEFAULT_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_END = LocalTime.of(10, 0);

    private DraftMapper() {
    }

    /** 제목은 앞뒤 공백을 지우고, 반복이 없으면 반복 종료일은 비운다 */
    static Schedule toSchedule(ScheduleDraft d, String id) {
        String title = d.title() == null ? null : d.title().trim();
        RepeatRule repeat = d.repeatRule() == null ? RepeatRule.NONE : d.repeatRule();
        LocalDate repeatEnd = repeat == RepeatRule.NONE ? null : d.repeatEndDate();
        return new Schedule(id, title, d.date(), d.startTime(), d.endTime(), d.categoryId(), d.memo(),
                repeat, repeatEnd, d.reminder());
    }

    static ScheduleDraft toDraft(Occurrence o, LocalDate repeatEndDate) {
        return new ScheduleDraft(o.title(), o.date(), o.startTime(), o.endTime(), o.categoryId(),
                o.memo(), o.repeatRule(), repeatEndDate, o.reminder());
    }

    static ScheduleDraft emptyDraft(LocalDate date, LocalTime start, LocalTime end) {
        return new ScheduleDraft("", date, start, end, Category.PERSONAL_ID, "", RepeatRule.NONE, null,
                ReminderOption.NONE);
    }

    static ScheduleDraft emptyDraft(LocalDate date) {
        return emptyDraft(date, DEFAULT_START, DEFAULT_END);
    }
}
