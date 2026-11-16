package proto.ui.layout;

import proto.ui.theme.Ui;

import javax.swing.JFrame;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.Optional;
import java.util.prefs.Preferences;

/**
 * [경계 boundary] 메인 창의 위치·크기·최대화 상태를 기억하고 다시 열 때 복원한다.
 * MainFrame이 시작할 때 restore, 창이 움직이거나 닫힐 때 remember와 save를 부른다. 저장 위치는 데이터 폴더(CSV)가 아니라 운영체제 환경설정(Preferences)이다.
 * 저장한 위치가 지금 연결된 화면 어디에서도 거의 보이지 않으면(모니터를 뺀 경우 등) 가운데 기본 위치로 돌아간다.
 */
public final class WindowState {

    private static final Preferences PREFS = Preferences.userNodeForPackage(WindowState.class);
    private static final String X = "winX";
    private static final String Y = "winY";
    private static final String W = "winW";
    private static final String H = "winH";
    private static final String MAX = "winMax";

    private Rectangle normal;

    /** 처음 실행할 때의 크기: 1200×780(논리 px)을 화면 쓸 수 있는 영역의 90%까지만 */
    public static Rectangle defaultBounds(Rectangle usable) {
        int w = Math.min(Ui.px(1200), usable.width * 9 / 10);
        int h = Math.min(Ui.px(780), usable.height * 9 / 10);
        return new Rectangle(usable.x + (usable.width - w) / 2, usable.y + (usable.height - h) / 2, w, h);
    }

    /** 저장된 위치로 창을 맞춘다. 저장값이 없거나 화면 밖이면 기본 위치. 최대화 여부를 돌려준다 */
    public boolean restore(JFrame frame, Dimension minimum) {
        Rectangle fallback = defaultBounds(ScreenFit.pointerScreen());
        Optional<Rectangle> saved = saved(minimum);
        Rectangle r = saved.filter(ScreenFit::mostlyVisible).orElse(fallback);
        frame.setBounds(r);
        normal = r;
        return saved.isPresent() && PREFS.getBoolean(MAX, false) && ScreenFit.mostlyVisible(r);
    }

    /** 최대화가 아닐 때의 크기·위치를 기억해 둔다. 최대화 중의 위치는 기억하지 않아서, 다음에 최대화를 풀면 원래 크기로 돌아간다. */
    public void remember(JFrame frame) {
        if ((frame.getExtendedState() & JFrame.MAXIMIZED_BOTH) == 0 && frame.isShowing()) {
            normal = frame.getBounds();
        }
    }

    /** 지금 상태를 저장한다 */
    public void save(JFrame frame) {
        remember(frame);
        Rectangle r = normal != null ? normal : frame.getBounds();
        PREFS.putInt(X, r.x);
        PREFS.putInt(Y, r.y);
        PREFS.putInt(W, r.width);
        PREFS.putInt(H, r.height);
        PREFS.putBoolean(MAX, (frame.getExtendedState() & JFrame.MAXIMIZED_BOTH) != 0);
    }

    private static Optional<Rectangle> saved(Dimension min) {
        int w = PREFS.getInt(W, -1);
        int h = PREFS.getInt(H, -1);
        if (w <= 0 || h <= 0) {
            return Optional.empty();
        }
        return Optional.of(new Rectangle(PREFS.getInt(X, 0), PREFS.getInt(Y, 0), Math.max(w, min.width),
                Math.max(h, min.height)));
    }
}
