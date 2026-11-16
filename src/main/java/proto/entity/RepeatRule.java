package proto.entity;

/**
 * [엔티티 entity] 반복 규칙 (F9). 매주는 시작 날짜의 요일, 매월은 시작 날짜의 일(day) 기준.
 * 규칙을 실제 날짜로 펼치는 일은 RepeatExpander가 하고, 이 enum은 "종류"만 나타낸다. CSV에는 이름(DAILY 등)으로 저장된다.
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

    /** 화면에 보여 줄 한국어 이름 */
    public String label() {
        return label;
    }
}
