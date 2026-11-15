package proto.logic;

import proto.entity.RepeatRule;
import proto.entity.Schedule;

import java.util.ArrayList;
import java.util.List;

/**
 * [논리 application logic] 일정 입력값이 F1 규칙을 지키는지 검사하고,
 * 어긴 이유를 한국어 문장 목록으로 돌려준다. 화면·저장소 없이 단독으로 쓸 수 있다.
 * F1(일정 등록), F3(일정 수정)에서 쓴다. ScheduleController가 저장하기 전에 부르고, 문제가 있으면 저장하지 않고 이유 목록을 화면에 돌려준다.
 * 그래서 화면은 제목 길이 같은 규칙을 직접 알 필요가 없다. 반복 종료일과 시작 날짜의 관계도 여기서 본다.
 * 하루 종일 일정은 Schedule이 시간을 00:00~23:59로, 알림을 NONE으로 돌려주므로 시간·알림 규칙에 걸리지 않는다
 * (하루 종일에 알림을 고른 입력은 오류로 막지 않고 DraftMapper가 알림 없음으로 바꾼다).
 */
public final class ScheduleValidator {

    public static final int MAX_TITLE_LENGTH = 50;
    public static final int MAX_MEMO_LENGTH = 200;

    private ScheduleValidator() {
    }

    /**
     * 일정을 검사해서 사용자에게 그대로 보여 줄 한국어 문장들을 돌려준다. 문제가 없으면 빈 목록이다.
     * 날짜나 시간이 비어 있어도(null) 오류 문장으로 알려 줄 뿐 예외를 던지지 않는다.
     * 시작과 끝이 같으면 끝이 "더 늦지" 않으므로 오류다.
     */
    public static List<String> validate(Schedule s) {
        List<String> errors = new ArrayList<>();
        checkTitle(s, errors);
        checkDateAndTimes(s, errors);
        if (s.getCategoryId() == null || s.getCategoryId().isBlank()) {
            errors.add("카테고리를 선택해 주세요");
        }
        if (s.getMemo().length() > MAX_MEMO_LENGTH) {
            errors.add("메모는 " + MAX_MEMO_LENGTH + "자까지 쓸 수 있어요");
        }
        checkRepeatEnd(s, errors);
        return errors;
    }

    private static void checkTitle(Schedule s, List<String> errors) {
        String title = s.getTitle() == null ? "" : s.getTitle().trim();
        if (title.isEmpty()) {
            errors.add("제목을 입력해 주세요");
        } else if (title.length() > MAX_TITLE_LENGTH) {
            errors.add("제목은 " + MAX_TITLE_LENGTH + "자까지 쓸 수 있어요");
        }
    }

    private static void checkDateAndTimes(Schedule s, List<String> errors) {
        if (s.getDate() == null) {
            errors.add("날짜를 입력해 주세요");
        }
        if (s.getStartTime() == null) {
            errors.add("시작 시간을 입력해 주세요");
        }
        if (s.getEndTime() == null) {
            errors.add("끝 시간을 입력해 주세요");
        }
        if (s.getStartTime() != null && s.getEndTime() != null
                && !s.getEndTime().isAfter(s.getStartTime())) {
            errors.add("끝 시간은 시작 시간보다 늦어야 해요");
        }
    }

    /** 반복 종료일은 반복이 있을 때만 의미가 있다 */
    private static void checkRepeatEnd(Schedule s, List<String> errors) {
        if (s.getRepeatRule() == RepeatRule.NONE || s.getRepeatEndDate() == null || s.getDate() == null) {
            return;
        }
        if (s.getRepeatEndDate().isBefore(s.getDate())) {
            errors.add("반복 종료일은 시작 날짜보다 빠를 수 없어요");
        }
    }
}
