package proto.control;

import proto.entity.Occurrence;
import proto.entity.ScheduleState;

import javax.swing.Timer;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * [제어 control] 1분마다 오늘 전후 일정의 상태(F6)를 다시 계산해, 바뀐 것이 있을 때만 화면에 알리는 타이머.
 * 어제부터 7일 뒤까지의 하루치 일정을 지난번 계산과 비교한다.
 *
 * <p>모든 public 메소드는 Swing 이벤트 스레드(EDT)에서 불러야 한다 (화면이 이미 EDT에서 부른다).</p>
 */
public class StateRefreshTimer {

    private static final int INTERVAL_MILLIS = 60_000;
    private static final int DAYS_BEFORE = 1;
    private static final int DAYS_AFTER = 7;

    private final ScheduleController controller;
    private final Timer timer = new Timer(INTERVAL_MILLIS, e -> tick());
    private Map<String, ScheduleState> previous;

    public StateRefreshTimer(ScheduleController controller) {
        this.controller = controller;
        this.previous = snapshot();
        controller.addListener(new Baseline());
    }

    public void start() {
        timer.start();
    }

    public void stop() {
        timer.stop();
    }

    /** 상태를 다시 계산해, 지난번과 달라진 일정이 있으면 statesChanged()를 알린다 (테스트에서 직접 부를 수 있음) */
    public void tick() {
        Map<String, ScheduleState> current = snapshot();
        boolean changed = !current.equals(previous);
        previous = current;
        if (changed) {
            controller.notifyStatesChanged();
        }
    }

    /**
     * 일정이 바뀌면 기준을 지금 상태로 다시 잡는다.
     * 상태 알림은 비워 둔다. 기본 구현은 schedulesChanged()를 부르므로, 그대로 두면
     * 이 타이머가 방금 보낸 알림이 기준을 덮어 다음 변화가 사라진다.
     */
    private final class Baseline implements ScheduleListener {
        @Override
        public void schedulesChanged() {
            previous = snapshot();
        }

        @Override
        public void statesChanged() {
        }
    }

    /** 일정 키(일정 id@날짜) → 지금 상태 */
    private Map<String, ScheduleState> snapshot() {
        LocalDate today = controller.today();
        Map<String, ScheduleState> states = new HashMap<>();
        for (Occurrence o : controller.getOccurrences(today.minusDays(DAYS_BEFORE),
                today.plusDays(DAYS_AFTER), null, null)) {
            states.put(o.key(), controller.stateOf(o));
        }
        return states;
    }
}
