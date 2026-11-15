package proto.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.entity.Schedule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static proto.TestData.d;
import static proto.TestData.t;

/** 일정만의 색(color 열, 14번째 칸) */
class ScheduleColorCsvTest {

    private static final String HEADER =
            "id,title,date,start,end,categoryId,memo,repeat,repeatEnd,reminder,completedDates,excludedDates,all_day,color\n";

    @TempDir
    Path dir;

    @Test
    void 색이_마지막_열로_오가고_없으면_빈_칸이다() throws IOException {
        Path file = dir.resolve("schedules.csv");
        ScheduleRepository repo = new ScheduleRepository(file);
        repo.load();
        Schedule a = new Schedule("a", "회의", d("2026-11-16"), t("09:00"), t("10:00"), "class", "",
                RepeatRule.NONE, null, ReminderOption.NONE);
        a.setColorKey("rose");
        repo.add(a);
        repo.add(new Schedule("b", "수업", d("2026-11-16"), t("11:00"), t("12:00"), "class", "",
                RepeatRule.NONE, null, ReminderOption.NONE));
        repo.save();
        assertEquals(HEADER + "a,회의,2026-11-16,09:00,10:00,class,,NONE,,NONE,,,false,rose\n"
                + "b,수업,2026-11-16,11:00,12:00,class,,NONE,,NONE,,,false,\n", Files.readString(file));
        ScheduleRepository loaded = new ScheduleRepository(file);
        loaded.load();
        assertEquals("rose", loaded.findById("a").orElseThrow().getColorKey());
        assertNull(loaded.findById("b").orElseThrow().getColorKey());
    }

    @Test
    void 열이_없는_13칸_파일과_모르는_색은_카테고리_색으로_읽는다() throws IOException {
        Path file = dir.resolve("schedules.csv");
        Files.writeString(file, HEADER + "a,옛날,2026-11-16,09:00,10:00,class,,NONE,,NONE,,,false\n"
                + "b,이상,2026-11-16,09:00,10:00,class,,NONE,,NONE,,,false,neon\n");
        ScheduleRepository repo = new ScheduleRepository(file);
        repo.load();
        assertNull(repo.findById("a").orElseThrow().getColorKey());
        assertNull(repo.findById("b").orElseThrow().getColorKey());
        assertTrue(repo.getWarnings().isEmpty());
    }
}
