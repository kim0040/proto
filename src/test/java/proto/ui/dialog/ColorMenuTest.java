package proto.ui.dialog;

import org.junit.jupiter.api.Test;
import proto.entity.CategoryPalette;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 일정의 "색 바꾸기" 하위 메뉴와 한국어 색 이름 */
class ColorMenuTest {

    @Test
    void 팔레트의_모든_색이_이름을_가진다() {
        for (String key : CategoryPalette.KEYS) {
            assertTrue(!ColorMenu.nameOf(key).equals(key), key + " 의 한국어 이름이 없어요");
        }
        assertEquals("카테고리 색", ColorMenu.nameOf(null));
        assertEquals("분홍", ColorMenu.nameOf("rose"));
    }

    @Test
    void 하위_메뉴는_카테고리_색_다음에_팔레트_색_열두_개이고_지금_색에_체크가_걸린다() {
        List<String> picked = new ArrayList<>();
        JMenu menu = ColorMenu.submenu("rose", () -> Color.GREEN, key -> picked.add(String.valueOf(key)));
        assertEquals(1 + CategoryPalette.KEYS.size(), menu.getItemCount());
        assertEquals("카테고리 색", menu.getItem(0).getText());
        List<String> checked = new ArrayList<>();
        for (int i = 0; i < menu.getItemCount(); i++) {
            if (((JCheckBoxMenuItem) menu.getItem(i)).isSelected()) {
                checked.add(menu.getItem(i).getText());
            }
        }
        assertEquals(List.of("분홍"), checked);
        menu.getItem(0).doClick();
        menu.getItem(2).doClick();
        assertEquals(List.of("null", CategoryPalette.KEYS.get(1)), picked);
    }

    @Test
    void 일정만의_색이_없으면_카테고리_색에_체크가_걸린다() {
        JMenu menu = ColorMenu.submenu(null, () -> Color.GREEN, key -> { });
        assertTrue(((JCheckBoxMenuItem) menu.getItem(0)).isSelected());
    }
}
