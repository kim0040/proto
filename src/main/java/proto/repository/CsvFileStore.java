package proto.repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * [저장소 repository] CSV 텍스트 파일을 UTF-8로 읽고, 중간에 끊겨도 기존 파일이 깨지지 않게 통째로 바꿔 쓴다.
 */
public final class CsvFileStore {

    private CsvFileStore() {
    }

    public static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /** 같은 폴더의 임시 파일에 먼저 쓴 뒤 옮겨서 교체한다. 폴더가 없으면 만든다 */
    public static void writeAtomically(Path file, String content) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path temp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            move(temp, file);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
