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
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 하루 종일 줄: 접힘/펼침, 가로 끌기로 날짜만 바꾸기, 시간표 본문에서는 하루 종일 일정이 빠진다 */
class AllDayRowTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 9);
    private boolean before;

    @BeforeEach
    void motionOff() {
        before = Motion.enabled();
        Motion.setEnabledTransient(false);
    }

    @AfterEach
    void restore() {
        Motion.setEnabledTransient(before);
    }

    /** 옮기기·옮기기 요청을 기록하는 연결 */
    private static final class Recorder implements TimelineHandler {
        final List<Object[]> moves = new ArrayList<>();
        final List<Occurrence> opened = new ArrayList<>();
        boolean accept = true;

        @Override
        public boolean move(Occurrence o, LocalDate date, LocalTime start, LocalTime end) {
            moves.add(new Object[] {o, date, start, end});
            return accept;
        }

        @Override
        public void create(LocalDate date, LocalTime start, LocalTime end) {
        }

        @Override
        public void selectDay(LocalDate day) {
        }

        @Override
        public void open(Occurrence o) {
            opened.add(o);
        }

        @Override
        public boolean delete(Occurrence o) {
            return false;
        }

        @Override
        public void toggleDone(Occurrence o) {
        }

        @Override
        public boolean nudge(Occurrence o, int days, int minutes) {
            moves.add(new Object[] {o, days, minutes});
            return true;
        }

        @Override
        public void showMenu(Component source, Point point, Occurrence o) {
        }

        @Override
        public ScheduleState stateOf(Occurrence o) {
            return ScheduleState.SCHEDULED;
        }

        @Override
        public String categoryName(String categoryId) {
            return "수업";
        }
    }

    private static Occurrence allDay(String title, int dayOffset) {
        return new Occurrence("id-" + title, MONDAY.plusDays(dayOffset), title, LocalTime.of(0, 0), LocalTime.of(23, 59),
                "class", "", RepeatRule.NONE, ReminderOption.NONE, false, true);
    }

    private static Occurrence timed(String title, int dayOffset) {
        return new Occurrence("id-" + title, MONDAY.plusDays(dayOffset), title, LocalTime.of(9, 0), LocalTime.of(10, 0),
                "class", "", RepeatRule.NONE, ReminderOption.NONE, false);
    }

    private static AllDayRow row(Recorder h, List<Occurrence> items) {
        AllDayRow row = new AllDayRow(h, 56);
        row.setWeek(MONDAY, MONDAY, items);
        row.setSize(756, row.getPreferredSize().height);
        row.doLayout();
        return row;
    }

    private static List<AllDayButton> buttons(AllDayRow row) {
        List<AllDayButton> out = new ArrayList<>();
        for (Component c : row.getComponents()) {
            if (c instanceof AllDayButton b) {
                out.add(b);
            }
        }
        return out;
    }

    private static void mouse(Component c, int id, int x, int y) {
        c.dispatchEvent(new MouseEvent(c, id, System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1));
    }

    @Test
    void 하루_종일이_없으면_줄의_높이가_0이다() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AllDayRow row = row(new Recorder(), List.of(timed("회의", 0)));
            assertEquals(0, row.getPreferredSize().height);
            assertEquals(0, row.chipCount());
        });
    }

    @Test
    void 한_날에_세_개_이상이면_접히고_더보기를_누르면_펼쳐진다() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AllDayRow row = row(new Recorder(), List.of(allDay("가", 1), allDay("나", 1), allDay("다", 1), allDay("라", 2)));
            int collapsedHeight = row.getPreferredSize().height;
            assertEquals(2, row.chipCount());
            assertEquals(2, buttons(row).size());
            AllDayButton more = buttons(row).stream().filter(b -> b.getAccessibleContext().getAccessibleName().contains("더 보기")).findFirst().orElseThrow();
            mouse(more, MouseEvent.MOUSE_RELEASED, 2, 2);
            assertEquals(4, row.chipCount());
            assertTrue(row.getPreferredSize().height > collapsedHeight);
        });
    }

    @Test
    void 둘_이하면_접히지_않고_화살표도_없다() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AllDayRow row = row(new Recorder(), List.of(allDay("가", 1), allDay("나", 1)));
            assertEquals(2, row.chipCount());
            assertTrue(buttons(row).isEmpty());
        });
    }

    @Test
    void 칩을_다른_날로_끌면_날짜만_바꿔_옮기기를_요청한다() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Recorder h = new Recorder();
            Occurrence item = allDay("행사", 1);
            AllDayRow row = row(h, List.of(item));
            Rectangle r = row.chipBounds(1, 0);
            AllDayChip chip = (AllDayChip) row.getComponentAt(r.x + 2, r.y + 2);
            int startX = r.x + r.width / 2;
            int y = r.height / 2;
            mouse(chip, MouseEvent.MOUSE_PRESSED, startX - r.x, y);
            mouse(chip, MouseEvent.MOUSE_DRAGGED, startX - r.x + 60, y);
            int toX = row.metrics().columnX(3) + 20;
            mouse(chip, MouseEvent.MOUSE_DRAGGED, toX - r.x, y);
            mouse(chip, MouseEvent.MOUSE_RELEASED, toX - r.x, y);
            assertEquals(1, h.moves.size());
            assertEquals(MONDAY.plusDays(3), h.moves.get(0)[1]);
            assertEquals(item.startTime(), h.moves.get(0)[2]);
            assertEquals(item.endTime(), h.moves.get(0)[3]);
            assertTrue(h.opened.isEmpty(), "끌기는 열기로 처리되지 않는다");
        });
    }

    @Test
    void 같은_날에_놓으면_옮기지_않고_칩만_눌렀으면_연다() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Recorder h = new Recorder();
            AllDayRow row = row(h, List.of(allDay("행사", 1)));
            Rectangle r = row.chipBounds(1, 0);
            AllDayChip chip = (AllDayChip) row.getComponentAt(r.x + 2, r.y + 2);
            mouse(chip, MouseEvent.MOUSE_PRESSED, 10, 5);
            mouse(chip, MouseEvent.MOUSE_DRAGGED, 40, 5);
            mouse(chip, MouseEvent.MOUSE_RELEASED, 40, 5);
            assertTrue(h.moves.isEmpty());
            mouse(chip, MouseEvent.MOUSE_PRESSED, 10, 5);
            mouse(chip, MouseEvent.MOUSE_RELEASED, 10, 5);
            assertEquals(1, h.opened.size());
        });
    }

    @Test
    void 시간표_본문은_하루_종일_일정을_블록으로_만들지_않는다() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TimelineGrid grid = new TimelineGrid(new Recorder(), java.time.LocalDateTime::now);
            grid.setWeek(MONDAY, List.of(timed("회의", 0), allDay("행사", 0)));
            long blocks = java.util.Arrays.stream(grid.getComponents()).filter(c -> c instanceof TimelineBlock).count();
            assertEquals(1, blocks);
        });
    }
    /** 칩을 눌러 dx만큼 끌어 둔다 (놓지는 않는다). 칩과 끈 자리(칩 좌표)를 돌려준다 */
    private static Object[] liftChip(AllDayRow row, int day, int dx) {
        Rectangle r = row.chipBounds(day, 0);
        AllDayChip chip = (AllDayChip) row.getComponentAt(r.x + 2, r.y + 2);
        mouse(chip, MouseEvent.MOUSE_PRESSED, 10, 5);
        mouse(chip, MouseEvent.MOUSE_DRAGGED, 10 + dx / 2, 5);
        mouse(chip, MouseEvent.MOUSE_DRAGGED, 10 + dx, 5);
        return new Object[] {chip, 10 + dx};
    }

    @Test
    void 끄는_도중_다음_주로_넘어가면_놓아도_옮기지도_열지도_않는다() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Recorder h = new Recorder();
            AllDayRow row = row(h, List.of(allDay("행사", 1)));
            Object[] held = liftChip(row, 1, row.metrics().columnX(3) - row.metrics().columnX(1));
            LocalDate next = MONDAY.plusWeeks(1);
            row.setWeek(next, next, List.of(new Occurrence("id-다음", next.plusDays(2), "다음 주", LocalTime.of(0, 0),
                    LocalTime.of(23, 59), "class", "", RepeatRule.NONE, ReminderOption.NONE, false, true)));
            mouse((AllDayChip) held[0], MouseEvent.MOUSE_RELEASED, (int) held[1], 5);
            assertTrue(h.moves.isEmpty(), "다른 주 날짜로 옮기면 안 된다");
            assertTrue(h.opened.isEmpty(), "거둔 끌기의 놓기는 열기로 처리되지 않는다");
        });
    }

    @Test
    void 끄는_도중_줄이_화면에서_떨어지면_놓아도_옮기지_않는다() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Recorder h = new Recorder();
            AllDayRow row = row(h, List.of(allDay("행사", 1)));
            Object[] held = liftChip(row, 1, row.metrics().columnX(3) - row.metrics().columnX(1));
            row.removeNotify();
            mouse((AllDayChip) held[0], MouseEvent.MOUSE_RELEASED, (int) held[1], 5);
            assertTrue(h.moves.isEmpty());
            assertTrue(h.opened.isEmpty());
        });
    }

    @Test
    void 옮긴_뒤_같은_제목의_다른_칩이_아니라_옮긴_일정의_칩을_찾는다() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Occurrence other = new Occurrence("other", MONDAY.plusDays(3), "행사", LocalTime.of(0, 0),
                    LocalTime.of(23, 59), "class", "", RepeatRule.NONE, ReminderOption.NONE, false, true);
            Occurrence mine = new Occurrence("mine", MONDAY.plusDays(1), "행사", LocalTime.of(0, 0),
                    LocalTime.of(23, 59), "class", "", RepeatRule.NONE, ReminderOption.NONE, false, true);
            AllDayRow row = row(new Recorder(), List.of(other, mine));
            MovedItem moved = row.movedItem(mine, MONDAY.plusDays(3));
            Occurrence after = new Occurrence("mine", MONDAY.plusDays(3), "행사", LocalTime.of(0, 0),
                    LocalTime.of(23, 59), "class", "", RepeatRule.NONE, ReminderOption.NONE, false, true);
            row.setWeek(MONDAY, MONDAY, List.of(other, after));
            row.doLayout();
            assertEquals("mine", row.chipFor(moved).occurrence().scheduleId());
        });
    }
}
