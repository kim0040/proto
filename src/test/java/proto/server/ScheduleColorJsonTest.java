package proto.server;

import org.junit.jupiter.api.Test;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.entity.Schedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 일정만의 색(color) JSON 필드: 보낼 때는 늘 넣고, 받을 때는 없어도 된다 */
class ScheduleColorJsonTest {

    private static Schedule schedule(String color) {
        Schedule s = new Schedule("a", "회의", LocalDate.of(2026, 11, 16), LocalTime.of(9, 0), LocalTime.of(10, 0),
                "class", "", RepeatRule.NONE, null, ReminderOption.NONE);
        s.setColorKey(color);
        return s;
    }

    private static Schedule readOne(String colorField) {
        String json = ScheduleJsonMapper.writeSnapshot(List.of(schedule(null)))
                .replace("\"color\":null", colorField);
        return ScheduleJsonMapper.readSnapshot(json).get(0);
    }

    @Test
    void 고른_색이_오가고_없으면_null로_보낸다() {
        String json = ScheduleJsonMapper.writeSnapshot(List.of(schedule("rose")));
        assertTrue(json.contains("\"color\":\"rose\""));
        assertEquals("rose", ScheduleJsonMapper.readSnapshot(json).get(0).getColorKey());
        assertTrue(ScheduleJsonMapper.writeSnapshot(List.of(schedule(null))).contains("\"color\":null"));
    }

    @Test
    void 필드가_없거나_모르는_색이면_카테고리_색이다() {
        assertNull(readOne("\"zzz\":1").getColorKey());
        assertNull(readOne("\"color\":\"neon\"").getColorKey());
        assertEquals("green", readOne("\"color\":\"green\"").getColorKey());
    }

    @Test
    void 문자열이_아닌_색은_거절한다() {
        assertThrows(IllegalArgumentException.class, () -> readOne("\"color\":3"));
    }
}
