package proto.ui.component;

import org.junit.jupiter.api.Test;
import proto.entity.Occurrence;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 옮긴 뒤 옮겨진 일정 알아보기: 제목·시각이 같은 일정이 여럿이어도 번호·날짜로, "이 일정만"으로 새 번호가 생기면 옮기기 전에 없던 번호로 */
class MovedItemTest {

    private static final LocalDate MON = LocalDate.of(2026, 11, 16);
    private static final LocalDate TUE = MON.plusDays(1);

    private static Occurrence at(String id, LocalDate date, int hour) {
        return new Occurrence(id, date, "회의", LocalTime.of(hour, 0), LocalTime.of(hour + 1, 0), "class", "",
                RepeatRule.NONE, ReminderOption.NONE, false);
    }

    @Test
    void 같은_날_같은_시각의_다른_일정이_먼저_있어도_옮긴_일정을_고른다() {
        Occurrence first = at("first", MON, 9);
        Occurrence second = at("second", MON, 10);
        MovedItem m = new MovedItem(second, MON, LocalTime.of(9, 0), List.of(first, second));
        Occurrence after = at("second", MON, 9);
        assertEquals(after, m.pick(List.of(first, after)));
    }

    @Test
    void 다른_날로_옮기면_그_날의_같은_번호를_고른다() {
        Occurrence other = at("other", TUE, 9);
        Occurrence mine = at("mine", MON, 9);
        MovedItem m = new MovedItem(mine, TUE, LocalTime.of(9, 0), List.of(other, mine));
        Occurrence after = at("mine", TUE, 9);
        assertEquals(after, m.pick(List.of(other, after)));
    }

    @Test
    void 이_일정만_떼어_내면_옮기기_전에_없던_번호를_고른다() {
        Occurrence other = at("other", TUE, 9);
        Occurrence series = at("series", MON, 9);
        Occurrence seriesTue = at("series", TUE, 9);
        MovedItem m = new MovedItem(series, TUE, LocalTime.of(9, 0), List.of(other, series, seriesTue));
        Occurrence detached = at("new", TUE, 9);
        assertEquals(detached, m.pick(List.of(other, seriesTue, detached)));
    }

    @Test
    void 맞는_일정이_없으면_null이다() {
        Occurrence mine = at("mine", MON, 9);
        MovedItem m = new MovedItem(mine, TUE, LocalTime.of(9, 0), List.of(mine));
        assertNull(m.pick(List.of(at("x", TUE, 10), at("mine", MON, 9))));
    }
}
