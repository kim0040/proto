package proto.entity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoryPaletteTest {

    @Test
    void paletteHasTwelveKeysInAStableOrder() {
        assertEquals(List.of("blue", "orange", "teal", "rose", "green", "purple", "sky", "brown",
                "red", "yellow", "indigo", "gray"), CategoryPalette.KEYS);
        assertEquals(4, CategoryPalette.indexOf("green"));
        assertEquals(-1, CategoryPalette.indexOf("pink"));
        assertEquals(-1, CategoryPalette.indexOf(null));
    }

    @Test
    void defaultsReproduceTheOldEightColourScreenRule() {
        // 예전 ui.theme.CategoryColors.indexOf: class 0, assignment 1, team 2, personal 4, 그 밖은 FNV-1a 해시 % 8
        assertEquals("blue", CategoryPalette.legacyDefault("class"));
        assertEquals("orange", CategoryPalette.legacyDefault("assignment"));
        assertEquals("teal", CategoryPalette.legacyDefault("team"));
        assertEquals("green", CategoryPalette.legacyDefault(Category.PERSONAL_ID));
        // 아래 두 값은 예전 CategoryColors.indexOf로 직접 구한 것이다 (club → 3, x → 7)
        assertEquals("rose", CategoryPalette.legacyDefault("club"));
        assertEquals("brown", CategoryPalette.legacyDefault("x"));
        assertEquals("brown", CategoryPalette.legacyDefault(null));
    }

    @Test
    void categoryColourIsNeverNullOrUnknown() {
        assertEquals("rose", new Category("club", "동아리").getColorKey());
        assertEquals("rose", new Category("club", "동아리", null).getColorKey());
        assertEquals("rose", new Category("club", "동아리", "pink").getColorKey());
        Category c = new Category("club", "동아리", "indigo");
        assertEquals("indigo", c.getColorKey());
        c.setColorKey("gray");
        assertEquals("gray", c.getColorKey());
        c.setColorKey("??");
        assertEquals("rose", c.getColorKey());
        assertTrue(CategoryPalette.isKnown("gray"));
        assertFalse(CategoryPalette.isKnown("Gray"));
    }
}
