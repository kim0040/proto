package proto.logic;

import proto.entity.Occurrence;
import proto.entity.TimeSlot;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * [논리 application logic] 하루 일정들 사이에서 비어 있는 시간 구간을 찾는다 (F12).
 * F12(빈 시간 찾기)에서 쓴다. ScheduleController가 그날의 하루치 일정과 함께 부르고, 결과(TimeSlot 목록)를 FreeSlotDialog가 보여 준다.
 * 찾는 구간 안에서 "일정이 하나도 없는 시간"만 돌려준다. 일정이 끝나는 시각부터 다음 일정이 시작하는 시각까지가 빈 시간이다.
 * 겹치거나 포개진 일정은 하나로 합쳐서 계산하고, 오늘이면 지금 시각을 5분 단위로 올림한 이후만 본다.
 */
public final class FreeSlotFinder {

    private static final int ROUND_MINUTES = 5;

    private FreeSlotFinder() {
    }

    /**
     * 일정이 없는 시간 구간을 시간 순서로 찾는다. 방법은 간단하다. 일정을 시작 시간 순으로 훑으면서
     * "지금까지 일정이 끝난 가장 늦은 시각(cursor)"을 기억하고, 다음 일정이 cursor 뒤에서 시작하면 그 사이가 빈 시간이다.
     * 일정이 서로 겹치거나 한 일정이 다른 일정 안에 들어 있어도 cursor는 뒤로 가지 않으므로 따로 합치지 않아도 된다.
     *
     * @param dayOccurrences date 하루의 모든 일정 (완료한 것 포함. 끝낸 일정도 그 시간에는 일을 했으니 빈 시간이 아니다)
     * @param date           찾을 날짜 (다른 날짜의 일정은 무시한다)
     * @param minutes        필요한 최소 길이(분). 이 길이와 같은 구간도 포함
     * @param from           찾는 구간의 시작 (예: 09:00)
     * @param to             찾는 구간의 끝 (예: 22:00)
     * @param now            지금 시각. date가 오늘이면 이미 지난 시간은 빼는 데 쓴다
     * @return 시간 순서의 빈 구간들. from이 to 이후이거나 오늘 남은 시간이 없으면 빈 목록
     */
    public static List<TimeSlot> find(List<Occurrence> dayOccurrences, LocalDate date, int minutes,
                                      LocalTime from, LocalTime to, LocalDateTime now) {
        LocalTime start = windowStart(date, from, now);
        if (start == null || !start.isBefore(to)) {
            return List.of();
        }
        List<Occurrence> busy = dayOccurrences.stream()
                .filter(o -> o.date().equals(date))
                .sorted(Comparator.comparing(Occurrence::startTime))
                .toList();
        List<TimeSlot> slots = new ArrayList<>();
        LocalTime cursor = start;
        for (Occurrence o : busy) {
            if (o.startTime().isAfter(cursor)) {
                addIfLongEnough(slots, date, cursor, earlier(o.startTime(), to), minutes);
            }
            cursor = later(cursor, o.endTime());
            if (!cursor.isBefore(to)) {
                return slots;
            }
        }
        addIfLongEnough(slots, date, cursor, to, minutes);
        return slots;
    }

    /**
     * 찾기 시작할 시각. 다른 날이면 from 그대로, 오늘이면 from과 "지금을 5분 단위로 올림한 시각" 중 늦은 쪽이다.
     * 이미 지난 시간을 추천하지 않기 위해서이고, 5분 단위로 올리는 것은 "14:03"보다 "14:05"가 일정 잡기 편해서이다.
     * 지금이 23:56 같을 때는 올림하면 자정을 넘어 0시가 되므로(지금보다 이르다) null을 돌려준다.
     */
    private static LocalTime windowStart(LocalDate date, LocalTime from, LocalDateTime now) {
        if (!date.equals(now.toLocalDate())) {
            return from;
        }
        LocalTime up = roundUp(now.toLocalTime());
        if (up.isBefore(now.toLocalTime())) {
            return null;
        }
        return later(from, up);
    }

    /** 초·나노초가 남아 있으면 다음 분으로 올리고, 분이 5의 배수가 되도록 위로 올린다. */
    private static LocalTime roundUp(LocalTime t) {
        LocalTime minute = t.truncatedTo(ChronoUnit.MINUTES);
        if (t.isAfter(minute)) {
            minute = minute.plusMinutes(1);
        }
        int rest = minute.getMinute() % ROUND_MINUTES;
        return rest == 0 ? minute : minute.plusMinutes(ROUND_MINUTES - rest);
    }

    private static void addIfLongEnough(List<TimeSlot> slots, LocalDate date,
                                        LocalTime start, LocalTime end, int minutes) {
        TimeSlot slot = new TimeSlot(date, start, end);
        if (slot.minutes() >= minutes) {
            slots.add(slot);
        }
    }

    private static LocalTime later(LocalTime a, LocalTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalTime earlier(LocalTime a, LocalTime b) {
        return a.isBefore(b) ? a : b;
    }
}
