package proto.ui.view;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import proto.TestData;
import proto.control.CategoryController;
import proto.control.ReminderTimer;
import proto.control.ScheduleController;
import proto.control.ScheduleDraft;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.repository.CategoryRepository;
import proto.repository.ScheduleRepository;
import proto.ui.UiContext;
import proto.ui.component.AllDayRow;
import proto.ui.component.Overlay;
import proto.ui.motion.Motion;

import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static proto.TestData.d;
import static proto.TestData.t;

/** 펼친 하루 종일 줄이 아무리 길어도 시간 본문이 보이고, 줄 안에서 스크롤해 마지막 칩까지 볼 수 있는지 (900×600, 하루에 40개) */
class WeekViewAllDayCapTest {

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
        for (int i = 0; i < 40; i++) {
            schedules.create(new ScheduleDraft("행사 " + i, d("2026-11-18"), t("00:00"), t("23:59"), "class", "",
                    RepeatRule.NONE, null, ReminderOption.NONE, true, null));
        }
    }

    @AfterEach
    void restore() {
        Motion.setEnabledTransient(motionBefore);
    }

    /** 창 없이(헤드리스) 배치를 끝까지 내려가며 한다 */
    private static void layoutTree(Component c) {
        if (c instanceof Container box) {
            box.doLayout();
            for (Component child : box.getComponents()) {
                layoutTree(child);
            }
        }
    }

    private static <T> T find(Component c, Class<T> type) {
        if (type.isInstance(c)) {
            return type.cast(c);
        }
        if (c instanceof Container box) {
            for (Component child : box.getComponents()) {
                T found = find(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    void 사십_개를_펼쳐도_시간_본문이_남고_줄_안에서_마지막_칩까지_스크롤된다() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            WeekView view = new WeekView(ctx);
            view.component().setSize(900, 600);
            view.refresh(false);
            layoutTree(view.component());
            AllDayRow row = find(view.component(), AllDayRow.class);
            assertNotNull(row);
            row.setExpanded(true);
            layoutTree(view.component());
            layoutTree(view.component());
            assertEquals(40, row.chipCount());

            JScrollPane scroll = find(view.component(), JScrollPane.class);
            int scrollHeight = scroll.getHeight();
            int timed = scroll.getViewport().getHeight();
            assertTrue(timed >= scrollHeight * 0.4, "시간 본문 높이 " + timed + " / 스크롤 영역 " + scrollHeight);

            JViewport pane = (JViewport) row.getParent();
            assertTrue(pane.getHeight() <= Math.round(scrollHeight * 0.4), "하루 종일 줄 높이 " + pane.getHeight());
            assertTrue(row.getHeight() > pane.getHeight(), "내용이 보이는 높이보다 길어 줄 안에서 스크롤된다");

            Rectangle last = row.chipBounds(2, 39);
            assertNotNull(last);
            row.scrollRectToVisible(last);
            Rectangle visible = pane.getViewRect();
            assertTrue(visible.contains(last), "마지막 칩이 보인다: " + last + " in " + visible);
        });
    }
}
