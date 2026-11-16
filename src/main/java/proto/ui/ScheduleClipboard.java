package proto.ui;

import proto.entity.Occurrence;
import proto.ui.AppState.Mode;

import java.time.LocalDate;
import java.util.Optional;

/**
 * [경계 boundary] 앱 안의 복사 보관함: 복사한 하루치 일정 하나를 기억하고, 붙여 넣을 날짜를 보기에 맞춰 정한다 (저장하지 않는 화면 전용 상태).
 * UiActions가 복사(Ctrl/⌘+C)할 때 채우고 붙여넣기(Ctrl/⌘+V)할 때 읽는다. 붙여 넣어도 비우지 않아 같은 일정을 여러 번 붙일 수 있다.
 * 운영체제 클립보드는 쓰지 않는다.
 */
public final class ScheduleClipboard {

    private Occurrence copied;

    /** 복사해 둔 일정으로 바꾼다 */
    public void set(Occurrence occurrence) {
        this.copied = occurrence;
    }

    /** 복사해 둔 일정. 없으면 빈 값 */
    public Optional<Occurrence> content() {
        return Optional.ofNullable(copied);
    }

    /**
     * 붙여 넣을 날짜: 오늘 보기는 오늘, 주 보기는 고른 날(그 주 안에 있을 때만, 아니면 그 주 월요일), 달력 보기는 고른 날.
     * 통계 보기는 붙여 넣지 않으므로 빈 값이다.
     */
    public static Optional<LocalDate> pasteTarget(Mode mode, LocalDate today, LocalDate selectedDay,
                                                  LocalDate weekStart) {
        return switch (mode) {
            case TODAY -> Optional.of(today);
            case WEEK -> Optional.of(inWeek(selectedDay, weekStart) ? selectedDay : weekStart);
            case MONTH -> Optional.of(selectedDay);
            default -> Optional.empty();
        };
    }

    private static boolean inWeek(LocalDate day, LocalDate weekStart) {
        return !day.isBefore(weekStart) && !day.isAfter(weekStart.plusDays(6));
    }
}
