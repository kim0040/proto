package proto.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import proto.repository.CategoryRepository;
import proto.repository.ScheduleRepository;
import proto.server.SyncingCategoryStore;
import proto.server.SyncingScheduleStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
}
