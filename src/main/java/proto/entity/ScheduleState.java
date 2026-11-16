package proto.entity;

/**
 * [엔티티 entity] 일정 상태 4가지. 저장하지 않고 StateCalculator가 매번 계산한다 (F6).
 * StateCalculator가 값을 정하고, 화면(상태 알약, 일정 줄)과 StatisticsCalculator(지연 수)가 읽는다.
 * 시간이 지나면 저절로 바뀌는 값이라, 저장해 두면 오히려 틀린 값이 남으므로 CSV에는 저장하지 않는다.
 */
public enum ScheduleState {
    SCHEDULED("예정"),
    IN_PROGRESS("진행 중"),
    DELAYED("지연"),
    DONE("완료");

    private final String label;

    ScheduleState(String label) {
        this.label = label;
    }

    /** 화면에 보여 줄 한국어 이름 */
    public String label() {
        return label;
    }
}
