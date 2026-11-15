package proto.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.entity.Schedule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static proto.TestData.d;
import static proto.TestData.t;

class AllDayCsvTest {

    private static final String OLD_HEADER =
            "id,title,date,start,end,categoryId,memo,repeat,repeatEnd,reminder,completedDates,excludedDates\n";
    private static final String NEW_HEADER =
            "id,title,date,start,end,categoryId,memo,repeat,repeatEnd,reminder,completedDates,excludedDates,all_day\n";
    /** 저장하면 끝에 일정만의 색(color) 열까지 붙은 14칸으로 쓴다 */
    private static final String SAVED_HEADER =
            "id,title,date,start,end,categoryId,memo,repeat,repeatEnd,reminder,completedDates,excludedDates,all_day,color\n";

    @TempDir
    Path dir;

    @Test
    void allDayFlagRoundTripsAsTheLastColumn() throws IOException {
        Path file = dir.resolve("schedules.csv");
        ScheduleRepository repo = new ScheduleRepository(file);
        repo.load();
        repo.add(new Schedule("a", "학회", d("2026-11-16"), t("13:00"), t("14:00"), "class", "",
                RepeatRule.WEEKLY, null, ReminderOption.TEN_MINUTES, true));
        repo.add(new Schedule("b", "수업", d("2026-11-16"), t("09:00"), t("10:00"), "class", "",
                RepeatRule.NONE, null, ReminderOption.TEN_MINUTES));
        repo.save();

        assertEquals(SAVED_HEADER
                + "a,학회,2026-11-16,00:00,23:59,class,,WEEKLY,,NONE,,,true,\n"
                + "b,수업,2026-11-16,09:00,10:00,class,,NONE,,TEN_MINUTES,,,false,\n", Files.readString(file));
        ScheduleRepository loaded = new ScheduleRepository(file);
        loaded.load();
        assertTrue(loaded.findById("a").orElseThrow().isAllDay());
        assertFalse(loaded.findById("b").orElseThrow().isAllDay());
        assertTrue(loaded.getWarnings().isEmpty());
    }

    @Test
    void oldFileWithoutTheColumnLoadsAsTimedAndIsRewrittenWithIt() throws IOException {
        Path file = dir.resolve("schedules.csv");
        Files.writeString(file, OLD_HEADER + "old,수업,2026-11-09,09:00,10:00,class,,NONE,,NONE,,\n");
        ScheduleRepository repo = new ScheduleRepository(file);
        repo.load();
        Schedule old = repo.findById("old").orElseThrow();
        assertFalse(old.isAllDay());
        assertEquals(LocalTime.of(9, 0), old.getStartTime());
        assertTrue(repo.getWarnings().isEmpty());

        repo.save();
        assertEquals(SAVED_HEADER + "old,수업,2026-11-09,09:00,10:00,class,,NONE,,NONE,,,false,\n",
                Files.readString(file));
    }

    @Test
    void allDayRowIgnoresStoredTimesAndReminder() throws IOException {
        Path file = dir.resolve("schedules.csv");
        Files.writeString(file, NEW_HEADER + "a,학회,2026-11-16,15:00,09:00,class,,NONE,,THIRTY_MINUTES,,,true\n"
                + "b,빈칸,2026-11-16,09:00,10:00,class,,NONE,,NONE,,,\n");
        ScheduleRepository repo = new ScheduleRepository(file);
        repo.load();
        Schedule a = repo.findById("a").orElseThrow();
        assertEquals(LocalTime.MIDNIGHT, a.getStartTime());
        assertEquals(ReminderOption.NONE, a.getReminder());
        assertFalse(repo.findById("b").orElseThrow().isAllDay());
        assertTrue(repo.getWarnings().isEmpty());
    }

    @Test
    void unknownAllDayValueSkipsOnlyThatRowWithAWarning() throws IOException {
        Path file = dir.resolve("schedules.csv");
        Files.writeString(file, NEW_HEADER + "a,이상함,2026-11-16,09:00,10:00,class,,NONE,,NONE,,,yes\n"
                + "b,정상,2026-11-16,09:00,10:00,class,,NONE,,NONE,,,true\n"
                + "c,넘침,2026-11-16,09:00,10:00,class,,NONE,,NONE,,,false,blue,extra\n");
        ScheduleRepository repo = new ScheduleRepository(file);
        repo.load();
        assertEquals(List.of("b"), repo.getAll().stream().map(Schedule::getId).toList());
        assertEquals(2, repo.getWarnings().size());
        assertTrue(repo.getWarnings().get(0).contains("2번째 줄"));
        assertTrue(repo.getWarnings().get(1).contains("4번째 줄"));
    }
}
