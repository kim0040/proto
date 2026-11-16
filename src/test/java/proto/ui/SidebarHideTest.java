package proto.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import proto.TestData;
import proto.control.CategoryController;
import proto.control.ReminderTimer;
import proto.control.ScheduleController;
import proto.repository.CategoryRepository;
import proto.repository.ScheduleRepository;
import proto.ui.component.Overlay;
import proto.ui.layout.SizeClass;
import proto.ui.layout.SizeClass.Width;
import proto.ui.motion.Motion;

import javax.swing.SwingUtilities;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 사이드바 숨기기: 숨기면 너비가 0이 되어 전체 사이드바도 아이콘 띠도 보이지 않고, 다시 보이면 원래 너비로 돌아온다 */
class SidebarHideTest {

    private static final SizeClass WIDE = new SizeClass(Width.WIDE, false, false);
    private static final SizeClass COMPACT = new SizeClass(Width.COMPACT, false, false);

    private UiContext ctx;
    private boolean motionBefore;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        motionBefore = Motion.enabled();
        Motion.setEnabledTransient(false);
        TestData.MutableClock clock = new TestData.MutableClock("2026-11-16T08:00:00");
        ScheduleRepository scheduleRepo = new ScheduleRepository(dir.resolve("schedules.csv"));
        CategoryRepository categoryRepo = new CategoryRepository(dir.resolve("categories.csv"));
        categoryRepo.loadOrSeed();
        ScheduleController schedules = new ScheduleController(scheduleRepo, categoryRepo, clock);
        CategoryController categories = new CategoryController(categoryRepo, scheduleRepo, schedules);
        ctx = new UiContext(schedules, categories, new ReminderTimer(schedules, clock), dir);
        ctx.setOverlay(new Overlay());
    }

    @AfterEach
    void restore() {
        Motion.setEnabledTransient(motionBefore);
    }

    private SidebarHost host(SizeClass sc) {
        Sidebar full = new Sidebar(ctx, m -> { }, c -> { }, d -> { }, () -> { }, () -> { });
        SidebarRail rail = new SidebarRail(ctx, m -> { }, () -> { }, () -> { });
        SidebarHost host = new SidebarHost(full, rail, sc);
        host.apply(sc, false);
        return host;
    }

    private static void layout(SidebarHost host) {
        host.setSize(host.getPreferredSize().width, 800);
        host.doLayout();
    }

    @Test
    void 넓은_화면에서_숨기면_너비가_0이고_다시_보이면_돌아온다() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SidebarHost host = host(WIDE);
            int shown = host.getPreferredSize().width;
            assertTrue(shown > 0);
            host.setHidden(true, false);
            layout(host);
            assertEquals(0, host.getPreferredSize().width);
            for (var c : host.getComponents()) {
                assertFalse(c.isVisible(), "숨긴 뒤에도 보인다: " + c.getClass().getSimpleName());
            }
            host.setHidden(false, false);
            layout(host);
            assertEquals(shown, host.getPreferredSize().width);
            assertTrue(find(host, Sidebar.class).isVisible(), "넓은 화면에서는 전체 사이드바가 다시 보인다");
        });
    }

    @Test
    void 숨긴_채로_크기_등급이_바뀌어도_숨김이_유지된다() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SidebarHost host = host(WIDE);
            host.setHidden(true, false);
            host.apply(COMPACT, false);
            assertEquals(0, host.getPreferredSize().width);
            host.setHidden(false, false);
            layout(host);
            assertTrue(host.getPreferredSize().width > 0);
            assertTrue(find(host, SidebarRail.class).isVisible(), "좁은 화면에서 다시 보이면 아이콘 띠가 보인다");
        });
    }

    private static <T> T find(SidebarHost host, Class<T> type) {
        for (var c : host.getComponents()) {
            if (type.isInstance(c)) {
                return type.cast(c);
            }
        }
        throw new AssertionError(type.getSimpleName() + " 없음");
    }
}
