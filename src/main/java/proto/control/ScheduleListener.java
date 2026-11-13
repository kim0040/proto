package proto.control;

/**
 * [제어 control] 일정 쪽 변화를 화면(boundary)에 알려 주는 리스너. 화면이 구현한다.
 * 모든 알림은 Swing 이벤트 스레드(EDT)에서 호출된다.
 */
public interface ScheduleListener {

    /** 일정이 등록/수정/삭제/완료되었거나, 카테고리 삭제로 일정이 옮겨져 목록이 바뀜 */
    void schedulesChanged();

    /** 1분마다 다시 계산한 결과 상태(F6)가 바뀐 일정이 있음. 기본은 schedulesChanged()와 같음 */
    default void statesChanged() {
        schedulesChanged();
    }

    /** CSV 저장/불러오기가 실패함 (F11). message는 사용자에게 그대로 보여 줄 한국어 문장 */
    default void storageFailed(String message) {
    }
}
