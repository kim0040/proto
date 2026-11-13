package proto.server;

import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.entity.Schedule;
import proto.logic.ScheduleValidator;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * [서버 대비 — 현재 사용 안 함] 일정 목록과 JSON 스냅샷을 서로 바꾼다.
 * [저장소 repository] 루트는 {"schedules":[...]}이고, 한 건은 CSV와 같은 필드
 * (id, title, date, start, end, categoryId, memo, repeat, repeatEnd, reminder, completedDates, excludedDates)다.
 * 날짜·시간은 ISO 문자열, repeatEnd가 없으면 null, 날짜 모음은 배열이다.
 */
public final class ScheduleJsonMapper {

    private ScheduleJsonMapper() {
    }

    public static String writeSnapshot(List<Schedule> schedules) {
        List<Object> items = new ArrayList<>();
        for (Schedule schedule : schedules) {
            items.add(toMap(schedule));
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schedules", items);
        return JsonCodec.write(root);
    }

    public static List<Schedule> readSnapshot(String json) {
        Map<String, Object> root = JsonValues.asMap(JsonCodec.parse(json));
        List<Schedule> schedules = new ArrayList<>();
        for (Object item : JsonValues.asList(root.get("schedules"))) {
            addUnique(schedules, fromMap(JsonValues.asMap(item)));
        }
        return schedules;
    }

    private static void addUnique(List<Schedule> schedules, Schedule schedule) {
        boolean duplicate = schedules.stream().anyMatch(other -> other.getId().equals(schedule.getId()));
        if (duplicate) {
            throw new IllegalArgumentException("id가 겹쳐요");
        }
        schedules.add(schedule);
    }

    private static Map<String, Object> toMap(Schedule schedule) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", schedule.getId());
        map.put("title", schedule.getTitle());
        map.put("date", schedule.getDate().toString());
        map.put("start", schedule.getStartTime().toString());
        map.put("end", schedule.getEndTime().toString());
        map.put("categoryId", schedule.getCategoryId());
        map.put("memo", schedule.getMemo());
        map.put("repeat", schedule.getRepeatRule().name());
        map.put("repeatEnd", endText(schedule.getRepeatEndDate()));
        map.put("reminder", schedule.getReminder().name());
        map.put("completedDates", dateTexts(schedule.getCompletedDates()));
        map.put("excludedDates", dateTexts(schedule.getExcludedDates()));
        return map;
    }

    private static String endText(LocalDate repeatEnd) {
        return repeatEnd == null ? null : repeatEnd.toString();
    }

    private static List<String> dateTexts(Set<LocalDate> dates) {
        return dates.stream().map(LocalDate::toString).toList();
    }

    private static Schedule fromMap(Map<String, Object> map) {
        Schedule schedule = new Schedule(text(map, "id", false), text(map, "title", false),
                date(map, "date"), time(map, "start"), time(map, "end"), text(map, "categoryId", false),
                text(map, "memo", true), RepeatRule.valueOf(text(map, "repeat", false)),
                optionalDate(map, "repeatEnd"), ReminderOption.valueOf(text(map, "reminder", false)));
        schedule.replaceDateSets(dateSet(map, "completedDates"), dateSet(map, "excludedDates"));
        List<String> errors = ScheduleValidator.validate(schedule);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(", ", errors));
        }
        return schedule;
    }

    private static String text(Map<String, Object> map, String key, boolean allowBlank) {
        if (!map.containsKey(key) || map.get(key) == null) {
            throw new IllegalArgumentException(key + " 값이 없어요");
        }
        if (!(map.get(key) instanceof String value)) {
            throw new IllegalArgumentException(key + " 값이 문자열이 아니에요");
        }
        if (!allowBlank && value.isBlank()) {
            throw new IllegalArgumentException(key + " 값이 비어 있어요");
        }
        return value;
    }

    private static LocalDate date(Map<String, Object> map, String key) {
        return LocalDate.parse(text(map, key, false));
    }

    private static LocalTime time(Map<String, Object> map, String key) {
        return LocalTime.parse(text(map, key, false));
    }

    /** 키는 꼭 있어야 하고, JSON null이면 자바 null (반복 종료일이 없음) */
    private static LocalDate optionalDate(Map<String, Object> map, String key) {
        if (!map.containsKey(key)) {
            throw new IllegalArgumentException(key + " 값이 없어요");
        }
        if (map.get(key) == null) {
            return null;
        }
        if (!(map.get(key) instanceof String value)) {
            throw new IllegalArgumentException(key + " 값이 날짜가 아니에요");
        }
        return LocalDate.parse(value);
    }

    private static Set<LocalDate> dateSet(Map<String, Object> map, String key) {
        Set<LocalDate> dates = new TreeSet<>();
        for (Object item : JsonValues.asList(map.get(key))) {
            if (!(item instanceof String value)) {
                throw new IllegalArgumentException(key + " 값이 날짜가 아니에요");
            }
            dates.add(LocalDate.parse(value));
        }
        return dates;
    }
}
