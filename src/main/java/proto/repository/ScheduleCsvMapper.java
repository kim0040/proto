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
 * 열: id,title,date,start,end,categoryId,memo,repeat,repeatEnd,reminder,completedDates,excludedDates
 * (날짜 모음은 ISO 날짜를 ';'로 이음)
 */
final class ScheduleCsvMapper {

    static final List<String> HEADER = List.of("id", "title", "date", "start", "end", "categoryId",
            "memo", "repeat", "repeatEnd", "reminder", "completedDates", "excludedDates");

    private ScheduleCsvMapper() {
    }

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

    /** 잘못된 행이면 RuntimeException(IllegalArgumentException, DateTimeException)을 던진다 */
    static Schedule fromRow(List<String> row) {
        if (row.size() < HEADER.size()) {
            throw new IllegalArgumentException("칸이 모자라요");
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
