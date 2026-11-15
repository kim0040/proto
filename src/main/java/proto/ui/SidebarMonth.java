package proto.ui;

import proto.entity.Occurrence;
import proto.ui.component.MiniCalendar;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * [경계 boundary] 사이드바 위쪽의 작은 월 달력: 오늘은 진한 원, 고른 날은 옅은 원, 보고 있는 주는 옅은 띠, 일정이 있는 날은 숫자 아래 작은 점이다.
 * Sidebar가 넣고 MainFrame이 화면이 바뀔 때마다 sync로, 일정이 바뀔 때마다 refreshMarks로 맞춘다. 날짜를 누르면 onDate로 그 날짜로 이동하라고 알린다 (F2 조회 이동).
 * 점은 달을 넘길 때와 일정이 바뀔 때만 ScheduleController#getOccurrences로 보이는 6주를 한 번 읽어 만든다 (읽기만 한다).
 * 어느 달을 보여 줄지: 고른 날이 속한 달이 바뀔 때만 따라가고, 그 사이에 사용자가 화살표로 넘겨 둔 달은 그대로 둔다.
 */
final class SidebarMonth extends JPanel {

    private final UiContext ctx;
    private final MiniCalendar calendar;
    private YearMonth followed;

    SidebarMonth(UiContext ctx, Consumer<LocalDate> onDate) {
        super(new BorderLayout());
        this.ctx = ctx;
        setOpaque(false);
        LocalDate today = ctx.schedules().today();
        this.calendar = MiniCalendar.forSidebar(today, onDate, month -> refreshMarks());
        add(calendar, BorderLayout.CENTER);
        sync();
    }

    /** 지금 보기·날짜에 맞게 오늘·고른 날·주 띠를 바꾸고, 고른 날의 달이 바뀌었으면 그 달을 보여 준다 */
    void sync() {
        AppState s = ctx.state();
        LocalDate today = ctx.schedules().today();
        LocalDate selected = switch (s.mode()) {
            case TODAY -> today;
            case STATS -> s.anchor();
            default -> s.selectedDay();
        };
        calendar.setToday(today);
        calendar.setSelected(selected);
        boolean weekly = s.mode() == AppState.Mode.WEEK || s.mode() == AppState.Mode.STATS;
        calendar.setRange(weekly ? s.weekStart() : null, weekly ? s.weekStart().plusDays(6) : null);
        YearMonth month = YearMonth.from(selected);
        if (!month.equals(followed)) {
            followed = month;
            calendar.showMonth(selected);
            refreshMarks();
        }
    }

    /** 보이는 6주 안에서 일정이 있는 날을 다시 읽는다 (일정·카테고리가 바뀌었거나 달을 넘겼을 때) */
    void refreshMarks() {
        LocalDate first = calendar.shownMonth().withDayOfMonth(1);
        LocalDate from = first.minusDays(first.getDayOfWeek().getValue() - 1L);
        List<Occurrence> found = ctx.schedules().getOccurrences(from, from.plusDays(41), null, null);
        Set<LocalDate> days = new HashSet<>();
        found.forEach(o -> days.add(o.date()));
        calendar.setMarked(days);
    }

    /** 화면 캡처·테스트가 보는 달력 */
    MiniCalendar calendar() {
        return calendar;
    }
}
