package proto.ui.component;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import proto.entity.Occurrence;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.entity.ScheduleState;
import proto.ui.motion.Motion;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Point;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static proto.TestData.t;

/** 일정 목록 변화(FLIP): 남은 행은 예전 자리에서 미끄러지고, 지워진 행은 그림자로 남고, 새 행은 나타나며, 빠른 연속 갱신은 움직이지 않는다 */
class OccurrenceListFlipTest {

    private boolean before;

    @BeforeEach
    void rememberSetting() {
        before = Motion.enabled();
        Motion.setEnabledTransient(true);
    }

    @AfterEach
    void restoreSetting() {
        Motion.setEnabledTransient(before);
    }

    /** 화면에 보이는 것처럼 행동하는 목록 */
    private static final class ShowingList extends OccurrenceList {
        private boolean showing;

        ShowingList() {
            super(new Handler(), false);
        }

        /** 처음 채울 때는 아직 안 보이는 상태, 그 뒤로 보이는 상태로 만든다 */
        void appear() {
            showing = true;
        }

        @Override
        public boolean isShowing() {
            return showing;
        }
    }

    private static final class Handler implements RowHandler {
        @Override
        public void open(Occurrence occurrence) {
        }

        @Override
        public boolean delete(Occurrence occurrence) {
            return false;
        }

        @Override
        public void toggleDone(Occurrence occurrence) {
        }

        @Override
        public boolean nudge(Occurrence occurrence, int days, int minutes) {
            return false;
        }

        @Override
        public void showMenu(Component source, Point point, Occurrence occurrence) {
        }

        @Override
        public ScheduleState stateOf(Occurrence occurrence) {
            return ScheduleState.SCHEDULED;
        }

        @Override
        public String categoryName(String categoryId) {
            return "수업";
        }
    }

    private static Occurrence occ(String id, String start) {
        return new Occurrence(id, LocalDate.of(2026, 11, 16), "일정 " + id, t(start), t("23:00"), "class", "",
                RepeatRule.NONE, ReminderOption.NONE, false);
    }

    private static OccurrenceRow row(OccurrenceList list, int index) {
        int seen = 0;
        for (Component c : list.getComponents()) {
            if (c instanceof OccurrenceRow r && seen++ == index) {
                return r;
            }
        }
        throw new AssertionError("행이 없다: " + index);
    }

    private static long ghosts(OccurrenceList list) {
        return java.util.Arrays.stream(list.getComponents()).filter(c -> c instanceof RowGhost).count();
    }

    private static void lay(OccurrenceList list) {
        list.setSize(700, 600);
        list.doLayout();
    }

    @Test
    void 행을_지우면_아래_행이_예전_자리에서_올라오고_지워진_행은_그림자로_남는다() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ShowingList list = new ShowingList();
            list.setItems(List.of(occ("a", "08:00"), occ("b", "09:00"), occ("c", "10:00")), false);
            lay(list);
            list.appear();
            int height = row(list, 0).getHeight();
            sleepQuietly(200);
            list.setItems(List.of(occ("b", "09:00"), occ("c", "10:00")), true);
            assertEquals(1, ghosts(list));
            assertEquals(height, row(list, 0).motion().offset(), "b는 한 줄 아래에서 올라온다");
            assertEquals(height, row(list, 1).motion().offset());
            assertEquals(0, row(list, 0).getY() - height, "그려지는 자리는 예전 자리");
            assertEquals(2, list.count());
        });
    }

    @Test
    void 새_행은_안_보이는_채_아래에서_시작해_나타난다() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ShowingList list = new ShowingList();
            list.setItems(List.of(occ("a", "08:00")), false);
            lay(list);
            list.appear();
            sleepQuietly(200);
            list.setItems(List.of(occ("a", "08:00"), occ("b", "09:00")), true);
            OccurrenceRow added = row(list, 1);
            assertEquals(0f, added.motion().alpha(), 0f);
            assertTrue(added.motion().offset() > 0, "6px 아래에서 시작");
            assertEquals(0, row(list, 0).motion().offset(), "그대로인 행은 안 움직인다");
        });
    }

    @Test
    void 글자를_빠르게_치는_중이면_움직이지_않고_바로_맞춘다() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ShowingList list = new ShowingList();
            list.setItems(List.of(occ("a", "08:00"), occ("b", "09:00")), false);
            lay(list);
            list.appear();
            list.setItems(List.of(occ("b", "09:00")), true);
            assertEquals(0, ghosts(list));
            assertEquals(0, row(list, 0).motion().offset());
            assertEquals(1f, row(list, 0).motion().alpha(), 0f);
        });
    }

    @Test
    void 애니메이션이_꺼져_있으면_움직이지_않는다() throws Exception {
        Motion.setEnabledTransient(false);
        SwingUtilities.invokeAndWait(() -> {
            ShowingList list = new ShowingList();
            list.setItems(List.of(occ("a", "08:00"), occ("b", "09:00")), false);
            lay(list);
            list.appear();
            sleepQuietly(200);
            list.setItems(List.of(occ("b", "09:00")), true);
            assertEquals(0, ghosts(list));
            assertEquals(0, row(list, 0).motion().offset());
        });
    }

    @Test
    void 화면에_안_붙은_목록은_움직이지_않고_첫_행을_알려_준다() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            OccurrenceList list = new OccurrenceList(new Handler(), false);
            list.setItems(List.of(occ("a", "08:00"), occ("b", "09:00")), false);
            sleepQuietly(200);
            list.setItems(List.of(occ("b", "09:00")), true);
            assertEquals(0, ghosts(list));
            assertSame(row(list, 0), list.firstRow());
            assertEquals(1, list.count());
        });
    }

    @Test
    void 행은_위에서_아래로_쌓인다() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            OccurrenceList list = new OccurrenceList(new Handler(), false);
            list.setItems(List.of(occ("a", "08:00"), occ("b", "09:00"), occ("c", "10:00")), false);
            lay(list);
            int h = row(list, 0).getHeight();
            assertEquals(0, row(list, 0).getY());
            assertEquals(h, row(list, 1).getY());
            assertEquals(2 * h, row(list, 2).getY());
            assertEquals(3 * h, list.getPreferredSize().height);
        });
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
