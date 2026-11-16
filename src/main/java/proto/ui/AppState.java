package proto.ui;

import java.time.LocalDate;

/**
 * [경계 boundary] 화면이 지금 어떤 보기·날짜·필터를 보고 있는지 담은 화면 전용 상태 (저장하지 않는다).
 * 화면 이동(MainFrame)과 각 보기(TodayView, WeekView 등)가 함께 읽고 쓰며, UiContext가 하나만 만들어 모두에게 나눠 준다.
 * 일정 데이터가 아니라 "지금 무엇을 보고 있나"만 담으므로 앱을 끄면 사라지고, controller나 저장소는 이 값을 모른다 (F2 조회의 기준 날짜, F7/F8의 필터).
 */
public final class AppState {

    /** 메인 보기 4가지. 설정은 보기가 아니라 지금 보기 위에 뜨는 시트(SettingsSheet)이다 */
    public enum Mode { TODAY, WEEK, MONTH, STATS }

    private Mode mode = Mode.TODAY;
    private LocalDate anchor;
    private LocalDate selectedDay;
    private String categoryId;
    private String keyword = "";

    public AppState(LocalDate today) {
        this.anchor = today;
        this.selectedDay = today;
    }

    public Mode mode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    /** 주·월·통계 보기의 기준 날짜 (그 날이 들어 있는 주/월을 보여 준다) */
    public LocalDate anchor() {
        return anchor;
    }

    public void setAnchor(LocalDate anchor) {
        this.anchor = anchor;
    }

    /** 달력에서 고른 날짜 (F2) */
    public LocalDate selectedDay() {
        return selectedDay;
    }

    public void setSelectedDay(LocalDate day) {
        this.selectedDay = day;
    }

    /** 고른 카테고리 id. 전체면 null */
    public String categoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    /** 검색어. 없으면 빈 문자열 */
    public String keyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword == null ? "" : keyword.strip();
    }

    /** 검색어나 카테고리 필터가 걸려 있는지 */
    public boolean isFiltered() {
        return categoryId != null || !keyword.isEmpty();
    }

    /**
     * 새 일정(N, C, 새 일정 버튼)을 처음 놓을 날짜. 지금 화면에 보이는 날이어야 하므로: 달력은 고른 날, 이번 주(주 보기)는 고른 날이 그 주 안에 있으면 그 날
     * (아니면 오늘이 그 주 안이면 오늘, 아니면 그 주의 월요일), 오늘 보기는 오늘, 통계는 오늘이 그 주 안이면 오늘, 아니면 그 주의 월요일이다.
     */
    public LocalDate newScheduleDay(LocalDate today) {
        LocalDate start = weekStart();
        LocalDate end = start.plusDays(6);
        boolean todayInWeek = !today.isBefore(start) && !today.isAfter(end);
        return switch (mode) {
            case MONTH -> selectedDay;
            case TODAY -> today;
            case WEEK -> !selectedDay.isBefore(start) && !selectedDay.isAfter(end) ? selectedDay
                    : todayInWeek ? today : start;
            case STATS -> todayInWeek ? today : start;
        };
    }

    /** anchor가 들어 있는 주의 월요일 */
    public LocalDate weekStart() {
        return anchor.minusDays(anchor.getDayOfWeek().getValue() - 1L);
    }
}
