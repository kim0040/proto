package proto.control;

import proto.entity.Occurrence;
import proto.entity.RepeatRule;
import proto.entity.Schedule;
import proto.entity.ScheduleState;
import proto.entity.TimeSlot;
import proto.entity.WeeklyStatistics;
import proto.logic.ConflictDetector;
import proto.logic.FreeSlotFinder;
import proto.logic.RepeatExpander;
import proto.logic.ScheduleFilter;
import proto.logic.ScheduleValidator;
import proto.logic.SeriesEditor;
import proto.logic.StateCalculator;
import proto.logic.StatisticsCalculator;
import proto.repository.CategoryRepository;
import proto.repository.ScheduleRepository;

import java.io.IOException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * [제어 control] 일정 등록·조회·수정·삭제·완료와 빈 시간 찾기, 주간 통계를 처리한다
 * (F1~F6, F8, F9, F11~F14). 화면은 이 클래스의 public 메소드만 부른다.
 *
 * <p>리스너 알림은 {@link EventThread} 규칙을 따른다(EDT이거나 headless면 즉시 호출).
 * 등록·수정·삭제·완료가 성공할 때마다 CSV에 저장하고(F11), 저장에 실패해도 메모리의 변경은 유지한 채
 * {@code storageFailed}로 알린다.</p>
 */
public class ScheduleController {

    private static final String NOT_FOUND = "일정을 찾을 수 없어요. 이미 바뀌었거나 지워졌을 수 있어요";
    private static final int MIN_FREE_MINUTES = 15;
    private static final int MAX_FREE_MINUTES = 480;

    private final ScheduleRepository schedules;
    private final CategoryRepository categories;
    private final Clock clock;
    private final List<ScheduleListener> listeners = new CopyOnWriteArrayList<>();

    public ScheduleController(ScheduleRepository schedules, CategoryRepository categories, Clock clock) {
        this.schedules = schedules;
        this.categories = categories;
        this.clock = clock;
    }

    public void addListener(ScheduleListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ScheduleListener listener) {
        listeners.remove(listener);
    }

    /**
     * 파일을 불러올 때 건너뛴 줄이나 읽기 실패(일정·카테고리 저장소의 경고)를 {@code storageFailed}로 알린다.
     * 화면 리스너를 등록한 뒤에 한 번 부른다 (추가된 public 메소드).
     */
    public void reportLoadWarnings() {
        List<String> warnings = new ArrayList<>(schedules.getWarnings());
        warnings.addAll(categories.getWarnings());
        warnings.forEach(this::notifyStorageFailed);
    }

    /** 오늘 날짜 (controller의 시계 기준. 화면도 이것을 써야 테스트 시계와 맞는다) */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /** 지금 시각 (controller의 시계 기준) */
    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /**
     * F2, F7, F8: from~to(둘 다 포함) 사이 하루치 일정. 반복은 날짜마다 펼친다.
     * 정렬: 날짜 → 시작 시간 → 제목.
     *
     * @param categoryId null이면 모든 카테고리
     * @param keyword    null이나 빈 문자열이면 검색 안 함. 제목 또는 메모에 들어 있으면 (대소문자 무시)
     */
    public List<Occurrence> getOccurrences(LocalDate from, LocalDate to, String categoryId, String keyword) {
        List<Occurrence> all = new ArrayList<>();
        for (Schedule s : schedules.getAll()) {
            all.addAll(RepeatExpander.expand(s, from, to));
        }
        return ScheduleFilter.filter(all, categoryId, keyword).stream()
                .sorted(Comparator.comparing(Occurrence::date)
                        .thenComparing(Occurrence::startTime)
                        .thenComparing(Occurrence::title))
                .toList();
    }

    /** F6: 지금 시각 기준 상태 */
    public ScheduleState stateOf(Occurrence occurrence) {
        return StateCalculator.stateOf(occurrence, now());
    }

    /** 바뀐 뒤의 최신 하루치 일정을 다시 찾는다. 삭제되었으면 빈 값 */
    public Optional<Occurrence> findOccurrence(String scheduleId, LocalDate date) {
        return schedules.findById(scheduleId)
                .filter(s -> RepeatExpander.occursOn(s, date))
                .map(s -> Occurrence.of(s, date));
    }

    /** F1: 새 일정 입력 화면의 기본값. 카테고리는 "개인", 반복·알림 없음 (시간은 09:00~10:00) */
    public ScheduleDraft newDraft(LocalDate date) {
        return DraftMapper.emptyDraft(date);
    }

    /** F12: 빈 시간 하나를 고르면 그 날짜·시작·끝이 채워진 새 일정 입력값 */
    public ScheduleDraft draftForSlot(TimeSlot slot) {
        return DraftMapper.emptyDraft(slot.date(), slot.start(), slot.end());
    }

    /** F3: 수정 화면에 채울 기존 값. 날짜는 고른 하루치 일정의 날짜 */
    public ScheduleDraft draftOf(Occurrence occurrence) {
        LocalDate repeatEnd = schedules.findById(occurrence.scheduleId())
                .map(Schedule::getRepeatEndDate).orElse(null);
        return DraftMapper.toDraft(occurrence, repeatEnd);
    }

    /** F1 검증 규칙. 문제가 없으면 빈 목록, 있으면 사용자에게 보여 줄 이유들 */
    public List<String> validate(ScheduleDraft draft) {
        List<String> errors = new ArrayList<>(ScheduleValidator.validate(DraftMapper.toSchedule(draft, "draft")));
        String categoryId = draft.categoryId();
        if (categoryId != null && !categoryId.isBlank() && categories.findById(categoryId).isEmpty()) {
            errors.add("카테고리를 선택해 주세요");
        }
        return errors;
    }

    /**
     * F13: 저장하려는 값과 시간이 겹치는 다른 하루치 일정들. 없으면 빈 목록.
     *
     * @param editing 수정 중인 하루치 일정 (새로 등록이면 null). 자기 자신과는 겹침으로 보지 않는다
     * @param scope   수정 범위 (새로 등록이거나 반복이 없으면 무시)
     */
    public List<Occurrence> findConflicts(ScheduleDraft draft, Occurrence editing, EditScope scope) {
        if (!validate(draft).isEmpty()) {
            return List.of();
        }
        Schedule candidate = DraftMapper.toSchedule(draft, "candidate");
        boolean thisOnly = editing != null && editing.isRepeating() && scope != EditScope.THIS_AND_FOLLOWING;
        if (thisOnly) {
            candidate.setRepeatRule(RepeatRule.NONE);
            candidate.setRepeatEndDate(null);
        }
        return ConflictDetector.findConflicts(candidate, schedules.getAll(), selfFilter(editing, thisOnly));
    }

    /** F1: 검증을 통과하면 저장(F11)하고 리스너에 알린다 */
    public OperationResult create(ScheduleDraft draft) {
        List<String> errors = validate(draft);
        if (!errors.isEmpty()) {
            return OperationResult.fail(errors);
        }
        schedules.add(DraftMapper.toSchedule(draft, UUID.randomUUID().toString()));
        commit();
        return OperationResult.ok();
    }

    /** F3: 수정. 반복 일정이면 scope에 따라 이 날짜만 / 이 날짜부터 뒤로 전부 바뀐다 */
    public OperationResult update(Occurrence target, ScheduleDraft draft, EditScope scope) {
        List<String> errors = validate(draft);
        if (!errors.isEmpty()) {
            return OperationResult.fail(errors);
        }
        Optional<Schedule> series = locate(target);
        if (series.isEmpty()) {
            return OperationResult.fail(NOT_FOUND);
        }
        Schedule candidate = DraftMapper.toSchedule(draft, UUID.randomUUID().toString());
        applyChange(editSeries(series.get(), target, candidate, scope));
        return OperationResult.ok();
    }

    /** F4: 삭제 (확인은 화면이 먼저 받는다). 반복 일정이면 scope에 따라 */
    public OperationResult delete(Occurrence target, EditScope scope) {
        Optional<Schedule> series = locate(target);
        if (series.isEmpty()) {
            return OperationResult.fail(NOT_FOUND);
        }
        Schedule s = series.get();
        if (!s.isRepeating()) {
            applyChange(new SeriesEditor.Change(List.of(), List.of(s)));
        } else if (scope == EditScope.THIS_AND_FOLLOWING) {
            applyChange(SeriesEditor.deleteFollowing(s, target.date()));
        } else {
            applyChange(SeriesEditor.deleteThisOnly(s, target.date()));
        }
        return OperationResult.ok();
    }

    /** F5: 완료 체크/해제. 반복 일정은 그 날짜만 */
    public OperationResult setCompleted(Occurrence target, boolean completed) {
        Optional<Schedule> series = locate(target);
        if (series.isEmpty()) {
            return OperationResult.fail(NOT_FOUND);
        }
        series.get().setCompletedOn(target.date(), completed);
        commit();
        return OperationResult.ok();
    }

    /**
     * F12: date의 from~to 사이에서 minutes분 이상 비어 있는 구간들 (시간 순서).
     * 그날의 모든 일정(완료 포함, 카테고리·검색 필터 무시)을 기준으로 하고, 오늘이면 지금 이후만.
     */
    public List<TimeSlot> findFreeSlots(LocalDate date, int minutes, LocalTime from, LocalTime to) {
        int needed = Math.max(MIN_FREE_MINUTES, Math.min(MAX_FREE_MINUTES, minutes));
        List<Occurrence> day = getOccurrences(date, date, null, null);
        return FreeSlotFinder.find(day, date, needed, from, to, now());
    }

    /** F14: anyDayInWeek가 들어 있는 주(월~일)의 통계 */
    public WeeklyStatistics weeklyStatistics(LocalDate anyDayInWeek) {
        LocalDate monday = anyDayInWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<Occurrence> week = getOccurrences(monday, monday.plusDays(6), null, null);
        return StatisticsCalculator.calculate(anyDayInWeek, week, now());
    }

    /** 같은 control 패키지의 타이머·카테고리 controller가 쓰는 알림 */
    void notifySchedulesChanged() {
        dispatch(ScheduleListener::schedulesChanged);
    }

    void notifyStatesChanged() {
        dispatch(ScheduleListener::statesChanged);
    }

    void notifyStorageFailed(String message) {
        dispatch(l -> l.storageFailed(message));
    }

    static String saveFailedMessage(IOException e) {
        return "저장하지 못했어요: " + (e.getMessage() == null ? e.toString() : e.getMessage());
    }

    private void dispatch(Consumer<ScheduleListener> action) {
        EventThread.run(() -> listeners.forEach(action));
    }

    /** 저장(F11)하고 화면에 알린다. 저장 실패는 알리되 메모리 변경은 그대로 둔다 */
    private void commit() {
        try {
            schedules.save();
        } catch (IOException e) {
            notifyStorageFailed(saveFailedMessage(e));
        }
        notifySchedulesChanged();
    }

    private void applyChange(SeriesEditor.Change change) {
        change.removed().forEach(schedules::remove);
        change.added().forEach(schedules::add);
        commit();
    }

    /** 이 하루치 일정이 아직 있는 일정(Schedule). 이미 바뀌었거나 지워졌으면 빈 값 */
    private Optional<Schedule> locate(Occurrence target) {
        return schedules.findById(target.scheduleId())
                .filter(s -> RepeatExpander.occursOn(s, target.date()));
    }

    private SeriesEditor.Change editSeries(Schedule series, Occurrence target, Schedule candidate,
                                           EditScope scope) {
        if (!series.isRepeating()) {
            return SeriesEditor.updateInPlace(series, candidate);
        }
        if (scope == EditScope.THIS_AND_FOLLOWING) {
            return SeriesEditor.updateFollowing(series, target.date(), candidate);
        }
        return SeriesEditor.updateThisOnly(series, target.date(), candidate);
    }

    /** 겹침 검사에서 뺄 "수정 중인 자기 자신" 조건 */
    private static Predicate<Occurrence> selfFilter(Occurrence editing, boolean thisOnly) {
        if (editing == null) {
            return o -> false;
        }
        if (!editing.isRepeating()) {
            return o -> o.scheduleId().equals(editing.scheduleId());
        }
        if (thisOnly) {
            return o -> o.scheduleId().equals(editing.scheduleId()) && o.date().equals(editing.date());
        }
        return o -> o.scheduleId().equals(editing.scheduleId()) && !o.date().isBefore(editing.date());
    }
}
