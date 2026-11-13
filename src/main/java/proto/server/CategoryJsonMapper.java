package proto.server;

import proto.entity.Category;
import proto.repository.CategoryRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * [서버 대비 — 현재 사용 안 함] 카테고리 목록과 JSON 스냅샷을 서로 바꾼다.
 * [저장소 repository] 루트는 {"categories":[{"id","name"}, ...]}이다. 이름은 앞뒤 공백을 떼고 1~20자다.
 */
public final class CategoryJsonMapper {

    private CategoryJsonMapper() {
    }

    public static String writeSnapshot(List<Category> categories) {
        List<Object> items = new ArrayList<>();
        for (Category category : categories) {
            items.add(toMap(category));
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("categories", items);
        return JsonCodec.write(root);
    }

    public static List<Category> readSnapshot(String json) {
        Map<String, Object> root = JsonValues.asMap(JsonCodec.parse(json));
        List<Category> categories = new ArrayList<>();
        for (Object item : JsonValues.asList(root.get("categories"))) {
            addUnique(categories, fromMap(JsonValues.asMap(item)));
        }
        return categories;
    }

    private static Map<String, Object> toMap(Category category) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", category.getId());
        map.put("name", category.getName());
        return map;
    }

    private static void addUnique(List<Category> categories, Category category) {
        if (categories.stream().anyMatch(other -> other.getId().equals(category.getId()))) {
            throw new IllegalArgumentException("id가 겹쳐요");
        }
        String name = category.getName().toLowerCase(Locale.ROOT);
        boolean sameName = categories.stream().anyMatch(other -> other.getName().toLowerCase(Locale.ROOT).equals(name));
        if (sameName) {
            throw new IllegalArgumentException("이미 있는 이름이에요");
        }
        categories.add(category);
    }

    private static Category fromMap(Map<String, Object> map) {
        String id = text(map, "id");
        String name = text(map, "name").trim();
        if (id.isBlank()) {
            throw new IllegalArgumentException("id 값이 비어 있어요");
        }
        if (name.isEmpty() || name.length() > CategoryRepository.MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("이름은 1~" + CategoryRepository.MAX_NAME_LENGTH + "자여야 해요");
        }
        return new Category(id, name);
    }

    private static String text(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(key + " 값이 문자열이 아니에요");
        }
        return text;
    }
}
