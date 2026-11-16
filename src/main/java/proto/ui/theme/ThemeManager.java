package proto.ui.theme;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import proto.ui.UiSettings;
import proto.ui.motion.Motion;

import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

/**
 * [경계 boundary] 밝은/어두운 테마와 강조색을 켜고 바꾸고 기억한다 (선택은 Preferences에 저장, 화면 전용 설정).
 * 강조색은 FlatLaf의 덮어쓰기 값(Accent#defaults)으로 넣어, 테마를 바꿀 때도 같은 강조색의 그 테마용 색이 쓰인다.
 */
public final class ThemeManager {

    private static final String PREF_KEY = "theme";
    private static final int THEME_FADE_MS = 260;
    private static final int ACCENT_FADE_MS = 180;
    /** 휠 한 칸을 부드럽게 이어 주는 스크롤 영역 모양 (proto.ui.component.SmoothScrollPaneUI) */
    private static final String SCROLL_PANE_UI = "proto.ui.component.SmoothScrollPaneUI";
    private static final Preferences PREFS = Preferences.userNodeForPackage(ThemeManager.class);

    /** 사용자가 고르는 테마 방식. 시스템이면 OS 설정을 따른다 */
    public enum Choice { SYSTEM, LIGHT, DARK }

    private static boolean dark;
    private static Accent accent = Accent.BLUE;

    private ThemeManager() {
    }

    /** 글꼴과 사용자 정의 색을 등록하고 저장된(없으면 OS를 따르는) 테마로 켠다 */
    public static void install() {
        Fonts.install();
        FlatLaf.registerCustomDefaultsSource("proto.ui.theme", ThemeManager.class.getClassLoader());
        String saved = PREFS.get(PREF_KEY, "system");
        dark = "system".equals(saved) ? systemPrefersDark() : "dark".equals(saved);
        accent = UiSettings.accent();
        setLaf(dark);
        installTooltipTiming();
    }

    public static boolean isDark() {
        return dark;
    }

    /**
     * 테마를 지정하고 저장한다. animated가 true이고 애니메이션이 켜져 있으면 FlatLaf의 스냅샷 크로스페이드로 바꾼다.
     * 이미 같은 테마면 아무것도 하지 않는다.
     */
    public static void apply(boolean toDark, boolean animated) {
        apply(toDark, animated, true);
    }

    /** 저장 여부를 고를 수 있는 테마 변경 (스크린샷 도구는 저장하지 않는다) */
    public static void apply(boolean toDark, boolean animated, boolean persist) {
        if (toDark == dark) {
            return;
        }
        relaf(animated, THEME_FADE_MS, () -> {
            dark = toDark;
            setLaf(toDark);
        });
        if (persist) {
            PREFS.put(PREF_KEY, toDark ? "dark" : "light");
        }
    }

    /** 지금 강조색 */
    public static Accent accent() {
        return accent;
    }

    /** 강조색을 바꾸고 저장해 열린 모든 화면에 바로 반영한다 (애니메이션이 켜져 있으면 짧게 섞인다). 같은 색이면 아무것도 하지 않는다 */
    public static void setAccent(Accent next, boolean animated) {
        if (next == accent) {
            return;
        }
        relaf(animated, ACCENT_FADE_MS, () -> {
            accent = next;
            setLaf(dark);
        });
        UiSettings.setAccent(next);
    }

    /** 저장된 테마 방식 (처음에는 시스템) */
    public static Choice choice() {
        try {
            return Choice.valueOf(PREFS.get(PREF_KEY, "system").toUpperCase());
        } catch (IllegalArgumentException e) {
            return Choice.SYSTEM;
        }
    }

    /** 테마 방식을 저장하고 바로 적용한다 (시스템이면 지금 OS 설정을 따른다) */
    public static void setChoice(Choice choice) {
        PREFS.put(PREF_KEY, choice.name().toLowerCase());
        boolean wantDark = choice == Choice.SYSTEM ? systemPrefersDark() : choice == Choice.DARK;
        apply(wantDark, true, false);
    }

    public static void toggle() {
        apply(!dark, true);
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

    /** 툴팁은 0.4초 머물러야 뜨고(스쳐 지나갈 때는 안 뜬다), 하나가 떠 있다가 옆으로 옮기면 바로 이어서 뜬다 */
    private static void installTooltipTiming() {
        ToolTipManager tips = ToolTipManager.sharedInstance();
        tips.setInitialDelay(400);
        tips.setReshowDelay(0);
        tips.setDismissDelay(8000);
    }

    /** LaF를 다시 켜서 열린 컴포넌트를 갱신한다. 애니메이션이 켜져 있으면 바뀌기 전 모습에서 새 모습으로 duration ms 동안 섞인다 */
    private static void relaf(boolean animated, int duration, Runnable change) {
        boolean fade = animated && Motion.enabled();
        if (fade) {
            FlatAnimatedLafChange.duration = duration;
            FlatAnimatedLafChange.showSnapshot();
        }
        change.run();
        FlatLaf.updateUI();
        if (fade) {
            FlatAnimatedLafChange.hideSnapshotWithAnimation();
        }
    }

    private static void setLaf(boolean toDark) {
        try {
            FlatLaf.setGlobalExtraDefaults(accent.defaults(toDark));
            UIManager.setLookAndFeel(toDark ? new FlatDarkLaf() : new FlatLightLaf());
            UIManager.put("ScrollPaneUI", SCROLL_PANE_UI);
        } catch (UnsupportedLookAndFeelException e) {
            throw new IllegalStateException("테마를 켤 수 없어요", e);
        }
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
