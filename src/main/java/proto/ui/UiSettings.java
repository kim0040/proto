package proto.ui;

import proto.ui.AppState.Mode;

import java.time.LocalTime;
import java.util.prefs.Preferences;

/**
 * [경계 boundary] 설정 화면에서 바꾸는 작은 환경설정(시작 화면, 알림 소리, 빈 시간 기본 시간대)을 기억한다.
 * 테마는 ThemeManager, 애니메이션은 Motion이 각각 저장한다.
 */
public final class UiSettings {

    private static final Preferences PREFS = Preferences.userNodeForPackage(UiSettings.class);
    private static final String START = "startMode";
    private static final String SOUND = "reminderSound";
    private static final String FREE_FROM = "freeFrom";
    private static final String FREE_TO = "freeTo";

    private UiSettings() {
    }

    /** 앱을 열 때 처음 보여 줄 화면 (오늘, 이번 주, 달력 중 하나) */
    public static Mode startMode() {
        try {
            Mode m = Mode.valueOf(PREFS.get(START, Mode.TODAY.name()));
            return m == Mode.TODAY || m == Mode.WEEK || m == Mode.MONTH ? m : Mode.TODAY;
        } catch (IllegalArgumentException e) {
            return Mode.TODAY;
        }
    }

    public static void setStartMode(Mode mode) {
        PREFS.put(START, mode.name());
    }

    /** 알림이 뜰 때 소리를 낼지 */
    public static boolean reminderSound() {
        return PREFS.getBoolean(SOUND, true);
    }

    public static void setReminderSound(boolean on) {
        PREFS.putBoolean(SOUND, on);
    }

    /** 빈 시간 찾기에서 기본으로 찾는 시작 시각 (기본 09:00) */
    public static LocalTime freeFrom() {
        return read(FREE_FROM, LocalTime.of(9, 0));
    }

    /** 빈 시간 찾기에서 기본으로 찾는 끝 시각 (기본 22:00) */
    public static LocalTime freeTo() {
        return read(FREE_TO, LocalTime.of(22, 0));
    }

    /** 기본 시간대를 저장한다. 시작이 끝보다 앞서야 한다 */
    public static void setFreeRange(LocalTime from, LocalTime to) {
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("시작은 끝보다 앞서야 해요");
        }
        PREFS.put(FREE_FROM, from.toString());
        PREFS.put(FREE_TO, to.toString());
    }

    private static LocalTime read(String key, LocalTime fallback) {
        try {
            return LocalTime.parse(PREFS.get(key, fallback.toString()));
        } catch (java.time.DateTimeException e) {
            return fallback;
        }
    }
}
