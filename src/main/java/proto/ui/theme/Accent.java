package proto.ui.theme;

import java.awt.Color;
import java.util.Map;

/**
 * [경계 boundary] 앱 강조색 7가지(파랑·보라·초록·주황·분홍·빨강·흑백)와 테마(밝음/어두움)별 색 값.
 * 설정 시트(SettingsView)의 "강조색" 줄이 고르고, ThemeManager가 {@link #defaults}를 FlatLaf에 덮어써 단추·포커스·선택·오늘 표시에 한꺼번에 반영한다.
 * 색마다 밝은 테마는 흰 글자와 ≥ 4.5:1, 어두운 테마는 어두운 글자와 ≥ 4.5:1이 되도록 골랐고, 강조색 글자(링크·오늘 표시)도 바탕 위에서 4.5:1 안팎으로 읽힌다.
 */
public enum Accent {
    BLUE("blue", "파랑", 0x1A71C8, 0x4094E6),
    PURPLE("purple", "보라", 0x8A5DAC, 0xA583BF),
    GREEN("green", "초록", 0x0F7E6F, 0x14A28E),
    ORANGE("orange", "주황", 0xAC5B0A, 0xDC750D),
    PINK("pink", "분홍", 0xBD4183, 0xCE73A3),
    RED("red", "빨강", 0xCF3732, 0xDD706C),
    GRAPHITE("graphite", "흑백", 0x3B3A37, 0xC9C8C4);

    private static final Color LIGHT_BG = new Color(0xFFFFFF);
    private static final Color DARK_BG = new Color(0x191919);
    private static final Color ON_LIGHT = new Color(0xFFFFFF);
    private static final Color ON_DARK = new Color(0x141414);

    private final String key;
    private final String label;
    private final Color light;
    private final Color dark;

    Accent(String key, String label, int light, int dark) {
        this.key = key;
        this.label = label;
        this.light = new Color(light);
        this.dark = new Color(dark);
    }

    /** 저장에 쓰는 이름 ("blue" 등) */
    public String key() {
        return key;
    }

    /** 화면에 보이는 이름 ("파랑" 등) */
    public String label() {
        return label;
    }

    /** 저장된 이름에서 되살린다. 모르는 이름이면 기본인 파랑 */
    public static Accent of(String key) {
        for (Accent a : values()) {
            if (a.key.equals(key)) {
                return a;
            }
        }
        return BLUE;
    }

    /** 이 테마에서의 강조색 */
    public Color color(boolean isDark) {
        return isDark ? dark : light;
    }

    /** 선택 표시(목록 선택, 알약)에 쓰는 아주 옅은 강조색 바탕: 테마 바탕에 강조색을 12%(어두운 테마는 22%) 섞는다 */
    public Color soft(boolean isDark) {
        return isDark ? Tokens.blend(DARK_BG, dark, 0.22f) : Tokens.blend(LIGHT_BG, light, 0.12f);
    }

    /** 강조색 바탕 위에 얹는 글자색: 밝은 테마는 흰색, 어두운 테마는 거의 검정 */
    public Color onAccent(boolean isDark) {
        return isDark ? ON_DARK : ON_LIGHT;
    }

    /** FlatLaf에 덮어쓸 값: 속성 파일의 @accentColor, @accentSoft, @onAccent 변수를 바꾸면 거기서 파생되는 색이 모두 따라온다 */
    public Map<String, String> defaults(boolean isDark) {
        return Map.of(
                "@accentColor", hex(color(isDark)),
                "@accentSoft", hex(soft(isDark)),
                "@onAccent", hex(onAccent(isDark)));
    }

    private static String hex(Color c) {
        return String.format("#%06X", c.getRGB() & 0xFFFFFF);
    }
}
