package proto.ui.component;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import proto.entity.Occurrence;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.ui.motion.Motion;

import java.awt.Point;
import java.awt.Rectangle;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static proto.TestData.d;
import static proto.TestData.t;

/** 달력 끌기: 4px 문턱, 놓일 날짜, 취소, 따라오는 클릭 삼키기, 일정 줄 찾기 계산 */
class MonthDragTest {

    private static final int CELL_W = 100;
    private static final int CELL_H = 80;
    private static final int HEADER = 30;
    private static final LocalDate FIRST = d("2026-10-26");

    private final Occurrence item = new Occurrence("id", d("2026-11-16"), "회의", t("09:00"), t("10:00"), "class",
            "", RepeatRule.NONE, ReminderOption.NONE, false);
    private final Occurrence twin = new Occurrence("twin", d("2026-11-16"), "회의", t("09:00"), t("10:00"), "class",
            "", RepeatRule.NONE, ReminderOption.NONE, false);
    private final List<Occurrence> shown = new ArrayList<>(List.of(item, twin));
    private final List<String> drops = new ArrayList<>();
    private LocalDate first = FIRST;
    private final javax.swing.JPanel view = new javax.swing.JPanel();
    private boolean dragging;
    private boolean motionBefore;
    private MonthDrag drag;

    /** 7×6 칸(너비 100, 높이 80, 위에 머리글 30). 칸 21(넷째 줄 월요일)=11월 16일 위쪽 줄(y 34~52)에만 일정이 있다 */
    private final MonthDrag.Host host = new MonthDrag.Host() {
        @Override
        public Occurrence itemAt(int x, int y) {
            return cellAt(x, y) == 21 && y - HEADER - 3 * CELL_H >= 34 && y - HEADER - 3 * CELL_H < 52 ? item : null;
        }

        @Override
        public Rectangle itemBounds(int x, int y) {
            return itemAt(x, y) == null ? null : new Rectangle(8, HEADER + 3 * CELL_H + 34, 86, 18);
        }

        @Override
        public Rectangle landingRow(LocalDate date, Occurrence landed) {
            return date.equals(d("2026-11-19")) ? new Rectangle(308, HEADER + 3 * CELL_H + 34, 86, 18) : null;
        }

        @Override
        public List<Occurrence> shownItems() {
            return shown;
        }

        @Override
        public javax.swing.JComponent view() {
            return view;
        }

        @Override
        public int cellAt(int x, int y) {
            if (y < HEADER || x < 0 || x >= CELL_W * 7 || y >= HEADER + CELL_H * 6) {
                return -1;
            }
            return (y - HEADER) / CELL_H * 7 + x / CELL_W;
        }

        @Override
        public Rectangle cellBounds(int index) {
            return new Rectangle(index % 7 * CELL_W, HEADER + index / 7 * CELL_H, CELL_W, CELL_H);
        }

        @Override
        public LocalDate dateAt(int index) {
            return first.plusDays(index);
        }

        @Override
        public void dragging(boolean on) {
            dragging = on;
        }

        @Override
        public void repaintGrid() {
        }
    };

    @BeforeEach
    void setUp() {
        motionBefore = Motion.enabled();
        Motion.setEnabledTransient(false);
        view.setSize(CELL_W * 7, HEADER + CELL_H * 6);
        drag = new MonthDrag(host, (o, day) -> drops.add(o.title() + "→" + day));
    }

    @AfterEach
    void tearDown() {
        Motion.setEnabledTransient(motionBefore);
    }

    private static final int ITEM_X = 20;
    private static final int ITEM_Y = HEADER + 3 * CELL_H + 40;

    @Test
    void 일정_줄이_아닌_곳을_누르면_끌기_후보가_아니다() {
        assertFalse(drag.press(20, 40));
        drag.drag(300, 200);
        assertFalse(drag.isActive());
    }

    @Test
    void 문턱_안에서는_끌기가_시작되지_않는다() {
        assertTrue(drag.press(ITEM_X, ITEM_Y));
        drag.drag(ITEM_X + 3, ITEM_Y + 2);
        assertFalse(drag.isActive());
        drag.release(ITEM_X + 3, ITEM_Y + 2);
        assertTrue(drops.isEmpty());
        assertFalse(drag.takeSwallowedClick());
    }

    @Test
    void 문턱을_넘겨_다른_날_칸에_놓으면_그_날로_옮긴다() {
        assertTrue(drag.press(ITEM_X, ITEM_Y));
        drag.drag(ITEM_X + 30, ITEM_Y);
        assertTrue(drag.isActive());
        assertTrue(dragging);
        drag.release(ITEM_X + 300, ITEM_Y);
        assertEquals(List.of("회의→2026-11-19"), drops);
        assertFalse(dragging);
        assertTrue(drag.takeSwallowedClick());
        assertFalse(drag.takeSwallowedClick());
    }

    @Test
    void 같은_날이나_칸_밖에_놓으면_아무것도_하지_않는다() {
        drag.press(ITEM_X, ITEM_Y);
        drag.drag(ITEM_X + 10, ITEM_Y + 20);
        drag.release(ITEM_X + 10, ITEM_Y + 20);
        drag.press(ITEM_X, ITEM_Y);
        drag.drag(ITEM_X + 10, ITEM_Y);
        drag.release(-5, 10);
        assertTrue(drops.isEmpty());
    }

    @Test
    void Esc로_취소하면_놓아도_옮기지_않는다() {
        drag.press(ITEM_X, ITEM_Y);
        drag.drag(ITEM_X + 100, ITEM_Y);
        drag.cancel();
        assertFalse(drag.isActive());
        assertFalse(dragging);
        drag.release(ITEM_X + 100, ITEM_Y);
        assertTrue(drops.isEmpty());
    }

    @Test
    void 새로_누르면_삼켜야_할_클릭_표시가_지워진다() {
        drag.press(ITEM_X, ITEM_Y);
        drag.drag(ITEM_X + 50, ITEM_Y);
        drag.release(ITEM_X + 50, ITEM_Y);
        drag.press(20, 40);
        assertFalse(drag.takeSwallowedClick());
    }

    @Test
    void 문턱_계산은_가로와_세로_어느_쪽이든_넘으면_참이다() {
        Point from = new Point(10, 10);
        assertFalse(MonthDrag.beyond(from, 14, 14, 4));
        assertTrue(MonthDrag.beyond(from, 15, 10, 4));
        assertTrue(MonthDrag.beyond(from, 10, 5 - 1, 4));
    }

    @Test
    void 줄_수는_높이에_맞고_넘치면_마지막_줄을_더하기_표시에_내준다() {
        assertEquals(2, MonthItems.shown(2, 54, 18));
        assertEquals(3, MonthItems.shown(3, 54, 18));
        assertEquals(2, MonthItems.shown(5, 54, 18));
        assertEquals(1, MonthItems.shown(5, 36, 18));
        assertEquals(0, MonthItems.shown(5, 10, 18));
    }

    @Test
    void 눌린_자리가_몇_번째_일정_줄인지_찾는다() {
        // 칸 폭 100: 줄은 x 8~86, 첫 줄 y 34부터 18씩, 높이 80이면 쓸 수 있는 높이 44
        assertEquals(0, MonthItems.indexAt(20, 34, 2, 34, 8, 86, 44, 18));
        assertEquals(1, MonthItems.indexAt(20, 52, 2, 34, 8, 86, 44, 18));
        assertEquals(-1, MonthItems.indexAt(20, 70, 2, 34, 8, 86, 44, 18));
        assertEquals(-1, MonthItems.indexAt(20, 20, 2, 34, 8, 86, 44, 18));
        assertEquals(-1, MonthItems.indexAt(4, 40, 2, 34, 8, 86, 44, 18));
        assertEquals(-1, MonthItems.indexAt(95, 40, 2, 34, 8, 86, 44, 18));
        // 일정 5개, 쓸 수 있는 높이 44(두 줄) → 첫 줄만 이름을 그리고 둘째 줄은 "+N"이라 끌 수 없다
        assertEquals(0, MonthItems.indexAt(20, 40, 5, 34, 8, 86, 44, 18));
        assertEquals(-1, MonthItems.indexAt(20, 52, 5, 34, 8, 86, 44, 18));
    }
    @Test
    void 끄는_도중_달이_바뀌면_놓아도_옮기지_않는다() {
        drag.press(ITEM_X, ITEM_Y);
        drag.drag(ITEM_X + 300, ITEM_Y);
        first = FIRST.plusWeeks(5);
        drag.release(ITEM_X + 300, ITEM_Y);
        assertTrue(drops.isEmpty(), "다른 달 날짜로 옮기면 안 된다: " + drops);
        assertFalse(drag.isActive());
        assertFalse(dragging);
    }

    @Test
    void 거두면_놓아도_옮기지_않고_따라오는_클릭을_삼킨다() {
        drag.press(ITEM_X, ITEM_Y);
        drag.drag(ITEM_X + 300, ITEM_Y);
        drag.abort();
        assertFalse(drag.isActive());
        assertFalse(dragging);
        drag.release(ITEM_X + 300, ITEM_Y);
        assertTrue(drops.isEmpty());
        assertTrue(drag.takeSwallowedClick());
    }

    @Test
    void 결과를_기다리는_동안_같은_제목과_시각의_다른_일정은_흐리게_하지_않는다() {
        drag.press(ITEM_X, ITEM_Y);
        drag.drag(ITEM_X + 300, ITEM_Y);
        drag.release(ITEM_X + 300, ITEM_Y);
        assertEquals(List.of("회의→2026-11-19"), drops);
        assertTrue(drag.dims(item));
        assertFalse(drag.dims(twin), "번호가 다른 일정은 원래 줄이 아니다");
    }

    @Test
    void 내려앉는_동안_숨기는_줄은_옮겨진_일정뿐이다() throws Exception {
        Motion.setEnabledTransient(true);
        Occurrence moved = new Occurrence("id", d("2026-11-19"), "회의", t("09:00"), t("10:00"), "class", "",
                RepeatRule.NONE, ReminderOption.NONE, false);
        Occurrence there = new Occurrence("there", d("2026-11-19"), "회의", t("09:00"), t("10:00"), "class", "",
                RepeatRule.NONE, ReminderOption.NONE, false);
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            drag = new MonthDrag(host, (o, day) -> {
                shown.clear();
                shown.addAll(List.of(there, twin, moved));
                drag.dataChanged();
            });
            drag.press(ITEM_X, ITEM_Y);
            drag.drag(ITEM_X + 300, ITEM_Y);
            drag.release(ITEM_X + 300, ITEM_Y);
            assertTrue(drag.hides(moved));
            assertFalse(drag.hides(there), "같은 제목·시각의 다른 일정은 그대로 보인다");
        });
    }
}
