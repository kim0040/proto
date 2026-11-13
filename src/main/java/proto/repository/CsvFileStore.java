package proto.repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
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
    /** 잠겨 있을 때 추가로 옮기는 횟수. 처음 한 번까지 합치면 최대 6번이다 */
    private static final int MAX_MOVE_RETRIES = 5;
    /** 다시 옮기기 전 대기(ms). 두 배씩 늘린다 */
    private static final long[] MOVE_BACKOFF_MS = {50, 100, 200, 400, 800};

    private CsvFileStore() {
    }

    public static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /** 같은 폴더의 임시 파일에 먼저 쓴 뒤 옮겨서 교체한다. 폴더가 없으면 만든다 */
    public static void writeAtomically(Path file, String content) throws IOException {
        writeAtomically(file, content, CsvFileStore::moveReplacing);
    }

    /**
     * {@link #writeAtomically(Path, String)}와 같고, 파일을 옮기는 동작만 바꿔 끼운다.
     * 테스트가 잠긴 파일(AccessDeniedException, FileSystemException)을 흉내 낼 때 쓴다.
     */
    static void writeAtomically(Path file, String content, FileMover mover) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path temp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            moveWithRetry(temp, file, mover);
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

    /** 같은 폴더 안에서는 원자적으로 옮기고, 파일 시스템이 지원하지 않으면 보통 옮기기로 바꾼다 */
    private static void moveReplacing(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 엑셀, OneDrive, 백신이 대상 파일을 잡고 있으면 AccessDeniedException(FileSystemException)이 난다.
     * 처음 한 번에 더해 최대 5번 더 옮기고, 다시 시도하기 전에 50·100·200·400·800ms를 기다린다.
     * 그래도 안 되면 그 예외를 그대로 던진다.
     */
    private static void moveWithRetry(Path from, Path to, FileMover mover) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt <= MAX_MOVE_RETRIES; attempt++) {
            if (attempt > 0) {
                pause(MOVE_BACKOFF_MS[attempt - 1]);
            }
            try {
                mover.move(from, to);
                return;
            } catch (FileSystemException e) {
                last = e;
            }
        }
        throw last == null ? new IOException("파일을 옮기지 못했어요") : last;
    }

    private static void pause(long millis) throws IOException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("파일을 옮기다가 중단됐어요", e);
        }
    }

    /** 임시 파일을 대상 자리로 옮긴다. 파일이 잠겨 있으면 FileSystemException을 던진다 */
    @FunctionalInterface
    interface FileMover {
        void move(Path from, Path to) throws IOException;
    }
}
