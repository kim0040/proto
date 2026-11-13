package proto.ui.component;

import proto.entity.Occurrence;
import proto.entity.ScheduleState;

import java.awt.Component;
import java.awt.Point;

/**
 * [경계 boundary] 일정 행·칩이 사용자 동작과 표시에 필요한 정보를 요청하는 통로.
 */
public interface RowHandler {

    /** 일정을 눌러 수정 창을 연다 */
    void open(Occurrence occurrence);

    /** 삭제 키를 눌렀다 (확인 후 삭제). 실제로 지웠으면 true */
    boolean delete(Occurrence occurrence);

    /** 완료 체크를 눌렀다 (F5) */
    void toggleDone(Occurrence occurrence);

    /** 오른쪽 클릭 메뉴를 연다 */
    void showMenu(Component source, Point point, Occurrence occurrence);

    /** 지금 시각 기준 상태 (F6) */
    ScheduleState stateOf(Occurrence occurrence);

    /** 카테고리 이름 */
    String categoryName(String categoryId);
}
