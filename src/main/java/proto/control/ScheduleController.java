package proto.control;

import proto.entity.Occurrence;
import proto.entity.ScheduleState;
import proto.entity.TimeSlot;
import proto.entity.WeeklyStatistics;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * [제어 control] 일정 등록·조회·수정·삭제·완료와 빈 시간 찾기, 주간 통계를 처리한다
 * (F1~F6, F8, F9, F11~F14). 화면은 이 클래스의 public 메소드만 부른다.
 *
 * <p>뼈대(API 고정용). 본문은 구현 단계에서 채운다. 생성자도 구현 단계에서 정한다.</p>
 */
public class ScheduleController {

    public void addListener(ScheduleListener listener) {
        throw todo();
    }

    public void removeListener(ScheduleListener listener) {
        throw todo();
    }

    /** 오늘 날짜 (controller의 시계 기준. 화면도 이것을 써야 테스트 시계와 맞는다) */
    public LocalDate today() {
        throw todo();
    }

    /** 지금 시각 (controller의 시계 기준) */
    public LocalDateTime now() {
        throw todo();
    }

    /**
     * F2, F7, F8: from~to(둘 다 포함) 사이 하루치 일정. 반복은 날짜마다 펼친다.
     * 정렬: 날짜 → 시작 시간 → 제목.
     *
     * @param categoryId null이면 모든 카테고리
     * @param keyword    null이나 빈 문자열이면 검색 안 함. 제목 또는 메모에 들어 있으면 (대소문자 무시)
     */
    public List<Occurrence> getOccurrences(LocalDate from, LocalDate to, String categoryId, String keyword) {
        throw todo();
    }

    /** F6: 지금 시각 기준 상태 */
    public ScheduleState stateOf(Occurrence occurrence) {
        throw todo();
    }

    /** 바뀐 뒤의 최신 하루치 일정을 다시 찾는다. 삭제되었으면 빈 값 */
    public Optional<Occurrence> findOccurrence(String scheduleId, LocalDate date) {
        throw todo();
    }

    /** F1: 새 일정 입력 화면의 기본값. 카테고리는 "개인", 반복·알림 없음 */
    public ScheduleDraft newDraft(LocalDate date) {
        throw todo();
    }

    /** F12: 빈 시간 하나를 고르면 그 날짜·시작·끝이 채워진 새 일정 입력값 */
    public ScheduleDraft draftForSlot(TimeSlot slot) {
        throw todo();
    }

    /** F3: 수정 화면에 채울 기존 값. 날짜는 고른 하루치 일정의 날짜 */
    public ScheduleDraft draftOf(Occurrence occurrence) {
        throw todo();
    }

    /** F1 검증 규칙. 문제가 없으면 빈 목록, 있으면 사용자에게 보여 줄 이유들 */
    public List<String> validate(ScheduleDraft draft) {
        throw todo();
    }

    /**
     * F13: 저장하려는 값과 시간이 겹치는 다른 하루치 일정들. 없으면 빈 목록.
     *
     * @param editing 수정 중인 하루치 일정 (새로 등록이면 null). 자기 자신과는 겹침으로 보지 않는다
     * @param scope   수정 범위 (새로 등록이거나 반복이 없으면 무시)
     */
    public List<Occurrence> findConflicts(ScheduleDraft draft, Occurrence editing, EditScope scope) {
        throw todo();
    }

    /** F1: 검증을 통과하면 저장(F11)하고 리스너에 알린다 */
    public OperationResult create(ScheduleDraft draft) {
        throw todo();
    }

    /** F3: 수정. 반복 일정이면 scope에 따라 이 날짜만 / 이 날짜부터 뒤로 전부 바뀐다 */
    public OperationResult update(Occurrence target, ScheduleDraft draft, EditScope scope) {
        throw todo();
    }

    /** F4: 삭제 (확인은 화면이 먼저 받는다). 반복 일정이면 scope에 따라 */
    public OperationResult delete(Occurrence target, EditScope scope) {
        throw todo();
    }

    /** F5: 완료 체크/해제. 반복 일정은 그 날짜만 */
    public OperationResult setCompleted(Occurrence target, boolean completed) {
        throw todo();
    }

    /**
     * F12: date의 from~to 사이에서 minutes분 이상 비어 있는 구간들 (시간 순서).
     * 그날의 모든 일정(완료 포함, 카테고리·검색 필터 무시)을 기준으로 하고, 오늘이면 지금 이후만.
     */
    public List<TimeSlot> findFreeSlots(LocalDate date, int minutes, LocalTime from, LocalTime to) {
        throw todo();
    }

    /** F14: anyDayInWeek가 들어 있는 주(월~일)의 통계 */
    public WeeklyStatistics weeklyStatistics(LocalDate anyDayInWeek) {
        throw todo();
    }

    private static UnsupportedOperationException todo() {
        return new UnsupportedOperationException("아직 구현되지 않음");
    }
}
