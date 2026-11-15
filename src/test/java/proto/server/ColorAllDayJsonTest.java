package proto.server;

import org.junit.jupiter.api.Test;
import proto.entity.Category;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.entity.Schedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorAllDayJsonTest {

    private static final String SCHEDULE_FIELDS = "\"id\":\"a\",\"title\":\"학회\",\"date\":\"2026-11-16\","
            + "\"start\":\"09:00\",\"end\":\"10:00\",\"categoryId\":\"class\",\"memo\":\"\",\"repeat\":\"NONE\","
            + "\"repeatEnd\":null,\"reminder\":\"FIVE_MINUTES\",\"completedDates\":[],\"excludedDates\":[]";

    @Test
    void categoryColourRoundTrips() {
        String json = CategoryJsonMapper.writeSnapshot(List.of(new Category("club", "동아리", "indigo")));
        assertTrue(json.contains("\"color\":\"indigo\""));
        assertEquals("indigo", CategoryJsonMapper.readSnapshot(json).get(0).getColorKey());
    }

    @Test
    void missingNullOrUnknownCategoryColourMeansTheDefault() {
        String json = "{\"categories\":[{\"id\":\"class\",\"name\":\"수업\"},"
                + "{\"id\":\"club\",\"name\":\"동아리\",\"color\":null},"
                + "{\"id\":\"x\",\"name\":\"기타\",\"color\":\"pink\"}]}";
        List<String> colors = CategoryJsonMapper.readSnapshot(json).stream().map(Category::getColorKey).toList();
        assertEquals(List.of("blue", "rose", "brown"), colors);
    }

    @Test
    void nonTextCategoryColourIsRejected() {
        String json = "{\"categories\":[{\"id\":\"class\",\"name\":\"수업\",\"color\":3}]}";
        assertThrows(IllegalArgumentException.class, () -> CategoryJsonMapper.readSnapshot(json));
    }

    @Test
    void allDayRoundTrips() {
        Schedule s = new Schedule("a", "학회", LocalDate.of(2026, 11, 16), LocalTime.of(9, 0),
                LocalTime.of(10, 0), "class", "", RepeatRule.NONE, null, ReminderOption.NONE, true);
        String json = ScheduleJsonMapper.writeSnapshot(List.of(s));
        assertTrue(json.contains("\"allDay\":true"));
        Schedule back = ScheduleJsonMapper.readSnapshot(json).get(0);
        assertTrue(back.isAllDay());
        assertEquals(LocalTime.MIDNIGHT, back.getStartTime());
        assertEquals(LocalTime.of(23, 59), back.getEndTime());
    }

    @Test
    void missingOrNullAllDayMeansTimed() {
        Schedule missing = ScheduleJsonMapper.readSnapshot("{\"schedules\":[{" + SCHEDULE_FIELDS + "}]}").get(0);
        assertFalse(missing.isAllDay());
        assertEquals(ReminderOption.FIVE_MINUTES, missing.getReminder());
        String withNull = "{\"schedules\":[{" + SCHEDULE_FIELDS + ",\"allDay\":null}]}";
        assertFalse(ScheduleJsonMapper.readSnapshot(withNull).get(0).isAllDay());
    }

    @Test
    void allDayTrueFromServerDropsTheReminderAndBadTypeIsRejected() {
        String allDay = "{\"schedules\":[{" + SCHEDULE_FIELDS + ",\"allDay\":true}]}";
        assertEquals(ReminderOption.NONE, ScheduleJsonMapper.readSnapshot(allDay).get(0).getReminder());
        String bad = "{\"schedules\":[{" + SCHEDULE_FIELDS + ",\"allDay\":\"true\"}]}";
        assertThrows(IllegalArgumentException.class, () -> ScheduleJsonMapper.readSnapshot(bad));
    }
}
