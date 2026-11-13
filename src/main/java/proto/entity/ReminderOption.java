package proto.entity;

/**
 * [엔티티 entity] 알림 시점 (F10): 없음 / 5분 / 10분 / 30분 전.
 */
public enum ReminderOption {
    NONE(0, "알림 없음"),
    FIVE_MINUTES(5, "5분 전"),
    TEN_MINUTES(10, "10분 전"),
    THIRTY_MINUTES(30, "30분 전");

    private final int minutesBefore;
    private final String label;

    ReminderOption(int minutesBefore, String label) {
        this.minutesBefore = minutesBefore;
        this.label = label;
    }

    /** 시작 몇 분 전에 알릴지. NONE이면 0 */
    public int minutesBefore() {
        return minutesBefore;
    }

    public String label() {
        return label;
    }
}
