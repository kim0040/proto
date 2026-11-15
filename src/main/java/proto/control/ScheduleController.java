package proto.control;

import proto.entity.Category;
import proto.entity.CategoryPalette;
import proto.entity.Occurrence;
import proto.entity.RepeatRule;
import proto.entity.Schedule;
import proto.entity.ScheduleState;
import proto.entity.TimeSlot;
import proto.entity.WeeklyStatistics;
import proto.logic.ConflictDetector;
import proto.logic.DateQueryParser;
import proto.logic.FreeSlotFinder;
import proto.logic.RepeatExpander;
import proto.logic.ScheduleFilter;
import proto.logic.ScheduleValidator;
import proto.logic.SeriesEditor;
import proto.logic.StateCalculator;
import proto.logic.StatisticsCalculator;
import proto.repository.CategoryStore;
import proto.repository.ScheduleStore;

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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * [제어 control] 일정 등록·조회·수정·삭제·완료와 빈 시간 찾기, 주간 통계를 처리한다
 * (F1~F6, F8, F9, F11~F14). 화면은 이 클래스의 public 메소드만 부른다.
 * 화면이 일정에 대해 하는 거의 모든 요청의 창구이다. 화면은 UiContext#schedules()로 이 객체를 받아 쓰고,
 * 이 클래스는 일을 logic(검증·반복 펼치기·겹침·상태·통계)과 ScheduleStore(저장)에 나눠 맡긴 뒤, 결과를 ScheduleListener로 화면에 알린다.
 * 화면은 entity나 저장소를 직접 만지지 않는다. 입력값은 ScheduleDraft로 받고 검증을 통과해야만 저장한다.
 *
 * <p>리스너 알림은 {@link EventThread} 규칙을 따른다(EDT이거나 headless면 즉시 호출).
 * 등록·수정·삭제·완료가 성공할 때마다 CSV에 저장하고(F11), 저장에 실패해도 메모리의 변경은 유지한 채
 * {@code storageFailed}로 알린다.</p>
 *
 * <p>등록·수정·삭제·완료가 성공할 때마다 ChangeHistory에 한 단계를 기록해서 되돌리기·다시 실행(undo/redo)을 할 수 있다.
 * 되돌릴 때도 같은 저장·알림 과정(commit)을 거친다.</p>
 *
 * <p>모든 public 메소드는 Swing 이벤트 스레드(EDT)에서 불러야 한다 (화면과 타이머가 이미 EDT에서 부른다).</p>
 */
public class ScheduleController {

    private static final String NOT_FOUND = "일정을 찾을 수 없어요. 이미 바뀌었거나 지워졌을 수 있어요";
    private static final int MIN_FREE_MINUTES = 15;
    private static final int MAX_FREE_MINUTES = 480;
    private static final String LABEL_CREATE = "일정 추가";
    private static final String LABEL_UPDATE = "일정 수정";
    private static final String LABEL_DELETE = "일정 삭제";
    private static final String LABEL_COMPLETE = "완료 처리";
    private static final String LABEL_COLOR = "색 바꾸기";
    private static final String LABEL_UNCOMPLETE = "완료 취소";

    private final ScheduleStore schedules;
    private final CategoryStore categories;
    private final Clock clock;
    private final List<ScheduleListener> listeners = new CopyOnWriteArrayList<>();
    private final ChangeHistory history = new ChangeHistory();

    /**
     * 불러온 일정 중 없는 카테고리를 가리키는 것은 메모리에서 "개인"으로 옮긴다 (다음 저장 때 파일에도 반영)
     * 저장 뒤 카테고리가 지워졌거나 파일을 손으로 고쳤을 때 "없는 카테고리를 가리키는 일정"이 생길 수 있어서 시작할 때 한 번 정리한다.
     */
    public ScheduleController(ScheduleStore schedules, CategoryStore categories, Clock clock) {
        this.schedules = schedules;
        this.categories = categories;
        this.clock = clock;
        moveUnknownCategoriesToPersonal();
    }

    /** 화면(MainFrame 등)이 일정 변화를 받으려고 자신을 등록한다. 같은 리스너를 두 번 넣으면 두 번 알림을 받는다. */
    public void addListener(ScheduleListener listener) {
        listeners.add(listener);
    }

    /** 등록했던 리스너를 뺀다. 등록하지 않은 것이면 아무 일도 없다. */
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

    /**
     * 사용자가 입력한 글을 날짜로 읽는다 ("내일", "11월 20일", "2026-11-20" 등. 상대 표현은 controller의 시계 기준 오늘로 계산한다).
     * 화면(명령 메뉴)이 logic을 직접 부르지 않고 이 메서드로 요청한다.
     *
     * @return 날짜로 읽히면 그 날짜, 아니면 빈 값
     */
    public Optional<LocalDate> resolveDate(String text) {
        return DateQueryParser.parse(text, today());
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
     * 정렬: 날짜 → 하루 종일 일정 먼저 → 시작 시간 → 제목. 한 번 펼친 뒤에 필터와 정렬을 하며,
     * 조건에 맞는 일정이 없으면 빈 목록이다.
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
                        .thenComparing(o -> !o.allDay())
                        .thenComparing(Occurrence::startTime)
                        .thenComparing(Occurrence::title))
                .toList();
    }

    /**
     * F6: 지금 시각 기준 상태.
     * 상태는 저장하지 않고 호출할 때마다 현재 시각(now)으로 계산한다.
     */
    public ScheduleState stateOf(Occurrence occurrence) {
        return StateCalculator.stateOf(occurrence, now());
    }

    /**
     * 바뀐 뒤의 최신 하루치 일정을 다시 찾는다. 삭제되었으면 빈 값.
     * 화면이 들고 있던 Occurrence는 낡은 스냅샷일 수 있어서, 수정·알림 전에 이 메서드로 최신 값을 다시 확인한다.
     * 날짜에 그 일정이 나타나지 않으면(삭제되었거나 반복에서 빠졌으면) Optional.empty()이다.
     */
    public Optional<Occurrence> findOccurrence(String scheduleId, LocalDate date) {
        return schedules.findById(scheduleId)
                .filter(s -> RepeatExpander.occursOn(s, date))
                .map(s -> Occurrence.of(s, date));
    }

    /** F1: 새 일정 대화상자를 열 때 채워 넣을 기본값. 카테고리는 "개인", 반복·알림 없음, 하루 종일 아님, 시간은 09:00~10:00 (DraftMapper#emptyDraft) */
    public ScheduleDraft newDraft(LocalDate date) {
        return DraftMapper.emptyDraft(date);
    }

    /** F12: 빈 시간 하나를 고르면("이 시간에 추가") 그 날짜·시작·끝이 채워진 새 일정 입력값을 만든다 (하루 종일 아님). */
    public ScheduleDraft draftForSlot(TimeSlot slot) {
        return DraftMapper.emptyDraft(slot.date(), slot.start(), slot.end());
    }

    /** F3: 수정 대화상자에 채울 기존 값을 만든다 (하루 종일 여부 포함). 날짜는 고른 하루치 일정의 날짜이다. Occurrence에는 반복 종료일이 없어서 원본 Schedule에서 찾아 넣는다. */
    public ScheduleDraft draftOf(Occurrence occurrence) {
        LocalDate repeatEnd = schedules.findById(occurrence.scheduleId())
                .map(Schedule::getRepeatEndDate).orElse(null);
        return DraftMapper.toDraft(occurrence, repeatEnd);
    }

    /**
     * 복제·붙여넣기용 입력값: 고른 하루치 일정과 제목·시간·카테고리·메모·알림·하루 종일 여부가 같고 날짜만 date인 반복 없는 일정이다.
     * 복사본은 언제나 반복 없는 새 일정이고 완료되지 않은 상태로 만들어진다. 화면은 이 값을 create에 넘긴다(되돌리기에는 "일정 추가"로 남는다).
     * 복사해 둔 사이에 카테고리가 지워졌으면 "개인"으로 채운다 (그대로 두면 등록 검증에서 막히기 때문이다).
     */
    public ScheduleDraft copyDraft(Occurrence source, LocalDate date) {
        String categoryId = categories.findById(source.categoryId()).isPresent()
                ? source.categoryId() : Category.PERSONAL_ID;
        return new ScheduleDraft(source.title(), date, source.startTime(), source.endTime(), categoryId,
                source.memo(), RepeatRule.NONE, null, source.reminder(), source.allDay(), source.colorKey());
    }

    /**
     * 수정 화면용 F1 검증. 반복 일정의 "이 일정만" 수정이면 반복 없음(반복 종료일 없음)으로 바꾼 값을 검사한다.
     * 실제 {@link #update}가 검사하는 값과 같다 (추가된 public 메소드).
     *
     * @param editing 수정 중인 하루치 일정 (새로 등록이면 null)
     * @param scope   수정 범위 (새로 등록이거나 반복이 없으면 무시)
     */
    public List<String> validate(ScheduleDraft draft, Occurrence editing, EditScope scope) {
        return validate(effectiveDraft(draft, editing, scope));
    }

    /**
     * F1 검증: ScheduleValidator 규칙에 "고른 카테고리가 실제로 있는지"를 더한다. 문제가 없으면 빈 목록,
     * 있으면 사용자에게 그대로 보여 줄 한국어 이유들이다. 저장하지 않으며, 예외를 던지지 않는다.
     */
    public List<String> validate(ScheduleDraft draft) {
        List<String> errors = new ArrayList<>(ScheduleValidator.validate(DraftMapper.toSchedule(draft, "draft")));
        String categoryId = draft.categoryId();
        if (categoryId != null && !categoryId.isBlank() && categories.findById(categoryId).isEmpty()) {
            errors.add("카테고리를 선택해 주세요");
        }
        return errors;
    }

    /**
     * F13: 저장하려는 값과 시간이 겹치는 다른 하루치 일정들. 없으면 빈 목록. 하루 종일 일정은 겹침을 따지지 않는다 (ConflictDetector).
     * 입력값이 아직 올바르지 않으면 겹침 검사를 하지 않고 빈 목록을 돌려준다 (검증 오류가 먼저 보이도록).
     *
     * @param editing 수정 중인 하루치 일정 (새로 등록이면 null). 자기 자신과는 겹침으로 보지 않는다
     * @param scope   수정 범위 (새로 등록이거나 반복이 없으면 무시)
     */
    public List<Occurrence> findConflicts(ScheduleDraft draft, Occurrence editing, EditScope scope) {
        ScheduleDraft effective = effectiveDraft(draft, editing, scope);
        if (!validate(effective).isEmpty()) {
            return List.of();
        }
        Schedule candidate = DraftMapper.toSchedule(effective, "candidate");
        return ConflictDetector.findConflicts(candidate, schedules.getAll(),
                selfFilter(editing, effective, isThisOnly(editing, scope)));
    }

    /**
     * F1 일정 등록. 검증을 통과하면 새 id(UUID)를 붙여 저장소에 넣고 저장한 뒤(F11) 리스너에 알린다.
     * 시간이 겹치는지(F13)는 여기서 보지 않는다. 화면이 findConflicts로 먼저 묻고, 사용자가 그래도 저장하겠다고 해야 이 메서드를 부른다.
     *
     * @return 성공이면 ok(), 검증에 실패하면 이유 목록이 든 fail(...) (저장하지 않음)
     */
    public OperationResult create(ScheduleDraft draft) {
        List<String> errors = validate(draft);
        if (!errors.isEmpty()) {
            return OperationResult.fail(errors);
        }
        ScheduleSnapshot before = ScheduleSnapshot.of(schedules);
        schedules.add(DraftMapper.toSchedule(draft, UUID.randomUUID().toString()));
        recordStep(LABEL_CREATE, before);
        commit();
        return OperationResult.ok();
    }

    /**
     * F3 일정 수정. 반복 일정이면 범위(scope)에 따라 "이 일정만" 또는 "이 날짜부터 앞으로 전부" 바뀐다.
     * 실제 수정 규칙은 SeriesEditor가 맡고, 이 메서드는 입력값 검증 → 일정 찾기 → 수정 → 저장·알림의 순서를 지킨다.
     *
     * @return 성공이면 ok(). 검증에 실패하면 이유 목록, 그 사이에 일정이 지워졌으면 "일정을 찾을 수 없어요" 실패 (저장하지 않음)
     */
    public OperationResult update(Occurrence target, ScheduleDraft draft, EditScope scope) {
        ScheduleDraft effective = effectiveDraft(draft, target, scope);
        List<String> errors = validate(effective);
        if (!errors.isEmpty()) {
            return OperationResult.fail(errors);
        }
        Optional<Schedule> series = locate(target);
        if (series.isEmpty()) {
            return OperationResult.fail(NOT_FOUND);
        }
        Schedule candidate = DraftMapper.toSchedule(effective, UUID.randomUUID().toString());
        ScheduleSnapshot before = ScheduleSnapshot.of(schedules);
        applyChange(LABEL_UPDATE, before, editSeries(series.get(), target, candidate, scope));
        return OperationResult.ok();
    }

    /**
     * F4 일정 삭제 (삭제 확인은 화면이 먼저 받는다). 반복이 없으면 일정 전체를 지우고, 반복이면 범위(scope)에 따라 SeriesEditor가 이 날짜만 빼거나 앞으로 전부 지운다.
     *
     * @return 성공이면 ok(), 일정을 찾을 수 없으면 실패
     */
    public OperationResult delete(Occurrence target, EditScope scope) {
        Optional<Schedule> series = locate(target);
        if (series.isEmpty()) {
            return OperationResult.fail(NOT_FOUND);
        }
        Schedule s = series.get();
        ScheduleSnapshot before = ScheduleSnapshot.of(schedules);
        if (!s.isRepeating()) {
            applyChange(LABEL_DELETE, before, new SeriesEditor.Change(List.of(), List.of(s)));
        } else if (scope == EditScope.THIS_AND_FOLLOWING) {
            applyChange(LABEL_DELETE, before, SeriesEditor.deleteFollowing(s, target.date()));
        } else {
            applyChange(LABEL_DELETE, before, SeriesEditor.deleteThisOnly(s, target.date()));
        }
        return OperationResult.ok();
    }

    /**
     * F5 완료 체크/해제. 반복 일정이어도 그 날짜 하나만 바뀐다. 상태(F6)는 저장하지 않으므로 완료 여부만 바꾸면 상태는 저절로 따라온다.
     *
     * @return 성공이면 ok(), 일정을 찾을 수 없으면 실패
     */
    public OperationResult setCompleted(Occurrence target, boolean completed) {
        Optional<Schedule> series = locate(target);
        if (series.isEmpty()) {
            return OperationResult.fail(NOT_FOUND);
        }
        ScheduleSnapshot before = ScheduleSnapshot.of(schedules);
        series.get().setCompletedOn(target.date(), completed);
        recordStep(completed ? LABEL_COMPLETE : LABEL_UNCOMPLETE, before);
        commit();
        return OperationResult.ok();
    }

    /**
     * 일정만의 색을 바꾼다 (null이면 "카테고리 색"으로 돌아간다). 반복 일정이면 그 반복 전체의 색이 바뀐다
     * (색은 날짜마다 다를 이유가 없는 표시용 값이라 "이 일정만"을 묻지 않는다). 되돌리기에는 "색 바꾸기"로 남는다.
     *
     * @return 성공이면 ok(), 일정을 찾을 수 없으면 실패. 이미 그 색이면 아무것도 하지 않고 ok()
     */
    public OperationResult setColor(Occurrence target, String colorKey) {
        Optional<Schedule> series = locate(target);
        if (series.isEmpty()) {
            return OperationResult.fail(NOT_FOUND);
        }
        String key = CategoryPalette.isKnown(colorKey) ? colorKey : null;
        if (Objects.equals(series.get().getColorKey(), key)) {
            return OperationResult.ok();
        }
        ScheduleSnapshot before = ScheduleSnapshot.of(schedules);
        series.get().setColorKey(key);
        recordStep(LABEL_COLOR, before);
        commit();
        return OperationResult.ok();
    }

    /** 되돌릴 일정 변경이 있는지 (되돌리기 버튼·메뉴를 켤지 정할 때 쓴다) */
    public boolean canUndo() {
        return history.canUndo();
    }

    /**
     * 다음 되돌리기가 되돌릴 변경 단계의 번호 (되돌릴 것이 없으면 0). 새 변경을 기록할 때마다 새 번호가 붙고, 한 단계의 번호는 바뀌지 않는다.
     * 그래서 어떤 동작 앞뒤로 이 값을 비교하면 "기록이 남았는지"를, 토스트가 뜬 뒤 같은 값인지 보면 "그 단계가 아직 맨 위인지"를 알 수 있다.
     */
    public long undoStepId() {
        return history.topId();
    }

    /** 다시 실행할 일정 변경이 있는지 */
    public boolean canRedo() {
        return history.canRedo();
    }

    /** 다음 되돌리기의 짧은 이름 ("일정 추가", "일정 수정", "일정 삭제", "완료 처리", "완료 취소"). 없으면 빈 값 */
    public Optional<String> undoLabel() {
        return history.undoLabel();
    }

    /** 다음 다시 실행의 짧은 이름. 없으면 빈 값 */
    public Optional<String> redoLabel() {
        return history.redoLabel();
    }

    /**
     * 마지막 일정 변경을 되돌린다. 일정 목록을 그 변경 전으로 돌리고 저장(F11)한 뒤 리스너에 알린다.
     *
     * @return 성공이면 ok(), 되돌릴 것이 없으면 "되돌릴 작업이 없어요" 실패 (저장·알림 없음)
     */
    public OperationResult undo() {
        return restore(history.undo(), "되돌릴 작업이 없어요");
    }

    /**
     * 마지막으로 되돌린 변경을 다시 실행한다. 일정 목록을 그 변경 뒤로 돌리고 저장(F11)한 뒤 리스너에 알린다.
     *
     * @return 성공이면 ok(), 다시 실행할 것이 없으면 "다시 실행할 작업이 없어요" 실패 (저장·알림 없음)
     */
    public OperationResult redo() {
        return restore(history.redo(), "다시 실행할 작업이 없어요");
    }

    /**
     * F12: date의 from~to 사이에서 minutes분 이상 비어 있는 구간들 (시간 순서).
     * 그날의 모든 일정(완료 포함, 카테고리·검색 필터 무시. 하루 종일 일정은 시간을 차지하지 않는다)을 기준으로 하고, 오늘이면 지금 이후만.
     * 필요한 시간(minutes)은 15분~480분 안으로 맞춘다. 너무 짧은 시간은 의미가 없고, 너무 긴 시간은 항상 빈 목록이 되기 때문이다.
     */
    public List<TimeSlot> findFreeSlots(LocalDate date, int minutes, LocalTime from, LocalTime to) {
        int needed = Math.max(MIN_FREE_MINUTES, Math.min(MAX_FREE_MINUTES, minutes));
        List<Occurrence> day = getOccurrences(date, date, null, null);
        return FreeSlotFinder.find(day, date, needed, from, to, now());
    }

    /**
     * F14: anyDayInWeek가 들어 있는 주(월~일)의 통계.
     * 그 주의 하루치 일정을 모아 StatisticsCalculator에 맡긴다. 하루 종일 일정은 개수·완료율에는 들어가고 카테고리별 시간에는 빠진다.
     */
    public WeeklyStatistics weeklyStatistics(LocalDate anyDayInWeek) {
        LocalDate monday = anyDayInWeek.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<Occurrence> week = getOccurrences(monday, monday.plusDays(6), null, null);
        return StatisticsCalculator.calculate(anyDayInWeek, week, now());
    }

    /** 일정 목록이 바뀌었음을 모든 리스너에 알린다. CategoryController(카테고리 삭제로 일정이 옮겨질 때)도 쓰므로 같은 패키지에 열어 두었다. */
    void notifySchedulesChanged() {
        dispatch(ScheduleListener::schedulesChanged);
    }

    /**
     * 되돌리기·다시 실행 기록을 모두 지운다. CategoryController가 카테고리를 지워 일정을 "개인"으로 옮긴 뒤 부른다.
     * 왜: 그 전으로 되돌리면 지워진 카테고리를 가리키는 일정이 되살아날 수 있기 때문이다.
     */
    void clearHistory() {
        history.clear();
    }

    /** 시간이 지나 일정 상태가 바뀌었음을 알린다 (StateRefreshTimer가 부른다). */
    void notifyStatesChanged() {
        dispatch(ScheduleListener::statesChanged);
    }

    /**
     * 저장·불러오기 문제를 사용자에게 알린다.
     *
     * @param message 사용자에게 그대로 보여 줄 한국어 문장
     */
    void notifyStorageFailed(String message) {
        dispatch(l -> l.storageFailed(message));
    }

    /**
     * 저장 뒤에 새로 생긴 저장소 경고(서버에 올리지 못함 등)를 storageFailed로 한 번씩 알린다.
     * 저장 전에도 있던 경고는 이미 알렸거나 알릴 것이므로 다시 알리지 않는다. 로컬 저장 성공은 되돌리지 않는다.
     */
    void reportNewWarnings(List<String> before, List<String> after) {
        after.stream().distinct().filter(w -> !before.contains(w)).forEach(this::notifyStorageFailed);
    }

    static String saveFailedMessage(IOException e) {
        return "저장하지 못했어요: " + (e.getMessage() == null ? e.toString() : e.getMessage());
    }

    /** 리스너 호출은 항상 Swing 이벤트 스레드에서 해야 화면이 안전하다. 그 규칙은 EventThread가 지키므로 여기서는 그저 맡긴다. */
    private void dispatch(Consumer<ScheduleListener> action) {
        EventThread.run(() -> listeners.forEach(action));
    }

    /**
     * 저장(F11)하고 화면에 알린다. 저장 실패는 알리되 메모리 변경은 그대로 둔다.
     * 왜 저장 실패 때 되돌리지 않나: 사용자가 방금 입력한 일정이 사라지는 것보다, 메모리에는 남기고 "저장하지 못했어요"라고 알려 주는 편이 낫다고 보았다.
     */
    private void commit() {
        List<String> before = List.copyOf(schedules.getWarnings());
        try {
            schedules.save();
            reportNewWarnings(before, schedules.getWarnings());
        } catch (IOException e) {
            notifyStorageFailed(saveFailedMessage(e));
        }
        notifySchedulesChanged();
    }

    /**
     * SeriesEditor가 알려 준 "뺄 일정과 더할 일정"을 저장소에 반영하고, 되돌리기 기록을 남긴 뒤 저장한다. 반복 일정을 나누면 일정이 새로 생기거나 사라질 수 있다.
     *
     * @param before SeriesEditor가 일정을 고치기 전에 찍어 둔 일정 목록
     */
    private void applyChange(String label, ScheduleSnapshot before, SeriesEditor.Change change) {
        change.removed().forEach(schedules::remove);
        change.added().forEach(schedules::add);
        recordStep(label, before);
        commit();
    }

    /** 방금 성공한 변경을 "바뀌기 전(before) → 지금" 한 단계로 기록한다. commit 전에 불러야 알림을 받은 화면이 canUndo를 바로 본다. */
    private void recordStep(String label, ScheduleSnapshot before) {
        history.record(label, before, ScheduleSnapshot.of(schedules));
    }

    /** 되돌리기·다시 실행 공통: 돌아갈 목록이 있으면 저장소를 그 모양으로 바꾸고 저장·알림, 없으면 실패 */
    private OperationResult restore(Optional<ScheduleSnapshot> target, String nothingMessage) {
        if (target.isEmpty()) {
            return OperationResult.fail(nothingMessage);
        }
        target.get().restoreInto(schedules);
        commit();
        return OperationResult.ok();
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

    /** 반복 일정의 하루치를 "이 일정만" 수정하는지 (scope가 null이어도 이 일정만으로 본다) */
    private static boolean isThisOnly(Occurrence editing, EditScope scope) {
        return editing != null && editing.isRepeating() && scope != EditScope.THIS_AND_FOLLOWING;
    }

    /**
     * 실제로 저장될 값: "이 일정만" 수정이면 반복 없음 + 반복 종료일 없음으로 바꾼다 (검증·겹침·저장에 똑같이 쓴다)
     * 왜: "이 일정만" 수정은 반복에서 그 날짜를 빼고 반복 없는 새 일정을 만들기 때문에, 검증·겹침·저장이 모두 "반복 없음"으로 본 값으로 일해야 서로 어긋나지 않는다.
     */
    private static ScheduleDraft effectiveDraft(ScheduleDraft d, Occurrence editing, EditScope scope) {
        if (!isThisOnly(editing, scope)) {
            return d;
        }
        return d.withoutRepeat();
    }

    /**
     * 없는 카테고리를 가리키는 일정은 화면에서 이름을 찾을 수 없으므로 "개인"으로 옮긴다. 메모리만 바꾸고 파일은 다음 저장 때 반영된다.
     * "개인"이 없으면(비정상) 옮길 곳이 없으므로 아무것도 하지 않는다.
     */
    private void moveUnknownCategoriesToPersonal() {
        if (categories.findById(Category.PERSONAL_ID).isEmpty()) {
            return;
        }
        for (Schedule s : schedules.getAll()) {
            if (categories.findById(s.getCategoryId()).isEmpty()) {
                s.setCategoryId(Category.PERSONAL_ID);
            }
        }
    }

    /**
     * 겹침 검사에서 뺄 "수정 중인 자기 자신".
     * 앞으로 전부 수정이면 고른 날과 새 시작일 중 이른 날부터 원래 반복을 뺀다 (저장과 같은 자르는 날).
     */
    private static Predicate<Occurrence> selfFilter(Occurrence editing, ScheduleDraft draft, boolean thisOnly) {
        if (editing == null) {
            return o -> false;
        }
        if (!editing.isRepeating()) {
            return o -> o.scheduleId().equals(editing.scheduleId());
        }
        if (thisOnly) {
            return o -> o.scheduleId().equals(editing.scheduleId()) && o.date().equals(editing.date());
        }
        LocalDate cut = earlier(editing.date(), draft.date());
        return o -> o.scheduleId().equals(editing.scheduleId()) && !o.date().isBefore(cut);
    }

    /** 두 날짜 중 더 이른 날. SeriesEditor가 앞으로 전부 수정을 자를 때와 같다 */
    private static LocalDate earlier(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
