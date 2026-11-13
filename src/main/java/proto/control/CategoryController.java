package proto.control;

import proto.entity.Category;

import java.util.List;

/**
 * [제어 control] 카테고리 추가·이름 변경·삭제를 처리한다 (F7).
 *
 * <p>뼈대(API 고정용). 본문은 구현 단계에서 채운다. 생성자도 구현 단계에서 정한다.</p>
 */
public class CategoryController {

    public void addListener(CategoryListener listener) {
        throw todo();
    }

    public void removeListener(CategoryListener listener) {
        throw todo();
    }

    /** 카테고리 목록. 기본 4개(수업, 과제, 팀플, 개인)가 앞, 추가한 것은 추가한 순서대로 뒤 */
    public List<Category> getCategories() {
        throw todo();
    }

    /** id의 이름. 없는 id면 "개인" 이름 */
    public String nameOf(String categoryId) {
        throw todo();
    }

    /** 삭제할 수 있는지 ("개인"은 불가) */
    public boolean canDelete(String categoryId) {
        throw todo();
    }

    /** 이 카테고리에 속한 일정(Schedule) 수. 삭제 확인 문구에 쓴다 */
    public int countSchedules(String categoryId) {
        throw todo();
    }

    /** 추가. 이름은 앞뒤 공백 제거 후 1~20자, 중복 금지(대소문자 무시) */
    public OperationResult add(String name) {
        throw todo();
    }

    /** 이름 변경. 규칙은 add와 같음 (자기 이름 그대로는 허용) */
    public OperationResult rename(String categoryId, String newName) {
        throw todo();
    }

    /** 삭제. 남은 일정은 "개인"으로 옮기고 저장(F11), 두 리스너(카테고리, 일정)에 모두 알린다 */
    public OperationResult delete(String categoryId) {
        throw todo();
    }

    private static UnsupportedOperationException todo() {
        return new UnsupportedOperationException("아직 구현되지 않음");
    }
}
