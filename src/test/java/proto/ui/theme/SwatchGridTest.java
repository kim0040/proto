package proto.ui.theme;

import org.junit.jupiter.api.Test;
import proto.ui.component.SwatchGrid;
import proto.ui.component.SwatchGrid.Swatch;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 색 견본 격자: 화살표로 이동(가장자리에서 멈춤), Space/Enter로 선택 */
class SwatchGridTest {

    private static SwatchGrid grid(String selected) {
        List<Swatch> items = new ArrayList<>();
        for (String id : List.of("a", "b", "c", "d", "e", "f")) {
            items.add(new Swatch(id, id.toUpperCase(), () -> Color.GRAY));
        }
        return new SwatchGrid(items, 3, false, selected);
    }

    private static void press(SwatchGrid g, int code) {
        KeyEvent e = new KeyEvent(g, KeyEvent.KEY_PRESSED, 0, 0, code, KeyEvent.CHAR_UNDEFINED);
        for (java.awt.event.KeyListener l : g.getKeyListeners()) {
            l.keyPressed(e);
        }
    }

    @Test
    void 화살표로_옮기고_엔터로_고른다() {
        SwatchGrid g = grid("a");
        List<String> picked = new ArrayList<>();
        g.addChangeListener(picked::add);
        press(g, KeyEvent.VK_RIGHT);
        press(g, KeyEvent.VK_DOWN);
        press(g, KeyEvent.VK_ENTER);
        assertEquals(List.of("e"), picked);
        assertEquals("e", g.selectedId());
    }

    @Test
    void 가장자리에서는_멈춘다() {
        SwatchGrid g = grid("a");
        List<String> picked = new ArrayList<>();
        g.addChangeListener(picked::add);
        press(g, KeyEvent.VK_LEFT);
        press(g, KeyEvent.VK_UP);
        press(g, KeyEvent.VK_SPACE);
        assertEquals(List.of("a"), picked);
        press(g, KeyEvent.VK_END);
        press(g, KeyEvent.VK_RIGHT);
        press(g, KeyEvent.VK_DOWN);
        press(g, KeyEvent.VK_SPACE);
        assertEquals(List.of("a", "f"), picked);
    }

    @Test
    void 처음_고른_견본은_id로_정한다_리스너는_부르지_않는다() {
        SwatchGrid g = grid("c");
        List<String> picked = new ArrayList<>();
        g.addChangeListener(picked::add);
        g.setSelected("d");
        assertEquals("d", g.selectedId());
        assertEquals(List.of(), picked);
    }
}
