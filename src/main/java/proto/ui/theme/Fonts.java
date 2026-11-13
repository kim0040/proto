package proto.ui.theme;

import com.formdev.flatlaf.FlatLaf;

import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;

/**
 * [경계 boundary] 번들된 Pretendard 글꼴을 등록하고 글자 크기 단계(타입 스케일)를 제공한다.
 */
public final class Fonts {

    /** 글자 굵기 4단계 */
    public enum Weight {
        REGULAR("Regular"), MEDIUM("Medium"), SEMIBOLD("SemiBold"), BOLD("Bold");

        private final String file;

        Weight(String file) {
            this.file = file;
        }
    }

    /** 타입 스케일: 12 / 13 / 14 / 16 / 20 / 26 */
    public static final float CAPTION = 12f;
    public static final float SMALL = 13f;
    public static final float BODY = 14f;
    public static final float LEAD = 16f;
    public static final float TITLE = 20f;
    public static final float DISPLAY = 26f;

    private static final Font[] BASE = new Font[Weight.values().length];
    private static boolean installed;

    private Fonts() {
    }

    /** 글꼴을 등록하고 FlatLaf 기본 글꼴로 지정한다. LaF를 켜기 전에 한 번 부른다 */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        boolean allBundled = true;
        for (Weight w : Weight.values()) {
            Font loaded = load(w);
            allBundled &= loaded != null;
            BASE[w.ordinal()] = loaded != null ? loaded : platform(w);
        }
        Font regular = BASE[Weight.REGULAR.ordinal()];
        if (allBundled) {
            FlatLaf.setPreferredFontFamily(regular.getFamily());
            FlatLaf.setPreferredSemiboldFontFamily(BASE[Weight.SEMIBOLD.ordinal()].getFamily());
        } else {
            FlatLaf.setPreferredFontFamily(platformFamily());
        }
        UIManager.put("defaultFont", new FontUIResource(regular.deriveFont(BODY)));
        installed = true;
    }

    /** 번들 글꼴을 읽어 등록한다. 어떤 이유로든 실패하면 조용히 null (플랫폼 글꼴로 대신한다) */
    private static Font load(Weight w) {
        String path = "/fonts/Pretendard-" + w.file + ".ttf";
        try (InputStream in = Fonts.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            Font font = Font.createFont(Font.TRUETYPE_FONT, in);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            return font;
        } catch (IOException | FontFormatException | RuntimeException e) {
            return null;
        }
    }

    /** 번들 글꼴을 못 쓸 때의 대체 글꼴: 굵기는 보통/굵게 두 단계로 단순화한다 */
    private static Font platform(Weight w) {
        boolean bold = w == Weight.SEMIBOLD || w == Weight.BOLD;
        return new Font(platformFamily(), bold ? Font.BOLD : Font.PLAIN, 14);
    }

    /** 운영체제 기본 한글 UI 글꼴 (Windows: 맑은 고딕, macOS: Apple SD Gothic Neo). 없으면 논리 글꼴 */
    static String platformFamily() {
        try {
            java.util.Set<String> names = new java.util.HashSet<>(java.util.Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            for (String candidate : new String[] {"Malgun Gothic", "Apple SD Gothic Neo", "Noto Sans CJK KR",
                    "NanumGothic"}) {
                if (names.contains(candidate)) {
                    return candidate;
                }
            }
        } catch (RuntimeException e) {
            // 글꼴 목록을 못 읽으면 논리 글꼴을 쓴다
        }
        return Font.SANS_SERIF;
    }

    /** 굵기와 크기(논리 px)로 글꼴을 만든다. 화면 배율을 반영한다 */
    public static Font of(float size, Weight weight) {
        Font base = installed ? BASE[weight.ordinal()] : UIManager.getFont("defaultFont");
        if (base == null) {
            base = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
        }
        return base.deriveFont(Ui.px(size));
    }

    public static Font regular(float size) {
        return of(size, Weight.REGULAR);
    }

    public static Font medium(float size) {
        return of(size, Weight.MEDIUM);
    }

    public static Font semibold(float size) {
        return of(size, Weight.SEMIBOLD);
    }

    public static Font bold(float size) {
        return of(size, Weight.BOLD);
    }

    /** 점검용: 자바가 인식한 글꼴 이름들 */
    public static String describe() {
        StringBuilder sb = new StringBuilder();
        for (Font f : BASE) {
            sb.append(f == null ? "null" : f.getFamily() + " / " + f.getFontName()).append('\n');
        }
        return sb.toString();
    }
}
