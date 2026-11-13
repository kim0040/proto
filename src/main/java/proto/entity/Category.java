package proto.entity;

import java.util.Objects;

/**
 * [엔티티 entity] 일정 카테고리 (F7). id는 바뀌지 않고 이름만 바뀐다.
 * "개인" 카테고리는 id가 PERSONAL_ID로 고정이며 삭제할 수 없다.
 */
public class Category {

    /** 삭제할 수 없는 기본 카테고리 "개인"의 고정 id */
    public static final String PERSONAL_ID = "personal";

    private final String id;
    private String name;

    public Category(String id, String name) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name);
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
