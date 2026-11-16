package proto.server;

import proto.entity.Category;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * [저장소 repository] 카테고리 목록과 JSON 스냅샷을 서로 바꾼다. 서버 대비 코드(현재 사용 안 함).
 * 루트는 {"categories":[{"id","name","color"}, ...]}이다. SyncingCategoryStore가 서버와 주고받을 때만 쓴다.
 * CategoryRepository의 CSV 규칙(이름은 앞뒤 공백을 뗀 1~20자, 대소문자 무시 중복 금지)을 서버 데이터에도 똑같이 적용해서,
 * 규칙을 어긴 카테고리가 로컬로 들어오지 않게 한다.
 * color는 나중에 더한 선택 필드다. 없거나 null이거나 모르는 키면 그 id의 기본 색으로 읽고, 문자열이 아니면 거절한다.
 * 보낼 때는 언제나 넣는다 (예전 서버·클라이언트는 모르는 필드를 무시한다).
 */
public final class CategoryJsonMapper {

    private CategoryJsonMapper() {
    }

    /** 카테고리 목록 전체를 JSON 문자열 하나로 만든다 */
    public static String writeSnapshot(List<Category> categories) {
        List<Object> items = new ArrayList<>();
        for (Category category : categories) {
            items.add(toMap(category));
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("categories", items);
        return JsonCodec.write(root);
    }

    /**
     * JSON 문자열을 카테고리 목록으로 읽는다. 형식이 깨졌거나 id가 비었거나 이름이 규칙에 맞지 않거나 id·이름이 겹치면 예외를 던진다.
     *
     * @throws JsonSyntaxException JSON 글자 구조가 깨졌을 때
     * @throws IllegalArgumentException 값이 규칙에 맞지 않을 때
     */
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
        map.put("color", category.getColorKey());
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
        if (name.isEmpty() || name.length() > Category.MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("이름은 1~" + Category.MAX_NAME_LENGTH + "자여야 해요");
        }
        return new Category(id, name, optionalText(map, "color"));
    }

    /** 없거나 JSON null이면 null, 문자열이면 그 값, 그 밖의 모양이면 예외 */
    private static String optionalText(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null || value instanceof String) {
            return (String) value;
        }
        throw new IllegalArgumentException(key + " 값이 문자열이 아니에요");
    }

    private static String text(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(key + " 값이 문자열이 아니에요");
        }
        return text;
    }
}
