package proto.ui.theme;

import proto.entity.Occurrence;

import java.awt.Color;

/**
 * [경계 boundary] 하루치 일정(Occurrence) 하나를 칠할 색을 돌려준다. 일정만의 색(colorKey)이 있으면 그 색, 없으면 카테고리 색이다.
 * 시간표 블록, 하루 종일 칩, 달력 칩, 목록의 색 사각형이 모두 이 클래스를 거쳐야 "이 일정만 다른 색"이 어디서나 똑같이 보인다.
 * 칠하는 규칙 (노션 캘린더와 같은 방식):
 * 시간이 있는 일정 = 연한 바탕(tint) + 왼쪽 진한 색 막대(bar) + 같은 계열의 진한 글자(tintText),
 * 하루 종일 일정과 고른(끌고 있는) 일정 = 진한 바탕(of) + 대비가 맞는 글자(onSolid).
 */
public final class EventColors {

    /** 밝은 테마에서 연한 바탕을 만들 때 일정 색을 섞는 비율 */
    private static final float TINT_LIGHT = 0.16f;
    /** 어두운 테마에서 연한 바탕을 만들 때 일정 색을 섞는 비율 */
    private static final float TINT_DARK = 0.26f;

    private EventColors() {
    }

    /** 이 일정에 쓸 색 키: 일정만의 색이 있으면 그것, 없으면 카테고리의 색 키 */
    public static String keyOf(Occurrence o) {
        return o.colorKey() != null ? o.colorKey() : CategoryColors.keyOf(o.categoryId());
    }

    /** 진한 색 (하루 종일 바탕, 고른 일정 바탕, 색 사각형) */
    public static Color of(Occurrence o) {
        return CategoryColors.ofKey(keyOf(o));
    }

    /** 시간이 있는 일정의 왼쪽 색 막대 */
    public static Color bar(Occurrence o) {
        return of(o);
    }

    /** 시간이 있는 일정의 연한 바탕 */
    public static Color tint(Occurrence o) {
        return tintOfKey(keyOf(o));
    }

    /** 연한 바탕 위의 글자색 */
    public static Color tintText(Occurrence o) {
        return tintTextOfKey(keyOf(o));
    }

    /** 진한 바탕 위의 글자색 */
    public static Color onSolid(Occurrence o) {
        return CategoryColors.onColor(of(o));
    }

    /** 색 키 하나의 연한 바탕: 일정 색을 배경에 조금 섞은 불투명한 색 */
    public static Color tintOfKey(String key) {
        float t = ThemeManager.isDark() ? TINT_DARK : TINT_LIGHT;
        return Tokens.blend(Tokens.get(Tokens.BG), CategoryColors.ofKey(key), t);
    }

    /** 색 키 하나의 연한 바탕 위 글자색: 밝은 테마는 색을 어둡게, 어두운 테마는 밝게 한다 */
    public static Color tintTextOfKey(String key) {
        Color c = CategoryColors.ofKey(key);
        return ThemeManager.isDark() ? Tokens.blend(c, Color.WHITE, 0.45f) : Tokens.blend(c, Color.BLACK, 0.38f);
    }
}
