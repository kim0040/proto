package proto.entity;

/**
 * [엔티티 entity] 알림 시점 (F10): 없음 / 5분 / 10분 / 30분 전.
 * 일정 등록 화면의 "알림" 선택값이고(ScheduleDraft) Schedule에 저장된다. CSV에는 이름(NONE 등)으로 저장된다.
 * ReminderChecker가 minutesBefore로 알릴 시각을 계산한다.
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

    /** 시작 몇 분 전에 알릴지. NONE이면 0이며, "알림을 안 쓴다"는 판단은 ReminderChecker가 NONE인지로 따로 한다 */
    public int minutesBefore() {
        return minutesBefore;
    }

    /** 화면에 보여 줄 한국어 이름 */
    public String label() {
        return label;
    }
}
