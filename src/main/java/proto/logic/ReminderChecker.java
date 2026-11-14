package proto.logic;

import proto.entity.Occurrence;
import proto.entity.ReminderOption;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * [논리 application logic] 지금 알려야 할 일정을 가려낸다 (F10, F15).
 * F10(알림), F15(다시 알리기)에서 쓴다. ReminderTimer가 30초마다 부르며, 반환된 일정만 ReminderListener로 화면에 알린다.
 * "이미 알렸는지"를 notifiedKeys로 건네받는다. 이 클래스는 아무것도 기억하지 않으므로 같은 입력이면 항상 같은 결과다.
 * 알림 시각은 시작 시각 − N분이다. 앱을 켠 뒤 ~ 지금 사이에 지났고, 완료하지 않았고,
 * 아직 알리지 않은 일정만 알린다. 앱이 꺼져 있던 동안 지난 알림은 보내지 않는다.
 */
public final class ReminderChecker {

    private ReminderChecker() {
    }

    /**
     * 지금 알려야 할 일정만 골라 돌려준다. 조건: 알림이 켜져 있고, 완료하지 않았고, 아직 알린 적이 없고,
     * 알림 시각(시작 − N분)이 "앱을 켠 시각 이상, 지금 이하"이다. 하나도 없으면 빈 목록이다.
     * 앱을 켜기 전에 지나간 알림은 일부러 보내지 않는다. 오래전 일정의 알림이 앱을 켜자마자 한꺼번에 뜨면 곤란하기 때문이다.
     *
     * @param candidates   알림 후보 하루치 일정들
     * @param now          지금 시각
     * @param appStartedAt 앱을 켠 시각
     * @param notifiedKeys 이미 알린 일정의 {@link Occurrence#key()} 들 (같은 알림이 30초마다 반복해서 뜨는 것을 막는다)
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
