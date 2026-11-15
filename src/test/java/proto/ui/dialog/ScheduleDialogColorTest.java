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
import proto.entity.CategoryPalette;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static proto.TestData.d;
import static proto.TestData.t;

/** 일정 창의 "색" 줄: 처음엔 카테고리 색, 고르면 그 색으로 저장되고, 다시 카테고리 색을 고르면 일정만의 색이 없어진다 */
class ScheduleDialogColorTest {

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
    void 새_일정은_카테고리_색을_따른다() throws Exception {
        ScheduleDialog dlg = open(null, schedules.newDraft(d("2026-11-17")));
        SwingUtilities.invokeAndWait(() -> {
            ColorSwatchRow row = find(dlg, ColorSwatchRow.class).get(0);
            assertNull(row.value());
            assertFalse(dlg.isDirty());
        });
    }

    @Test
    void 방향키로_색을_고르고_저장하면_일정만의_색이_된다() throws Exception {
        ScheduleDialog dlg = open(null, schedules.newDraft(d("2026-11-17")));
        SwingUtilities.invokeAndWait(() -> {
            find(dlg, JTextField.class).get(0).setText("스터디");
            ColorSwatchRow row = find(dlg, ColorSwatchRow.class).get(0);
            press(row, KeyEvent.VK_RIGHT);
            assertEquals(CategoryPalette.KEYS.get(0), row.value());
            assertTrue(dlg.isDirty());
            press(row, KeyEvent.VK_RIGHT);
            assertEquals(CategoryPalette.KEYS.get(1), row.value());
            find(dlg, AbstractButton.class).stream().filter(b -> "저장".equals(b.getText())).findFirst().orElseThrow().doClick();
        });
        Occurrence o = schedules.getOccurrences(d("2026-11-17"), d("2026-11-17"), null, "스터디").get(0);
        assertEquals(CategoryPalette.KEYS.get(1), o.colorKey());
    }

    @Test
    void 일정만의_색이_있는_일정은_그_색을_고른_채_열리고_Home으로_되돌린다() throws Exception {
        schedules.create(new ScheduleDraft("분홍 일정", d("2026-11-18"), t("09:00"), t("10:00"), "class", "", null, null, null, false, "rose"));
        Occurrence o0 = schedules.getOccurrences(d("2026-11-18"), d("2026-11-18"), null, null).get(0);
        ScheduleDialog dlg = open(o0, schedules.draftOf(o0));
        SwingUtilities.invokeAndWait(() -> {
            ColorSwatchRow row = find(dlg, ColorSwatchRow.class).get(0);
            assertEquals("rose", row.value());
            press(row, KeyEvent.VK_HOME);
            assertNull(row.value());
        });
    }
}
