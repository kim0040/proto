package proto.logic;

import org.junit.jupiter.api.Test;
import proto.entity.Occurrence;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static proto.TestData.d;
import static proto.TestData.t;

class ScheduleFilterTest {

    private static Occurrence occ(String title, String memo, String categoryId) {
        return new Occurrence("id-" + title, d("2026-11-09"), title, t("09:00"), t("10:00"), categoryId,
                memo, RepeatRule.NONE, ReminderOption.NONE, false);
    }

    private final Occurrence meeting = occ("팀 Meeting", "Bring LAPTOP", "team");
    private final Occurrence lecture = occ("알고리즘", "meeting notes 정리", "class");
    private final Occurrence gym = occ("운동", "", "personal");
    private final List<Occurrence> all = List.of(meeting, lecture, gym);

    @Test
    void blankOrNullKeywordAndNullCategoryKeepEverything() {
        assertEquals(all, ScheduleFilter.filter(all, null, null));
        assertEquals(all, ScheduleFilter.filter(all, null, ""));
        assertEquals(all, ScheduleFilter.filter(all, null, "   "));
    }

    @Test
    void keywordMatchesTitleOrMemoIgnoringCase() {
        assertEquals(List.of(meeting, lecture), ScheduleFilter.filter(all, null, "MEETING"));
        assertEquals(List.of(meeting), ScheduleFilter.filter(all, null, "laptop"));
        assertEquals(List.of(gym), ScheduleFilter.filter(all, null, "운동"));
    }

    @Test
    void categoryAndKeywordAreCombinedWithAnd() {
        assertEquals(List.of(lecture), ScheduleFilter.filter(all, "class", "meeting"));
        assertEquals(List.of(meeting), ScheduleFilter.filter(all, "team", "meeting"));
        assertEquals(List.of(), ScheduleFilter.filter(all, "personal", "meeting"));
        assertEquals(List.of(gym), ScheduleFilter.filter(all, "personal", null));
    }
}
