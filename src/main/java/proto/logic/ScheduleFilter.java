package proto.logic;

import proto.entity.Occurrence;

import java.util.List;
import java.util.Locale;

/**
 * [논리 application logic] 카테고리와 검색어로 하루치 일정을 걸러 낸다 (F7, F8).
 * F7(카테고리 필터), F8(검색)에서 쓴다. ScheduleController가 조회 결과를 이 조건으로 걸러서 화면에 돌려준다.
 * 두 조건은 함께 적용(AND)되고, 검색어는 제목 또는 메모에서 대소문자 없이 찾는다.
 */
public final class ScheduleFilter {

    private ScheduleFilter() {
    }

    /**
     * 하루치 일정 하나가 조건에 맞는지. 카테고리와 검색어 조건이 모두 맞아야 true이다.
     * 검색어는 앞뒤 공백을 떼고 소문자로 바꿔서 제목·메모와 비교한다 (영어 대소문자를 구별하지 않기 위해서).
     *
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

    /** 목록에서 조건에 맞는 것만 남긴 새 목록을 돌려준다. 원래 순서는 그대로이고, 맞는 것이 없으면 빈 목록이다. */
    public static List<Occurrence> filter(List<Occurrence> all, String categoryId, String keyword) {
        return all.stream().filter(o -> matches(o, categoryId, keyword)).toList();
    }
}
