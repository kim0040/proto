package proto.ui;

import org.junit.jupiter.api.Test;
import proto.ui.AppState.Mode;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 새 일정(N, C, 버튼)을 처음 놓는 날: 지금 화면에 보이는 날이어야 한다 */
class AppStateNewDayTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16); // 월요일

    private static AppState state(Mode mode, String anchor, String selected) {
        AppState s = new AppState(TODAY);
        s.setMode(mode);
        s.setAnchor(LocalDate.parse(anchor));
        s.setSelectedDay(LocalDate.parse(selected));
        return s;
    }

    @Test
    void 달력은_고른_날() {
        assertEquals(LocalDate.parse("2026-12-03"), state(Mode.MONTH, "2026-12-03", "2026-12-03").newScheduleDay(TODAY));
    }

    @Test
    void 오늘_보기는_오늘() {
        assertEquals(TODAY, state(Mode.TODAY, "2026-11-16", "2026-11-16").newScheduleDay(TODAY));
    }

    @Test
    void 주_보기는_고른_날이_그_주_안이면_그_날() {
        assertEquals(LocalDate.parse("2026-11-19"), state(Mode.WEEK, "2026-11-19", "2026-11-19").newScheduleDay(TODAY));
    }

    @Test
    void 주_보기에서_오늘이_없는_주면_그_주의_월요일() {
        // 다음 주를 보면서 고른 날이 그 주 밖(지난 선택)인 경우
        assertEquals(LocalDate.parse("2026-11-23"), state(Mode.WEEK, "2026-11-25", "2026-11-16").newScheduleDay(TODAY));
    }

    @Test
    void 주_보기에서_고른_날이_밖이고_오늘이_주_안이면_오늘() {
        assertEquals(TODAY, state(Mode.WEEK, "2026-11-18", "2026-12-30").newScheduleDay(TODAY));
    }

    @Test
    void 통계는_오늘이_그_주_안이면_오늘_아니면_그_주의_월요일() {
        assertEquals(TODAY, state(Mode.STATS, "2026-11-18", "2026-11-18").newScheduleDay(TODAY));
        assertEquals(LocalDate.parse("2026-11-23"), state(Mode.STATS, "2026-11-26", "2026-11-26").newScheduleDay(TODAY));
    }
}
