package proto.control;

import proto.entity.Occurrence;
import proto.logic.ReminderChecker;

import javax.swing.Timer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * [제어 control] 30초마다 알림 시각이 된 일정을 찾아 리스너에 알리는 타이머 액터 역할 (F10, F15).
 * 앱을 켠 시각(생성 시각) 이전에 지난 알림은 보내지 않고, 같은 일정(같은 날짜)은 한 번만 알린다.
 * "다시 알림"(snooze)은 메모리에만 보관한다.
 */
public class ReminderTimer {

    private static final int INTERVAL_MILLIS = 30_000;
    private static final int MIN_SNOOZE_MINUTES = 1;
    private static final int MAX_SNOOZE_MINUTES = 60;

    /** 다시 알림 예약: 이 일정을 dueTime에 다시 알린다 */
    private record Snooze(Occurrence occurrence, LocalDateTime dueTime) {
    }

    private final ScheduleController controller;
    private final Clock clock;
    private final LocalDateTime appStartedAt;
    private final Timer timer = new Timer(INTERVAL_MILLIS, e -> checkNow());
    private final Set<String> notifiedKeys = new HashSet<>();
    private final Map<String, Snooze> snoozes = new HashMap<>();
    private final List<ReminderListener> listeners = new CopyOnWriteArrayList<>();

    public ReminderTimer(ScheduleController controller, Clock clock) {
        this.controller = controller;
        this.clock = clock;
        this.appStartedAt = LocalDateTime.now(clock);
    }

    public void addListener(ReminderListener listener) {
        listeners.add(listener);
    }

    /** 30초 간격 확인 시작 (javax.swing.Timer) */
    public void start() {
        timer.start();
    }

    public void stop() {
        timer.stop();
    }

    /** 지금 바로 한 번 확인 (타이머가 부르는 것과 같음. 테스트용으로도 씀) */
    public void checkNow() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        // 내일 일정도 본다: 00:10 일정의 30분 전 알림은 전날 23:40이다
        List<Occurrence> candidates = controller.getOccurrences(today, today.plusDays(1), null, null);
        for (Occurrence o : ReminderChecker.findDue(candidates, now, appStartedAt, notifiedKeys)) {
            notifiedKeys.add(o.key());
            fire(o, false);
        }
        fireDueSnoozes(now);
    }

    /** F15: minutes분 뒤에 이 일정을 한 번 더 알린다 (메모리에만 보관) */
    public OperationResult snooze(Occurrence occurrence, int minutes) {
        if (minutes < MIN_SNOOZE_MINUTES || minutes > MAX_SNOOZE_MINUTES) {
            return OperationResult.fail("다시 알림은 " + MIN_SNOOZE_MINUTES + "분에서 "
                    + MAX_SNOOZE_MINUTES + "분 사이로 정해 주세요");
        }
        LocalDateTime due = LocalDateTime.now(clock).plusMinutes(minutes);
        snoozes.put(occurrence.key(), new Snooze(occurrence, due));
        return OperationResult.ok();
    }

    /** 시각이 된 다시 알림을 한 번씩 알린다. 그 사이 완료했거나 지운 일정은 조용히 버린다 */
    private void fireDueSnoozes(LocalDateTime now) {
        List<Snooze> due = new ArrayList<>();
        for (Snooze s : snoozes.values()) {
            if (!s.dueTime().isAfter(now)) {
                due.add(s);
            }
        }
        for (Snooze s : due) {
            snoozes.remove(s.occurrence().key());
            Optional<Occurrence> latest = controller.findOccurrence(s.occurrence().scheduleId(),
                    s.occurrence().date());
            latest.filter(o -> !o.completed()).ifPresent(o -> fire(o, true));
        }
    }

    private void fire(Occurrence occurrence, boolean snoozed) {
        EventThread.run(() -> listeners.forEach(l -> l.reminderDue(occurrence, snoozed)));
    }
}
