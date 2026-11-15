package proto.ui.theme;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 강조색 7가지: 이름 저장/복원, 글자 대비, 속성 파일의 기본값과 일치 */
class AccentTest {

    private static final java.awt.Color LIGHT_BG = new java.awt.Color(0xFFFFFF);
    private static final java.awt.Color LIGHT_SIDEBAR = new java.awt.Color(0xF7F7F5);
    private static final java.awt.Color DARK_BG = new java.awt.Color(0x191919);
    private static final java.awt.Color DARK_SURFACE = new java.awt.Color(0x252525);

    @Test
    void 일곱_가지이고_이름으로_되살린다() {
        assertEquals(7, Accent.values().length);
        for (Accent a : Accent.values()) {
            assertEquals(a, Accent.of(a.key()));
        }
        assertEquals(Accent.BLUE, Accent.of("없는색"));
        assertEquals(Accent.BLUE, Accent.of(null));
    }

    @Test
    void 강조색_위_글자는_두_테마_모두_4점5_이상() {
        for (Accent a : Accent.values()) {
            for (boolean dark : new boolean[] {false, true}) {
                double r = Contrast.ratio(a.onAccent(dark), a.color(dark));
                assertTrue(r >= 4.5, a + (dark ? " dark " : " light ") + r);
            }
        }
    }

    @Test
    void 강조색_글자는_바탕_위에서도_4점5_이상() {
        for (Accent a : Accent.values()) {
            assertTrue(Contrast.ratio(a.color(false), LIGHT_BG) >= 4.5, a + " light/white");
            assertTrue(Contrast.ratio(a.color(false), LIGHT_SIDEBAR) >= 4.5, a + " light/sidebar");
            assertTrue(Contrast.ratio(a.color(true), DARK_BG) >= 4.5, a + " dark/bg");
            assertTrue(Contrast.ratio(a.color(true), DARK_SURFACE) >= 4.5, a + " dark/surface");
        }
    }

    @Test
    void 선택_바탕_위_본문_글자는_읽힌다() {
        java.awt.Color lightText = new java.awt.Color(0x2A2926);
        java.awt.Color darkText = new java.awt.Color(0xE6E6E4);
        for (Accent a : Accent.values()) {
            assertTrue(Contrast.ratio(lightText, a.soft(false)) >= 4.5, a + " light soft");
            assertTrue(Contrast.ratio(darkText, a.soft(true)) >= 4.5, a + " dark soft");
        }
    }

    @Test
    void 속성_파일의_기본_강조색은_파랑과_같다() throws IOException {
        assertEquals(Accent.BLUE.defaults(false), accentVariables("FlatLightLaf.properties"));
        assertEquals(Accent.BLUE.defaults(true), accentVariables("FlatDarkLaf.properties"));
    }

    private static Map<String, String> accentVariables(String file) throws IOException {
        Properties p = new Properties();
        try (InputStream in = AccentTest.class.getResourceAsStream("/proto/ui/theme/" + file)) {
            p.load(in);
        }
        return Map.of("@accentColor", p.getProperty("@accentColor").trim().toUpperCase(),
                "@accentSoft", p.getProperty("@accentSoft").trim().toUpperCase(),
                "@onAccent", p.getProperty("@onAccent").trim().toUpperCase());
    }
}
