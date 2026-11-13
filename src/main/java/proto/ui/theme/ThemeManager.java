package proto.ui.theme;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import proto.ui.motion.Motion;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

/**
 * [경계 boundary] 밝은/어두운 테마를 켜고 바꾸고 기억한다 (선택은 Preferences에 저장, 화면 전용 설정).
 */
public final class ThemeManager {

    private static final String PREF_KEY = "theme";
    private static final Preferences PREFS = Preferences.userNodeForPackage(ThemeManager.class);

    private static boolean dark;

    private ThemeManager() {
    }

    /** 글꼴과 사용자 정의 색을 등록하고 저장된(없으면 OS를 따르는) 테마로 켠다 */
    public static void install() {
        Fonts.install();
        FlatLaf.registerCustomDefaultsSource("proto.ui.theme", ThemeManager.class.getClassLoader());
        String saved = PREFS.get(PREF_KEY, "system");
        dark = "system".equals(saved) ? systemPrefersDark() : "dark".equals(saved);
        setLaf(dark);
    }

    public static boolean isDark() {
        return dark;
    }

    /** 테마를 지정한다. animated가 true이고 애니메이션이 켜져 있으면 크로스페이드로 바꾼다 */
    public static void apply(boolean toDark, boolean animated) {
        if (toDark == dark) {
            return;
        }
        boolean fade = animated && Motion.enabled();
        if (fade) {
            FlatAnimatedLafChange.duration = 260;
            FlatAnimatedLafChange.showSnapshot();
        }
        dark = toDark;
        setLaf(toDark);
        FlatLaf.updateUI();
        if (fade) {
            FlatAnimatedLafChange.hideSnapshotWithAnimation();
        }
        PREFS.put(PREF_KEY, toDark ? "dark" : "light");
    }

    public static void toggle() {
        apply(!dark, true);
    }

    private static void setLaf(boolean toDark) {
        try {
            UIManager.setLookAndFeel(toDark ? new FlatDarkLaf() : new FlatLightLaf());
        } catch (UnsupportedLookAndFeelException e) {
            throw new IllegalStateException("테마를 켤 수 없어요", e);
        }
    }

    /** OS 설정이 어두운 모드인지 (알 수 없으면 false) */
    static boolean systemPrefersDark() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            return "Dark".equalsIgnoreCase(run("defaults", "read", "-g", "AppleInterfaceStyle"));
        }
        if (os.contains("win")) {
            String out = run("reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                    "/v", "AppsUseLightTheme");
            return out != null && out.trim().endsWith("0x0");
        }
        return false;
    }

    private static String run(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            if (!p.waitFor(800, TimeUnit.MILLISECONDS)) {
                p.destroyForcibly();
                return null;
            }
            return new String(p.getInputStream().readAllBytes()).trim();
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
