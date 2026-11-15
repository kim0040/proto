package proto.entity;

import java.util.List;

/**
 * [엔티티 entity] 카테고리에 고를 수 있는 색 12개의 이름표(키)와, 색을 고른 적 없는 카테고리의 기본 색을 정한다 (F7).
 * Category가 색 키를 검사할 때, CategoryRepository·CategoryJsonMapper가 색 칸이 없거나 모르는 값일 때, CategoryController가
 * 새 카테고리에 안 쓴 색을 고를 때 쓴다. 키는 저장 파일에 그대로 들어가므로 이름과 순서를 바꾸면 안 된다.
 * 실제 색(RGB)은 화면(ui.theme)이 키마다 정한다. entity는 다른 패키지를 모르기 때문에 이 규칙을 logic이 아니라 여기에 둔다.
 * 기본 색은 예전 화면(CategoryColors)의 8색 규칙과 똑같다: 수업 blue, 과제 orange, 팀플 teal, 개인 green,
 * 나머지는 id의 FNV-1a 해시를 8로 나눈 나머지 번째 색. 그래서 색을 고르기 전의 사용자 화면 색이 바뀌지 않는다.
 */
public final class CategoryPalette {

    /** 고를 수 있는 색 키. 앞 8개가 예전 8색 팔레트(proto.cat.0~7)와 같은 순서이다 */
    public static final List<String> KEYS = List.of("blue", "orange", "teal", "rose", "green", "purple",
            "sky", "brown", "red", "yellow", "indigo", "gray");

    /** 예전 팔레트의 색 수. 기본 색은 이 안에서만 고른다 */
    private static final int LEGACY_SIZE = 8;

    private CategoryPalette() {
    }

    /** 팔레트에 있는 키인지 (null이면 false) */
    public static boolean isKnown(String key) {
        return key != null && KEYS.contains(key);
    }

    /** 키의 팔레트 순번 (0~11). 모르는 키나 null이면 -1 */
    public static int indexOf(String key) {
        return key == null ? -1 : KEYS.indexOf(key);
    }

    /** 키가 팔레트에 있으면 그대로, 아니면(null 포함) 그 카테고리의 기본 색 키 */
    public static String normalize(String key, String categoryId) {
        return isKnown(key) ? key : legacyDefault(categoryId);
    }

    /**
     * 색을 고른 적 없는 카테고리의 기본 색 키. 기본 카테고리는 정해진 색, 나머지는 id 해시로 앞 8색 중 하나.
     * id가 null이면 예전 화면처럼 8번째 색(brown)이다.
     */
    public static String legacyDefault(String categoryId) {
        if (categoryId == null) {
            return KEYS.get(LEGACY_SIZE - 1);
        }
        int index = switch (categoryId) {
            case "class" -> 0;
            case "assignment" -> 1;
            case "team" -> 2;
            case Category.PERSONAL_ID -> 4;
            default -> Math.floorMod(stableHash(categoryId), LEGACY_SIZE);
        };
        return KEYS.get(index);
    }

    /** 같은 id는 언제나 같은 값이 나오는 FNV-1a 해시. 마지막에 위쪽 비트를 아래로 섞는다 (예전 CategoryColors와 같은 식). */
    private static int stableHash(String s) {
        int h = 0x811C9DC5;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x01000193;
        }
        return h ^ (h >>> 15);
    }
}
