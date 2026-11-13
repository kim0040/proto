package proto.entity;

/**
 * [엔티티 entity] 일정 상태 4가지. 저장하지 않고 StateCalculator가 매번 계산한다 (F6).
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
