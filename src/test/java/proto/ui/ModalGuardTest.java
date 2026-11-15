package proto.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import proto.TestData;
import proto.control.CategoryController;
import proto.control.EditScope;
import proto.control.ReminderTimer;
import proto.control.ScheduleController;
import proto.control.ScheduleDraft;
import proto.entity.Occurrence;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.ui.component.Overlay;

import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static proto.TestData.d;
import static proto.TestData.t;

/** 편집 단축키가 시트·명령 메뉴가 열린 동안 꺼지는지, 하루 종일로 바뀐 일정의 알림 카드 규칙 */
class ModalGuardTest {

    private ScheduleController schedules;
    private UiContext ctx;
    private final List<String> toasts = new ArrayList<>();

    @BeforeEach
    void setUp(@TempDir Path dir) {
        TestData.MutableClock clock = new TestData.MutableClock("2026-11-16T08:00:00");
        var scheduleRepo = new proto.repository.ScheduleRepository(dir.resolve("schedules.csv"));
        var categoryRepo = new proto.repository.CategoryRepository(dir.resolve("categories.csv"));
        categoryRepo.loadOrSeed();
        schedules = new ScheduleController(scheduleRepo, categoryRepo, clock);
        ctx = new UiContext(schedules, new CategoryController(categoryRepo, scheduleRepo, schedules),
                new ReminderTimer(schedules, clock), dir);
        ctx.setOverlay(new Overlay() {
            @Override
            public void showToast(String message, boolean error) {
                toasts.add(message);
            }

            @Override
            public proto.ui.component.ActionToastCard showActionToast(String message, String label, Runnable onAction) {
                toasts.add(message + " [" + label + "]");
                return null;
            }

            @Override
            public void showNotice(String message) {
                toasts.add("~" + message);
            }
        });
    }

    private Occurrence add(String title) {
        schedules.create(new ScheduleDraft(title, d("2026-11-16"), t("09:00"), t("10:00"), "class", "",
                RepeatRule.NONE, null, ReminderOption.TEN_MINUTES));
        return schedules.getOccurrences(d("2026-11-16"), d("2026-11-16"), null, title).get(0);
    }

    private boolean press(JRootPane root, int mask, int key) {
        KeyStroke stroke = KeyStroke.getKeyStroke(key, mask);
        Object id = root.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).get(stroke);
        javax.swing.Action action = (javax.swing.Action) root.getActionMap().get(id);
        return javax.swing.SwingUtilities.notifyAction(action, stroke,
                new KeyEvent(root, KeyEvent.KEY_PRESSED, 0, mask, key, (char) key), root, mask);
    }

    @Test
    void 창_단축키_가드가_닫혀_있으면_되돌리기_붙여넣기가_뒤_화면을_건드리지_않는다() {
        Occurrence o = add("회의");
        ctx.actions().copy(o);
        AtomicBoolean free = new AtomicBoolean(false);
        JRootPane root = new JRootPane();
        new EditShortcuts(ctx, root, free::get).install(InputEvent.CTRL_DOWN_MASK);

        assertFalse(press(root, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_Z));
        assertFalse(press(root, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, KeyEvent.VK_Z));
        assertFalse(press(root, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_V));
        assertEquals(1, schedules.getOccurrences(d("2026-11-16"), d("2026-11-16"), null, "").size());
        assertTrue(schedules.canUndo());

        free.set(true);
        assertTrue(press(root, InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_Z));
        assertTrue(schedules.getOccurrences(d("2026-11-16"), d("2026-11-16"), null, "").isEmpty());
    }

    @Test
    void 하루_종일로_바뀐_일정의_알림_카드는_더_이상_유효하지_않다() {
        Occurrence o = add("회의");
        assertTrue(ctx.actions().reminderStillActive(o));
        ScheduleDraft allDay = new ScheduleDraft("회의", o.date(), null, null, "class", "", RepeatRule.NONE, null,
                ReminderOption.NONE, true);
        assertTrue(schedules.update(o, allDay, EditScope.THIS_ONLY).success());
        assertFalse(ctx.actions().reminderStillActive(o));
    }

    @Test
    void 열려_있던_알림_카드의_다시_알림은_하루_종일로_바뀐_일정에는_예약하지_않는다() {
        Occurrence o = add("회의");
        ScheduleDraft allDay = new ScheduleDraft("회의", o.date(), null, null, "class", "", RepeatRule.NONE, null,
                ReminderOption.NONE, true);
        schedules.update(o, allDay, EditScope.THIS_ONLY);
        toasts.clear();
        assertTrue(ctx.actions().snoozeReminder(o, 5));
        assertEquals(List.of("하루 종일 일정은 알림 대상이 아니에요"), toasts);
    }
}
