package proto.ui.theme;

import proto.entity.CategoryPalette;

import java.awt.Color;
import java.util.function.Function;

/**
 * [경계 boundary] 카테고리 id의 색과, 그 색으로 칠할 때 쓰는 바탕·글자색을 돌려준다 (12색 팔레트, 노션 캘린더처럼 차분한 색).
 * CategoryDot, 시간표 블록, 달력 칩, 사이드바 점, 통계 막대가 같은 색을 쓰도록 이 클래스를 거친다 (F7). 색 자체는 테마 파일의 "proto.cat.<키>" 토큰이다.
 * 카테고리마다 고른 색 키는 CategoryController#colorOf가 알고 있다. 화면이 뜰 때 UiLauncher가 그 함수를 setResolver로 넘겨 준다.
 * 하루치 일정을 칠할 때는 일정만의 색까지 따지는 EventColors를 쓴다. 연한 바탕·글자색 계산도 EventColors에 있다.
 */
public final class CategoryColors {

    /** 진한 바탕 위의 어두운 글자와 그 상대 휘도 (#1F1F1F) */
    private static final Color DARK_TEXT = new Color(0x1F, 0x1F, 0x1F);
    private static final double DARK_TEXT_LUMINANCE = 0.0137;

    private static Function<String, String> resolver = CategoryPalette::legacyDefault;

    private CategoryColors() {
    }

    /** 카테고리 id → 색 키를 알려 줄 함수를 정한다 (보통 CategoryController::colorOf). null이면 예전 기본색 규칙으로 돌아간다. */
    public static void setResolver(Function<String, String> keyOf) {
        resolver = keyOf == null ? CategoryPalette::legacyDefault : keyOf;
    }

    /** 카테고리의 색 키 ("blue" 등) */
    public static String keyOf(String categoryId) {
        String key = categoryId == null ? "gray" : resolver.apply(categoryId);
        return CategoryPalette.isKnown(key) ? key : CategoryPalette.legacyDefault(categoryId);
    }

    /** 팔레트 번호 (0~11). 통계처럼 순서가 필요한 곳에서 쓴다 */
    public static int indexOf(String categoryId) {
        return CategoryPalette.indexOf(keyOf(categoryId));
    }

    /** 지금 테마(밝음/어두움)에 맞는 카테고리 색 (점, 진한 바탕) */
    public static Color of(String categoryId) {
        return ofKey(keyOf(categoryId));
    }

    /** 색 키 하나의 지금 테마 색 (색 고르기 견본에 쓴다) */
    public static Color ofKey(String key) {
        return Tokens.get("proto.cat." + key);
    }

    /** 시간이 있는 일정의 연한 바탕: 카테고리 색을 배경에 조금 섞은 불투명한 색 */
    public static Color tint(String categoryId) {
        return EventColors.tintOfKey(keyOf(categoryId));
    }

    /** 연한 바탕 위의 글자색: 밝은 테마는 카테고리 색을 어둡게, 어두운 테마는 밝게 한다 */
    public static Color tintText(String categoryId) {
        return EventColors.tintTextOfKey(keyOf(categoryId));
    }

    /** 진한 바탕(하루 종일, 고른 일정) 위의 글자색: 바탕 밝기에 따라 흰색 또는 거의 검정 */
    public static Color onSolid(String categoryId) {
        return onColor(of(categoryId));
    }

    /** 아무 바탕색 위에서 읽히는 글자색 (WCAG 상대 휘도로 흰색/어두운 글자(#1F1F1F) 중 실제 대비가 큰 쪽) */
    public static Color onColor(Color bg) {
        double lum = luminance(bg);
        double withWhite = 1.05 / (lum + 0.05);
        double withDark = (lum + 0.05) / (DARK_TEXT_LUMINANCE + 0.05);
        return withWhite >= withDark ? Color.WHITE : DARK_TEXT;
    }

    private static double luminance(Color c) {
        return 0.2126 * channel(c.getRed()) + 0.7152 * channel(c.getGreen()) + 0.0722 * channel(c.getBlue());
    }

    private static double channel(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }
}
