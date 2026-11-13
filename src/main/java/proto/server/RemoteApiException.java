package proto.server;

/**
 * [서버 대비 — 현재 사용 안 함] 서버에 닿지 못했거나 서버가 오류로 답했을 때 던진다.
 * [경계 boundary] 검사 예외라서, 호출하는 쪽이 로컬 저장으로 물러날지 직접 정한다.
 */
public class RemoteApiException extends Exception {

    public RemoteApiException(String message) {
        super(message);
    }

    public RemoteApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
