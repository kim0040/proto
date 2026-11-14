package proto.control;

/**
 * [제어 control] 반복 일정을 수정/삭제할 때 범위 (F3, F4). 반복이 없는 일정에는 무시된다.
 * F3(수정), F4(삭제)의 반복 일정에서 쓴다. 화면의 "이 일정만 / 앞으로 전부" 선택이 이 값으로 ScheduleController에 넘어가고,
 * ScheduleController가 SeriesEditor의 알맞은 메서드를 고르는 데 쓴다.
 */
public enum EditScope {
    /** 이 일정만: 그 날짜 하나만 예외로 바뀜 */
    THIS_ONLY("이 일정만"),
    /** 앞으로 전부: 고른 날짜부터 뒤로 전부 바뀜 (그 전 날짜는 그대로) */
    THIS_AND_FOLLOWING("앞으로 전부");

    private final String label;

    EditScope(String label) {
        this.label = label;
    }

    /** 화면에 보여 줄 한국어 이름 ("이 일정만" 같은 글자). */
    public String label() {
        return label;
    }
}
