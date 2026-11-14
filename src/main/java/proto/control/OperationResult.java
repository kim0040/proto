package proto.control;

import java.util.List;

/**
 * [제어 control] 등록/수정/삭제 같은 요청의 결과. 실패하면 사용자에게 보여 줄 이유(한국어)가 들어 있다.
 * F1, F3, F4, F5, F7, F15에서 쓴다. controller의 요청 메서드(create, update, delete, rename 등)가 돌려주고,
 * 화면은 success인지 보고 실패면 errors를 사용자에게 보여 준다. 예외 대신 이 값을 쓰는 이유는 "입력이 틀렸다"는 흔한 일이라 흐름을 끊지 않고 문장으로 알리기 위해서이다.
 */
public record OperationResult(boolean success, List<String> errors) {

    public OperationResult {
        errors = List.copyOf(errors);
    }

    /** 성공 결과. errors는 빈 목록이다. */
    public static OperationResult ok() {
        return new OperationResult(true, List.of());
    }

    /**
     * 실패 결과.
     *
     * @param reasons 사용자에게 보여 줄 한국어 이유들
     */
    public static OperationResult fail(String... reasons) {
        return new OperationResult(false, List.of(reasons));
    }

    /**
     * 실패 결과.
     *
     * @param reasons 사용자에게 보여 줄 한국어 이유들
     */
    public static OperationResult fail(List<String> reasons) {
        return new OperationResult(false, reasons);
    }

    /** 이유들을 줄바꿈으로 이은 문장 */
    public String message() {
        return String.join("\n", errors);
    }
}
