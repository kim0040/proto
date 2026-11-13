package proto.control;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import proto.repository.CategoryRepository;
import proto.repository.ScheduleRepository;
import proto.server.FakeApiServer;
import proto.server.RemoteApiClient;
import proto.server.SyncingCategoryStore;
import proto.server.SyncingScheduleStore;
import proto.TestData;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 서버로 올리지 못한 경고가 저장 뒤에 storageFailed로 사용자에게 한 번씩 닿는지 (S4) */
class ServerWarningForwardingTest {

    private static final String SCHEDULES = "/api/v1/schedules";
    private static final String CATEGORIES = "/api/v1/categories";
    private static final String CLASS_ONLY = "{\"categories\":[{\"id\":\"class\",\"name\":\"수업\"}]}";

    @TempDir
    Path dir;

    @Test
    void pushFailureWarningsReachStorageFailedOnceEach() throws Exception {
        try (FakeApiServer api = FakeApiServer.start().get(SCHEDULES, "{\"schedules\":[]}")
                .get(CATEGORIES, CLASS_ONLY).putStatus(SCHEDULES, 500).putStatus(CATEGORIES, 500)) {
            Fixture f = new Fixture(api);
            assertTrue(f.recorder.storageErrors.isEmpty());

            assertTrue(f.schedules.create(ControllerFixture.draft("회의", "2026-11-10", "09:00", "10:00")).success());
            assertEquals(1, f.recorder.storageErrors.size());
            assertEquals(f.scheduleStore.getWarnings().get(0), f.recorder.storageErrors.get(0));

            f.schedules.create(ControllerFixture.draft("점심", "2026-11-10", "12:00", "13:00"));
            assertEquals(1, f.recorder.storageErrors.size());

            assertTrue(f.categories.add("스터디").success());
            assertEquals(2, f.recorder.storageErrors.size());
            assertEquals(f.categoryStore.getWarnings().get(0), f.recorder.storageErrors.get(1));
            f.categories.add("운동");
            assertEquals(2, f.recorder.storageErrors.size());
        }
    }

    @Test
    void localSaveStaysWhenPushFailsAndWarningComesBackAfterRecovery() throws Exception {
        try (FakeApiServer api = FakeApiServer.start().get(SCHEDULES, "{\"schedules\":[]}")
                .get(CATEGORIES, CLASS_ONLY).putStatus(SCHEDULES, 500)) {
            Fixture f = new Fixture(api);
            f.schedules.create(ControllerFixture.draft("회의", "2026-11-10", "09:00", "10:00"));
            assertEquals(1, f.recorder.storageErrors.size());
            assertEquals(1, f.scheduleStore.getAll().size());

            api.putStatus(SCHEDULES, 204);
            f.schedules.create(ControllerFixture.draft("점심", "2026-11-10", "12:00", "13:00"));
            assertEquals(1, f.recorder.storageErrors.size());
            assertTrue(f.scheduleStore.getWarnings().isEmpty());

            api.putStatus(SCHEDULES, 500);
            f.schedules.create(ControllerFixture.draft("저녁", "2026-11-10", "18:00", "19:00"));
            assertEquals(2, f.recorder.storageErrors.size());
            assertEquals(3, f.scheduleStore.getAll().size());
        }
    }

    @Test
    void startupWarningsAreNotRepeatedByLaterSaves() throws Exception {
        try (FakeApiServer api = FakeApiServer.start().putStatus(SCHEDULES, 204).putStatus(CATEGORIES, 204)) {
            Fixture f = new Fixture(api);
            f.schedules.reportLoadWarnings();
            int atStartup = f.recorder.storageErrors.size();
            assertEquals(2, atStartup);
            f.schedules.create(ControllerFixture.draft("회의", "2026-11-10", "09:00", "10:00"));
            f.categories.add("스터디");
            assertEquals(atStartup, f.recorder.storageErrors.size());
        }
    }

    /** 서버에 연결된 저장소와 controller. GET이 없으면 시작 때 OFFLINE 경고가 쌓인다 */
    private final class Fixture {
        final SyncingScheduleStore scheduleStore;
        final SyncingCategoryStore categoryStore;
        final ScheduleController schedules;
        final CategoryController categories;
        final ControllerFixture.Recorder recorder = new ControllerFixture.Recorder();

        Fixture(FakeApiServer api) throws Exception {
            RemoteApiClient client = api.client(dir, "t", 2);
            scheduleStore = new SyncingScheduleStore(new ScheduleRepository(dir.resolve("schedules.csv")), client);
            categoryStore = new SyncingCategoryStore(new CategoryRepository(dir.resolve("categories.csv")), client);
            categoryStore.loadOrSeed();
            scheduleStore.loadOrSeed(TestData.d("2026-11-09"));
            schedules = new ScheduleController(scheduleStore, categoryStore, new TestData.MutableClock("2026-11-09T10:00"));
            categories = new CategoryController(categoryStore, scheduleStore, schedules);
            schedules.addListener(recorder);
        }
    }
}
