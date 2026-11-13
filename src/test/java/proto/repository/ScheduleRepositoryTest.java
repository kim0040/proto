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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static proto.TestData.d;
import static proto.TestData.t;

class ScheduleRepositoryTest {

    @TempDir
    Path dir;

    private static final String HEADER =
            "id,title,date,start,end,categoryId,memo,repeat,repeatEnd,reminder,completedDates,excludedDates\n";

    @Test
    void saveAndLoadRoundTripsEveryField() throws IOException {
        Path file = dir.resolve("data/schedules.csv");
        ScheduleRepository repo = new ScheduleRepository(file);
        Schedule s = new Schedule("id-1", "제목, \"쉼표\"와 따옴표", d("2026-11-09"), t("09:30"), t("10:45"),
                "team", "메모 1줄\n메모 2줄, 쉼표\r\n끝", RepeatRule.WEEKLY, d("2026-12-31"),
                ReminderOption.THIRTY_MINUTES);
        s.setCompletedOn(d("2026-11-09"), true);
        s.setCompletedOn(d("2026-11-16"), true);
        s.excludeDate(d("2026-11-23"));
        repo.add(s);
        repo.add(new Schedule("id-2", "단순", d("2026-11-10"), t("00:00"), t("23:59"), "class", "",
                RepeatRule.NONE, null, ReminderOption.NONE));
        repo.save();

        ScheduleRepository loaded = new ScheduleRepository(file);
        loaded.load();

        assertTrue(loaded.getWarnings().isEmpty());
        assertEquals(2, loaded.getAll().size());
        assertSameValues(s, loaded.findById("id-1").orElseThrow());
        assertEquals(LocalTime.of(23, 59), loaded.findById("id-2").orElseThrow().getEndTime());
        assertNull(loaded.findById("id-2").orElseThrow().getRepeatEndDate());
    }

    private static void assertSameValues(Schedule expected, Schedule back) {
        assertEquals(expected.getTitle(), back.getTitle());
        assertEquals(expected.getMemo(), back.getMemo());
        assertEquals(expected.getDate(), back.getDate());
        assertEquals(expected.getStartTime(), back.getStartTime());
        assertEquals(expected.getEndTime(), back.getEndTime());
        assertEquals(expected.getCategoryId(), back.getCategoryId());
        assertEquals(expected.getRepeatRule(), back.getRepeatRule());
        assertEquals(expected.getRepeatEndDate(), back.getRepeatEndDate());
        assertEquals(expected.getReminder(), back.getReminder());
        assertEquals(expected.getCompletedDates(), back.getCompletedDates());
        assertEquals(expected.getExcludedDates(), back.getExcludedDates());
    }

    @Test
    void corruptRowsAreSkippedWithAWarningAndLoadingContinues() throws IOException {
        Path file = dir.resolve("schedules.csv");
        Files.writeString(file, HEADER
                + "ok-1,첫째,2026-11-09,09:00,10:00,class,,NONE,,NONE,,\n"
                + "bad-date,둘째,not-a-date,09:00,10:00,class,,NONE,,NONE,,\n"
                + "too,short\n"
                + "bad-enum,셋째,2026-11-09,09:00,10:00,class,,SOMETIMES,,NONE,,\n"
                + "ok-2,넷째,2026-11-10,09:00,10:00,class,\"a,\nb\",DAILY,2026-11-20,FIVE_MINUTES,,2026-11-12\n");
        ScheduleRepository repo = new ScheduleRepository(file);
        repo.load();

        assertEquals(List.of("ok-1", "ok-2"), repo.getAll().stream().map(Schedule::getId).toList());
        assertEquals(3, repo.getWarnings().size());
        assertTrue(repo.getWarnings().get(0).contains("3번째 줄"));
        assertEquals("a,\nb", repo.findById("ok-2").orElseThrow().getMemo());
        assertTrue(repo.findById("ok-2").orElseThrow().isExcludedOn(d("2026-11-12")));
    }

    @Test
    void missingFileLoadsAsEmpty() {
        ScheduleRepository repo = new ScheduleRepository(dir.resolve("none.csv"));
        repo.load();
        assertTrue(repo.getAll().isEmpty());
        assertFalse(repo.fileExists());
    }

    @Test
    void firstRunCreatesFiveSamplesAndSavesThemImmediately() {
        Path file = dir.resolve("data/schedules.csv");
        ScheduleRepository repo = new ScheduleRepository(file);
        repo.loadOrSeed(d("2026-11-09"));

        assertTrue(Files.exists(file));
        assertEquals(5, repo.getAll().size());
        Schedule team = repo.getAll().stream().filter(s -> s.getTitle().equals("팀플 회의")).findFirst().orElseThrow();
        assertEquals(RepeatRule.WEEKLY, team.getRepeatRule());
        assertEquals("team", team.getCategoryId());
        assertEquals(ReminderOption.TEN_MINUTES, team.getReminder());
        assertEquals(1, repo.getAll().stream().filter(s -> s.isCompletedOn(d("2026-11-09"))).count());
        assertTrue(repo.getAll().stream().anyMatch(s -> s.getMemo().contains(",") && s.getMemo().contains("\n")));
        assertTrue(repo.getAll().stream().anyMatch(s -> s.getDate().equals(d("2026-11-10"))));

        ScheduleRepository again = new ScheduleRepository(file);
        again.loadOrSeed(d("2030-01-01")); // 파일이 있으니 예시를 다시 만들지 않고 읽어 옴
        assertEquals(5, again.getAll().size());
        assertTrue(again.getWarnings().isEmpty());
        assertTrue(again.findById(team.getId()).isPresent());
    }

    @Test
    void saveReplacesTheFileWithoutLeavingTempFiles() throws IOException {
        Path file = dir.resolve("schedules.csv");
        ScheduleRepository repo = new ScheduleRepository(file);
        repo.save();
        repo.save();
        try (var files = Files.list(dir)) {
            assertEquals(List.of("schedules.csv"), files.map(p -> p.getFileName().toString()).toList());
        }
    }
}
