package proto.control;

/**
 * [제어 control] 카테고리 목록이 바뀌었음을 화면에 알려 주는 리스너 (F7). EDT에서 호출된다.
 */
public interface CategoryListener {

    /** 카테고리가 추가/이름 변경/삭제됨 */
    void categoriesChanged();
}
