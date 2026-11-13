package proto.control;

import proto.entity.Occurrence;

/**
 * [제어 control] 30초마다 알림 시각이 된 일정을 찾아 리스너에 알리는 타이머 액터 역할 (F10, F15).
 *
 * <p>뼈대(API 고정용). 본문은 구현 단계에서 채운다. 생성자도 구현 단계에서 정한다.</p>
 */
public class ReminderTimer {

    public void addListener(ReminderListener listener) {
        throw todo();
    }

    /** 30초 간격 확인 시작 (javax.swing.Timer) */
    public void start() {
        throw todo();
    }

    public void stop() {
        throw todo();
    }

    /** 지금 바로 한 번 확인 (타이머가 부르는 것과 같음. 테스트용으로도 씀) */
    public void checkNow() {
        throw todo();
    }

    /** F15: minutes분 뒤에 이 일정을 한 번 더 알린다 (메모리에만 보관) */
    public OperationResult snooze(Occurrence occurrence, int minutes) {
        throw todo();
    }

    private static UnsupportedOperationException todo() {
        return new UnsupportedOperationException("아직 구현되지 않음");
    }
}
