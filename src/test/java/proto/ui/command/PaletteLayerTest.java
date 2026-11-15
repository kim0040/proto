package proto.ui.command;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import proto.entity.Category;
import proto.ui.AppState.Mode;
import proto.ui.motion.Motion;
import proto.ui.theme.ThemeManager.Choice;

import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 명령 메뉴 층: 열고 닫기, 입력으로 거르기, ↑/↓·Enter 실행, 바깥 클릭·Esc로 닫기, 한글 조합 중이 아닐 때만 실행 */
class PaletteLayerTest {

    private final List<String> calls = new ArrayList<>();
    private boolean motionBefore;
    private JRootPane root;
    private PaletteLayer layer;
    private CommandCatalog catalog;

    @BeforeEach
    void setUp() throws Exception {
        motionBefore = Motion.enabled();
        Motion.setEnabledTransient(false);
        catalog = new CommandCatalog(new Host());
        SwingUtilities.invokeAndWait(() -> {
            root = new JRootPane();
            root.setSize(1200, 800);
            root.getLayeredPane().setSize(1200, 800);
            layer = PaletteLayer.install(root.getLayeredPane());
        });
    }

    @AfterEach
    void tearDown() {
        Motion.setEnabledTransient(motionBefore);
    }

    private void onEdt(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    @Test
    void 열고_닫는다() throws Exception {
        onEdt(() -> {
            assertFalse(layer.isOpen());
            layer.open(catalog);
            assertTrue(layer.isOpen());
            assertTrue(layer.contains(5, 5));
            layer.toggle(catalog);
            assertFalse(layer.isOpen());
            assertFalse(layer.contains(5, 5));
        });
    }

    @Test
    void 카드는_위쪽_가운데_560폭() throws Exception {
        onEdt(() -> {
            layer.open(catalog);
            var r = layer.palette().getBounds();
            assertEquals(560, r.width);
            assertEquals((1200 - 560) / 2, r.x);
            assertTrue(r.y < 200);
        });
    }

    @Test
    void 입력하면_거르고_Enter로_실행한다() throws Exception {
        onEdt(() -> {
            layer.open(catalog);
            layer.palette().field().setText("달력");
            assertEquals("달력 보기", layer.palette().results().selectedCommand().title());
            layer.palette().field().postActionEvent();
        });
        onEdt(() -> assertEquals(List.of("mode:MONTH"), calls));
        onEdt(() -> assertFalse(layer.isOpen()));
    }

    @Test
    void 방향키로_고르고_끝에서는_반대쪽으로_이어진다() throws Exception {
        onEdt(() -> {
            layer.open(catalog);
            PaletteResults r = layer.palette().results();
            String first = r.selectedCommand().title();
            r.move(-1);
            assertFalse(first.equals(r.selectedCommand().title()));
            r.move(1);
            assertEquals(first, r.selectedCommand().title());
        });
    }

    @Test
    void 아래_화살표_키_연결로_다음_줄() throws Exception {
        onEdt(() -> {
            layer.open(catalog);
            var field = layer.palette().field();
            String first = layer.palette().results().selectedCommand().title();
            field.getActionMap().get("proto-palette-down").actionPerformed(new ActionEvent(field, 0, ""));
            assertFalse(first.equals(layer.palette().results().selectedCommand().title()));
        });
    }

    @Test
    void Tab과_Shift_Tab은_포커스를_넘기지_않고_결과_이동으로_쓰인다() throws Exception {
        onEdt(() -> {
            layer.open(catalog);
            var field = layer.palette().field();
            assertFalse(field.getFocusTraversalKeysEnabled());
            assertTrue(layer.isFocusCycleRoot());
            PaletteResults r = layer.palette().results();
            String first = r.selectedCommand().title();
            field.getActionMap().get("proto-palette-tab").actionPerformed(new ActionEvent(field, 0, ""));
            assertFalse(first.equals(r.selectedCommand().title()));
            field.getActionMap().get("proto-palette-shift-tab").actionPerformed(new ActionEvent(field, 0, ""));
            assertEquals(first, r.selectedCommand().title());
        });
    }

    @Test
    void Esc_연결과_바깥_클릭으로_닫힌다() throws Exception {
        onEdt(() -> {
            layer.open(catalog);
            var field = layer.palette().field();
            field.getActionMap().get("proto-palette-esc").actionPerformed(new ActionEvent(field, 0, ""));
            assertFalse(layer.isOpen());
            layer.open(catalog);
            layer.dispatchEvent(new MouseEvent(layer, MouseEvent.MOUSE_PRESSED, 0, 0, 3, 3, 1, false));
            assertFalse(layer.isOpen());
        });
    }

    @Test
    void 카드_안을_눌러도_닫히지_않는다() throws Exception {
        onEdt(() -> {
            layer.open(catalog);
            var r = layer.palette().getBounds();
            layer.dispatchEvent(new MouseEvent(layer, MouseEvent.MOUSE_PRESSED, 0, 0, r.x + 10, r.y + 10, 1, false));
            assertTrue(layer.isOpen());
        });
    }

    @Test
    void 맞는_게_없으면_일정_만들기만_남는다() throws Exception {
        onEdt(() -> {
            layer.open(catalog);
            layer.palette().field().setText("zzz");
            assertEquals(1, layer.palette().results().commandCount());
            assertEquals("‘zzz’ 일정 만들기", layer.palette().results().selectedCommand().title());
        });
    }

    @Test
    void 단축키_안내_시트를_만들고_열_수_있다() throws Exception {
        onEdt(() -> {
            var host = proto.ui.sheet.SheetHost.install(root.getLayeredPane());
            ShortcutHelpDialog d = new ShortcutHelpDialog(root);
            d.open();
            assertTrue(host.isOpen());
            d.cancel();
        });
    }

    /** 부른 일을 적는 가짜 */
    private final class Host implements CommandHost {
        @Override public LocalDate today() { return LocalDate.of(2026, 11, 16); }
        @Override public java.util.Optional<LocalDate> resolveDate(String text) {
            return proto.logic.DateQueryParser.parse(text, today());
        }
        @Override public void showMode(Mode mode) { calls.add("mode:" + mode); }
        @Override public void goToday() { calls.add("today"); }
        @Override public void goToDate(LocalDate date) { calls.add("date:" + date); }
        @Override public void newSchedule(String title) { calls.add("new:" + title); }
        @Override public void filterCategory(String id) { calls.add("cat:" + id); }
        @Override public List<Category> categories() { return List.of(new Category("class", "수업")); }
        @Override public String currentCategory() { return null; }
        @Override public void openFreeSlots() { calls.add("free"); }
        @Override public void setTheme(Choice c) { calls.add("theme:" + c); }
        @Override public Choice theme() { return Choice.LIGHT; }
        @Override public void openSettings() { calls.add("settings"); }
        @Override public void showShortcuts() { calls.add("help"); }
        @Override public boolean canUndo() { return false; }
        @Override public boolean canRedo() { return false; }
        @Override public String undoLabel() { return ""; }
        @Override public String redoLabel() { return ""; }
        @Override public void undo() { calls.add("undo"); }
        @Override public void redo() { calls.add("redo"); }
    }
}
