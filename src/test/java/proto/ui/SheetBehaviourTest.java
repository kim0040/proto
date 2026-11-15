package proto.ui;

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
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.ui.AppState.Mode;
import proto.ui.component.ActionToastCard;
import proto.ui.component.Overlay;
import proto.ui.dialog.ConfirmDialog;
import proto.ui.motion.Motion;
import proto.ui.sheet.SheetHost;

import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static proto.TestData.d;
import static proto.TestData.t;

/** 시트가 열려 있는지(메인 창 단축키를 끄는 기준), 확인 없이 바로 지우기(되돌리기 가능), 예전 "설정" 시작 화면 처리 */
class SheetBehaviourTest {

    private ScheduleController schedules;
    private UiContext ctx;
    private final List<String> toasts = new ArrayList<>();
    private Runnable lastUndo;
    private boolean motionBefore;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        motionBefore = Motion.enabled();
        Motion.setEnabledTransient(false);
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
            public ActionToastCard showActionToast(String message, String actionLabel, Runnable onAction) {
                toasts.add(message + " [" + actionLabel + "]");
                lastUndo = onAction;
                return null;
            }
        });
    }

    @AfterEach
    void tearDown() {
        Motion.setEnabledTransient(motionBefore);
    }

    /** 메인 창 단축키(MainFrame#bind, EditShortcuts)는 이 값으로 꺼진다. 헤드리스에서는 단축키 마스크를 읽을 수 없어 판단 값만 확인한다 */
    @Test
    void 시트가_열려_있는_동안에만_창이_시트_열림으로_보인다() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JRootPane root = new JRootPane();
            root.setSize(1000, 700);
            root.doLayout();
            SheetHost.install(root.getLayeredPane());
            assertFalse(SheetHost.isOpen(root));
            ConfirmDialog sheet = new ConfirmDialog(root.getContentPane(), "확인", "계속할까요?", null, null,
                    List.of(new ConfirmDialog.Choice("확인", ConfirmDialog.Kind.PRIMARY)));
            sheet.open();
            assertTrue(SheetHost.isOpen(root));
            assertTrue(SheetHost.isOpen(root.getContentPane()));
            sheet.cancel();
            assertFalse(SheetHost.isOpen(root));
        });
    }

    @Test
    void 반복_없는_일정은_묻지_않고_바로_지우고_되돌릴_수_있다() {
        schedules.create(new ScheduleDraft("회의", d("2026-11-17"), t("14:00"), t("15:00"), "class", "",
                RepeatRule.NONE, null, ReminderOption.NONE));
        Occurrence o = schedules.getOccurrences(d("2026-11-17"), d("2026-11-17"), null, "").get(0);
        assertTrue(ctx.actions().delete(o));
        assertTrue(schedules.getOccurrences(d("2026-11-17"), d("2026-11-17"), null, "").isEmpty());
        assertEquals(List.of("일정을 삭제했어요 [되돌리기]"), toasts);
        lastUndo.run();
        assertEquals(1, schedules.getOccurrences(d("2026-11-17"), d("2026-11-17"), null, "").size());
    }

    @Test
    void 예전에_저장한_설정_시작_화면은_오늘로_연다() {
        assertEquals(Mode.TODAY, UiSettings.startModeOf("SETTINGS"));
        assertEquals(Mode.TODAY, UiSettings.startModeOf("STATS"));
        assertEquals(Mode.TODAY, UiSettings.startModeOf(null));
        assertEquals(Mode.WEEK, UiSettings.startModeOf("WEEK"));
        assertEquals(Mode.MONTH, UiSettings.startModeOf("MONTH"));
    }
}
