package proto.repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * [저장소 repository] CSV 텍스트 파일을 UTF-8로 읽고, 중간에 끊겨도 기존 파일이 깨지지 않게 통째로 바꿔 쓴다.
 * 읽다가 문제가 생긴 파일은 덮어쓰기 전에 같은 폴더에 원본 그대로 보관(백업)한다.
 */
public final class CsvFileStore {

    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int MAX_BACKUP_TRIES = 1000;

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

    /**
     * file을 같은 폴더의 "이름-backup-yyyyMMdd-HHmmss.csv"로 바이트 그대로 복사한다.
     * 같은 이름의 백업이 이미 있으면 덮어쓰지 않고 뒤에 -1, -2 ...를 붙인다.
     *
     * @return 만든 백업 파일 경로
     */
    public static Path backup(Path file, LocalDateTime now) throws IOException {
        String name = file.getFileName().toString();
        String base = name.endsWith(".csv") ? name.substring(0, name.length() - 4) : name;
        String stamp = base + "-backup-" + now.format(BACKUP_STAMP);
        for (int n = 0; n < MAX_BACKUP_TRIES; n++) {
            Path target = backupPath(file, stamp, n);
            if (Files.exists(target)) {
                continue;
            }
            try {
                return Files.copy(file, target);
            } catch (FileAlreadyExistsException e) {
                // 확인 뒤에 같은 이름이 생겼다: 다음 번호로
            }
        }
        throw new IOException("백업 파일 이름을 정하지 못했어요");
    }

    private static Path backupPath(Path file, String stamp, int n) {
        return file.resolveSibling(stamp + (n == 0 ? "" : "-" + n) + ".csv");
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
