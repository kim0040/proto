package proto.ui.component;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import proto.entity.Occurrence;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.entity.ScheduleState;
import proto.ui.motion.EdtErrors;
import proto.ui.motion.Motion;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 시간표 놓기 통합: 애니메이션을 꺼도 놓은 뒤 배치가 깨지지 않는지, 끄는 도중 주가 바뀌거나 다시 채워지거나 화면에서 떨어지면 옮기지 않는지,
 * 제목·시각이 같은 일정이 여럿이어도 옮겨진 일정을 번호·날짜로 알아보는지.
 */
class TimelineGridDropTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);
    private boolean motionBefore;

    @BeforeEach
    void remember() {
        motionBefore = Motion.enabled();
    }

    @AfterEach
    void restore() {
        Motion.setEnabledTransient(motionBefore);
    }

    /** 옮기기 요청을 받아 목록을 바꾸고, 컨트롤러처럼 그 자리에서 시간표를 다시 채운다 */
    private static final class Store implements TimelineHandler {
        final List<Occurrence> data = new ArrayList<>();
        final List<String> moves = new ArrayList<>();
        TimelineGrid grid;
        LocalDate week = MONDAY;
        /** null이 아니면 "이 일정만" 옮기기처럼 이 새 번호로 떼어 낸다 */
        String detachAs;

        @Override
        public boolean move(Occurrence o, LocalDate date, LocalTime start, LocalTime end) {
            moves.add(o.scheduleId() + "@" + o.date() + "→" + date + " " + start);
            data.removeIf(n -> n.key().equals(o.key()));
            String id = detachAs != null ? detachAs : o.scheduleId();
            data.add(new Occurrence(id, date, o.title(), start, end, o.categoryId(), o.memo(), RepeatRule.NONE,
                    ReminderOption.NONE, false));
            grid.setWeek(week, data);
            return true;
        }

        @Override
        public void create(LocalDate date, LocalTime start, LocalTime end) {
        }

        @Override
        public void selectDay(LocalDate day) {
        }

        @Override
        public void open(Occurrence o) {
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
            return false;
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

    private static Occurrence timed(String id, LocalDate date, String title, int hour) {
        return new Occurrence(id, date, title, LocalTime.of(hour, 0), LocalTime.of(hour + 1, 0), "class", "",
                RepeatRule.NONE, ReminderOption.NONE, false);
    }

    private static TimelineGrid grid(Store store, List<Occurrence> items) {
        TimelineGrid g = new TimelineGrid(store, () -> LocalDateTime.of(2026, 11, 16, 8, 0));
        store.grid = g;
        store.data.addAll(items);
        g.setWeek(MONDAY, store.data);
        g.setSize(756, g.getPreferredSize().height);
        g.doLayout();
        return g;
    }

    private static TimelineBlock blockOf(TimelineGrid g, String id) {
        for (Component c : g.getComponents()) {
            if (c instanceof TimelineBlock b && b.occurrence().scheduleId().equals(id)) {
                return b;
            }
        }
        throw new AssertionError("블록 없음: " + id);
    }

    private static Point center(TimelineBlock b) {
        Rectangle r = b.getBounds();
        return new Point(r.x + r.width / 2, r.y + r.height / 2);
    }

    /** 블록을 눌러 dy만큼 끌기 시작한다 (놓지는 않는다) */
    private static Point lift(TimelineGrid g, TimelineBlock b, int dy) {
        Point p = center(b);
        g.drag().pressBlock(b, p, TimelineBlock.Zone.BODY);
        Point to = new Point(p.x, p.y + dy);
        g.drag().dragged(new Point(p.x, p.y + dy / 2));
        g.drag().dragged(to);
        return to;
    }

    @Test
    void 애니메이션을_끄고_옮겨도_다시_배치할_때_예외가_나지_않는다() throws Exception {
        Motion.setEnabledTransient(false);
        try (EdtErrors edt = new EdtErrors()) {
            Store store = new Store();
            SwingUtilities.invokeAndWait(() -> {
                TimelineGrid g = grid(store, List.of(timed("a", MONDAY, "회의", 9)));
                Point to = lift(g, blockOf(g, "a"), g.metrics().hourHeight() * 2);
                g.drag().released(to, 1);
                g.doLayout();
                assertEquals(1, store.moves.size());
                Rectangle r = blockOf(g, "a").getBounds();
                assertEquals(g.yOfMinute(11 * 60) + 1, r.y, "새 자리에 바로 놓인다: " + r);
            });
            EdtErrors.flush();
            assertEquals(List.of(), edt.errors());
        }
    }

    @Test
    void 애니메이션을_켜고_옮기면_내려앉은_뒤_새_자리에_선다() throws Exception {
        Motion.setEnabledTransient(true);
        try (EdtErrors edt = new EdtErrors()) {
            Store store = new Store();
            TimelineGrid[] holder = new TimelineGrid[1];
            SwingUtilities.invokeAndWait(() -> {
                TimelineGrid g = grid(store, List.of(timed("a", MONDAY, "회의", 9)));
                holder[0] = g;
                Point to = lift(g, blockOf(g, "a"), g.metrics().hourHeight() * 2);
                g.drag().released(to, 1);
                g.doLayout();
            });
            Thread.sleep(500);
            SwingUtilities.invokeAndWait(() -> {
                holder[0].doLayout();
                assertEquals(holder[0].yOfMinute(11 * 60) + 1, blockOf(holder[0], "a").getY());
            });
            assertEquals(List.of(), edt.errors());
        }
    }

    @Test
    void 끄는_도중_다음_주로_넘어가면_놓아도_옮기지_않는다() throws Exception {
        Motion.setEnabledTransient(false);
        SwingUtilities.invokeAndWait(() -> {
            Store store = new Store();
            TimelineGrid g = grid(store, List.of(timed("a", MONDAY, "회의", 9)));
            Point to = lift(g, blockOf(g, "a"), 10);
            LocalDate next = MONDAY.plusWeeks(1);
            g.setWeek(next, List.of(timed("b", next, "다른 주", 14)));
            g.drag().dragged(new Point(to.x, to.y + 5));
            g.drag().released(new Point(to.x, to.y + 5), 1);
            assertEquals(List.of(), store.moves, "다른 주 날짜로 옮기면 안 된다");
        });
    }

    @Test
    void 끄는_도중_같은_주가_다시_채워져도_옮기지_않고_원래_블록의_흐림이_풀린다() throws Exception {
        Motion.setEnabledTransient(false);
        SwingUtilities.invokeAndWait(() -> {
            Store store = new Store();
            TimelineGrid g = grid(store, List.of(timed("a", MONDAY, "회의", 9)));
            TimelineBlock old = blockOf(g, "a");
            Point to = lift(g, old, 60);
            g.setWeek(MONDAY, store.data);
            g.drag().released(to, 1);
            assertEquals(List.of(), store.moves);
            assertFalse(old.isDimmed());
        });
    }

    @Test
    void 끄는_도중_시간표가_화면에서_떨어지면_놓아도_옮기지_않는다() throws Exception {
        Motion.setEnabledTransient(false);
        SwingUtilities.invokeAndWait(() -> {
            Store store = new Store();
            TimelineGrid g = grid(store, List.of(timed("a", MONDAY, "회의", 9)));
            Point to = lift(g, blockOf(g, "a"), 60);
            g.removeNotify();
            g.drag().released(to, 1);
            assertEquals(List.of(), store.moves);
        });
    }

    @Test
    void 제목과_시각이_같은_일정_칸으로_옮겨도_옮긴_일정에_내려앉는다() throws Exception {
        Motion.setEnabledTransient(true);
        try (EdtErrors edt = new EdtErrors()) {
            SwingUtilities.invokeAndWait(() -> {
                Store store = new Store();
                TimelineGrid g = grid(store, List.of(timed("first", MONDAY, "회의", 9), timed("second", MONDAY, "회의", 10)));
                Point to = lift(g, blockOf(g, "second"), -g.metrics().hourHeight());
                g.drag().released(to, 1);
                assertEquals(List.of("second@2026-11-16→2026-11-16 09:00"), store.moves);
                Occurrence landing = g.landingItem();
                assertNotNull(landing, "내려앉을 블록이 있어야 한다");
                assertEquals("second", landing.scheduleId());
            });
            Thread.sleep(400);
            EdtErrors.flush();
            assertEquals(List.of(), edt.errors());
        }
    }

    @Test
    void 이_일정만_떼어_내_새_번호가_생겨도_같은_제목의_다른_일정이_아니라_새_일정에_내려앉는다() throws Exception {
        Motion.setEnabledTransient(true);
        SwingUtilities.invokeAndWait(() -> {
            Store store = new Store();
            store.detachAs = "detached";
            TimelineGrid g = grid(store, List.of(timed("other", MONDAY.plusDays(1), "회의", 9),
                    timed("series", MONDAY, "회의", 9)));
            Point to = lift(g, blockOf(g, "series"), 0);
            to.translate(g.metrics().columnX(1) - g.metrics().columnX(0), 0);
            g.drag().dragged(to);
            g.drag().released(to, 1);
            assertEquals(List.of("series@2026-11-16→2026-11-17 09:00"), store.moves);
            assertEquals("detached", g.landingItem().scheduleId());
        });
    }

    @Test
    void 옮긴_뒤_포커스는_번호와_날짜로_찾은_블록에_간다() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Store store = new Store();
            TimelineGrid g = grid(store, List.of(timed("first", MONDAY, "회의", 9), timed("second", MONDAY, "회의", 10)));
            List<Occurrence> before = List.copyOf(store.data);
            Occurrence second = before.get(1);
            MovedItem moved = new MovedItem(second, MONDAY, LocalTime.of(9, 0), before);
            store.move(second, MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0));
            assertEquals("second", g.movedBlock(moved).occurrence().scheduleId());
        });
    }
}
