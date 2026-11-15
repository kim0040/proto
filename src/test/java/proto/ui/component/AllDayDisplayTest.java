package proto.ui.component;

import org.junit.jupiter.api.Test;
import proto.entity.Occurrence;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 목록·달력에 쓰는 시간 문구: 하루 종일은 "하루 종일", 시간 있는 일정은 시간 범위, 달력 알약 앞 시간은 앞 0을 뗀다 */
class AllDayDisplayTest {

    private static Occurrence occ(LocalTime start, LocalTime end, boolean allDay) {
        return new Occurrence("s", LocalDate.of(2026, 11, 12), "제목", start, end, "class", "", RepeatRule.NONE,
                ReminderOption.NONE, false, allDay);
    }

    @Test
    void 하루_종일은_시간_범위_대신_이름표를_쓴다() {
        assertEquals("하루 종일", AllDayTag.when(occ(LocalTime.of(0, 0), LocalTime.of(23, 59), true)));
    }

    @Test
    void 시간이_있는_일정은_시간_범위를_쓴다() {
        assertEquals("09:00 – 10:30", AllDayTag.when(occ(LocalTime.of(9, 0), LocalTime.of(10, 30), false)));
    }

    @Test
    void 달력_알약의_시작_시간은_앞_0을_뗀다() {
        assertEquals("9:00", MonthPill.shortTime(occ(LocalTime.of(9, 0), LocalTime.of(10, 0), false)));
        assertEquals("14:05", MonthPill.shortTime(occ(LocalTime.of(14, 5), LocalTime.of(15, 0), false)));
    }
}
