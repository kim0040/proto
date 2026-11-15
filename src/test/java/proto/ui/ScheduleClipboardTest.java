package proto.ui;

import org.junit.jupiter.api.Test;
import proto.entity.Occurrence;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.ui.AppState.Mode;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static proto.TestData.d;
import static proto.TestData.t;

/** 붙여 넣을 날짜 고르기와 복사 보관함 */
class ScheduleClipboardTest {

    private static final java.time.LocalDate TODAY = d("2026-11-16");
    private static final java.time.LocalDate MONDAY = d("2026-11-16");

    @Test
    void 오늘_보기는_오늘에_붙인다() {
        assertEquals(Optional.of(TODAY), ScheduleClipboard.pasteTarget(Mode.TODAY, TODAY, d("2026-11-20"), MONDAY));
    }

    @Test
    void 주_보기는_그_주_안의_고른_날에_붙인다() {
        assertEquals(Optional.of(d("2026-11-20")),
                ScheduleClipboard.pasteTarget(Mode.WEEK, TODAY, d("2026-11-20"), MONDAY));
        assertEquals(Optional.of(d("2026-11-22")),
                ScheduleClipboard.pasteTarget(Mode.WEEK, TODAY, d("2026-11-22"), MONDAY));
    }

    @Test
    void 주_보기에서_고른_날이_그_주_밖이면_월요일에_붙인다() {
        assertEquals(Optional.of(MONDAY), ScheduleClipboard.pasteTarget(Mode.WEEK, TODAY, d("2026-11-23"), MONDAY));
        assertEquals(Optional.of(MONDAY), ScheduleClipboard.pasteTarget(Mode.WEEK, TODAY, d("2026-11-15"), MONDAY));
    }

    @Test
    void 달력_보기는_고른_날에_붙인다() {
        assertEquals(Optional.of(d("2026-12-03")),
                ScheduleClipboard.pasteTarget(Mode.MONTH, TODAY, d("2026-12-03"), MONDAY));
    }

    @Test
    void 통계와_설정에서는_붙일_곳이_없다() {
        assertTrue(ScheduleClipboard.pasteTarget(Mode.STATS, TODAY, TODAY, MONDAY).isEmpty());
    }

    @Test
    void 복사해_둔_일정은_붙여도_남아_있다() {
        ScheduleClipboard clipboard = new ScheduleClipboard();
        assertTrue(clipboard.content().isEmpty());
        Occurrence o = new Occurrence("id", TODAY, "회의", t("09:00"), t("10:00"), "class", "", RepeatRule.NONE,
                ReminderOption.NONE, false);
        clipboard.set(o);
        assertEquals(Optional.of(o), clipboard.content());
        assertEquals(Optional.of(o), clipboard.content());
    }
}
