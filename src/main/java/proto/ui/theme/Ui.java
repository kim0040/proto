package proto.ui.theme;

import com.formdev.flatlaf.util.SystemInfo;
import com.formdev.flatlaf.util.UIScale;

import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * [경계 boundary] 크기 환산·그리기 힌트 같은 작은 화면 도우미.
 * 모든 화면 클래스가 크기를 Ui.px로 감싸 쓴다. 화면 배율(HiDPI)이 바뀌어도 모양이 비슷하게 보이도록 논리 px만 코드에 적는다.
 */
public final class Ui {

    private Ui() {
    }

    /** 논리 픽셀을 화면 배율(FlatLaf 스케일)에 맞춘 값으로 바꾼다 */
    public static int px(int value) {
        return UIScale.scale(value);
    }

    public static float px(float value) {
        return UIScale.scale(value);
    }

    public static boolean isMac() {
        return SystemInfo.isMacOS;
    }

    /** 단축키를 사람이 읽는 글로 (macOS "⌘N", Windows "Ctrl+N") */
    public static String shortcut(String key) {
        return isMac() ? "\u2318" + key : "Ctrl+" + key;
    }

    /** 도형과 글자를 부드럽게 그리도록 힌트를 켠다 */
    public static void smooth(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }
}
