package proto.ui.theme;

import javax.swing.UIManager;
import java.awt.Color;

/**
 * [경계 boundary] 테마 색 토큰 이름표. 색은 코드에 박지 않고 UIManager의 "proto.*" 키로만 읽는다.
 */
public final class Tokens {

    public static final String BG = "proto.bg";
    public static final String SIDEBAR = "proto.sidebar";
    public static final String SURFACE = "proto.surface";
    public static final String SUBTLE = "proto.subtle";
    public static final String HOVER = "proto.hover";
    public static final String BORDER = "proto.border";
    public static final String BORDER_STRONG = "proto.borderStrong";
    public static final String TEXT = "proto.text";
    public static final String MUTED = "proto.muted";
    public static final String FAINT = "proto.faint";
    public static final String ACCENT = "proto.accent";
    public static final String ACCENT_SOFT = "proto.accentSoft";
    public static final String ON_ACCENT = "proto.onAccent";
    public static final String DANGER = "proto.danger";
    public static final String DANGER_SOFT = "proto.dangerSoft";
    public static final String SUCCESS = "proto.success";
    public static final String WARNING = "proto.warning";

    private Tokens() {
    }

    /** 토큰 색을 지금 테마에서 읽는다. 없으면 눈에 띄는 마젠타(개발 중 실수 발견용) */
    public static Color get(String key) {
        Color c = UIManager.getColor(key);
        return c != null ? c : Color.MAGENTA;
    }

    /** a에서 b로 t(0~1)만큼 섞은 색 */
    public static Color blend(Color a, Color b, float t) {
        float u = Math.max(0f, Math.min(1f, t));
        int r = Math.round(a.getRed() + (b.getRed() - a.getRed()) * u);
        int g = Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * u);
        int bl = Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * u);
        int al = Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * u);
        return new Color(r, g, bl, al);
    }

    /** 색은 그대로, 투명도만 바꾼 색 (alpha 0~1) */
    public static Color alpha(Color c, float alpha) {
        int a = Math.round(255 * Math.max(0f, Math.min(1f, alpha)) * (c.getAlpha() / 255f));
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }
}
