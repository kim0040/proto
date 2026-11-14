package proto.ui.theme;

import java.awt.Color;

/**
 * [경계 boundary] 카테고리 id마다 고정된 색을 돌려준다 (8색 팔레트, id 해시로 안정적으로 배정).
 * CategoryDot, WeekChip, 카테고리 콤보, 사이드바 점이 같은 색을 쓰도록 이 클래스를 거친다 (F7). 색 자체는 테마 파일의 "proto.cat.N" 토큰이다.
 */
public final class CategoryColors {

    private static final int PALETTE = 8;

    private CategoryColors() {
    }

    /** 팔레트 번호. 기본 카테고리는 정해진 색, 나머지는 id 해시 */
    public static int indexOf(String categoryId) {
        if (categoryId == null) {
            return 7;
        }
        return switch (categoryId) {
            case "class" -> 0;
            case "assignment" -> 1;
            case "team" -> 2;
            case "personal" -> 4;
            default -> Math.floorMod(stableHash(categoryId), PALETTE);
        };
    }

    /** 지금 테마(밝음/어두움)에 맞는 카테고리 색 */
    public static Color of(String categoryId) {
        return Tokens.get("proto.cat." + indexOf(categoryId));
    }

    /** 같은 id는 언제나 같은 색이 나오도록 값이 고정된 FNV-1a 해시를 쓴다. 마지막에 위쪽 비트를 아래로 섞는다(h ^ (h >>> 15)). */
    private static int stableHash(String s) {
        int h = 0x811C9DC5;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x01000193;
        }
        return h ^ (h >>> 15);
    }
}
