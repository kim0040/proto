package proto.repository;

import proto.entity.Category;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * [저장소 repository] 카테고리 목록을 읽고 고치고 저장하는 통로.
 * 오늘은 {@link CategoryRepository}(CSV)가 이 역할을 하고, controller와 Main은 구현 클래스가 아니라 이 인터페이스만 본다.
 */
public interface CategoryStore {

    /** 저장된 카테고리가 있으면 읽고, 없으면 기본 4개(수업, 과제, 팀플, 개인)로 시작해 바로 저장한다 */
    void loadOrSeed();

    /** 메모리에 있는 카테고리 전체를 저장한다 */
    void save() throws IOException;

    /** 불러오거나 저장할 때 생긴 경고 (한국어 문장). 없으면 빈 목록 */
    List<String> getWarnings();

    /** 메모리에 있는 카테고리. 고칠 수 없는 목록 */
    List<Category> getAll();

    Optional<Category> findById(String id);

    void add(Category category);

    void remove(Category category);
}
