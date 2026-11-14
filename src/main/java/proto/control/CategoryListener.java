package proto.control;

/**
 * [제어 control] 카테고리 목록이 바뀌었음을 화면에 알려 주는 리스너 (F7). EDT에서 호출된다.
 * F7(카테고리 관리)에서 쓴다. CategoryController가 알리고, 카테고리 목록을 보여 주는 CategoryDialog가 구현한다.
 */
public interface CategoryListener {

    /** 카테고리가 추가/이름 변경/삭제됨 */
    void categoriesChanged();
}
