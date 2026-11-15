package proto.ui.component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 날짜 칸 위치와 분 ↔ 세로 위치 환산 */
class TimelineMetricsTest {

    private final TimelineMetrics m = new TimelineMetrics(56, 56 + 700, 48, 12);

    @Test
    void 날짜_칸은_빈틈없이_이어진다() {
        for (int d = 0; d < 7; d++) {
            assertEquals(m.columnX(d + 1), m.columnX(d) + m.columnWidth(d));
        }
        assertEquals(56, m.columnX(0));
        assertEquals(756, m.columnX(7));
    }

    @Test
    void 위치로_날짜_칸을_찾고_바깥은_가장자리_칸이다() {
        assertEquals(0, m.dayAt(56));
        assertEquals(2, m.dayAt(56 + 250));
        assertEquals(6, m.dayAt(755));
        assertEquals(0, m.dayAt(10));
        assertEquals(6, m.dayAt(5000));
    }

    @Test
    void 분과_세로_위치는_서로_바뀐다() {
        assertEquals(12, m.yOf(0));
        assertEquals(12 + 48 * 9, m.yOf(9 * 60));
        assertEquals(9 * 60, m.minuteAt(m.yOf(9 * 60)));
        assertEquals(0, m.minuteAt(-50));
        assertEquals(1440, m.minuteAt(100000));
        assertEquals(60, m.minutesOf(48));
        assertEquals(12 * 2 + 48 * 24, m.totalHeight());
    }
}
