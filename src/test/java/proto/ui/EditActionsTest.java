package proto.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import proto.TestData;
import proto.control.CategoryController;
import proto.control.ReminderTimer;
import proto.control.ScheduleController;
import proto.control.ScheduleDraft;
import proto.entity.Occurrence;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.ui.AppState.Mode;
import proto.ui.component.ActionToastCard;
import proto.ui.component.Overlay;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static proto.TestData.d;
import static proto.TestData.t;

/** 편의 기능: 복제, 복사·붙여넣기, 미루기, 되돌리기·다시 실행과 토스트 [되돌리기] 규칙 */
class EditActionsTest {

    private ScheduleController schedules;
    private UiContext ctx;
    private final List<String> toasts = new ArrayList<>();
    private Runnable lastUndoButton;
    private ActionToastCard lastCard;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        TestData.MutableClock clock = new TestData.MutableClock("2026-11-16T08:00:00");
        var scheduleRepo = new proto.repository.ScheduleRepository(dir.resolve("schedules.csv"));
        var categoryRepo = new proto.repository.CategoryRepository(dir.resolve("categories.csv"));
        categoryRepo.loadOrSeed();
        schedules = new ScheduleController(scheduleRepo, categoryRepo, clock);
        CategoryController categories = new CategoryController(categoryRepo, scheduleRepo, schedules);
        ctx = new UiContext(schedules, categories, new ReminderTimer(schedules, clock), dir);
        ctx.setOverlay(new Overlay() {
            @Override
            public void showToast(String message, boolean error) {
                toasts.add((error ? "!" : "") + message);
            }

            @Override
            public ActionToastCard showActionToast(String message, String actionLabel, Runnable onAction) {
                toasts.add(message + " [" + actionLabel + "]");
                lastUndoButton = onAction;
                lastCard = ActionToastCard.withAction(message, actionLabel, onAction);
                return lastCard;
            }

            @Override
            public void showNotice(String message) {
                toasts.add("~" + message);
            }
        });
    }

    private Occurrence add(String title, String date, String start, String end, RepeatRule repeat) {
        schedules.create(new ScheduleDraft(title, d(date), t(start), t(end), "class", "메모", repeat, null,
                ReminderOption.TEN_MINUTES));
        return schedules.getOccurrences(d(date), d(date), null, title).get(0);
    }

    private List<Occurrence> on(String date) {
        return schedules.getOccurrences(d(date), d(date), null, "");
    }

    @Test
    void 복제하면_같은_날_같은_시간에_반복_없는_완료_안_한_복사본이_생긴다() {
        Occurrence o = add("수업", "2026-11-16", "09:00", "10:00", RepeatRule.WEEKLY);
        schedules.setCompleted(o, true);
        toasts.clear();
        ctx.actions().duplicate(schedules.findOccurrence(o.scheduleId(), o.date()).orElseThrow());
        List<Occurrence> day = on("2026-11-16");
        assertEquals(2, day.size());
        Occurrence copy = day.stream().filter(x -> !x.scheduleId().equals(o.scheduleId())).findFirst().orElseThrow();
        assertEquals(RepeatRule.NONE, copy.repeatRule());
        assertFalse(copy.completed());
        assertEquals("수업", copy.title());
        assertEquals(t("09:00"), copy.startTime());
        assertEquals(List.of("일정을 복제했어요 [되돌리기]"), toasts);
    }

    @Test
    void 복사_후_붙여넣기는_보기에_맞는_날짜에_여러_번_붙는다() {
        Occurrence o = add("회의", "2026-11-17", "14:00", "15:00", RepeatRule.NONE);
        ctx.actions().copy(o);
        assertEquals("일정을 복사했어요", toasts.get(toasts.size() - 1));
        toasts.clear();
        ctx.state().setMode(Mode.TODAY);
        ctx.actions().paste();
        ctx.actions().paste();
        assertEquals(2, on("2026-11-16").size());
        assertEquals(t("14:00"), on("2026-11-16").get(0).startTime());
        assertEquals("‘회의’을(를) 11월 16일에 붙여 넣었어요 [되돌리기]", toasts.get(0));
        ctx.state().setMode(Mode.WEEK);
        ctx.state().setAnchor(d("2026-11-16"));
        ctx.state().setSelectedDay(d("2026-11-19"));
        ctx.actions().paste();
        assertEquals(1, on("2026-11-19").size());
        ctx.state().setSelectedDay(d("2026-12-01"));
        ctx.actions().paste();
        assertEquals(3, on("2026-11-16").size());
        ctx.state().setMode(Mode.MONTH);
        ctx.actions().paste();
        assertEquals(1, on("2026-12-01").size());
    }

    @Test
    void 복사한_게_없거나_통계_설정_보기면_붙여넣기는_아무것도_하지_않는다() {
        ctx.state().setMode(Mode.TODAY);
        ctx.actions().paste();
        assertTrue(toasts.isEmpty());
        Occurrence o = add("회의", "2026-11-17", "14:00", "15:00", RepeatRule.NONE);
        ctx.actions().copy(o);
        toasts.clear();
        ctx.state().setMode(Mode.STATS);
        ctx.actions().paste();
        assertTrue(toasts.isEmpty());
        assertEquals(1, schedules.getOccurrences(d("2026-11-01"), d("2026-11-30"), null, "").size());
    }

    @Test
    void 긴_제목은_줄여서_알린다() {
        Occurrence o = add("가".repeat(40), "2026-11-17", "14:00", "15:00", RepeatRule.NONE);
        ctx.actions().copy(o);
        ctx.state().setMode(Mode.TODAY);
        toasts.clear();
        ctx.actions().paste();
        assertEquals("‘" + "가".repeat(23) + "…’을(를) 11월 16일에 붙여 넣었어요 [되돌리기]", toasts.get(0));
    }

    @Test
    void 내일로_다음_주로_미루면_날짜만_밀리고_알림에_되돌리기가_붙는다() {
        Occurrence o = add("회의", "2026-11-16", "09:00", "10:00", RepeatRule.NONE);
        ctx.actions().postpone(o, 1, "내일로 미뤘어요");
        Occurrence next = on("2026-11-17").get(0);
        assertEquals(t("09:00"), next.startTime());
        ctx.actions().postpone(next, 7, "다음 주로 미뤘어요");
        assertEquals(1, on("2026-11-24").size());
        assertEquals(List.of("내일로 미뤘어요 [되돌리기]", "다음 주로 미뤘어요 [되돌리기]"), toasts);
    }

    @Test
    void 토스트의_되돌리기_버튼은_그_변경을_되돌린다() {
        Occurrence o = add("회의", "2026-11-16", "09:00", "10:00", RepeatRule.NONE);
        ctx.actions().duplicate(o);
        assertEquals(2, on("2026-11-16").size());
        lastUndoButton.run();
        assertEquals(1, on("2026-11-16").size());
        assertEquals("되돌렸어요 · 일정 추가", toasts.get(toasts.size() - 1));
    }

    @Test
    void 그_사이_다른_변경이_있으면_토스트_버튼은_아무것도_되돌리지_않는다() {
        Occurrence o = add("회의", "2026-11-16", "09:00", "10:00", RepeatRule.NONE);
        ctx.actions().duplicate(o);
        Runnable stale = lastUndoButton;
        add("점심", "2026-11-16", "12:00", "13:00", RepeatRule.NONE);
        stale.run();
        assertEquals(3, on("2026-11-16").size());
        assertEquals("~그 뒤에 다른 변경이 있어 되돌릴 수 없어요", toasts.get(toasts.size() - 1));
    }

    @Test
    void 토스트_버튼은_되돌린_뒤_다시_눌러도_두_번_되돌리지_않는다() {
        Occurrence o = add("회의", "2026-11-16", "09:00", "10:00", RepeatRule.NONE);
        ctx.actions().duplicate(o);
        Runnable button = lastUndoButton;
        button.run();
        button.run();
        assertEquals(1, on("2026-11-16").size());
        assertEquals("~그 뒤에 다른 변경이 있어 되돌릴 수 없어요", toasts.get(toasts.size() - 1));
    }

    @Test
    void 키로_되돌리고_다시_실행하면_이름표가_토스트에_나온다() {
        Occurrence o = add("회의", "2026-11-16", "09:00", "10:00", RepeatRule.NONE);
        schedules.setCompleted(o, true);
        toasts.clear();
        ctx.actions().undo();
        assertFalse(on("2026-11-16").get(0).completed());
        ctx.actions().redo();
        assertTrue(on("2026-11-16").get(0).completed());
        assertEquals(List.of("되돌렸어요 · 완료 처리", "다시 실행했어요 · 완료 처리"), toasts);
    }

    @Test
    void 되돌릴_것이_없으면_컨트롤러의_안내를_보여_준다() {
        ctx.actions().undo();
        ctx.actions().redo();
        assertEquals(List.of("~되돌릴 작업이 없어요", "~다시 실행할 작업이 없어요"), toasts);
    }

    @Test
    void 완료_토글과_삭제_알림에도_되돌리기가_붙는다() {
        Occurrence o = add("회의", "2026-11-16", "09:00", "10:00", RepeatRule.NONE);
        toasts.clear();
        ctx.actions().toggleDone(o);
        assertEquals(List.of("일정을 완료했어요 [되돌리기]"), toasts);
        lastUndoButton.run();
        assertFalse(on("2026-11-16").get(0).completed());
    }

    @Test
    void 이미_지워진_일정은_복제도_복사도_하지_않는다() {
        Occurrence o = add("회의", "2026-11-16", "09:00", "10:00", RepeatRule.NONE);
        schedules.delete(o, proto.control.EditScope.THIS_AND_FOLLOWING);
        toasts.clear();
        ctx.actions().duplicate(o);
        ctx.actions().copy(o);
        assertEquals(List.of("이미 지워진 일정이에요", "이미 지워진 일정이에요"), toasts);
        assertTrue(on("2026-11-16").isEmpty());
    }

    @Test
    void 이미_그_색인_색_바꾸기는_토스트도_되돌리기_버튼도_만들지_않는다() {
        Occurrence o = add("회의", "2026-11-16", "09:00", "10:00", RepeatRule.NONE);
        toasts.clear();
        lastUndoButton = null;
        ctx.actions().changeColor(schedules.findOccurrence(o.scheduleId(), o.date()).orElseThrow(), null);
        assertTrue(toasts.isEmpty());
        assertEquals(null, lastUndoButton);
        assertEquals(1, on("2026-11-16").size());
        assertTrue(schedules.canUndo());
        assertEquals("일정 추가", schedules.undoLabel().orElseThrow());
    }

    @Test
    void 색을_실제로_바꾸면_그_색_바꾸기만_되돌리는_버튼이_붙는다() {
        Occurrence o = add("회의", "2026-11-16", "09:00", "10:00", RepeatRule.NONE);
        String key = proto.entity.CategoryPalette.KEYS.get(3);
        ctx.actions().changeColor(o, key);
        assertTrue(toasts.get(toasts.size() - 1).endsWith("[되돌리기]"));
        lastUndoButton.run();
        assertEquals(1, on("2026-11-16").size());
        assertEquals(null, on("2026-11-16").get(0).colorKey());
    }

    @Test
    void 카테고리_색을_바꿔_다시_그려도_일정_변경_토스트의_되돌리기는_계속_된다() {
        Occurrence o = add("회의", "2026-11-16", "09:00", "10:00", RepeatRule.NONE);
        ctx.actions().duplicate(o);
        ActionToastCard card = lastCard;
        assertTrue(card.isActionEnabled());
        String other = proto.entity.CategoryPalette.KEYS.stream()
                .filter(k -> !k.equals(ctx.categories().getCategories().stream()
                        .filter(c -> c.getId().equals("class")).findFirst().orElseThrow().getColorKey()))
                .findFirst().orElseThrow();
        assertTrue(ctx.categories().setColor("class", other).success());
        assertTrue(card.isActionEnabled());
        card.clickAction();
        assertEquals(1, on("2026-11-16").size());
        assertEquals("되돌렸어요 · 일정 추가", toasts.get(toasts.size() - 1));
    }

    @Test
    void 다른_변경이_끼어들면_이전_토스트의_버튼은_꺼지고_눌러도_되돌리지_않는다() {
        Occurrence o = add("회의", "2026-11-16", "09:00", "10:00", RepeatRule.NONE);
        ctx.actions().duplicate(o);
        ActionToastCard card = lastCard;
        Runnable stale = lastUndoButton;
        add("점심", "2026-11-16", "12:00", "13:00", RepeatRule.NONE);
        assertFalse(card.isActionEnabled());
        stale.run();
        assertEquals(3, on("2026-11-16").size());
        assertEquals("~그 뒤에 다른 변경이 있어 되돌릴 수 없어요", toasts.get(toasts.size() - 1));
    }
}
