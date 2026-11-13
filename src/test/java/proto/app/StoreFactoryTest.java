package proto.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import proto.control.ScheduleController;
import proto.control.ScheduleListener;
import proto.repository.CategoryRepository;
import proto.repository.ScheduleRepository;
import proto.server.SyncingCategoryStore;
import proto.server.SyncingScheduleStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class StoreFactoryTest {

    @TempDir
    Path dir;

    @Test
    void disabledByDefaultReturnsCsvStoresAndCreatesNoFile() throws IOException {
        StoreFactory.Stores stores = StoreFactory.create(dir);
        assertInstanceOf(ScheduleRepository.class, stores.schedules());
        assertInstanceOf(CategoryRepository.class, stores.categories());
        try (Stream<Path> files = Files.list(dir)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void enabledSettingsReturnSyncingStores() throws IOException {
        Files.writeString(dir.resolve("server.properties"), """
                enabled=true
                baseUrl=http://127.0.0.1:9
                token=t
                timeoutSeconds=2
                """, StandardCharsets.UTF_8);
        StoreFactory.Stores stores = StoreFactory.create(dir);
        assertInstanceOf(SyncingScheduleStore.class, stores.schedules());
        assertInstanceOf(SyncingCategoryStore.class, stores.categories());
    }

    @Test
    void settingsWarningReachesScheduleStoreWarnings() throws IOException {
        Files.writeString(dir.resolve("server.properties"), "enabled=true\nbaseUrl=http://[\ntoken=t\n",
                StandardCharsets.UTF_8);
        StoreFactory.Stores stores = StoreFactory.create(dir);
        stores.categories().loadOrSeed();
        stores.schedules().loadOrSeed(LocalDateTime.of(2026, 11, 9, 10, 0));
        assertEquals(List.of("server.properties의 baseUrl이 http 주소가 아니어서 서버를 쓰지 않아요"),
                stores.schedules().getWarnings());
        assertEquals(List.of(), stores.categories().getWarnings());
    }

    @Test
    void settingsWarningReachesStorageFailedAtStartup() throws IOException {
        Files.writeString(dir.resolve("server.properties"), "enabled=true\nbaseUrl=http://host:99999\n",
                StandardCharsets.UTF_8);
        StoreFactory.Stores stores = StoreFactory.create(dir);
        stores.categories().loadOrSeed();
        stores.schedules().loadOrSeed(LocalDateTime.of(2026, 11, 9, 10, 0));
        ScheduleController controller = new ScheduleController(stores.schedules(), stores.categories(),
                Clock.fixed(Instant.parse("2026-11-09T01:00:00Z"), ZoneOffset.UTC));
        List<String> failures = new ArrayList<>();
        controller.addListener(new ScheduleListener() {
            @Override
            public void schedulesChanged() {
            }

            @Override
            public void storageFailed(String message) {
                failures.add(message);
            }
        });
        controller.reportLoadWarnings();
        assertEquals(List.of("server.properties의 baseUrl이 http 주소가 아니어서 서버를 쓰지 않아요"), failures);
    }

    @Test
    void deliberatelyDisabledSettingsKeepPlainCsvStores() throws IOException {
        Files.writeString(dir.resolve("server.properties"), "enabled=false\nbaseUrl=http://[\n",
                StandardCharsets.UTF_8);
        StoreFactory.Stores stores = StoreFactory.create(dir);
        assertInstanceOf(ScheduleRepository.class, stores.schedules());
        assertInstanceOf(CategoryRepository.class, stores.categories());
    }
}
