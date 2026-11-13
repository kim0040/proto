package proto.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CsvFileStoreTest {

    @TempDir
    Path dir;

    /** 처음 두 번은 잠김(AccessDeniedException), 세 번째는 옮겨진다. 대기는 50ms + 100ms */
    @Test
    void retriesLockedMoveTwiceThenSucceeds() throws IOException {
        Path file = dir.resolve("schedules.csv");
        int[] calls = {0};
        CsvFileStore.writeAtomically(file, "가,나", (from, to) -> {
            calls[0]++;
            if (calls[0] <= 2) {
                throw new AccessDeniedException(to.toString());
            }
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        });
        assertEquals(3, calls[0]);
        assertEquals("가,나", Files.readString(file, StandardCharsets.UTF_8));
    }

    /** 처음 한 번과 재시도 5번(50·100·200·400·800ms)이 모두 FileSystemException이면 그 예외로 끝난다 */
    @Test
    void givesUpWhenEveryMoveIsDenied() {
        Path file = dir.resolve("schedules.csv");
        int[] calls = {0};
        IOException error = assertThrows(IOException.class, () ->
                CsvFileStore.writeAtomically(file, "abc", (from, to) -> {
                    calls[0]++;
                    throw new FileSystemException(to.toString());
                }));
        assertEquals(6, calls[0]);
        assertFalse(Files.exists(file));
        assertEquals(FileSystemException.class, error.getClass());
    }
}
