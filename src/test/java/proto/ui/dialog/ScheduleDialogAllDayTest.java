package proto.ui.dialog;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import proto.TestData;
import proto.control.CategoryController;
import proto.control.ReminderTimer;
import proto.control.ScheduleController;
import proto.control.ScheduleDraft;
import proto.entity.Occurrence;
import proto.ui.UiContext;
import proto.ui.component.ToggleSwitch;
import proto.ui.motion.Motion;
import proto.ui.sheet.SheetHost;

import javax.swing.AbstractButton;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static proto.TestData.d;
import static proto.TestData.t;

/** 일정 창의 "하루 종일" 스위치: 켜면 시간·알림 줄이 접히고 하루 종일로 저장되며, 하루 종일 일정은 켠 채 열리고, 끄면 시간이 돌아온다 */
class ScheduleDialogAllDayTest {

    private boolean motionBefore;
    private JRootPane root;
    private UiContext ctx;
    private ScheduleController schedules;

    @BeforeEach
    void setUp(@TempDir Path dir) throws Exception {
        motionBefore = Motion.enabled();
        Motion.setEnabledTransient(false);
        SwingUtilities.invokeAndWait(() -> {
            root = new JRootPane();
            root.setSize(1200, 900);
            root.doLayout();
            SheetHost.install(root.getLayeredPane());
        });
        TestData.MutableClock clock = new TestData.MutableClock("2026-11-16T08:00:00");
        var scheduleRepo = new proto.repository.ScheduleRepository(dir.resolve("schedules.csv"));
        var categoryRepo = new proto.repository.CategoryRepository(dir.resolve("categories.csv"));
        categoryRepo.loadOrSeed();
        schedules = new ScheduleController(scheduleRepo, categoryRepo, clock);
        ctx = new UiContext(schedules, new CategoryController(categoryRepo, scheduleRepo, schedules),
                new ReminderTimer(schedules, clock), dir);
    }

    @AfterEach
    void tearDown() {
        Motion.setEnabledTransient(motionBefore);
    }

    private ScheduleDialog open(Occurrence editing, ScheduleDraft draft) throws Exception {
        ScheduleDialog[] dialog = new ScheduleDialog[1];
        SwingUtilities.invokeAndWait(() -> {
            dialog[0] = new ScheduleDialog(ctx, editing, draft);
            dialog[0].openOn(SheetHost.of(root));
        });
        return dialog[0];
    }

    private static <T> List<T> find(Container c, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (Component child : c.getComponents()) {
            if (type.isInstance(child)) {
                out.add(type.cast(child));
            }
            if (child instanceof Container sub) {
                out.addAll(find(sub, type));
            }
        }
        return out;
    }

    private static void press(Component c, int code) {
        KeyEvent e = new KeyEvent(c, KeyEvent.KEY_PRESSED, 0, 0, code, ' ');
        for (java.awt.event.KeyListener l : c.getKeyListeners()) {
            l.keyPressed(e);
        }
    }

    @Test
    void 스위치를_켜면_시간과_알림_줄이_접힌다() throws Exception {
        ScheduleDialog dlg = open(null, schedules.newDraft(d("2026-11-17")));
        SwingUtilities.invokeAndWait(() -> {
            List<CollapseRow> rows = find(dlg, CollapseRow.class);
            assertEquals(2, rows.size());
            assertTrue(rows.stream().noneMatch(CollapseRow::isCollapsed));
            press(find(dlg, ToggleSwitch.class).get(0), KeyEvent.VK_SPACE);
            assertTrue(rows.stream().allMatch(CollapseRow::isCollapsed));
            press(find(dlg, ToggleSwitch.class).get(0), KeyEvent.VK_SPACE);
            assertTrue(rows.stream().noneMatch(CollapseRow::isCollapsed));
        });
    }

    @Test
    void 켠_채_저장하면_하루_종일_일정이_된다() throws Exception {
        ScheduleDialog dlg = open(null, schedules.newDraft(d("2026-11-17")));
        SwingUtilities.invokeAndWait(() -> {
            find(dlg, JTextField.class).get(0).setText("휴가");
            press(find(dlg, ToggleSwitch.class).get(0), KeyEvent.VK_SPACE);
            save(dlg);
        });
        Occurrence o = schedules.getOccurrences(d("2026-11-17"), d("2026-11-17"), null, "휴가").get(0);
        assertTrue(o.allDay());
        assertEquals(t("00:00"), o.startTime());
        assertEquals(t("23:59"), o.endTime());
    }

    private static void save(ScheduleDialog dlg) {
        find(dlg, AbstractButton.class).stream().filter(b -> "저장".equals(b.getText())).findFirst().orElseThrow()
                .doClick();
    }

    @Test
    void 하루_종일_일정은_스위치를_켠_채_열리고_끄면_아홉시부터_열시() throws Exception {
        schedules.create(new ScheduleDraft("출장", d("2026-11-18"), null, null, "class", "", null, null, null, true));
        Occurrence o = schedules.getOccurrences(d("2026-11-18"), d("2026-11-18"), null, "출장").get(0);
        ScheduleDialog dlg = open(o, schedules.draftOf(o));
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(find(dlg, ToggleSwitch.class).get(0).isOn());
            assertTrue(find(dlg, CollapseRow.class).stream().allMatch(CollapseRow::isCollapsed));
            press(find(dlg, ToggleSwitch.class).get(0), KeyEvent.VK_SPACE);
            assertFalse(find(dlg, ToggleSwitch.class).get(0).isOn());
            save(dlg);
        });
        Occurrence after = schedules.getOccurrences(d("2026-11-18"), d("2026-11-18"), null, "출장").get(0);
        assertFalse(after.allDay());
        assertEquals(t("09:00"), after.startTime());
        assertEquals(t("10:00"), after.endTime());
    }

    @Test
    void 스위치를_바꾸면_바꾼_것으로_친다() throws Exception {
        ScheduleDialog dlg = open(null, schedules.newDraft(d("2026-11-17")));
        SwingUtilities.invokeAndWait(() -> {
            assertFalse(dlg.isDirty());
            press(find(dlg, ToggleSwitch.class).get(0), KeyEvent.VK_SPACE);
            assertTrue(dlg.isDirty());
        });
    }
}
