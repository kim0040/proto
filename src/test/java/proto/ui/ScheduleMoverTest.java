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
import proto.repository.CategoryRepository;
import proto.repository.ScheduleRepository;
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

/** 일정 옮기기(반복 없는 일정): 날짜·시간만 바뀌고, 바뀐 게 없으면 아무것도 하지 않고, 알림 글이 맞다 */
class ScheduleMoverTest {

    private ScheduleController schedules;
    private ScheduleMover mover;
    private final List<String> toasts = new ArrayList<>();

    @BeforeEach
    void setUp(@TempDir Path dir) {
        TestData.MutableClock clock = new TestData.MutableClock("2026-11-16T08:00:00");
        ScheduleRepository scheduleRepo = new ScheduleRepository(dir.resolve("schedules.csv"));
        CategoryRepository categoryRepo = new CategoryRepository(dir.resolve("categories.csv"));
        categoryRepo.loadOrSeed();
        schedules = new ScheduleController(scheduleRepo, categoryRepo, clock);
        CategoryController categories = new CategoryController(categoryRepo, scheduleRepo, schedules);
        UiContext ctx = new UiContext(schedules, categories, new ReminderTimer(schedules, clock), dir);
        ctx.setOverlay(new Overlay() {
            @Override
            public void showToast(String message, boolean error) {
                toasts.add((error ? "!" : "") + message);
            }

            @Override
            public ActionToastCard showActionToast(String message, String actionLabel, Runnable onAction) {
                toasts.add(message);
                return null;
            }
        });
        mover = new ScheduleMover(ctx);
    }

    private Occurrence add(String title, String date, String start, String end) {
        schedules.create(new ScheduleDraft(title, d(date), t(start), t(end), "class", "", RepeatRule.NONE, null,
                ReminderOption.NONE));
        return schedules.getOccurrences(d(date), d(date), null, title).get(0);
    }

    private Occurrence find(Occurrence o, String date) {
        return schedules.findOccurrence(o.scheduleId(), d(date)).orElseThrow();
    }

    @Test
    void 옮겨도_일정만의_색은_그대로이다() {
        schedules.create(new ScheduleDraft("회의", d("2026-11-16"), t("09:00"), t("10:00"), "class", "", RepeatRule.NONE,
                null, ReminderOption.NONE, false, "rose"));
        Occurrence o = schedules.getOccurrences(d("2026-11-16"), d("2026-11-16"), null, "회의").get(0);
        assertTrue(mover.move(o, d("2026-11-18"), t("14:00"), t("15:00")));
        assertEquals("rose", find(o, "2026-11-18").colorKey());
    }

    @Test
    void 다른_날짜와_시간으로_옮긴다() {
        Occurrence o = add("회의", "2026-11-16", "09:00", "10:00");
        assertTrue(mover.move(o, d("2026-11-18"), t("14:15"), t("15:15")));
        Occurrence moved = find(o, "2026-11-18");
        assertEquals(t("14:15"), moved.startTime());
        assertEquals(t("15:15"), moved.endTime());
        assertEquals(List.of("일정을 옮겼어요"), toasts);
    }

    @Test
    void 끝만_바뀌면_시간을_바꿨다고_알린다() {
        Occurrence o = add("회의", "2026-11-16", "09:00", "10:00");
        assertTrue(mover.move(o, d("2026-11-16"), t("09:00"), t("11:30")));
        assertEquals(t("11:30"), find(o, "2026-11-16").endTime());
        assertEquals(List.of("시간을 바꿨어요"), toasts);
    }

    @Test
    void 바뀐_게_없으면_저장도_알림도_없다() {
        Occurrence o = add("회의", "2026-11-16", "09:00", "10:00");
        assertFalse(mover.move(o, d("2026-11-16"), t("09:00"), t("10:00")));
        assertTrue(toasts.isEmpty());
    }

    @Test
    void 겹치는_일정이_있어도_막지_않고_알림에_덧붙인다() {
        add("수업", "2026-11-17", "09:00", "10:00");
        Occurrence o = add("회의", "2026-11-16", "09:00", "10:00");
        assertTrue(mover.move(o, d("2026-11-17"), t("09:30"), t("10:30")));
        assertEquals(List.of("일정을 옮겼어요 · 다른 일정과 시간이 겹쳐요"), toasts);
    }

    @Test
    void 방향키_이동은_하루_또는_15분씩_밀고_하루_끝에서_멈춘다() {
        Occurrence o = add("회의", "2026-11-16", "09:00", "10:00");
        assertTrue(mover.nudge(o, 1, 0));
        Occurrence next = find(o, "2026-11-17");
        assertTrue(mover.nudge(next, 0, -15));
        Occurrence up = find(o, "2026-11-17");
        assertEquals(t("08:45"), up.startTime());
        assertEquals(t("09:45"), up.endTime());
    }

    @Test
    void 자정_앞에서는_더_내려가지_않는다() {
        Occurrence o = add("야식", "2026-11-16", "23:00", "23:59");
        assertFalse(mover.nudge(o, 0, 15));
        assertEquals(t("23:59"), find(o, "2026-11-16").endTime());
        Occurrence first = add("새벽", "2026-11-16", "00:00", "00:30");
        assertFalse(mover.nudge(first, 0, -15));
    }

    @Test
    void 이미_지워진_일정은_옮기지_않는다() {
        Occurrence o = add("회의", "2026-11-16", "09:00", "10:00");
        schedules.delete(o, proto.control.EditScope.THIS_AND_FOLLOWING);
        assertFalse(mover.move(o, d("2026-11-17"), t("09:00"), t("10:00")));
        assertEquals(List.of("이미 지워진 일정이에요"), toasts);
    }

    @Test
    void 검증에_실패하면_첫_메시지를_오류로_보여_준다() {
        Occurrence o = add("회의", "2026-11-16", "09:00", "10:00");
        assertFalse(mover.move(o, d("2026-11-16"), t("10:00"), t("10:00")));
        assertEquals(List.of("!끝 시간은 시작 시간보다 늦어야 해요"), toasts);
    }

    private Occurrence addAllDay(String title, String date) {
        schedules.create(new ScheduleDraft(title, d(date), null, null, "class", "", RepeatRule.NONE, null,
                ReminderOption.NONE, true));
        return schedules.getOccurrences(d(date), d(date), null, title).get(0);
    }

    @Test
    void 하루_종일_일정은_옮겨도_하루_종일이고_날짜만_바뀐다() {
        Occurrence o = addAllDay("휴가", "2026-11-16");
        // 시간 인자는 무시된다 (끌기가 시간표 좌표를 넘겨도)
        assertTrue(mover.move(o, d("2026-11-18"), t("14:15"), t("15:15")));
        Occurrence moved = find(o, "2026-11-18");
        assertTrue(moved.allDay());
        assertEquals(t("00:00"), moved.startTime());
        assertEquals(t("23:59"), moved.endTime());
        assertEquals(List.of("일정을 옮겼어요"), toasts);
    }

    @Test
    void 하루_종일_일정은_날짜가_같으면_시간이_달라도_바뀐_게_없다() {
        Occurrence o = addAllDay("휴가", "2026-11-16");
        assertFalse(mover.move(o, d("2026-11-16"), t("09:00"), t("10:00")));
        assertTrue(toasts.isEmpty());
    }

    @Test
    void 하루_종일_일정의_방향키는_날짜만_밀고_시간_방향은_아무것도_안_한다() {
        Occurrence o = addAllDay("휴가", "2026-11-16");
        assertFalse(mover.nudge(o, 0, 15));
        assertFalse(mover.nudge(o, 0, -15));
        assertTrue(toasts.isEmpty());
        assertTrue(mover.nudge(o, 1, 15));
        assertTrue(find(o, "2026-11-17").allDay());
    }

    @Test
    void 하루_종일_일정도_내일로_미루기가_된다() {
        Occurrence o = addAllDay("휴가", "2026-11-16");
        assertTrue(mover.nudge(o, 1, 0, "내일로 미뤘어요"));
        assertTrue(find(o, "2026-11-17").allDay());
        assertEquals(List.of("내일로 미뤘어요"), toasts);
    }
}
