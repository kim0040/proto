package proto.ui.view;

import javax.swing.JComponent;

/**
 * [경계 boundary] 메인 창 가운데에 들어가는 보기(오늘·이번 주·달력·주간 통계)가 지켜야 할 약속.
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

    /** 위쪽 줄에 검색창을 보여 줄지 (설정 보기는 숨긴다) */
    default boolean hasSearch() {
        return true;
    }

    /** controller에서 다시 읽어 화면을 맞춘다. animate가 true면 바뀐 부분만 부드럽게 바뀐다 */
    void refresh(boolean animate);

    /** 이 보기가 화면에 새로 나타났을 때 한 번 불린다 (통계 막대 자라기 등) */
    default void shown() {
    }
}
