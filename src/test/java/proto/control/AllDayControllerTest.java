package proto.control;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import proto.entity.Category;
import proto.entity.Occurrence;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.entity.Schedule;
import proto.entity.ScheduleState;
import proto.entity.TimeSlot;
import proto.entity.WeeklyStatistics;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static proto.TestData.d;
import static proto.TestData.t;
import static proto.control.ControllerFixture.draft;

/** 하루 종일 일정. 시계는 2026-11-09(월) 10:00 */
class AllDayControllerTest {

    @TempDir
    Path dir;
    ControllerFixture f;
    ScheduleController c;

    @BeforeEach
    void setUp() {
        f = new ControllerFixture(dir, "2026-11-09T10:00");
        c = f.schedules;
    }

    /** 화면이 시간 칸을 비우고 알림을 골라도 하루 종일이면 무시돼야 한다 */
    private static ScheduleDraft allDay(String title, String date, RepeatRule repeat, String category) {
        return new ScheduleDraft(title, d(date), null, null, category, "", repeat, null,
                ReminderOption.THIRTY_MINUTES, true);
    }

    private static ScheduleDraft allDay(String title, String date) {
        return allDay(title, date, RepeatRule.NONE, "class");
    }

    private List<String> titles(String date) {
        return c.getOccurrences(d(date), d(date), null, null).stream().map(Occurrence::title).toList();
    }

    @Test
    void allDayDraftIgnoresTimesAndReminderAndPassesValidation() {
        assertTrue(c.validate(allDay("학회", "2026-11-10")).isEmpty());
        assertTrue(c.create(allDay("학회", "2026-11-10")).success());
        Occurrence o = f.occurrenceOn("학회", "2026-11-10");
        assertTrue(o.allDay());
        assertEquals(LocalTime.MIDNIGHT, o.startTime());
        assertEquals(LocalTime.of(23, 59), o.endTime());
        assertEquals(ReminderOption.NONE, o.reminder());
    }

    @Test
    void newDraftsAreNotAllDayAndDraftOfAndCopyDraftKeepIt() {
        assertFalse(c.newDraft(d("2026-11-10")).allDay());
        assertFalse(c.draftForSlot(new TimeSlot(d("2026-11-10"), t("09:00"), t("10:00"))).allDay());
        assertTrue(c.create(allDay("학회", "2026-11-10")).success());
        Occurrence o = f.occurrenceOn("학회", "2026-11-10");
        assertTrue(c.draftOf(o).allDay());
        ScheduleDraft copy = c.copyDraft(o, d("2026-11-12"));
        assertTrue(copy.allDay());
        assertTrue(c.create(copy).success());
        assertTrue(f.occurrenceOn("학회", "2026-11-12").allDay());
    }

    @Test
    void allDayItemsComeFirstWithinADayThenStartTimeThenTitle() {
        assertTrue(c.create(draft("아침", "2026-11-10", "08:00", "09:00")).success());
        assertTrue(c.create(draft("자정", "2026-11-10", "00:00", "01:00")).success());
        assertTrue(c.create(allDay("학회", "2026-11-10")).success());
        assertTrue(c.create(allDay("가족 행사", "2026-11-10")).success());
        assertTrue(c.create(draft("전날", "2026-11-09", "23:00", "23:30")).success());
        assertEquals(List.of("전날", "가족 행사", "학회", "자정", "아침"),
                c.getOccurrences(d("2026-11-09"), d("2026-11-10"), null, null).stream()
                        .map(Occurrence::title).toList());
    }

    @Test
    void allDaySchedulesNeverConflict() {
        assertTrue(c.create(allDay("학회", "2026-11-10")).success());
        assertTrue(c.findConflicts(draft("수업", "2026-11-10", "09:00", "10:00"), null, null).isEmpty());
        assertTrue(c.create(draft("수업", "2026-11-10", "09:00", "10:00")).success());
        assertTrue(c.findConflicts(allDay("휴가", "2026-11-10"), null, null).isEmpty());
        assertEquals(1, c.findConflicts(draft("겹침", "2026-11-10", "09:30", "10:30"), null, null).size());
    }

    @Test
    void allDaySchedulesDoNotOccupyFreeTime() {
        assertTrue(c.create(allDay("학회", "2026-11-10")).success());
        assertTrue(c.create(draft("수업", "2026-11-10", "10:00", "11:00")).success());
        List<TimeSlot> slots = c.findFreeSlots(d("2026-11-10"), 60, t("09:00"), t("13:00"));
        assertEquals(List.of(new TimeSlot(d("2026-11-10"), t("09:00"), t("10:00")),
                new TimeSlot(d("2026-11-10"), t("11:00"), t("13:00"))), slots);
    }

    @Test
    void stateIsScheduledBeforeTheDayInProgressAllDayAndDelayedAfter() {
        assertTrue(c.create(allDay("학회", "2026-11-10")).success());
        Occurrence o = f.occurrenceOn("학회", "2026-11-10");
        f.clock.set("2026-11-09T23:59:59");
        assertEquals(ScheduleState.SCHEDULED, c.stateOf(o));
        f.clock.set("2026-11-10T00:00");
        assertEquals(ScheduleState.IN_PROGRESS, c.stateOf(o));
        f.clock.set("2026-11-10T23:59:30");
        assertEquals(ScheduleState.IN_PROGRESS, c.stateOf(o));
        f.clock.set("2026-11-11T00:00");
        assertEquals(ScheduleState.DELAYED, c.stateOf(o));
        assertTrue(c.setCompleted(o, true).success());
        assertEquals(ScheduleState.DONE, c.stateOf(f.occurrenceOn("학회", "2026-11-10")));
    }

    @Test
    void statisticsCountAllDayButLeaveItOutOfCategoryMinutes() {
        assertTrue(c.create(allDay("휴가", "2026-11-10", RepeatRule.NONE, Category.PERSONAL_ID)).success());
        assertTrue(c.create(draft("수업", "2026-11-11", "13:00", "14:30")).success());
        assertTrue(c.setCompleted(f.occurrenceOn("휴가", "2026-11-10"), true).success());

        WeeklyStatistics stats = c.weeklyStatistics(d("2026-11-12"));
        assertEquals(2, stats.total());
        assertEquals(1, stats.completed());
        assertEquals(0.5, stats.completionRate());
        assertEquals(Map.of("class", 90L), stats.minutesByCategory());
        assertEquals(1, stats.countByDay().get(DayOfWeek.TUESDAY));
    }

    @Test
    void thisOnlyEditOfAnAllDaySeriesStaysAllDay() {
        assertTrue(c.create(allDay("휴가", "2026-11-09", RepeatRule.WEEKLY, "class")).success());
        Occurrence target = f.occurrenceOn("휴가", "2026-11-16");
        ScheduleDraft edited = c.draftOf(target);
        ScheduleDraft renamed = new ScheduleDraft("특별 휴가", edited.date(), edited.startTime(), edited.endTime(),
                edited.categoryId(), edited.memo(), edited.repeatRule(), edited.repeatEndDate(), edited.reminder(),
                edited.allDay());
        assertTrue(c.update(target, renamed, EditScope.THIS_ONLY).success());
        Occurrence single = f.occurrenceOn("특별 휴가", "2026-11-16");
        assertTrue(single.allDay());
        assertFalse(single.isRepeating());
        assertTrue(f.occurrenceOn("휴가", "2026-11-23").allDay());
    }

    @Test
    void followingEditCanTurnAllDayOffFromThatDayOnly() {
        assertTrue(c.create(allDay("운동", "2026-11-09", RepeatRule.WEEKLY, "class")).success());
        Occurrence target = f.occurrenceOn("운동", "2026-11-23");
        ScheduleDraft timed = new ScheduleDraft("운동", d("2026-11-23"), t("07:00"), t("08:00"), "class", "",
                RepeatRule.WEEKLY, null, ReminderOption.NONE, false);
        assertTrue(c.update(target, timed, EditScope.THIS_AND_FOLLOWING).success());
        assertTrue(f.occurrenceOn("운동", "2026-11-16").allDay());
        Occurrence later = f.occurrenceOn("운동", "2026-11-30");
        assertFalse(later.allDay());
        assertEquals(t("07:00"), later.startTime());

        ScheduleDraft backToAllDay = allDay("운동", "2026-11-09", RepeatRule.WEEKLY, "class");
        assertTrue(c.update(f.occurrenceOn("운동", "2026-11-09"), backToAllDay, EditScope.THIS_AND_FOLLOWING)
                .success());
        assertTrue(f.occurrenceOn("운동", "2026-11-09").allDay());
    }

    @Test
    void undoAndRedoRestoreAllDay() {
        assertTrue(c.create(draft("수업", "2026-11-10", "13:00", "14:00")).success());
        Occurrence timed = f.occurrenceOn("수업", "2026-11-10");
        assertTrue(c.update(timed, allDay("수업", "2026-11-10"), EditScope.THIS_ONLY).success());
        assertTrue(f.occurrenceOn("수업", "2026-11-10").allDay());

        assertTrue(c.undo().success());
        Occurrence back = f.occurrenceOn("수업", "2026-11-10");
        assertFalse(back.allDay());
        assertEquals(t("13:00"), back.startTime());
        assertTrue(c.redo().success());
        assertTrue(f.occurrenceOn("수업", "2026-11-10").allDay());
    }

    @Test
    void snapshotComparisonSeesAllDayAlone() {
        Schedule timed = new Schedule("a", "t", d("2026-11-10"), Schedule.ALL_DAY_START, Schedule.ALL_DAY_END,
                "class", "", RepeatRule.NONE, null, ReminderOption.NONE, false);
        Schedule whole = timed.copy();
        whole.setAllDay(true);
        assertFalse(ScheduleSnapshot.sameValues(timed, whole));
        assertTrue(ScheduleSnapshot.sameValues(whole, whole.copy()));
    }
}
