package proto.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import proto.entity.Category;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.entity.Schedule;
import proto.repository.CategoryRepository;
import proto.repository.ScheduleRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 서버 응답이 깨졌을 때 이 컴퓨터의 CSV와 앱 시작이 그대로 지켜지는지 (S1, S2, S6) */
class SyncingStoreSafetyTest {

    @TempDir
    Path dir;

    @Test
    void malformedUtf8SnapshotKeepsLocalCsv() throws Exception {
        Schedule remote = schedule("remote-1", "REPLACE");
        byte[] body = ScheduleJsonMapper.writeSnapshot(List.of(remote)).getBytes(StandardCharsets.UTF_8);
        int at = new String(body, StandardCharsets.UTF_8).indexOf("REPLACE");
        body[at] = (byte) 0xFF;
        ScheduleRepository repo = savedSchedule("local-1", "원본");
        try (FakeApiServer api = FakeApiServer.start().get("/api/v1/schedules", body)) {
            SyncingScheduleStore store = new SyncingScheduleStore(repo, api.client(dir, "t", 2));
            store.load();
            assertEquals(List.of(SyncingScheduleStore.OFFLINE_WARNING), store.getWarnings());
            assertEquals("원본", store.findById("local-1").orElseThrow().getTitle());
            assertTrue(Files.readString(dir.resolve("schedules.csv"), StandardCharsets.UTF_8).contains("원본"));
        }
    }

    @Test
    void deeplyNestedSnapshotFallsBackToLocalInsteadOfCrashing() throws Exception {
        String deep = "{\"schedules\":[],\"ignored\":" + "[".repeat(20_000) + "]".repeat(20_000) + "}";
        ScheduleRepository repo = savedSchedule("local-1", "원본");
        try (FakeApiServer api = FakeApiServer.start().get("/api/v1/schedules", deep)) {
            SyncingScheduleStore store = new SyncingScheduleStore(repo, api.client(dir, "t", 2));
            store.load();
            assertEquals(List.of(SyncingScheduleStore.OFFLINE_WARNING), store.getWarnings());
            assertEquals("원본", store.findById("local-1").orElseThrow().getTitle());
        }
    }

    @Test
    void categorySnapshotNamingPersonalsNameKeepsLocalCategories() throws Exception {
        CategoryRepository repo = new CategoryRepository(dir.resolve("categories.csv"));
        repo.loadOrSeed();
        try (FakeApiServer api = FakeApiServer.start()
                .get("/api/v1/categories", "{\"categories\":[{\"id\":\"x\",\"name\":\"개인\"}]}")) {
            SyncingCategoryStore store = new SyncingCategoryStore(repo, api.client(dir, "t", 2));
            store.loadOrSeed();
            assertEquals(List.of(SyncingCategoryStore.NAME_CONFLICT_WARNING), store.getWarnings());
            assertEquals(List.of("수업", "과제", "팀플", "개인"), names(store.getAll()));
            assertTrue(store.findById("x").isEmpty());
        }
    }

    @Test
    void categorySnapshotWithPersonalNameInDifferentCaseIsAlsoRejected() throws Exception {
        CategoryRepository repo = new CategoryRepository(dir.resolve("categories.csv"));
        repo.loadOrSeed();
        try (FakeApiServer api = FakeApiServer.start()
                .get("/api/v1/categories", "{\"categories\":[{\"id\":\"x\",\"name\":\"개인\"},{\"id\":\"y\",\"name\":\"Study\"}]}")) {
            SyncingCategoryStore store = new SyncingCategoryStore(repo, api.client(dir, "t", 2));
            store.loadOrSeed();
            assertEquals(List.of(SyncingCategoryStore.NAME_CONFLICT_WARNING), store.getWarnings());
            assertEquals(4, store.getAll().size());
        }
    }

    @Test
    void categorySnapshotWithoutConflictAddsPersonalAndReplacesLocal() throws Exception {
        CategoryRepository repo = new CategoryRepository(dir.resolve("categories.csv"));
        repo.loadOrSeed();
        try (FakeApiServer api = FakeApiServer.start()
                .get("/api/v1/categories", "{\"categories\":[{\"id\":\"x\",\"name\":\"공부\"}]}")) {
            SyncingCategoryStore store = new SyncingCategoryStore(repo, api.client(dir, "t", 2));
            store.loadOrSeed();
            assertEquals(List.of(), store.getWarnings());
            assertEquals(List.of("공부", "개인"), names(store.getAll()));
        }
    }

    private ScheduleRepository savedSchedule(String id, String title) throws IOException {
        ScheduleRepository repo = new ScheduleRepository(dir.resolve("schedules.csv"));
        repo.add(schedule(id, title));
        repo.save();
        return repo;
    }

    private static Schedule schedule(String id, String title) {
        return new Schedule(id, title, LocalDate.of(2026, 11, 9), LocalTime.of(9, 30), LocalTime.of(10, 45),
                "team", "", RepeatRule.NONE, null, ReminderOption.NONE);
    }

    private static List<String> names(List<Category> categories) {
        return categories.stream().map(Category::getName).toList();
    }
}
