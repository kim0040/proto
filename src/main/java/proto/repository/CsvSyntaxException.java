package proto.repository;

/**
 * [저장소 repository] CSV 글자 구조가 깨졌을 때(닫히지 않은 따옴표, 칸 중간의 따옴표 등) 던지는 예외.
 * 몇 번째 줄(파일의 실제 줄, 1부터)에서 깨졌는지 알려 준다.
 */
public class CsvSyntaxException extends IllegalArgumentException {

    private final int lineNumber;

    public CsvSyntaxException(int lineNumber, String reason) {
        super(lineNumber + "번째 줄: " + reason);
        this.lineNumber = lineNumber;
    }

    /** 구조가 깨진 곳의 줄 번호 (1부터) */
    public int lineNumber() {
        return lineNumber;
    }
}
