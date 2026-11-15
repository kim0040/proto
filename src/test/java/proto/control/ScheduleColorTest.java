package proto.control;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import proto.entity.Occurrence;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static proto.TestData.d;
import static proto.TestData.t;

/** 일정만의 색(카테고리와 따로 고르는 색). 시계는 2026-11-09(월) 10:00 */
class ScheduleColorTest {

    @TempDir
    Path dir;
    ControllerFixture f;
    ScheduleController c;

    @BeforeEach
    void setUp() {
        f = new ControllerFixture(dir, "2026-11-09T10:00");
        c = f.schedules;
    }

    private static ScheduleDraft draft(String title, String date, RepeatRule repeat, String color) {
        return new ScheduleDraft(title, d(date), t("09:00"), t("10:00"), "class", "", repeat, null,
                ReminderOption.NONE, false, color);
    }

    @Test
    void 새_일정에_고른_색이_저장된다() {
        assertTrue(c.create(draft("회의", "2026-11-10", RepeatRule.NONE, "rose")).success());
        assertEquals("rose", f.occurrenceOn("회의", "2026-11-10").colorKey());
    }

    @Test
    void 색을_고르지_않거나_모르는_색이면_카테고리_색을_따른다() {
        assertTrue(c.create(draft("수업", "2026-11-10", RepeatRule.NONE, null)).success());
        assertTrue(c.create(draft("이상", "2026-11-11", RepeatRule.NONE, "neon")).success());
        assertNull(f.occurrenceOn("수업", "2026-11-10").colorKey());
        assertNull(f.occurrenceOn("이상", "2026-11-11").colorKey());
    }

    @Test
    void setColor는_반복_전체를_바꾸고_되돌릴_수_있다() {
        assertTrue(c.create(draft("스터디", "2026-11-09", RepeatRule.WEEKLY, null)).success());
        Occurrence second = f.occurrenceOn("스터디", "2026-11-16");
        assertTrue(c.setColor(second, "green").success());
        assertEquals("green", f.occurrenceOn("스터디", "2026-11-09").colorKey());
        assertEquals("색 바꾸기", c.undoLabel().orElseThrow());
        assertTrue(c.undo().success());
        assertNull(f.occurrenceOn("스터디", "2026-11-23").colorKey());
    }

    @Test
    void 같은_색으로_바꾸면_되돌리기_기록이_생기지_않는다() {
        assertTrue(c.create(draft("회의", "2026-11-10", RepeatRule.NONE, "blue")).success());
        c.clearHistory();
        assertTrue(c.setColor(f.occurrenceOn("회의", "2026-11-10"), "blue").success());
        assertFalse(c.canUndo());
    }

    @Test
    void 수정_입력값과_복제_입력값이_색을_가져간다() {
        assertTrue(c.create(draft("회의", "2026-11-10", RepeatRule.NONE, "purple")).success());
        Occurrence o = f.occurrenceOn("회의", "2026-11-10");
        assertEquals("purple", c.draftOf(o).colorKey());
        assertEquals("purple", c.copyDraft(o, d("2026-11-12")).colorKey());
        assertEquals("purple", c.draftOf(o).withoutRepeat().colorKey());
    }

    @Test
    void 반복_일정을_이_일정만_고쳐도_색이_남는다() {
        assertTrue(c.create(draft("스터디", "2026-11-09", RepeatRule.WEEKLY, "teal")).success());
        Occurrence o = f.occurrenceOn("스터디", "2026-11-16");
        ScheduleDraft changed = c.draftOf(o).withColorKey("red");
        assertTrue(c.update(o, changed, EditScope.THIS_ONLY).success());
        assertEquals("red", f.occurrenceOn("스터디", "2026-11-16").colorKey());
        assertEquals("teal", f.occurrenceOn("스터디", "2026-11-23").colorKey());
    }
}
