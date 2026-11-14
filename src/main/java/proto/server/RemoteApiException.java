package proto.server;

/**
 * [서버 대비 — 현재 사용 안 함] 서버에 닿지 못했거나 서버가 오류로 답했을 때 던진다.
 * [경계 boundary] 검사 예외라서, 호출하는 쪽이 로컬 저장으로 물러날지 직접 정한다.
 * RemoteApiClient가 던지고, SyncingScheduleStore와 SyncingCategoryStore가 받아서 "서버에 연결하지 못했어요" 같은 경고로 바꾼다.
 * 서버가 안 돼도 앱은 이 컴퓨터의 CSV로 계속 쓸 수 있어야 하므로, 이 예외는 오류가 아니라 "경고로 물러나라"는 신호이다.
 */
public class RemoteApiException extends Exception {

    /** @param message 사용자에게 보여 줄 수 있는 한국어 문장 (토큰 같은 비밀은 넣지 않는다) */
    public RemoteApiException(String message) {
        super(message);
    }

    /**
     * @param message 사용자에게 보여 줄 수 있는 한국어 문장 (토큰 같은 비밀은 넣지 않는다)
     * @param cause   원인이 된 예외
     */
    public RemoteApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
