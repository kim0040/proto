package proto.repository;

/**
 * [저장소 repository] CSV 글자 구조가 깨졌을 때(닫히지 않은 따옴표, 칸 중간의 따옴표 등) 던지는 예외.
 * F11(자동 저장)의 불러오기에서 쓴다. CsvCodec#parse가 던지고, ScheduleRepository와 CategoryRepository가 받아서
 * "파일 전체를 읽지 못했어요" 경고로 바꾼다. 줄 번호를 같이 넣어서 사용자가 파일의 어디가 깨졌는지 찾을 수 있게 한다.
 * 몇 번째 줄(파일의 실제 줄, 1부터)에서 깨졌는지 알려 준다.
 */
public class CsvSyntaxException extends IllegalArgumentException {

    private final int lineNumber;

    /**
     * @param lineNumber 깨진 곳의 줄 번호 (1부터)
     * @param reason     깨진 이유 (한국어 문장)
     */
    public CsvSyntaxException(int lineNumber, String reason) {
        super(lineNumber + "번째 줄: " + reason);
        this.lineNumber = lineNumber;
    }

    /** 구조가 깨진 곳의 줄 번호 (1부터) */
    public int lineNumber() {
        return lineNumber;
    }
}
