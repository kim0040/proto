package proto.control;

import java.util.List;

/**
 * [제어 control] 등록/수정/삭제 같은 요청의 결과. 실패하면 사용자에게 보여 줄 이유(한국어)가 들어 있다.
 */
public record OperationResult(boolean success, List<String> errors) {

    public OperationResult {
        errors = List.copyOf(errors);
    }

    public static OperationResult ok() {
        return new OperationResult(true, List.of());
    }

    public static OperationResult fail(String... reasons) {
        return new OperationResult(false, List.of(reasons));
    }

    public static OperationResult fail(List<String> reasons) {
        return new OperationResult(false, reasons);
    }

    /** 이유들을 줄바꿈으로 이은 문장 */
    public String message() {
        return String.join("\n", errors);
    }
}
