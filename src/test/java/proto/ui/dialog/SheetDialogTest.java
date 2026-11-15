package proto.ui.dialog;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import proto.control.CategoryController;
import proto.control.ReminderTimer;
import proto.control.ScheduleController;
import proto.TestData;
import proto.ui.UiContext;
import proto.ui.motion.Motion;
import proto.ui.sheet.SheetHost;

import javax.swing.AbstractButton;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 창 안 시트: showDialog가 닫힐 때까지 기다리기(겹친 시트 포함), 바깥 클릭·Esc 규칙, Enter 기본 버튼 되돌리기 */
class SheetDialogTest {

    private final List<String> log = Collections.synchronizedList(new ArrayList<>());
    private boolean motionBefore;
    private JRootPane root;
    private SheetHost host;
    private UiContext ctx;

    @BeforeEach
    void setUp(@TempDir Path dir) throws Exception {
        motionBefore = Motion.enabled();
        Motion.setEnabledTransient(false);
        SwingUtilities.invokeAndWait(() -> {
            root = new JRootPane();
            root.setSize(1200, 800);
            root.doLayout();
            host = SheetHost.install(root.getLayeredPane());
        });
        TestData.MutableClock clock = new TestData.MutableClock("2026-11-16T08:00:00");
        var scheduleRepo = new proto.repository.ScheduleRepository(dir.resolve("schedules.csv"));
        var categoryRepo = new proto.repository.CategoryRepository(dir.resolve("categories.csv"));
        categoryRepo.loadOrSeed();
        ScheduleController schedules = new ScheduleController(scheduleRepo, categoryRepo, clock);
        ctx = new UiContext(schedules, new CategoryController(categoryRepo, scheduleRepo, schedules),
                new ReminderTimer(schedules, clock), dir);
    }

    @AfterEach
    void tearDown() {
        Motion.setEnabledTransient(motionBefore);
    }

    private ConfirmDialog confirm(Component owner, String name) {
        return new ConfirmDialog(owner, name, name + "?", null, null, List.of(
                new ConfirmDialog.Choice("취소", ConfirmDialog.Kind.SECONDARY),
                new ConfirmDialog.Choice("확인", ConfirmDialog.Kind.PRIMARY)));
    }

    @Test
    void 시트_위에_연_확인_시트는_위에_쌓이고_각자_닫힐_때_돌아온다() throws Exception {
        ConfirmDialog[] d = new ConfirmDialog[2];
        SwingUtilities.invokeAndWait(() -> d[0] = confirm(root.getContentPane(), "바깥"));
        SwingUtilities.invokeLater(() -> log.add("바깥=" + d[0].ask()));
        waitUntil(() -> host.depth() == 1);
        SwingUtilities.invokeAndWait(() -> d[1] = confirm(d[0], "안쪽"));
        SwingUtilities.invokeLater(() -> log.add("안쪽=" + d[1].ask()));
        waitUntil(() -> host.depth() == 2);
        assertTrue(log.isEmpty(), "두 시트 모두 아직 기다리는 중이어야 한다");
        assertSame(button(d[1], "확인"), onEdt(root::getDefaultButton));

        SwingUtilities.invokeAndWait(() -> button(d[1], "확인").doClick());
        waitUntil(() -> log.size() == 1);
        assertEquals(List.of("안쪽=1"), log);
        assertEquals(1, onEdt(host::depth));
        assertSame(button(d[0], "확인"), onEdt(root::getDefaultButton));

        SwingUtilities.invokeAndWait(() -> d[0].scrimClicked());
        waitUntil(() -> log.size() == 2);
        assertEquals(List.of("안쪽=1", "바깥=-1"), log);
        assertFalse(onEdt(() -> host.isOpen()));
        assertNull(onEdt(root::getDefaultButton));
    }

    @Test
    void 확인_시트는_Esc와_바깥_클릭이_모두_취소이다() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ConfirmDialog a = confirm(root.getContentPane(), "가");
            a.open();
            a.escapePressed();
            assertTrue(a.wasCancelled());
            assertFalse(a.isOpen());
            ConfirmDialog b = confirm(root.getContentPane(), "나");
            b.open();
            b.scrimClicked();
            assertTrue(b.wasCancelled());
            assertFalse(host.isOpen());
        });
    }

    @Test
    void 일정_시트는_바꾼_게_없으면_바깥_클릭으로_닫히고_바꿨으면_닫히지_않는다() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ScheduleDialog clean = newSchedule();
            clean.scrimClicked();
            assertFalse(clean.isOpen());
            ScheduleDialog dirty = newSchedule();
            find(dirty, JTextField.class).setText("회의");
            assertTrue(dirty.isDirty());
            dirty.scrimClicked();
            assertTrue(dirty.isOpen(), "입력한 내용이 있으면 바깥 클릭으로 닫히지 않는다");
            dirty.cancel();
        });
    }

    @Test
    void 바꾼_일정_시트의_Esc는_버릴지_묻고_계속_편집이면_남는다() throws Exception {
        ScheduleDialog[] d = new ScheduleDialog[1];
        SwingUtilities.invokeAndWait(() -> {
            d[0] = newSchedule();
            find(d[0], JTextField.class).setText("회의");
            SwingUtilities.invokeLater(() -> button(topSheet(), "계속 편집").doClick());
            d[0].escapePressed();
        });
        assertTrue(onEdt(() -> d[0].isOpen()), "계속 편집이면 시트가 남는다");
        SwingUtilities.invokeAndWait(() -> {
            SwingUtilities.invokeLater(() -> button(topSheet(), "버리기").doClick());
            d[0].escapePressed();
        });
        assertFalse(onEdt(() -> d[0].isOpen()));
        assertTrue(d[0].wasCancelled());
        assertFalse(onEdt(() -> host.isOpen()));
    }

    @Test
    void 카테고리_시트는_새_이름을_쓰는_중이면_바깥_클릭을_막고_Esc는_먼저_칸을_비운다() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            CategoryDialog d = new CategoryDialog(ctx);
            d.openOn(host);
            JTextField addField = addField(d);
            addField.setText("동아리");
            d.scrimClicked();
            assertTrue(d.isOpen());
            d.escapePressed();
            assertEquals("", addField.getText());
            assertTrue(d.isOpen());
            d.escapePressed();
            assertFalse(d.isOpen());
        });
    }

    private ScheduleDialog newSchedule() {
        ScheduleDialog d = new ScheduleDialog(ctx, null, ctx.schedules().newDraft(ctx.schedules().today()));
        d.openOn(host);
        return d;
    }

    /** 맨 위 시트: 시트 층의 0번(맨 앞) 층 안의 카드 */
    private BaseDialog topSheet() {
        return (BaseDialog) ((Container) host.getComponent(0)).getComponent(0);
    }

    private static JTextField addField(Container root) {
        for (JTextField f : all(root, JTextField.class)) {
            if ("새 카테고리 이름".equals(f.getClientProperty("JTextField.placeholderText"))) {
                return f;
            }
        }
        throw new AssertionError("새 이름 칸이 없다");
    }

    private static AbstractButton button(Container root, String text) {
        return all(root, AbstractButton.class).stream().filter(b -> text.equals(b.getText())).findFirst()
                .orElseThrow(() -> new AssertionError(text + " 버튼이 없다"));
    }

    private static <T> T find(Container root, Class<T> type) {
        return all(root, type).get(0);
    }

    private static <T> List<T> all(Container root, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Component c : root.getComponents()) {
            if (type.isInstance(c)) {
                found.add(type.cast(c));
            }
            if (c instanceof Container inner) {
                found.addAll(all(inner, type));
            }
        }
        return found;
    }

    private static <T> T onEdt(java.util.function.Supplier<T> s) throws Exception {
        Object[] out = new Object[1];
        SwingUtilities.invokeAndWait(() -> out[0] = s.get());
        @SuppressWarnings("unchecked")
        T t = (T) out[0];
        return t;
    }

    private static void waitUntil(BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 200; i++) {
            boolean[] ok = new boolean[1];
            SwingUtilities.invokeAndWait(() -> ok[0] = condition.getAsBoolean());
            if (ok[0]) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("기다리던 상태가 되지 않았다");
    }
}
