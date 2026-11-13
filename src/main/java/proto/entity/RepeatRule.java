package proto.entity;

/**
 * [엔티티 entity] 반복 규칙 (F9). 매주는 시작 날짜의 요일, 매월은 시작 날짜의 일(day) 기준.
 */
public enum RepeatRule {
    NONE("반복 안 함"),
    DAILY("매일"),
    WEEKLY("매주"),
    MONTHLY("매월");

    private final String label;

    RepeatRule(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
