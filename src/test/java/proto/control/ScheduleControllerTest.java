package proto.control;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import proto.entity.Occurrence;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.entity.Schedule;
import proto.entity.ScheduleState;
import proto.entity.TimeSlot;
import proto.entity.WeeklyStatistics;
import proto.repository.ScheduleRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static proto.TestData.d;
import static proto.TestData.t;
import static proto.control.ControllerFixture.draft;
import static proto.control.ControllerFixture.weekly;

/** 시계는 2026-11-09(월) 10:00 에 고정. 매주 월요일 회의는 11-09, 11-16, 11-23, 11-30 에 나타난다 */
class ScheduleControllerTest {

    @TempDir
    Path dir;
    ControllerFixture f;
    ScheduleController c;

    @BeforeEach
    void setUp() {
        f = new ControllerFixture(dir, "2026-11-09T10:00");
        c = f.schedules;
    }

    private void createWeeklyMeeting() {
        assertTrue(c.create(weekly("회의", "2026-11-09", "15:00", "16:00")).success());
    }

    private Occurrence meetingOn(String date) {
        return f.occurrenceOn("회의", date);
    }

    // ---- 등록 / 저장 ----

    @Test
    void createPersistsAndReloadsFromFile() {
        ScheduleDraft d = new ScheduleDraft("  수업  ", d("2026-11-10"), t("09:00"), t("10:00"), "class",
                "메모, 쉼표\n둘째 줄", RepeatRule.NONE, d("2026-12-01"), ReminderOption.FIVE_MINUTES);
        assertTrue(c.create(d).success());

        assertEquals(1, f.recorder.changed);
        ScheduleRepository reloaded = new ScheduleRepository(f.scheduleFile);
        reloaded.load();
        assertEquals(1, reloaded.getAll().size());
        Schedule s = reloaded.getAll().get(0);
        assertEquals("수업", s.getTitle());
        assertEquals("메모, 쉼표\n둘째 줄", s.getMemo());
        assertEquals(RepeatRule.NONE, s.getRepeatRule());
        assertNull(s.getRepeatEndDate()); // 반복이 없으면 반복 종료일은 비워진다
        assertEquals(ReminderOption.FIVE_MINUTES, s.getReminder());
        assertEquals(36, s.getId().length()); // UUID
    }

    @Test
    void invalidDraftIsRejectedWithoutSavingOrNotifying() {
        OperationResult r = c.create(draft("  ", "2026-11-10", "10:00", "10:00"));
        assertFalse(r.success());
        assertEquals(List.of("제목을 입력해 주세요", "끝 시간은 시작 시간보다 늦어야 해요"), r.errors());
        assertEquals(0, f.recorder.changed);
        assertFalse(Files.exists(f.scheduleFile));
        assertTrue(c.getOccurrences(d("2026-11-01"), d("2026-11-30"), null, null).isEmpty());
    }

    @Test
    void unknownCategoryIsRejected() {
        ScheduleDraft d = new ScheduleDraft("a", d("2026-11-10"), t("09:00"), t("10:00"), "nope", "",
                RepeatRule.NONE, null, ReminderOption.NONE);
        assertEquals(List.of("카테고리를 선택해 주세요"), c.validate(d));
    }

    @Test
    void storageFailureIsReportedButTheChangeStaysInMemory() throws IOException {
        Path blocker = dir.resolve("blocker");
        Files.writeString(blocker, "파일이라서 폴더로 못 씀");
        ScheduleRepository broken = new ScheduleRepository(blocker.resolve("schedules.csv"));
        ScheduleController controller = new ScheduleController(broken, f.categoryRepo, f.clock);
        ControllerFixture.Recorder recorder = new ControllerFixture.Recorder();
        controller.addListener(recorder);

        assertTrue(controller.create(draft("a", "2026-11-10", "09:00", "10:00")).success());

        assertEquals(1, recorder.storageErrors.size());
        assertTrue(recorder.storageErrors.get(0).startsWith("저장하지 못했어요: "));
        assertEquals(1, recorder.changed);
        assertEquals(1, controller.getOccurrences(d("2026-11-10"), d("2026-11-10"), null, null).size());
    }

    @Test
    void loadWarningsAreReportedThroughStorageFailed() throws IOException {
        Files.writeString(f.scheduleFile, "id,title\nbroken\n");
        f.scheduleRepo.load();
        c.reportLoadWarnings();
        assertEquals(1, f.recorder.storageErrors.size());
    }

    // ---- 조회 ----

    @Test
    void occurrencesAreFilteredAndSortedByDateStartAndTitle() {
        c.create(draft("나중", "2026-11-10", "13:00", "14:00"));
        c.create(draft("b 회의", "2026-11-10", "09:00", "10:00"));
        c.create(draft("a 회의", "2026-11-10", "09:00", "10:00"));
        c.create(draft("어제", "2026-11-09", "23:00", "23:30"));

        assertEquals(List.of("어제", "a 회의", "b 회의", "나중"),
                c.getOccurrences(d("2026-11-01"), d("2026-11-30"), null, null).stream().map(Occurrence::title).toList());
        assertEquals(List.of("a 회의", "b 회의"),
                c.getOccurrences(d("2026-11-01"), d("2026-11-30"), "class", "회의").stream().map(Occurrence::title).toList());
        assertTrue(c.getOccurrences(d("2026-11-01"), d("2026-11-30"), "team", null).isEmpty());
    }

    @Test
    void stateUsesTheControllerClock() {
        c.create(draft("a", "2026-11-09", "10:00", "11:00"));
        Occurrence o = f.occurrenceOn("a", "2026-11-09");
        assertEquals(ScheduleState.IN_PROGRESS, c.stateOf(o));
        f.clock.set("2026-11-09T11:00");
        assertEquals(ScheduleState.DELAYED, c.stateOf(o));
        assertEquals(d("2026-11-09"), c.today());
    }

    @Test
    void draftsCarryTheRightDefaultsAndExistingValues() {
        ScheduleDraft blank = c.newDraft(d("2026-11-12"));
        assertEquals(proto.entity.Category.PERSONAL_ID, blank.categoryId());
        assertEquals(RepeatRule.NONE, blank.repeatRule());
        assertEquals(ReminderOption.NONE, blank.reminder());
        assertEquals(d("2026-11-12"), blank.date());

        ScheduleDraft slotDraft = c.draftForSlot(new TimeSlot(d("2026-11-12"), t("13:00"), t("14:30")));
        assertEquals(t("13:00"), slotDraft.startTime());
        assertEquals(t("14:30"), slotDraft.endTime());

        c.create(new ScheduleDraft("회의", d("2026-11-09"), t("15:00"), t("16:00"), "team", "m",
                RepeatRule.WEEKLY, d("2026-12-31"), ReminderOption.TEN_MINUTES));
        ScheduleDraft back = c.draftOf(meetingOn("2026-11-16"));
        assertEquals(d("2026-11-16"), back.date());
        assertEquals(d("2026-12-31"), back.repeatEndDate());
        assertEquals("team", back.categoryId());
    }

    // ---- 수정 ----

    @Test
    void nonRepeatingUpdateChangesInPlaceAndMovesCompletion() {
        c.create(draft("a", "2026-11-10", "09:00", "10:00"));
        Occurrence o = f.occurrenceOn("a", "2026-11-10");
        c.setCompleted(o, true);

        assertTrue(c.update(f.occurrenceOn("a", "2026-11-10"), draft("b", "2026-11-12", "11:00", "12:00"),
                EditScope.THIS_ONLY).success());

        assertEquals(List.of("b@2026-11-12 11:00 완료"), f.summary("2026-11-01", "2026-11-30"));
        assertEquals(1, f.scheduleRepo.getAll().size());
    }

    @Test
    void updateThisOnlyOnWeeklySeriesDetachesOneDate() {
        createWeeklyMeeting();
        OperationResult r = c.update(meetingOn("2026-11-16"),
                weekly("회의(변경)", "2026-11-17", "17:00", "18:00"), EditScope.THIS_ONLY);

        assertTrue(r.success());
        assertEquals(List.of("회의@2026-11-09 15:00", "회의(변경)@2026-11-17 17:00",
                "회의@2026-11-23 15:00", "회의@2026-11-30 15:00"), f.summary("2026-11-09", "2026-11-30"));
        assertEquals(RepeatRule.NONE, f.occurrenceOn("회의(변경)", "2026-11-17").repeatRule());
        assertEquals(2, f.scheduleRepo.getAll().size());
    }

    @Test
    void updateThisOnlyKeepsCompletionOfTheDetachedDate() {
        createWeeklyMeeting();
        c.setCompleted(meetingOn("2026-11-16"), true);
        c.update(meetingOn("2026-11-16"), draft("회의(변경)", "2026-11-17", "17:00", "18:00"), EditScope.THIS_ONLY);

        assertEquals(List.of("회의@2026-11-09 15:00", "회의(변경)@2026-11-17 17:00 완료",
                "회의@2026-11-23 15:00", "회의@2026-11-30 15:00"), f.summary("2026-11-09", "2026-11-30"));
    }

    @Test
    void updateFollowingFromALaterDateSplitsTheSeries() {
        createWeeklyMeeting();
        c.setCompleted(meetingOn("2026-11-09"), true);
        c.setCompleted(meetingOn("2026-11-23"), true);

        assertTrue(c.update(meetingOn("2026-11-16"), weekly("회의B", "2026-11-16", "16:00", "17:00"),
                EditScope.THIS_AND_FOLLOWING).success());

        assertEquals(List.of("회의@2026-11-09 15:00 완료", "회의B@2026-11-16 16:00",
                "회의B@2026-11-23 16:00 완료", "회의B@2026-11-30 16:00"), f.summary("2026-11-09", "2026-11-30"));
        Schedule original = f.scheduleRepo.getAll().stream().filter(s -> s.getTitle().equals("회의")).findFirst().orElseThrow();
        assertEquals(d("2026-11-15"), original.getRepeatEndDate());
        assertEquals(2, f.scheduleRepo.getAll().size());
    }

    @Test
    void updateFollowingDropsCompletionsThatNoLongerMatchTheNewRule() {
        createWeeklyMeeting();
        c.setCompleted(meetingOn("2026-11-23"), true);

        // 화요일(11-17)로 옮겨서 매주 화요일로 바뀌면 월요일에 한 완료 표시는 사라진다
        c.update(meetingOn("2026-11-16"), weekly("회의B", "2026-11-17", "16:00", "17:00"), EditScope.THIS_AND_FOLLOWING);

        assertEquals(List.of("회의@2026-11-09 15:00", "회의B@2026-11-17 16:00", "회의B@2026-11-24 16:00"),
                f.summary("2026-11-09", "2026-11-30"));
    }

    @Test
    void updateFollowingOnTheFirstDateRewritesTheSeriesInPlace() {
        createWeeklyMeeting();
        String id = meetingOn("2026-11-09").scheduleId();
        c.setCompleted(meetingOn("2026-11-16"), true);

        assertTrue(c.update(meetingOn("2026-11-09"), weekly("새회의", "2026-11-09", "17:00", "18:00"),
                EditScope.THIS_AND_FOLLOWING).success());

        assertEquals(List.of("새회의@2026-11-09 17:00", "새회의@2026-11-16 17:00 완료",
                "새회의@2026-11-23 17:00", "새회의@2026-11-30 17:00"), f.summary("2026-11-09", "2026-11-30"));
        assertEquals(1, f.scheduleRepo.getAll().size());
        assertEquals(id, f.scheduleRepo.getAll().get(0).getId());
    }

    @Test
    void updateOfAnAlreadyRemovedOccurrenceFails() {
        createWeeklyMeeting();
        Occurrence stale = meetingOn("2026-11-16");
        c.delete(stale, EditScope.THIS_ONLY);
        OperationResult r = c.update(stale, weekly("x", "2026-11-16", "09:00", "10:00"), EditScope.THIS_ONLY);
        assertFalse(r.success());
        assertTrue(c.findOccurrence(stale.scheduleId(), d("2026-11-16")).isEmpty());
    }

    // ---- 삭제 ----

    @Test
    void deleteThisOnlyRemovesOneDate() {
        createWeeklyMeeting();
        assertTrue(c.delete(meetingOn("2026-11-16"), EditScope.THIS_ONLY).success());
        assertEquals(List.of("회의@2026-11-09 15:00", "회의@2026-11-23 15:00", "회의@2026-11-30 15:00"),
                f.summary("2026-11-09", "2026-11-30"));
    }

    @Test
    void deleteFollowingEndsTheSeriesTheDayBefore() {
        createWeeklyMeeting();
        assertTrue(c.delete(meetingOn("2026-11-23"), EditScope.THIS_AND_FOLLOWING).success());
        assertEquals(List.of("회의@2026-11-09 15:00", "회의@2026-11-16 15:00"), f.summary("2026-11-09", "2026-12-31"));
    }

    @Test
    void deleteFollowingOnTheFirstDateRemovesTheWholeSchedule() {
        createWeeklyMeeting();
        assertTrue(c.delete(meetingOn("2026-11-09"), EditScope.THIS_AND_FOLLOWING).success());
        assertTrue(f.scheduleRepo.getAll().isEmpty());
    }

    @Test
    void deletingTheLastRemainingOccurrenceRemovesTheSeries() {
        ScheduleDraft oneWeek = new ScheduleDraft("회의", d("2026-11-09"), t("15:00"), t("16:00"), "class", "",
                RepeatRule.WEEKLY, d("2026-11-15"), ReminderOption.NONE);
        c.create(oneWeek);
        assertTrue(c.delete(meetingOn("2026-11-09"), EditScope.THIS_ONLY).success());
        assertTrue(f.scheduleRepo.getAll().isEmpty());
    }

    @Test
    void deleteNonRepeatingIgnoresScopeAndPersists() {
        c.create(draft("a", "2026-11-10", "09:00", "10:00"));
        assertTrue(c.delete(f.occurrenceOn("a", "2026-11-10"), EditScope.THIS_ONLY).success());
        ScheduleRepository reloaded = new ScheduleRepository(f.scheduleFile);
        reloaded.load();
        assertTrue(reloaded.getAll().isEmpty());
    }

    // ---- 완료 ----

    @Test
    void setCompletedOnlyAffectsThatDateOfASeries() {
        createWeeklyMeeting();
        assertTrue(c.setCompleted(meetingOn("2026-11-16"), true).success());
        assertEquals(List.of("회의@2026-11-09 15:00", "회의@2026-11-16 15:00 완료", "회의@2026-11-23 15:00"),
                f.summary("2026-11-09", "2026-11-23"));
        assertTrue(c.setCompleted(meetingOn("2026-11-16"), false).success());
        assertEquals(List.of("회의@2026-11-09 15:00", "회의@2026-11-16 15:00", "회의@2026-11-23 15:00"),
                f.summary("2026-11-09", "2026-11-23"));
    }

    @Test
    void everySuccessfulChangeNotifiesListenersOnce() {
        c.create(draft("a", "2026-11-10", "09:00", "10:00"));
        assertEquals(1, f.recorder.changed);
        c.setCompleted(f.occurrenceOn("a", "2026-11-10"), true);
        assertEquals(2, f.recorder.changed);
        c.update(f.occurrenceOn("a", "2026-11-10"), draft("b", "2026-11-10", "09:00", "10:00"), EditScope.THIS_ONLY);
        assertEquals(3, f.recorder.changed);
        c.delete(f.occurrenceOn("b", "2026-11-10"), EditScope.THIS_ONLY);
        assertEquals(4, f.recorder.changed);
        c.create(draft(" ", "2026-11-10", "09:00", "10:00"));
        assertEquals(4, f.recorder.changed);
    }

    // ---- 겹침 ----

    @Test
    void conflictsExcludeTheScheduleBeingEdited() {
        c.create(draft("A", "2026-11-10", "09:00", "10:00"));
        Occurrence a = f.occurrenceOn("A", "2026-11-10");

        assertEquals(List.of(a), c.findConflicts(draft("새", "2026-11-10", "09:30", "10:30"), null, null));
        assertTrue(c.findConflicts(draft("새", "2026-11-10", "10:00", "11:00"), null, null).isEmpty());
        assertTrue(c.findConflicts(draft("A", "2026-11-10", "09:30", "10:30"), a, EditScope.THIS_ONLY).isEmpty());
        assertTrue(c.findConflicts(draft(" ", "2026-11-10", "09:30", "10:30"), null, null).isEmpty());
    }

    @Test
    void conflictsForThisOnlyIgnoreOnlyTheEditedDateAndTreatTheDraftAsNonRepeating() {
        createWeeklyMeeting();
        c.create(draft("B", "2026-11-16", "15:30", "16:30"));
        Occurrence b = f.occurrenceOn("B", "2026-11-16");
        Occurrence edited = meetingOn("2026-11-16");

        // 새로 등록하면 같은 날 회의(15:00~16:00)와 B가 모두 겹친다
        assertEquals(List.of(edited, b),
                c.findConflicts(draft("x", "2026-11-16", "15:30", "16:30"), null, null));
        // 그 날짜 회의를 "이 일정만" 고치면 회의 자신은 빠지고 B만 남는다 (반복 설정은 무시)
        assertEquals(List.of(b), c.findConflicts(weekly("x", "2026-11-16", "15:30", "16:30"), edited, EditScope.THIS_ONLY));
    }

    @Test
    void conflictsForThisAndFollowingIgnoreTheSeriesFromThatDateOn() {
        createWeeklyMeeting();
        c.create(draft("B", "2026-11-16", "15:30", "16:30"));
        Occurrence b = f.occurrenceOn("B", "2026-11-16");
        Occurrence edited = meetingOn("2026-11-16");

        assertEquals(List.of(b), c.findConflicts(weekly("x", "2026-11-16", "15:30", "16:30"), edited,
                EditScope.THIS_AND_FOLLOWING));
        // 11-09 회의는 수정 범위 밖이라 그 날짜와 겹치면 충돌로 보인다
        Occurrence first = meetingOn("2026-11-09");
        assertEquals(List.of(first), c.findConflicts(draft("x", "2026-11-09", "15:30", "16:30"), edited,
                EditScope.THIS_AND_FOLLOWING));
    }

    // ---- 빈 시간 / 통계 ----

    @Test
    void freeSlotsStartFromNowTodayAndSkipTodaysSchedules() {
        createWeeklyMeeting();
        assertEquals(List.of(new TimeSlot(d("2026-11-09"), t("10:00"), t("15:00")),
                        new TimeSlot(d("2026-11-09"), t("16:00"), t("22:00"))),
                c.findFreeSlots(d("2026-11-09"), 30, t("09:00"), t("22:00")));
        assertEquals(List.of(new TimeSlot(d("2026-11-10"), t("09:00"), t("22:00"))),
                c.findFreeSlots(d("2026-11-10"), 480, t("09:00"), t("22:00")));
    }

    @Test
    void weeklyStatisticsCoversMondayToSundayOfTheGivenDay() {
        createWeeklyMeeting();
        c.create(draft("어제 일", "2026-11-09", "08:00", "09:00")); // 지금(10:00) 기준 끝났고 미완료 → 지연
        WeeklyStatistics s = c.weeklyStatistics(d("2026-11-12"));
        assertEquals(d("2026-11-09"), s.weekStart());
        assertEquals(2, s.total());
        assertEquals(0, s.completed());
        assertEquals(1, s.delayed());
        assertEquals(2, s.countByDay().get(java.time.DayOfWeek.MONDAY));
        assertEquals(120L, s.minutesByCategory().get("class")); // 60 + 60
    }
}
