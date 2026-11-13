package proto.logic;

import proto.entity.Occurrence;
import proto.entity.ReminderOption;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * [논리 application logic] 지금 알려야 할 일정을 가려낸다 (F10, F15).
 * 알림 시각은 시작 시각 − N분이다. 앱을 켠 뒤 ~ 지금 사이에 지났고, 완료하지 않았고,
 * 아직 알리지 않은 일정만 알린다. 앱이 꺼져 있던 동안 지난 알림은 보내지 않는다.
 */
public final class ReminderChecker {

    private ReminderChecker() {
    }

    /**
     * @param candidates   알림 후보 하루치 일정들
     * @param appStartedAt 앱을 켠 시각
     * @param notifiedKeys 이미 알린 일정의 {@link Occurrence#key()} 들
     */
    public static List<Occurrence> findDue(Collection<Occurrence> candidates, LocalDateTime now,
                                           LocalDateTime appStartedAt, Set<String> notifiedKeys) {
        return candidates.stream()
                .filter(o -> isDue(o, now, appStartedAt, notifiedKeys))
                .toList();
    }

    private static boolean isDue(Occurrence o, LocalDateTime now, LocalDateTime appStartedAt,
                                 Set<String> notifiedKeys) {
        if (o.reminder() == ReminderOption.NONE || o.completed() || notifiedKeys.contains(o.key())) {
            return false;
        }
        LocalDateTime remindAt = o.startDateTime().minusMinutes(o.reminder().minutesBefore());
        return !remindAt.isBefore(appStartedAt) && !remindAt.isAfter(now);
    }
}
