package proto.repository;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * [저장소 repository] 파일을 불러오며 생긴 문제(읽기 실패, 구조 오류, 건너뛴 줄)를 모은다.
 * 문제가 하나라도 있으면 다음 저장이 원본을 덮어쓰기 전에 원본을 백업하고, 경고 문장에 백업 파일 이름을 붙인다.
 */
final class LoadProblems {

    private final List<String> messages = new ArrayList<>();

    void add(String message) {
        messages.add(message);
    }

    boolean isEmpty() {
        return messages.isEmpty();
    }

    /**
     * 원본을 백업하고 경고 문장들을 warnings에 넣는다. 문제가 없으면 아무것도 하지 않는다.
     *
     * @return 원본을 안전하게 보관했거나 보관할 필요가 없으면 true, 백업에 실패하면 false
     *         (false면 저장소는 이 파일을 덮어쓰면 안 된다)
     */
    boolean preserveOriginal(Path file, LocalDateTime now, List<String> warnings) {
        if (messages.isEmpty()) {
            return true;
        }
        try {
            String backupName = CsvFileStore.backup(file, now).getFileName().toString();
            messages.forEach(m -> warnings.add(m + ". 원본은 " + backupName + "에 보관했어요"));
            return true;
        } catch (IOException e) {
            messages.forEach(m -> warnings.add(m + ". 원본을 따로 보관하지 못해서 이 파일에는 저장하지 않아요 ("
                    + e.getMessage() + ")"));
            return false;
        }
    }

    /** 원본 보관에 실패한 파일을 저장하려 할 때 던질 예외 */
    static IOException saveBlocked(Path file) {
        return new IOException(file.getFileName() + " 원본을 보관하지 못해서 덮어쓰지 않았어요");
    }
}
