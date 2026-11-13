package proto.logic;

import org.junit.jupiter.api.Test;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.entity.Schedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static proto.TestData.d;
import static proto.TestData.t;

class ScheduleValidatorTest {

    private static Schedule make(String title, String start, String end, String memo,
                                 RepeatRule rule, String repeatEnd) {
        return new Schedule("id", title, d("2026-11-09"), start == null ? null : t(start),
                end == null ? null : t(end), "class", memo, rule,
                repeatEnd == null ? null : d(repeatEnd), ReminderOption.NONE);
    }

    private static List<String> check(String title, String start, String end) {
        return ScheduleValidator.validate(make(title, start, end, "", RepeatRule.NONE, null));
    }

    @Test
    void validInputHasNoErrors() {
        assertTrue(check("수업", "09:00", "10:00").isEmpty());
    }

    @Test
    void emptyOrBlankTitleIsRejected() {
        assertEquals(List.of("제목을 입력해 주세요"), check("", "09:00", "10:00"));
        assertEquals(List.of("제목을 입력해 주세요"), check("   ", "09:00", "10:00"));
        assertEquals(List.of("제목을 입력해 주세요"), check(null, "09:00", "10:00"));
    }

    @Test
    void titleLimitIs50CharactersAfterTrim() {
        assertTrue(check("가".repeat(50), "09:00", "10:00").isEmpty());
        assertTrue(check(" " + "가".repeat(50) + " ", "09:00", "10:00").isEmpty());
        assertEquals(List.of("제목은 50자까지 쓸 수 있어요"), check("가".repeat(51), "09:00", "10:00"));
    }

    @Test
    void endMustBeAfterStart() {
        String message = "끝 시간은 시작 시간보다 늦어야 해요";
        assertEquals(List.of(message), check("a", "10:00", "10:00"));
        assertEquals(List.of(message), check("a", "10:00", "09:59"));
        assertTrue(check("a", "10:00", "10:01").isEmpty());
    }

    @Test
    void missingDateAndTimesAreReported() {
        Schedule s = new Schedule("id", "a", null, null, null, "class", "", RepeatRule.NONE, null,
                ReminderOption.NONE);
        assertEquals(List.of("날짜를 입력해 주세요", "시작 시간을 입력해 주세요", "끝 시간을 입력해 주세요"),
                ScheduleValidator.validate(s));
    }

    @Test
    void memoLimitIs200Characters() {
        assertTrue(ScheduleValidator.validate(make("a", "09:00", "10:00", "x".repeat(200),
                RepeatRule.NONE, null)).isEmpty());
        assertEquals(List.of("메모는 200자까지 쓸 수 있어요"), ScheduleValidator.validate(
                make("a", "09:00", "10:00", "x".repeat(201), RepeatRule.NONE, null)));
    }

    @Test
    void categoryIsRequired() {
        Schedule s = new Schedule("id", "a", LocalDate.of(2026, 11, 9), LocalTime.of(9, 0),
                LocalTime.of(10, 0), " ", "", RepeatRule.NONE, null, ReminderOption.NONE);
        assertEquals(List.of("카테고리를 선택해 주세요"), ScheduleValidator.validate(s));
    }

    @Test
    void repeatEndBeforeDateIsRejectedOnlyForRepeatingSchedules() {
        assertEquals(List.of("반복 종료일은 시작 날짜보다 빠를 수 없어요"),
                ScheduleValidator.validate(make("a", "09:00", "10:00", "", RepeatRule.WEEKLY, "2026-11-08")));
        assertTrue(ScheduleValidator.validate(make("a", "09:00", "10:00", "", RepeatRule.WEEKLY, "2026-11-09"))
                .isEmpty());
        assertTrue(ScheduleValidator.validate(make("a", "09:00", "10:00", "", RepeatRule.NONE, "2026-11-08"))
                .isEmpty());
    }
}
