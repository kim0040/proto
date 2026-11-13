package proto.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.entity.Schedule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void syntaxErrorLoadsNothingAndKeepsTheOriginalBytesInABackup() throws IOException {
        Path file = dir.resolve("schedules.csv");
        String original = HEADER
                + "before,first,2026-11-09,09:00,10:00,class,,NONE,,NONE,,\n"
                + "bad,broken,2026-11-09,09:00,10:00,class,,NONE,,NONE,,\"\n"
                + "after,last,2026-11-09,09:00,10:00,class,,NONE,,NONE,,\n";
        Files.writeString(file, original);
        ScheduleRepository repo = new ScheduleRepository(file, fixedClock());
        repo.load();

        assertTrue(repo.getAll().isEmpty());
        String warning = repo.getWarnings().get(0);
        assertTrue(warning.contains("일정 파일 3번째 줄"));
        assertTrue(warning.contains("원본은 schedules-backup-20261114-101500.csv에 보관했어요"));
        Path backup = dir.resolve("schedules-backup-20261114-101500.csv");
        assertEquals(original, Files.readString(backup));

        repo.add(new Schedule("new", "새 일정", d("2026-11-11"), t("09:00"), t("10:00"), "class", "",
                RepeatRule.NONE, null, ReminderOption.NONE));
        repo.save();
        assertEquals(original, Files.readString(backup));
        assertFalse(Files.readString(file).contains("after,last"));
    }

    @Test
    void invalidUtf8IsBackedUpBeforeALaterSaveReplacesTheFile() throws IOException {
        Path file = dir.resolve("schedules.csv");
        byte[] good = (HEADER + "old,important,2026-11-09,09:00,10:00,class,,NONE,,NONE,,\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] broken = new byte[good.length + 1];
        System.arraycopy(good, 0, broken, 0, good.length);
        broken[broken.length - 1] = (byte) 0xff;
        Files.write(file, broken);

        ScheduleRepository repo = new ScheduleRepository(file, fixedClock());
        repo.load();

        assertTrue(repo.getAll().isEmpty());
        assertTrue(repo.getWarnings().get(0).contains("schedules-backup-20261114-101500.csv"));
        assertArrayEquals(broken, Files.readAllBytes(dir.resolve("schedules-backup-20261114-101500.csv")));

        repo.add(new Schedule("new", "새 일정", d("2026-11-11"), t("09:00"), t("10:00"), "class", "",
                RepeatRule.NONE, null, ReminderOption.NONE));
        repo.save();
        assertArrayEquals(broken, Files.readAllBytes(dir.resolve("schedules-backup-20261114-101500.csv")));
        assertFalse(Files.readString(file).contains("important"));
    }

    @Test
    void rowsThatBreakScheduleRulesAreSkippedAndTheOriginalIsBackedUp() throws IOException {
        Path file = dir.resolve("schedules.csv");
        String original = HEADER
                + "good,정상,2026-11-09,09:00,10:00,class,,NONE,,NONE,,\n"
                + "bad,,2026-11-09,11:00,10:00,class,,NONE,,NONE,,\n"
                + "early,종료,2026-11-10,09:00,10:00,class,,DAILY,2026-11-01,NONE,,\n";
        Files.writeString(file, original);
        ScheduleRepository repo = new ScheduleRepository(file, fixedClock());
        repo.load();

        assertEquals(List.of("good"), repo.getAll().stream().map(Schedule::getId).toList());
        assertEquals(2, repo.getWarnings().size());
        assertTrue(repo.getWarnings().get(0).contains("일정 파일 3번째 줄을 읽지 못해 건너뛰었어요"));
        assertTrue(repo.getWarnings().get(0).contains("원본은 schedules-backup-20261114-101500.csv에 보관했어요"));
        assertEquals(original, Files.readString(dir.resolve("schedules-backup-20261114-101500.csv")));
    }

    @Test
    void failedBackupBlocksALaterSaveAndLeavesTheOriginalUntouched() throws IOException {
        Path folder = dir.resolve("locked");
        Files.createDirectories(folder);
        Path file = folder.resolve("schedules.csv");
        byte[] original = (HEADER + "old,important,2026-11-09,09:00,10:00,class,,NONE,,NONE,,\n\"\n")
                .getBytes(StandardCharsets.UTF_8);
        Files.write(file, original);
        Set<PosixFilePermission> saved = Files.getPosixFilePermissions(folder);
        ScheduleRepository repo = new ScheduleRepository(file, fixedClock());
        try {
            Files.setPosixFilePermissions(folder, EnumSet.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_EXECUTE));
            repo.load();
            assertTrue(repo.getAll().isEmpty());
            assertTrue(repo.getWarnings().get(0).contains("보관하지 못해서"));
            IOException blocked = assertThrows(IOException.class, repo::save);
            assertTrue(blocked.getMessage().contains("덮어쓰지 않았어요"));
            assertArrayEquals(original, Files.readAllBytes(file));
        } finally {
            Files.setPosixFilePermissions(folder, saved);
        }
    }

    @Test
    void backupAddsASuffixInsteadOfOverwriting() throws IOException {
        Path file = dir.resolve("schedules.csv");
        Files.writeString(file, "a");
        LocalDateTime now = LocalDateTime.of(2026, 11, 14, 10, 15);
        Path first = CsvFileStore.backup(file, now);
        Files.writeString(file, "b");
        Path second = CsvFileStore.backup(file, now);
        assertEquals("schedules-backup-20261114-101500.csv", first.getFileName().toString());
        assertEquals("schedules-backup-20261114-101500-1.csv", second.getFileName().toString());
        assertEquals("a", Files.readString(first));
        assertEquals("b", Files.readString(second));
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-11-14T10:15:00Z"), ZoneOffset.UTC);
    }
}
