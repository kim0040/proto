package proto.entity;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * [엔티티 entity] 저장되는 일정 하나. 반복 일정도 "일정 하나 + 반복 규칙"으로 저장한다 (F9).
 * 저장소(ScheduleStore)가 목록으로 들고 있고, 읽기 전용 "하루치"는 Occurrence.of로 만든다.
 * 고치는 곳은 ScheduleController(와 SeriesEditor)뿐이다. 같은 id면 같은 일정으로 본다(equals).
 *
 * <ul>
 *   <li>date: 일정 날짜. 반복 일정이면 반복이 시작되는 첫 날짜</li>
 *   <li>completedDates: 완료 처리한 날짜들 (F5). 반복 일정은 날짜별로 따로 완료된다.
 *       반복이 없는 일정은 date 하나만 들어갈 수 있다</li>
 *   <li>excludedDates: 반복 중 빠진 날짜들. "이 일정만" 수정/삭제(F3, F4)하면 그 날짜가 여기에 들어간다</li>
 * </ul>
 * 상태(예정/진행 중/지연/완료)는 필드로 두지 않는다. StateCalculator가 계산한다 (F6).
 *
 * <p>하루 종일(allDay) 일정은 시간이 없는 일정이다. 그래도 "끝이 시작보다 늦다" 같은 기존 규칙이 그대로 통하도록
 * 시작·끝은 언제나 {@link #ALL_DAY_START}~{@link #ALL_DAY_END}(00:00~23:59)로, 알림은 언제나 NONE으로 읽힌다
 * (getStartTime, getEndTime, getReminder가 그렇게 돌려주므로 CSV·서버에도 그 값이 저장된다).</p>
 */
public class Schedule {

    /** 하루 종일 일정의 시작 시간 (00:00) */
    public static final LocalTime ALL_DAY_START = LocalTime.MIDNIGHT;
    /** 하루 종일 일정의 끝 시간 (23:59). 상태 계산은 Occurrence#endDateTime이 다음 날 0시로 본다 */
    public static final LocalTime ALL_DAY_END = LocalTime.of(23, 59);

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
    private boolean allDay;
    /** 이 일정만의 색 키 (CategoryPalette.KEYS 중 하나). null이면 카테고리 색을 따른다 */
    private String colorKey;
    private final Set<LocalDate> completedDates = new TreeSet<>();
    private final Set<LocalDate> excludedDates = new TreeSet<>();

    /**
     * 일정을 만든다. id는 null이면 NullPointerException이다. 메모·반복 규칙·알림이 null이면 각각 "빈 글", NONE으로 바꿔 넣는다.
     * 나머지 값이 올바른지는 여기서 보지 않는다 (ScheduleValidator가 저장 전에 검사한다).
     */
    public Schedule(String id, String title, LocalDate date, LocalTime startTime, LocalTime endTime,
                    String categoryId, String memo, RepeatRule repeatRule, LocalDate repeatEndDate,
                    ReminderOption reminder) {
        this(id, title, date, startTime, endTime, categoryId, memo, repeatRule, repeatEndDate, reminder, false);
    }

    /**
     * 하루 종일 여부까지 받아 일정을 만든다. allDay가 true면 시작·끝·알림 값은 무시되고 00:00~23:59, NONE으로 읽힌다.
     * 나머지 규칙은 위 생성자와 같다.
     */
    public Schedule(String id, String title, LocalDate date, LocalTime startTime, LocalTime endTime,
                    String categoryId, String memo, RepeatRule repeatRule, LocalDate repeatEndDate,
                    ReminderOption reminder, boolean allDay) {
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
        setAllDay(allDay);
    }

    /** 같은 값을 가진 복사본 (id도 같음). 수정 전 값을 보관하거나 테스트할 때 쓴다. 날짜 목록도 따로 복사한다 */
    public Schedule copy() {
        return copyWithId(id);
    }

    /** 값은 같고 id만 다른 복사본. 반복 일정을 나눌 때(F3 "앞으로 전부") 쓴다. 날짜 목록은 따로 복사하므로 서로 영향이 없다 */
    public Schedule copyWithId(String newId) {
        Schedule copy = new Schedule(newId, title, date, startTime, endTime, categoryId, memo,
                repeatRule, repeatEndDate, reminder, allDay);
        copy.colorKey = colorKey;
        copy.completedDates.addAll(completedDates);
        copy.excludedDates.addAll(excludedDates);
        return copy;
    }

    /** 반복 규칙이 NONE이 아니면 true */
    public boolean isRepeating() {
        return repeatRule != RepeatRule.NONE;
    }

    /** 그 날짜에 완료 처리됐는지. 반복 일정은 날짜마다 따로 본다 (F5) */
    public boolean isCompletedOn(LocalDate day) {
        return completedDates.contains(day);
    }

    /** 그 날짜의 완료 표시를 켜거나(true) 끈다(false). 이미 그 상태여도 오류 없이 넘어간다. */
    public void setCompletedOn(LocalDate day, boolean completed) {
        if (completed) {
            completedDates.add(day);
        } else {
            completedDates.remove(day);
        }
    }

    /** 그 날짜가 반복에서 빠져 있는지 (F3, F4의 "이 일정만" 수정·삭제 결과). */
    public boolean isExcludedOn(LocalDate day) {
        return excludedDates.contains(day);
    }

    /** 그 날짜를 반복에서 뺀다. RepeatExpander가 펼칠 때 이 날짜는 건너뛴다 */
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
    /** 시작 시간. 하루 종일 일정이면 언제나 00:00 */
    public LocalTime getStartTime() { return allDay ? ALL_DAY_START : startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    /** 끝 시간. 하루 종일 일정이면 언제나 23:59 */
    public LocalTime getEndTime() { return allDay ? ALL_DAY_END : endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo == null ? "" : memo; }
    public RepeatRule getRepeatRule() { return repeatRule; }
    public void setRepeatRule(RepeatRule repeatRule) {
        this.repeatRule = repeatRule == null ? RepeatRule.NONE : repeatRule;
    }
    public LocalDate getRepeatEndDate() { return repeatEndDate; }
    public void setRepeatEndDate(LocalDate repeatEndDate) { this.repeatEndDate = repeatEndDate; }
    /** 알림. 하루 종일 일정이면 언제나 NONE (시작 시각이 없어 알릴 때를 정할 수 없다) */
    public ReminderOption getReminder() { return allDay ? ReminderOption.NONE : reminder; }
    public void setReminder(ReminderOption reminder) { this.reminder = reminder == null ? ReminderOption.NONE : reminder; }

    /** 하루 종일 일정인지 */
    public boolean isAllDay() { return allDay; }

    /** 이 일정만의 색 키. 따로 고르지 않았으면 null (카테고리 색을 쓴다) */
    public String getColorKey() { return colorKey; }

    /** 이 일정만의 색을 정한다. null이나 팔레트에 없는 키면 "따로 고르지 않음"(null)이 된다 */
    public void setColorKey(String key) { this.colorKey = CategoryPalette.isKnown(key) ? key : null; }

    /**
     * 하루 종일 여부를 바꾼다. true로 바꾸면 들고 있던 시간도 00:00~23:59, 알림도 NONE으로 덮어쓴다.
     * 그래서 나중에 false로 되돌려도 예전 시간이 아니라 00:00~23:59가 남는다(화면은 입력값으로 새 시간을 넣는다).
     */
    public void setAllDay(boolean allDay) {
        this.allDay = allDay;
        if (allDay) {
            this.startTime = ALL_DAY_START;
            this.endTime = ALL_DAY_END;
            this.reminder = ReminderOption.NONE;
        }
    }

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
        String time = allDay ? "하루 종일" : startTime + "-" + endTime;
        return "Schedule[" + id + ", " + title + ", " + date + " " + time + "]";
    }
}
