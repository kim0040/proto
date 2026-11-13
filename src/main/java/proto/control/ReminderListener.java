package proto.control;

import proto.entity.Occurrence;

/**
 * [제어 control] 알림 시각이 된 일정을 화면에 알려 주는 리스너 (F10, F15). EDT에서 호출된다.
 */
public interface ReminderListener {

    /**
     * @param occurrence 알릴 하루치 일정
     * @param snoozed    사용자가 "5분 뒤 다시"를 눌러서 다시 울리는 알림이면 true (F15)
     */
    void reminderDue(Occurrence occurrence, boolean snoozed);
}
