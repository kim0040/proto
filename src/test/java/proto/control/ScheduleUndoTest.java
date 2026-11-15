package proto.control;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import proto.entity.Category;
import proto.entity.Occurrence;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.entity.Schedule;
import proto.repository.ScheduleRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static proto.TestData.d;
import static proto.TestData.t;
import static proto.control.ControllerFixture.draft;
import static proto.control.ControllerFixture.weekly;

/** 일정 변경 되돌리기·다시 실행. 시계는 2026-11-09(월) 10:00, 매주 월요일 회의는 11-09, 11-16, 11-23, 11-30 */
class ScheduleUndoTest {

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

    /** 저장소의 일정 전체를 id·값·완료·제외 날짜까지 문자열로 (정렬해서 비교) */
    private List<String> state() {
        return f.scheduleRepo.getAll().stream()
                .map(s -> s.getId() + "|" + s.getTitle() + "|" + s.getDate() + "|" + s.getStartTime() + "-"
                        + s.getEndTime() + "|" + s.getCategoryId() + "|" + s.getRepeatRule() + "|"
                        + s.getRepeatEndDate() + "|" + s.getCompletedDates() + "|" + s.getExcludedDates())
                .sorted().toList();
    }

    /** 되돌린 뒤 저장 파일을 다시 읽은 결과도 메모리와 같은지 */
    private void assertFileMatchesMemory() {
        ScheduleRepository reloaded = new ScheduleRepository(f.scheduleFile);
        reloaded.load();
        assertEquals(f.scheduleRepo.getAll().size(), reloaded.getAll().size());
        for (Schedule s : reloaded.getAll()) {
            Schedule live = f.scheduleRepo.findById(s.getId()).orElseThrow();
            assertTrue(ScheduleSnapshot.sameValues(s, live), s.toString());
        }
    }

    // ---- 등록 ----

    @Test
    void undoCreateRemovesItAndRedoBringsBackTheSameId() {
        assertTrue(c.create(draft("a", "2026-11-10", "09:00", "10:00")).success());
        String id = f.scheduleRepo.getAll().get(0).getId();
        assertEquals(Optional.of("일정 추가"), c.undoLabel());

        assertTrue(c.undo().success());
        assertTrue(f.scheduleRepo.getAll().isEmpty());
        assertFalse(c.canUndo());
        assertTrue(c.canRedo());
        assertEquals(Optional.of("일정 추가"), c.redoLabel());
        assertFileMatchesMemory();

        assertTrue(c.redo().success());
        assertEquals(id, f.scheduleRepo.getAll().get(0).getId());
        assertTrue(c.canUndo());
        assertFalse(c.canRedo());
        assertFileMatchesMemory();
    }

    @Test
    void undoAndRedoSaveAndNotifyListeners() {
        c.create(draft("a", "2026-11-10", "09:00", "10:00"));
        assertEquals(1, f.recorder.changed);
        c.undo();
        assertEquals(2, f.recorder.changed);
        c.redo();
        assertEquals(3, f.recorder.changed);
        assertTrue(f.recorder.storageErrors.isEmpty());
    }

    // ---- 수정 ----

    @Test
    void undoPlainUpdateRestoresOldValuesAndCompletion() {
        c.create(draft("a", "2026-11-10", "09:00", "10:00"));
        c.setCompleted(f.occurrenceOn("a", "2026-11-10"), true);
        List<String> before = state();
        c.update(f.occurrenceOn("a", "2026-11-10"), draft("b", "2026-11-12", "11:00", "12:00"), EditScope.THIS_ONLY);
        List<String> after = state();
        assertEquals(Optional.of("일정 수정"), c.undoLabel());

        assertTrue(c.undo().success());
        assertEquals(before, state());
        assertEquals(List.of("a@2026-11-10 09:00 완료"), f.summary("2026-11-01", "2026-11-30"));

        assertTrue(c.redo().success());
        assertEquals(after, state());
        assertFileMatchesMemory();
    }

    @Test
    void undoThisOnlyUpdateOfRepeatingScheduleRemovesTheDetachedCopyAndExclusion() {
        createWeeklyMeeting();
        List<String> before = state();
        c.update(meetingOn("2026-11-16"), weekly("회의(변경)", "2026-11-17", "17:00", "18:00"), EditScope.THIS_ONLY);
        assertEquals(2, f.scheduleRepo.getAll().size());
        List<String> after = state();

        assertTrue(c.undo().success());
        assertEquals(before, state());
        assertEquals(List.of("회의@2026-11-09 15:00", "회의@2026-11-16 15:00", "회의@2026-11-23 15:00",
                "회의@2026-11-30 15:00"), f.summary("2026-11-09", "2026-11-30"));

        assertTrue(c.redo().success());
        assertEquals(after, state());
    }

    @Test
    void undoFollowingUpdateJoinsTheSplitSeriesBackTogether() {
        createWeeklyMeeting();
        c.setCompleted(meetingOn("2026-11-23"), true);
        List<String> before = state();
        c.update(meetingOn("2026-11-16"), weekly("회의B", "2026-11-17", "16:00", "17:00"), EditScope.THIS_AND_FOLLOWING);
        List<String> after = state();
        assertEquals(2, after.size());

        assertTrue(c.undo().success());
        assertEquals(before, state());
        assertEquals(List.of("회의@2026-11-09 15:00", "회의@2026-11-16 15:00", "회의@2026-11-23 15:00 완료",
                "회의@2026-11-30 15:00"), f.summary("2026-11-09", "2026-11-30"));

        assertTrue(c.redo().success());
        assertEquals(after, state());
        assertFileMatchesMemory();
    }

    // ---- 삭제 ----

    @Test
    void undoDeleteRestoresTheScheduleWithTheSameId() {
        c.create(draft("a", "2026-11-10", "09:00", "10:00"));
        String id = f.scheduleRepo.getAll().get(0).getId();
        c.delete(f.occurrenceOn("a", "2026-11-10"), EditScope.THIS_ONLY);
        assertTrue(f.scheduleRepo.getAll().isEmpty());
        assertEquals(Optional.of("일정 삭제"), c.undoLabel());

        assertTrue(c.undo().success());
        assertEquals(id, f.scheduleRepo.getAll().get(0).getId());
        assertTrue(c.findOccurrence(id, d("2026-11-10")).isPresent());
        assertFileMatchesMemory();

        assertTrue(c.redo().success());
        assertTrue(f.scheduleRepo.getAll().isEmpty());
    }

    @Test
    void undoThisOnlyDeleteOfRepeatingScheduleBringsTheDateBack() {
        createWeeklyMeeting();
        c.setCompleted(meetingOn("2026-11-16"), true);
        List<String> before = state();
        c.delete(meetingOn("2026-11-16"), EditScope.THIS_ONLY);
        assertEquals(3, f.summary("2026-11-09", "2026-11-30").size());

        assertTrue(c.undo().success());
        assertEquals(before, state());
        assertEquals(List.of("회의@2026-11-09 15:00", "회의@2026-11-16 15:00 완료", "회의@2026-11-23 15:00",
                "회의@2026-11-30 15:00"), f.summary("2026-11-09", "2026-11-30"));
    }

    // ---- 완료 ----

    @Test
    void undoSetCompletedFlipsItBackWithMatchingLabels() {
        c.create(draft("a", "2026-11-10", "09:00", "10:00"));
        c.setCompleted(f.occurrenceOn("a", "2026-11-10"), true);
        assertEquals(Optional.of("완료 처리"), c.undoLabel());
        c.setCompleted(f.occurrenceOn("a", "2026-11-10"), false);
        assertEquals(Optional.of("완료 취소"), c.undoLabel());

        assertTrue(c.undo().success());
        assertTrue(f.occurrenceOn("a", "2026-11-10").completed());
        assertEquals(Optional.of("완료 처리"), c.undoLabel());
        assertEquals(Optional.of("완료 취소"), c.redoLabel());

        assertTrue(c.undo().success());
        assertFalse(f.occurrenceOn("a", "2026-11-10").completed());
        assertFileMatchesMemory();
    }

    // ---- 기록 규칙 ----

    @Test
    void nothingToUndoOrRedoFailsWithoutSavingOrNotifying() {
        assertFalse(c.canUndo());
        assertFalse(c.canRedo());
        assertEquals(Optional.empty(), c.undoLabel());
        assertEquals(Optional.empty(), c.redoLabel());

        OperationResult undo = c.undo();
        OperationResult redo = c.redo();

        assertFalse(undo.success());
        assertEquals("되돌릴 작업이 없어요", undo.message());
        assertFalse(redo.success());
        assertEquals("다시 실행할 작업이 없어요", redo.message());
        assertEquals(0, f.recorder.changed);
        assertFalse(Files.exists(f.scheduleFile));
    }

    @Test
    void failedCallsRecordNothing() {
        assertFalse(c.create(draft(" ", "2026-11-10", "09:00", "10:00")).success());
        assertFalse(c.canUndo());

        c.create(draft("a", "2026-11-10", "09:00", "10:00"));
        Occurrence o = f.occurrenceOn("a", "2026-11-10");
        c.undo();
        assertTrue(c.canRedo());

        // 이미 사라진 일정을 고치거나 지우거나 완료하려는 요청은 실패하고 기록도, 다시 실행 기록 지우기도 하지 않는다
        assertFalse(c.update(o, draft("b", "2026-11-10", "09:00", "10:00"), EditScope.THIS_ONLY).success());
        assertFalse(c.delete(o, EditScope.THIS_ONLY).success());
        assertFalse(c.setCompleted(o, true).success());
        assertFalse(c.canUndo());
        assertTrue(c.canRedo());
    }

    @Test
    void newChangeClearsRedo() {
        c.create(draft("a", "2026-11-10", "09:00", "10:00"));
        c.undo();
        assertTrue(c.canRedo());

        c.create(draft("b", "2026-11-11", "09:00", "10:00"));

        assertFalse(c.canRedo());
        assertEquals("다시 실행할 작업이 없어요", c.redo().message());
        assertEquals(List.of("b@2026-11-11 09:00"), f.summary("2026-11-01", "2026-11-30"));
    }

    @Test
    void keepsOnlyTheLastThirtySteps() {
        for (int i = 1; i <= 31; i++) {
            assertTrue(c.create(draft("일정" + i, "2026-11-10", "09:00", "10:00")).success());
        }
        for (int i = 0; i < 30; i++) {
            assertTrue(c.undo().success(), "undo " + i);
        }
        assertFalse(c.canUndo());
        assertFalse(c.undo().success());
        // 가장 오래된 첫 등록은 기록에서 빠졌으므로 남아 있다
        assertEquals(List.of("일정1@2026-11-10 09:00"), f.summary("2026-11-10", "2026-11-10"));
        for (int i = 0; i < 30; i++) {
            assertTrue(c.redo().success(), "redo " + i);
        }
        assertEquals(31, f.scheduleRepo.getAll().size());
    }

    @Test
    void deletingACategoryClearsTheHistory() {
        c.create(draft("a", "2026-11-10", "09:00", "10:00")); // 카테고리 "class"
        c.create(draft("b", "2026-11-11", "09:00", "10:00"));
        c.undo();
        assertTrue(c.canUndo());
        assertTrue(c.canRedo());

        assertTrue(f.categories.delete("class").success());

        assertFalse(c.canUndo());
        assertFalse(c.canRedo());
        assertEquals(Category.PERSONAL_ID, f.occurrenceOn("a", "2026-11-10").categoryId());
    }

    @Test
    void restoredSchedulesAreNotSharedWithTheHistory() {
        c.create(draft("a", "2026-11-10", "09:00", "10:00"));
        String id = f.scheduleRepo.getAll().get(0).getId();
        c.delete(f.occurrenceOn("a", "2026-11-10"), EditScope.THIS_ONLY);
        c.undo();

        // 되살린 객체를 직접 고쳐도 (controller 밖의 변경을 흉내) 기록 속 값은 그대로여야 한다
        Schedule restored = f.scheduleRepo.findById(id).orElseThrow();
        restored.setTitle("망가뜨림");
        restored.setCompletedOn(d("2026-11-10"), true);
        c.redo();
        c.undo();

        Schedule again = f.scheduleRepo.findById(id).orElseThrow();
        assertNotSame(restored, again);
        assertEquals("a", again.getTitle());
        assertFalse(again.isCompletedOn(d("2026-11-10")));
    }

    @Test
    void snapshotIsTakenBeforeInPlaceCompletionChange() {
        c.create(draft("a", "2026-11-10", "09:00", "10:00"));
        Schedule live = f.scheduleRepo.getAll().get(0);
        c.setCompleted(f.occurrenceOn("a", "2026-11-10"), true);
        assertTrue(live.isCompletedOn(d("2026-11-10")));

        c.undo();

        assertFalse(f.occurrenceOn("a", "2026-11-10").completed());
    }

    // ---- 복제·붙여넣기 ----

    @Test
    void copyDraftKeepsValuesOnTheNewDateWithoutRepeat() {
        ScheduleDraft series = new ScheduleDraft("회의", d("2026-11-09"), t("15:00"), t("16:00"), "team",
                "메모", RepeatRule.WEEKLY, d("2026-12-31"), ReminderOption.FIVE_MINUTES);
        c.create(series);
        c.setCompleted(meetingOn("2026-11-16"), true);

        ScheduleDraft copy = c.copyDraft(meetingOn("2026-11-16"), d("2026-11-20"));

        assertEquals(new ScheduleDraft("회의", d("2026-11-20"), t("15:00"), t("16:00"), "team", "메모",
                RepeatRule.NONE, null, ReminderOption.FIVE_MINUTES), copy);
        assertTrue(c.create(copy).success());
        assertEquals(Optional.of("일정 추가"), c.undoLabel());
        Occurrence pasted = f.scheduleRepo.getAll().stream()
                .filter(s -> !s.isRepeating()).map(s -> Occurrence.of(s, d("2026-11-20"))).findFirst().orElseThrow();
        assertFalse(pasted.completed());
        assertNull(f.scheduleRepo.findById(pasted.scheduleId()).orElseThrow().getRepeatEndDate());
    }

    @Test
    void copyDraftFallsBackToPersonalWhenTheCategoryWasDeleted() {
        c.create(draft("a", "2026-11-10", "09:00", "10:00"));
        Occurrence copied = f.occurrenceOn("a", "2026-11-10"); // 카테고리 "class"
        f.categories.delete("class");

        ScheduleDraft copy = c.copyDraft(copied, d("2026-11-11"));

        assertEquals(Category.PERSONAL_ID, copy.categoryId());
        assertTrue(c.create(copy).success());
    }

    @Test
    void 단계_번호는_기록마다_새로_붙고_되돌리면_이전_단계로_돌아가며_색_없는_변경은_번호를_바꾸지_않는다() {
        assertEquals(0, c.undoStepId());
        createWeeklyMeeting();
        long first = c.undoStepId();
        assertTrue(first > 0);
        assertTrue(c.setColor(meetingOn("2026-11-09"), null).success()); // 이미 그 색: 기록 없음
        assertEquals(first, c.undoStepId());
        assertTrue(c.setCompleted(meetingOn("2026-11-09"), true).success());
        long second = c.undoStepId();
        assertTrue(second > first);
        assertTrue(c.undo().success());
        assertEquals(first, c.undoStepId());
        assertTrue(c.setCompleted(meetingOn("2026-11-09"), true).success());
        assertTrue(c.undoStepId() > second);
    }

    @Test
    void 글을_날짜로_읽는다_상대_표현은_controller_시계_기준() {
        assertEquals(Optional.of(d("2026-11-10")), c.resolveDate("내일"));
        assertEquals(Optional.empty(), c.resolveDate("회의 준비"));
    }
}
