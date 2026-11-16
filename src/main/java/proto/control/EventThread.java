package proto.control;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;

/**
 * [제어 control] 리스너 호출을 Swing 이벤트 스레드(EDT)에서 하도록 맞춰 주는 도우미.
 * ScheduleController, CategoryController, ReminderTimer가 리스너를 부를 때 거친다.
 * Swing 화면은 이벤트 스레드에서만 건드려야 안전하므로 그 규칙을 이 한 곳에서 지킨다.
 * 이미 EDT이거나 화면이 없는 환경(headless, 테스트)이면 바로 실행하고, 그 밖의 스레드에서는 {@code invokeLater}로 맡긴다.
 * 그래서 테스트는 기다리지 않고 알림을 확인할 수 있다.
 */
final class EventThread {

    private EventThread() {
    }

    /** action을 이벤트 스레드에서 실행한다 */
    static void run(Runnable action) {
        if (GraphicsEnvironment.isHeadless() || SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }
}
