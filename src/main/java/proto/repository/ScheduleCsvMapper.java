package proto.repository;

import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.entity.Schedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * [저장소 repository] Schedule 하나와 CSV 한 행(12칸)을 서로 바꾼다.
 * F11(자동 저장)에서 쓴다. ScheduleRepository가 저장할 때 toRow, 불러올 때 fromRow를 부른다.
 * CSV 형식(열 순서와 값의 모양)을 정하는 곳이 여기 하나뿐이라, 열을 바꾸려면 이 클래스만 고치면 된다. enum은 이름으로, 날짜는 ISO 형식(2026-11-14)으로 쓴다.
 * 열: id,title,date,start,end,categoryId,memo,repeat,repeatEnd,reminder,completedDates,excludedDates
 * (날짜 모음은 ISO 날짜를 ';'로 이음)
 */
final class ScheduleCsvMapper {

    static final List<String> HEADER = List.of("id", "title", "date", "start", "end", "categoryId",
            "memo", "repeat", "repeatEnd", "reminder", "completedDates", "excludedDates");

    private ScheduleCsvMapper() {
    }

    /** Schedule 한 개를 CSV 한 행(12칸)으로 바꾼다. 반복 종료일이 없으면 그 칸은 빈 글이다. 문제 있는 값을 미리 걸러 주지는 않는다. */
    static List<String> toRow(Schedule s) {
        List<String> row = new ArrayList<>();
        row.add(s.getId());
        row.add(s.getTitle());
        row.add(s.getDate().toString());
        row.add(s.getStartTime().toString());
        row.add(s.getEndTime().toString());
        row.add(s.getCategoryId());
        row.add(s.getMemo());
        row.add(s.getRepeatRule().name());
        row.add(s.getRepeatEndDate() == null ? "" : s.getRepeatEndDate().toString());
        row.add(s.getReminder().name());
        row.add(joinDates(s.getCompletedDates()));
        row.add(joinDates(s.getExcludedDates()));
        return row;
    }

    /**
     * 잘못된 행이면 RuntimeException(IllegalArgumentException, DateTimeException)을 던진다.
     * 칸 수가 12가 아니거나 id가 비었으면 IllegalArgumentException이다. 날짜·시간 모양이 틀리거나 반복/알림 이름을 모르면
     * DateTimeParseException, IllegalArgumentException이 난다. 호출한 ScheduleRepository가 이 예외를 잡아 그 줄만 건너뛴다.
     */
    static Schedule fromRow(List<String> row) {
        if (row.size() != HEADER.size()) {
            throw new IllegalArgumentException("칸 수가 " + HEADER.size() + "개가 아니에요");
        }
        if (row.get(0).isBlank()) {
            throw new IllegalArgumentException("id가 비어 있어요");
        }
        LocalDate end = row.get(8).isBlank() ? null : LocalDate.parse(row.get(8));
        Schedule s = new Schedule(row.get(0), row.get(1), LocalDate.parse(row.get(2)),
                LocalTime.parse(row.get(3)), LocalTime.parse(row.get(4)), row.get(5), row.get(6),
                RepeatRule.valueOf(row.get(7)), end, ReminderOption.valueOf(row.get(9)));
        s.replaceDateSets(splitDates(row.get(10)), splitDates(row.get(11)));
        return s;
    }

    private static String joinDates(Set<LocalDate> dates) {
        return String.join(";", dates.stream().map(LocalDate::toString).toList());
    }

    private static Set<LocalDate> splitDates(String text) {
        Set<LocalDate> dates = new TreeSet<>();
        for (String part : text.split(";")) {
            if (!part.isBlank()) {
                dates.add(LocalDate.parse(part.strip()));
            }
        }
        return dates;
    }
}
