package proto.ui.command;

import proto.entity.Category;
import proto.entity.Occurrence;
import proto.ui.AppState.Mode;
import proto.ui.theme.ThemeManager.Choice;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * [경계 boundary] 명령 메뉴가 화면에 부탁할 수 있는 일과 물어볼 수 있는 것의 목록. 메뉴 항목은 이 약속만 알고 창(MainFrame)의 속은 모른다.
 * MainFrame이 FrameCommandHost로 구현해서 CommandCatalog에 넘겨 준다. 테스트에서는 가짜로 바꿔 끼워 항목이 맞는 일을 부르는지 확인한다.
 * 일정 데이터는 여기서 바꾸지 않는다: 새 일정·되돌리기도 UiActions를 거쳐 controller가 한다.
 */
public interface CommandHost {

    /** 지금 날짜 (controller의 시계) */
    LocalDate today();

    /** 입력한 글을 날짜로 읽는다 (controller에 요청한다. 날짜로 읽히지 않으면 빈 값) */
    Optional<LocalDate> resolveDate(String text);

    /** 보기 이동 (오늘·이번 주·달력·주간 통계) */
    void showMode(Mode mode);

    /** 지금 보기에서 오늘로 */
    void goToday();

    /** 지금 보기에서 그 날짜로 (오늘 보기라면 이번 주 보기로 바꿔서) */
    void goToDate(LocalDate date);

    /** 새 일정 창. title이 비어 있지 않으면 제목을 미리 채운다 */
    void newSchedule(String title);

    /** 카테고리 보기 전환 (null이면 전체) */
    void filterCategory(String categoryId);

    List<Category> categories();

    /** 지금 보고 있는 카테고리 id (전체면 null) */
    String currentCategory();

    void openFreeSlots();

    /** 테마 방식 바꾸기 */
    void setTheme(Choice choice);

    /** 지금 고른 테마 방식 */
    Choice theme();

    void openSettings();

    void showShortcuts();

    /** 사이드바를 숨기거나 다시 보인다 (테스트용 가짜 호스트는 아무것도 하지 않아도 된다) */
    default void toggleSidebar() {
    }

    /** 지금 사이드바를 숨겨 두었는지 (명령 이름을 "숨기기"/"보이기"로 고르려고) */
    default boolean sidebarHidden() {
        return false;
    }

    boolean canUndo();

    boolean canRedo();

    /** 되돌릴 일의 이름 (없으면 빈 글) */
    String undoLabel();

    String redoLabel();

    void undo();

    void redo();

    /** 명령 메뉴를 열기 직전에 포커스가 놓여 있던 일정 (없으면 빈 값). "선택한 일정 색 바꾸기"가 쓴다 */
    default Optional<Occurrence> targetSchedule() {
        return Optional.empty();
    }

    /** 일정의 색을 바꾼다 (null이면 카테고리 색을 따른다) */
    default void changeColor(Occurrence target, String colorKey) {
    }
}
