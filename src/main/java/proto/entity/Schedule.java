package proto.entity;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * [엔티티 entity] 저장되는 일정 하나. 반복 일정도 "일정 하나 + 반복 규칙"으로 저장한다 (F9).
 *
 * <ul>
 *   <li>date: 일정 날짜. 반복 일정이면 반복이 시작되는 첫 날짜</li>
 *   <li>completedDates: 완료 처리한 날짜들 (F5). 반복 일정은 날짜별로 따로 완료된다.
 *       반복이 없는 일정은 date 하나만 들어갈 수 있다</li>
 *   <li>excludedDates: 반복 중 빠진 날짜들. "이 일정만" 수정/삭제(F3, F4)하면 그 날짜가 여기에 들어간다</li>
 * </ul>
 * 상태(예정/진행 중/지연/완료)는 필드로 두지 않는다. StateCalculator가 계산한다 (F6).
 */
public class Schedule {

    private final String id;
    private String title;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private String categoryId;
    private String memo;
    private RepeatRule repeatRule;
    private LocalDate repeatEndDate;
    private ReminderOption reminder;
    private final Set<LocalDate> completedDates = new TreeSet<>();
    private final Set<LocalDate> excludedDates = new TreeSet<>();

    public Schedule(String id, String title, LocalDate date, LocalTime startTime, LocalTime endTime,
                    String categoryId, String memo, RepeatRule repeatRule, LocalDate repeatEndDate,
                    ReminderOption reminder) {
        this.id = Objects.requireNonNull(id);
        this.title = title;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.categoryId = categoryId;
        this.memo = memo == null ? "" : memo;
        this.repeatRule = repeatRule == null ? RepeatRule.NONE : repeatRule;
        this.repeatEndDate = repeatEndDate;
        this.reminder = reminder == null ? ReminderOption.NONE : reminder;
    }

    /** 같은 값을 가진 복사본 (id도 같음). 수정 전 값을 보관하거나 테스트할 때 쓴다 */
    public Schedule copy() {
        return copyWithId(id);
    }

    /** 값은 같고 id만 다른 복사본. 반복 일정을 나눌 때(F3 "앞으로 전부") 쓴다 */
    public Schedule copyWithId(String newId) {
        Schedule s = new Schedule(newId, title, date, startTime, endTime, categoryId, memo,
                repeatRule, repeatEndDate, reminder);
        s.completedDates.addAll(completedDates);
        s.excludedDates.addAll(excludedDates);
        return s;
    }

    public boolean isRepeating() {
        return repeatRule != RepeatRule.NONE;
    }

    public boolean isCompletedOn(LocalDate day) {
        return completedDates.contains(day);
    }

    public void setCompletedOn(LocalDate day, boolean completed) {
        if (completed) {
            completedDates.add(day);
        } else {
            completedDates.remove(day);
        }
    }

    public boolean isExcludedOn(LocalDate day) {
        return excludedDates.contains(day);
    }

    public void excludeDate(LocalDate day) {
        excludedDates.add(day);
    }

    /** 완료 날짜 목록 (읽기 전용) */
    public Set<LocalDate> getCompletedDates() {
        return Collections.unmodifiableSet(completedDates);
    }

    /** 반복에서 빠진 날짜 목록 (읽기 전용) */
    public Set<LocalDate> getExcludedDates() {
        return Collections.unmodifiableSet(excludedDates);
    }

    /** CSV 불러오기 등에서 완료/제외 날짜를 통째로 바꿀 때 쓴다 */
    public void replaceDateSets(Set<LocalDate> completed, Set<LocalDate> excluded) {
        completedDates.clear();
        completedDates.addAll(completed);
        excludedDates.clear();
        excludedDates.addAll(excluded);
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo == null ? "" : memo; }
    public RepeatRule getRepeatRule() { return repeatRule; }
    public void setRepeatRule(RepeatRule rule) { this.repeatRule = rule == null ? RepeatRule.NONE : rule; }
    public LocalDate getRepeatEndDate() { return repeatEndDate; }
    public void setRepeatEndDate(LocalDate repeatEndDate) { this.repeatEndDate = repeatEndDate; }
    public ReminderOption getReminder() { return reminder; }
    public void setReminder(ReminderOption r) { this.reminder = r == null ? ReminderOption.NONE : r; }

    @Override
    public boolean equals(Object o) {
        return o instanceof Schedule s && s.id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Schedule[" + id + ", " + title + ", " + date + " " + startTime + "-" + endTime + "]";
    }
}
