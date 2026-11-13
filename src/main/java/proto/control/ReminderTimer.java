package proto.control;

import proto.entity.Occurrence;
import proto.entity.ReminderOption;
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
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * [제어 control] 30초마다 알림 시각이 된 일정을 찾아 리스너에 알리는 타이머 액터 역할 (F10, F15).
 * 앱을 켠 시각(생성 시각) 이전에 지난 알림은 보내지 않고, 같은 일정(같은 날짜)은 한 번만 알린다.
 * "다시 알림"(snooze)은 메모리에만 보관한다.
 *
 * <p>모든 public 메소드는 Swing 이벤트 스레드(EDT)에서 불러야 한다 (화면이 이미 EDT에서 부른다).</p>
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
    private boolean checking;

    public ReminderTimer(ScheduleController controller, Clock clock) {
        this.controller = controller;
        this.clock = clock;
        this.appStartedAt = LocalDateTime.now(clock);
    }

    public void addListener(ReminderListener listener) {
        listeners.add(listener);
    }

    /** 등록했던 리스너를 뺀다 (화면을 닫을 때) */
    public void removeListener(ReminderListener listener) {
        listeners.remove(listener);
    }

    /** 30초 간격 확인 시작 (javax.swing.Timer) */
    public void start() {
        timer.start();
    }

    public void stop() {
        timer.stop();
    }

    /**
     * 지금 바로 한 번 확인 (타이머가 부르는 것과 같음. 테스트용으로도 씀).
     * 이미 확인 중이면 바로 돌아간다. 리스너가 다시 불러도 같은 알림이 두 번 나가지 않게.
     */
    public void checkNow() {
        if (checking) {
            return;
        }
        checking = true;
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            remindDue(now);
            fireDueSnoozes(now);
        } finally {
            checking = false;
        }
    }

    /** 최신 알림 시각이 지금 맞은 일정만 키를 찍고 리스너를 부른다 */
    private void remindDue(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        // 내일 일정도 본다: 00:10 일정의 30분 전 알림은 전날 23:40이다
        List<Occurrence> due = ReminderChecker.findDue(candidates(today), now, appStartedAt, notifiedKeys);
        for (Occurrence snapshot : due) {
            fireFresh(snapshot, false, now);
        }
    }

    private List<Occurrence> candidates(LocalDate today) {
        return controller.getOccurrences(today, today.plusDays(1), null, null);
    }

    /**
     * 없거나 완료된 일정은 알리지 않는다.
     * 처음 알림은 최신 알림 시각이 앱을 켠 뒤부터 지금까지일 때만 보내고, 그때만 키를 찍는다.
     * 다시 알림은 예약 시각에 보내고, 완료·삭제만 조용히 버린다.
     */
    private void fireFresh(Occurrence snapshot, boolean snoozed, LocalDateTime now) {
        controller.findOccurrence(snapshot.scheduleId(), snapshot.date())
                .filter(o -> snoozed ? !o.completed() : stillDue(o, now))
                .ifPresent(o -> deliver(o, snoozed));
    }

    /** 최신 일정의 알림 시각이 앱을 켠 뒤부터 지금까지인지 */
    private boolean stillDue(Occurrence occurrence, LocalDateTime now) {
        if (occurrence.reminder() == ReminderOption.NONE || occurrence.completed()) {
            return false;
        }
        int minutes = occurrence.reminder().minutesBefore();
        LocalDateTime remindAt = occurrence.startDateTime().minusMinutes(minutes);
        return !remindAt.isBefore(appStartedAt) && !remindAt.isAfter(now);
    }

    /** 처음 알림은 키를 찍은 뒤에 보낸다. 다시 알림은 예약에서 이미 빠졌다 */
    private void deliver(Occurrence occurrence, boolean snoozed) {
        if (!snoozed) {
            notifiedKeys.add(occurrence.key());
        }
        fire(occurrence, snoozed);
    }

    /** F15: minutes분 뒤에 이 일정을 한 번 더 알린다 (메모리에만 보관). 없거나 완료됐으면 거절한다 */
    public OperationResult snooze(Occurrence occurrence, int minutes) {
        if (minutes < MIN_SNOOZE_MINUTES || minutes > MAX_SNOOZE_MINUTES) {
            return OperationResult.fail("다시 알림은 " + MIN_SNOOZE_MINUTES + "분에서 "
                    + MAX_SNOOZE_MINUTES + "분 사이로 정해 주세요");
        }
        if (alreadyGone(occurrence)) {
            return OperationResult.fail("이미 지워졌거나 완료한 일정이에요");
        }
        LocalDateTime due = LocalDateTime.now(clock).plusMinutes(minutes);
        snoozes.put(occurrence.key(), new Snooze(occurrence, due));
        return OperationResult.ok();
    }

    /** 지금 그 날짜의 일정이 없거나 이미 완료했으면 true */
    private boolean alreadyGone(Occurrence occurrence) {
        return controller.findOccurrence(occurrence.scheduleId(), occurrence.date())
                .filter(o -> !o.completed())
                .isEmpty();
    }

    /** 시각이 된 다시 알림을 예약에서 모두 뺀 뒤 한 번씩 알린다. 완료·삭제된 일정은 조용히 버린다 */
    private void fireDueSnoozes(LocalDateTime now) {
        List<Snooze> due = new ArrayList<>();
        for (Snooze s : snoozes.values()) {
            if (!s.dueTime().isAfter(now)) {
                due.add(s);
            }
        }
        due.forEach(s -> snoozes.remove(s.occurrence().key()));
        for (Snooze s : due) {
            fireFresh(s.occurrence(), true, now);
        }
    }

    private void fire(Occurrence occurrence, boolean snoozed) {
        EventThread.run(() -> listeners.forEach(l -> l.reminderDue(occurrence, snoozed)));
    }
}
