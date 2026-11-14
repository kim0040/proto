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
 * F1, F3, F12에서 쓴다. ScheduleController만 부르며(같은 패키지에서만 보인다), 입력값 ↔ 엔티티 변환을 여기 모아 두어
 * controller 안에서 필드를 하나하나 옮기는 코드가 퍼지지 않게 한다.
 */
final class DraftMapper {

    private static final LocalTime DEFAULT_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_END = LocalTime.of(10, 0);

    private DraftMapper() {
    }

    /**
     * 검증과 저장에 쓸 Schedule을 만든다. 제목의 앞뒤 공백은 지우고, 반복이 없으면 반복 종료일은 비운다.
     * 왜 반복 종료일을 비우나: "반복 안 함"으로 바꿨는데 예전에 입력한 종료일이 남아 있으면 검증에서 엉뚱한 오류가 나기 때문이다.
     *
     * @param id 새 일정의 id. 검증만 할 때는 아무 글이나 넣는다 ("draft" 등).
     */
    static Schedule toSchedule(ScheduleDraft d, String id) {
        String title = d.title() == null ? null : d.title().trim();
        RepeatRule repeat = d.repeatRule() == null ? RepeatRule.NONE : d.repeatRule();
        LocalDate repeatEnd = repeat == RepeatRule.NONE ? null : d.repeatEndDate();
        return new Schedule(id, title, d.date(), d.startTime(), d.endTime(), d.categoryId(), d.memo(),
                repeat, repeatEnd, d.reminder());
    }

    /**
     * 기존 하루치 일정으로 수정 화면의 입력값을 만든다.
     *
     * @param repeatEndDate Occurrence에는 없어서 따로 받는 반복 종료일 (없으면 null)
     */
    static ScheduleDraft toDraft(Occurrence o, LocalDate repeatEndDate) {
        return new ScheduleDraft(o.title(), o.date(), o.startTime(), o.endTime(), o.categoryId(),
                o.memo(), o.repeatRule(), repeatEndDate, o.reminder());
    }

    /** 새 일정의 기본값: 제목 없음, 카테고리 "개인", 반복·알림 없음, 주어진 날짜와 시간. */
    static ScheduleDraft emptyDraft(LocalDate date, LocalTime start, LocalTime end) {
        return new ScheduleDraft("", date, start, end, Category.PERSONAL_ID, "", RepeatRule.NONE, null,
                ReminderOption.NONE);
    }

    /** 새 일정의 기본값에 기본 시간(09:00~10:00)을 넣는다. */
    static ScheduleDraft emptyDraft(LocalDate date) {
        return emptyDraft(date, DEFAULT_START, DEFAULT_END);
    }
}
