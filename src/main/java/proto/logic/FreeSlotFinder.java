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
 * 겹치거나 포개진 일정은 하나로 합쳐서 계산하고, 오늘이면 지금 시각을 5분 단위로 올림한 이후만 본다.
 */
public final class FreeSlotFinder {

    private static final int ROUND_MINUTES = 5;

    private FreeSlotFinder() {
    }

    /**
     * @param dayOccurrences date 하루의 모든 일정 (완료한 것 포함)
     * @param minutes        필요한 최소 길이(분). 이 길이와 같은 구간도 포함
     * @param from           찾는 구간의 시작 (예: 09:00)
     * @param to             찾는 구간의 끝 (예: 22:00)
     * @return 시간 순서의 빈 구간들. from이 to 이후이면 빈 목록
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

    /** 오늘이면 max(from, 지금을 5분 단위로 올림). 올림하면 자정을 넘으면 null */
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
