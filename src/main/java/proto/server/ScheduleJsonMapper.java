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
 * [저장소 repository] 일정 목록과 JSON 스냅샷을 서로 바꾼다. 서버 대비 코드(현재 사용 안 함).
 * 루트는 {"schedules":[...]}이고, 한 건은 CSV와 같은 14개 필드
 * (id, title, date, start, end, categoryId, memo, repeat, repeatEnd, reminder, completedDates, excludedDates, allDay, color)다.
 * 날짜·시간은 ISO 문자열, repeatEnd가 없으면 null, 날짜 모음은 배열이다. CSV 쪽 변환은 ScheduleCsvMapper가 맡는다.
 * SyncingScheduleStore가 서버와 주고받을 때만 쓰며, 서버에서 받은 값도 ScheduleValidator를 통과해야 로컬로 들어온다.
 * allDay와 color는 나중에 더한 선택 필드이고 보낼 때는 언제나 넣는다 (따로 고른 색이 없으면 color는 null).
 * allDay는 없거나 null이면 false로 읽고 참·거짓이 아니면 거절한다.
 * color는 없거나 null이면 "카테고리 색"이고 문자열이 아니면 거절하며, 모르는 키는 "카테고리 색"으로 읽는다.
 */
public final class ScheduleJsonMapper {

    private ScheduleJsonMapper() {
    }

    /** 일정 목록 전체를 JSON 문자열 하나로 만든다. 일정이 없으면 {"schedules":[]}이다 */
    public static String writeSnapshot(List<Schedule> schedules) {
        List<Object> items = new ArrayList<>();
        for (Schedule schedule : schedules) {
            items.add(toMap(schedule));
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schedules", items);
        return JsonCodec.write(root);
    }

    /**
     * JSON 문자열을 일정 목록으로 읽는다. 형식이 깨졌거나, 필드가 없거나 모양이 틀렸거나, F1 규칙을 어기거나, id가 겹치면 예외를 던진다.
     * 한 건이라도 이상하면 전체를 거절한다 (일부만 받으면 서버와 로컬이 어긋나기 때문).
     *
     * @throws JsonSyntaxException JSON 글자 구조가 깨졌을 때
     * @throws IllegalArgumentException 값이 규칙에 맞지 않을 때 (날짜 모양 오류 등 DateTimeException도 있을 수 있다)
     */
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
        map.put("allDay", schedule.isAllDay());
        map.put("color", schedule.getColorKey());
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
                optionalDate(map, "repeatEnd"), ReminderOption.valueOf(text(map, "reminder", false)),
                optionalFlag(map, "allDay"));
        schedule.replaceDateSets(dateSet(map, "completedDates"), dateSet(map, "excludedDates"));
        schedule.setColorKey(optionalText(map, "color"));
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

    /** 없어도 되는 문자열 필드: 없거나 null이면 null, 문자열이 아니면 거절 */
    private static String optionalText(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(key + " 값이 문자열이 아니에요");
        }
        return text;
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

    /** 없거나 JSON null이면 false, 참·거짓이면 그 값, 그 밖의 모양이면 예외 */
    private static boolean optionalFlag(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return false;
        }
        if (!(value instanceof Boolean flag)) {
            throw new IllegalArgumentException(key + " 값이 true나 false가 아니에요");
        }
        return flag;
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
