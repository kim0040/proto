package proto.ui.view;

import proto.ui.layout.SizeClass;

import javax.swing.JComponent;

/**
 * [경계 boundary] 메인 창 가운데에 들어가는 보기(오늘 TodayView, 이번 주 WeekView, 달력 MonthView, 주간 통계 StatsView)가 지켜야 할 약속.
 * MainFrame이 보기를 모두 이 모양으로만 다루므로, 보기를 하나 더해도 MainFrame의 나머지 코드는 바꾸지 않는다.
 */
public interface ContentView {

    /** 화면에 붙일 컴포넌트 */
    JComponent component();

    /** 상단에 보여 줄 제목 */
    String title();

    /** 제목 아래 작은 글씨 (날짜 등) */
    String subtitle();

    /** 이전/다음/오늘 버튼이 필요한 보기인지 */
    boolean hasNavigator();

    /** 검색·카테고리 필터가 적용되는 보기인지 */
    boolean usesFilters();

    /** 위쪽 줄에 검색창을 보여 줄지 (기본은 보여 준다) */
    default boolean hasSearch() {
        return true;
    }

    /** 화면 크기 등급이 바뀌었을 때 배치(구조)를 맞춘다. 내용은 이어서 부르는 refresh가 다시 채운다 */
    default void applySize(SizeClass sizeClass) {
    }

    /** controller에서 다시 읽어 화면을 맞춘다. animate가 true면 바뀐 부분만 부드럽게 바뀐다 */
    void refresh(boolean animate);

    /** 이 보기가 화면에 새로 나타났을 때 한 번 불린다 (통계 막대 자라기 등) */
    default void shown() {
    }
}
