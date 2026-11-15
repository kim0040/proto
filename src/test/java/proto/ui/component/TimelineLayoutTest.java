package proto.ui.component;

import org.junit.jupiter.api.Test;
import proto.ui.component.TimelineLayout.Placement;
import proto.ui.component.TimelineLayout.Span;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 시간이 겹치는 일정을 나란히 나누는 계산 (겹침 없음, 2개, 3개 연쇄, 끝=시작은 겹치지 않음) */
class TimelineLayoutTest {

    private static Span span(int startHour, int startMin, int endHour, int endMin) {
        return new Span(startHour * 60 + startMin, endHour * 60 + endMin);
    }

    @Test
    void 겹치지_않으면_모두_한_줄을_쓴다() {
        List<Placement> p = TimelineLayout.pack(List.of(span(9, 0, 10, 0), span(11, 0, 12, 0)), 0);
        assertEquals(new Placement(0, 1), p.get(0));
        assertEquals(new Placement(0, 1), p.get(1));
    }

    @Test
    void 끝과_시작이_같으면_겹치지_않는다() {
        List<Placement> p = TimelineLayout.pack(List.of(span(9, 0, 10, 0), span(10, 0, 11, 0)), 0);
        assertEquals(new Placement(0, 1), p.get(0));
        assertEquals(new Placement(0, 1), p.get(1));
    }

    @Test
    void 둘이_겹치면_반씩_나눈다() {
        List<Placement> p = TimelineLayout.pack(List.of(span(9, 0, 10, 30), span(10, 0, 11, 0)), 0);
        assertEquals(new Placement(0, 2), p.get(0));
        assertEquals(new Placement(1, 2), p.get(1));
    }

    @Test
    void 셋이_이어져_겹치면_한_묶음으로_두_줄을_나눠_쓴다() {
        // A 09:00-10:00, B 09:30-10:30, C 10:00-11:00: C는 A가 끝난 첫째 줄을 다시 쓰고, 셋 모두 줄 수는 2
        List<Placement> p = TimelineLayout.pack(List.of(span(9, 0, 10, 0), span(9, 30, 10, 30), span(10, 0, 11, 0)), 0);
        assertEquals(new Placement(0, 2), p.get(0));
        assertEquals(new Placement(1, 2), p.get(1));
        assertEquals(new Placement(0, 2), p.get(2));
    }

    @Test
    void 셋이_한꺼번에_겹치면_세_줄이다() {
        List<Placement> p = TimelineLayout.pack(
                List.of(span(9, 0, 12, 0), span(9, 30, 10, 0), span(9, 45, 10, 15)), 0);
        assertEquals(List.of(new Placement(0, 3), new Placement(1, 3), new Placement(2, 3)), p);
    }

    @Test
    void 묶음이_끝나면_다음_묶음은_다시_한_줄이다() {
        List<Placement> p = TimelineLayout.pack(
                List.of(span(9, 0, 10, 0), span(9, 30, 10, 0), span(13, 0, 14, 0)), 0);
        assertEquals(new Placement(0, 1), p.get(2));
        assertEquals(2, p.get(0).columns());
    }

    @Test
    void 입력_순서와_상관없이_같은_자리에_놓이고_결과는_입력_순서를_따른다() {
        List<Placement> p = TimelineLayout.pack(List.of(span(10, 0, 11, 0), span(9, 0, 10, 30)), 0);
        assertEquals(new Placement(1, 2), p.get(0));
        assertEquals(new Placement(0, 2), p.get(1));
    }

    @Test
    void 짧은_일정은_최소_길이만큼_차지한다고_본다() {
        // 09:00-09:10 과 09:20-10:00 : 최소 28분이면 앞 일정이 09:28까지라 겹친다
        List<Span> spans = List.of(span(9, 0, 9, 10), span(9, 20, 10, 0));
        assertEquals(new Placement(0, 1), TimelineLayout.pack(spans, 0).get(0));
        assertEquals(2, TimelineLayout.pack(spans, 28).get(0).columns());
    }

    @Test
    void 빈_목록은_빈_결과() {
        assertEquals(List.of(), TimelineLayout.pack(List.of(), 28));
    }
}
