package proto.logic;

import proto.entity.Occurrence;

import java.util.List;
import java.util.Locale;

/**
 * [논리 application logic] 카테고리와 검색어로 하루치 일정을 걸러 낸다 (F7, F8).
 * 두 조건은 함께 적용(AND)되고, 검색어는 제목 또는 메모에서 대소문자 없이 찾는다.
 */
public final class ScheduleFilter {

    private ScheduleFilter() {
    }

    /**
     * @param categoryId null이면 모든 카테고리
     * @param keyword    null이나 공백뿐이면 검색 안 함 (앞뒤 공백은 무시)
     */
    public static boolean matches(Occurrence o, String categoryId, String keyword) {
        if (categoryId != null && !categoryId.equals(o.categoryId())) {
            return false;
        }
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String needle = keyword.strip().toLowerCase(Locale.ROOT);
        return o.title().toLowerCase(Locale.ROOT).contains(needle)
                || o.memo().toLowerCase(Locale.ROOT).contains(needle);
    }

    public static List<Occurrence> filter(List<Occurrence> all, String categoryId, String keyword) {
        return all.stream().filter(o -> matches(o, categoryId, keyword)).toList();
    }
}
