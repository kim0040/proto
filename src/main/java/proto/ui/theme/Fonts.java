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
        for (Weight w : Weight.values()) {
            BASE[w.ordinal()] = load(w);
        }
        Font regular = BASE[Weight.REGULAR.ordinal()];
        FlatLaf.setPreferredFontFamily(regular.getFamily());
        FlatLaf.setPreferredSemiboldFontFamily(BASE[Weight.SEMIBOLD.ordinal()].getFamily());
        UIManager.put("defaultFont", new FontUIResource(regular.deriveFont(BODY)));
        installed = true;
    }

    private static Font load(Weight w) {
        String path = "/fonts/Pretendard-" + w.file + ".ttf";
        try (InputStream in = Fonts.class.getResourceAsStream(path)) {
            if (in == null) {
                return new Font(Font.SANS_SERIF, w == Weight.REGULAR ? Font.PLAIN : Font.BOLD, 14);
            }
            Font font = Font.createFont(Font.TRUETYPE_FONT, in);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            return font;
        } catch (IOException | FontFormatException e) {
            return new Font(Font.SANS_SERIF, Font.PLAIN, 14);
        }
    }

    /** 굵기와 크기(논리 px)로 글꼴을 만든다. 화면 배율을 반영한다 */
    public static Font of(float size, Weight weight) {
        Font base = installed ? BASE[weight.ordinal()] : UIManager.getFont("defaultFont");
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
