package proto.ui.theme;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import proto.entity.CategoryPalette;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 12색 팔레트가 두 테마에서 점·연한 바탕(+글자)·진한 바탕(+글자) 어느 쪽으로 써도 읽히는지 */
class CategoryPaletteContrastTest {

    private static boolean darkBefore;

    @BeforeAll
    static void install() {
        ThemeManager.install();
        darkBefore = ThemeManager.isDark();
        CategoryColors.setResolver(id -> id);
    }

    @AfterAll
    static void restore() {
        CategoryColors.setResolver(null);
        ThemeManager.apply(darkBefore, false, false);
    }

    @Test
    void 밝은_테마() {
        ThemeManager.apply(false, false, false);
        check(false);
    }

    @Test
    void 어두운_테마() {
        ThemeManager.apply(true, false, false);
        check(true);
    }

    private static void check(boolean dark) {
        Color bg = Tokens.get(Tokens.BG);
        for (String key : CategoryPalette.KEYS) {
            String where = key + (dark ? " (dark)" : " (light)");
            Color base = CategoryColors.of(key);
            assertTrue(Contrast.ratio(CategoryColors.tintText(key), CategoryColors.tint(key)) >= 4.5,
                    "연한 바탕 글자 " + where);
            assertTrue(Contrast.ratio(CategoryColors.onSolid(key), base) >= 4.5, "진한 바탕 글자 " + where);
            assertTrue(Contrast.ratio(base, bg) >= 3.0, "점 " + where);
        }
    }
}
