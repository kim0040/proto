package proto.entity;

import java.util.Objects;

/**
 * [엔티티 entity] 일정 카테고리 (F7). id는 바뀌지 않고 이름과 색만 바뀐다.
 * 일정은 카테고리의 id만 들고 있고(Schedule#categoryId), 이름은 여기서 찾는다.
 * CategoryController가 만들고 고치며 CategoryRepository가 CSV로 저장한다.
 * "개인" 카테고리는 id가 PERSONAL_ID로 고정이며 삭제할 수 없다.
 * 색은 CategoryPalette의 키 하나(blue, orange …)로 들고, 비어 있거나 모르는 키는 그 id의 기본 색으로 바꿔 넣는다.
 * 그래서 getColorKey()는 언제나 팔레트에 있는 키를 돌려준다. 실제 색(RGB)은 화면이 정한다.
 */
public class Category {

    /** 삭제할 수 없는 기본 카테고리 "개인"의 고정 id */
    public static final String PERSONAL_ID = "personal";

    /** 카테고리 이름의 최대 길이 (앞뒤 공백 제외) */
    public static final int MAX_NAME_LENGTH = 20;

    private final String id;
    private String name;
    private String colorKey;

    /**
     * id와 이름으로 카테고리를 만든다. 둘 중 하나라도 null이면 NullPointerException이다. 색은 그 id의 기본 색이다.
     * (이름이 비었는지, 길이가 20자 이내인지는 여기서 보지 않고 CategoryController가 검사한다.)
     */
    public Category(String id, String name) {
        this(id, name, null);
    }

    /**
     * id·이름·색 키로 카테고리를 만든다. 색 키가 null이거나 팔레트에 없으면 그 id의 기본 색으로 바꿔 넣는다.
     * id나 이름이 null이면 NullPointerException이다.
     */
    public Category(String id, String name, String colorKey) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.colorKey = CategoryPalette.normalize(colorKey, id);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /** 이름만 바꾼다. id는 final이라 바뀌지 않는다. null이면 NullPointerException이다. */
    public void setName(String name) {
        this.name = Objects.requireNonNull(name);
    }

    /** 색 키 (CategoryPalette.KEYS 중 하나, null 아님) */
    public String getColorKey() {
        return colorKey;
    }

    /** 색을 바꾼다. null이거나 팔레트에 없는 키면 그 id의 기본 색으로 바꾼다 (거절하는 일은 CategoryController가 한다). */
    public void setColorKey(String colorKey) {
        this.colorKey = CategoryPalette.normalize(colorKey, id);
    }

    /** "개인" 카테고리인지 (삭제 불가) */
    public boolean isPersonal() {
        return PERSONAL_ID.equals(id);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Category c && c.id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
