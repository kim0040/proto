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
 * ScheduleRepository와 CategoryRepository가 저장할 때 writeAtomically, 불러올 때 read, 문제가 있을 때 backup을 부른다 (F11).
 * 파일 입출력을 이 클래스에 모아 두어 "저장 도중에 꺼져도 파일이 반쯤만 써지지 않는다"는 약속을 한 곳에서 지킨다.
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

    /** 파일 전체를 UTF-8 글자로 읽는다. 파일이 없거나 읽을 수 없으면 IOException이다 */
    public static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /**
     * 같은 폴더의 임시 파일에 먼저 다 쓴 뒤 그 파일을 원래 이름으로 옮겨서 교체한다. 폴더가 없으면 만든다.
     * 원래 파일을 바로 열어 쓰다가 전원이 꺼지면 파일이 반쯤만 남아 일정을 잃는다. 임시 파일에 다 쓴 뒤 옮기면
     * 파일은 "옛 내용 전체" 아니면 "새 내용 전체"이다. 옮기기가 끝내 실패하면 IOException이고, 임시 파일은 항상 지운다.
     */
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
     * 불러올 때 문제가 있었던 파일을 덮어쓰기 전에 원본 그대로 보관한다. 이름에 시각을 넣어 여러 번 백업해도 서로 덮어쓰지 않는다.
     *
     * @throws IOException 백업 이름을 1000번 시도해도 정하지 못했거나 복사에 실패했을 때
     */
    public static Path backup(Path file, LocalDateTime now) throws IOException {
        String name = file.getFileName().toString();
        String base = name.endsWith(".csv") ? name.substring(0, name.length() - 4) : name;
        String stamp = base + "-backup-" + now.format(BACKUP_STAMP);
        for (int number = 0; number < MAX_BACKUP_TRIES; number++) {
            Path target = backupPath(file, stamp, number);
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

    private static Path backupPath(Path file, String stamp, int number) {
        return file.resolveSibling(stamp + (number == 0 ? "" : "-" + number) + ".csv");
    }

    /** ATOMIC_MOVE는 반쯤 옮겨진 상태가 보이지 않게 해 준다. 일부 파일 시스템은 못 하므로 그때만 보통 옮기기로 바꾼다 */
    private static void moveReplacing(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** 재시도하는 이유: 윈도우에서는 다른 프로그램이 파일을 열고 있으면 교체가 잠시 막힐 수 있고, 기다리면 풀리는 경우가 많다 */
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
