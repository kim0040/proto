package proto.ui.layout;

import com.formdev.flatlaf.util.UIScale;
import proto.ui.layout.SizeClass.Width;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * [경계 boundary] 화면 크기에 따라 배치를 바꾸는 단 하나의 기준표. 본문 크기를 배율(UIScale)로 나눈 논리 px로
 * 크기 등급을 정하고, 등급이 바뀔 때만 구독자에게 알린다. 창 크기가 연달아 바뀌어도 이벤트 한 번에 한 번만 계산한다.
 * UiContext가 하나 만들고 MainFrame이 본문(layeredPane)에 attach한다. 사이드바·헤더·각 보기는 onChange로 등급이 바뀔 때만 배치를 바꾼다 (SizeClass가 값, 이
 * 클래스가 계산과 알림).
 * 창이 열리기 전에는 prime으로 처음 등급을 어림잡는다.
 */
public final class Breakpoints {

    /** 아이콘 띠로 줄었을 때의 사이드바 너비 (논리 px) */
    public static final int RAIL_WIDTH = 64;
    /** 이 너비 이상이면 WIDE */
    public static final int WIDE_MIN = 1280;
    /** 이 너비 이상이면 MEDIUM, 미만이면 COMPACT */
    public static final int MEDIUM_MIN = 1000;
    /** 세로로 긴 화면의 기준: 높이가 너비의 이 배수보다 크면 */
    public static final double TALL_RATIO = 0.9;
    /** 낮은 화면의 기준 높이 */
    public static final int SHORT_HEIGHT = 620;

    private final List<Consumer<SizeClass>> listeners = new ArrayList<>();
    private JComponent watched;
    private SizeClass current = new SizeClass(Width.WIDE, false, false);
    private boolean pending;

    /** 논리 px 크기로 등급을 계산한다 (배율과 무관한 순수 계산) */
    public static SizeClass classify(int logicalWidth, int logicalHeight) {
        Width w = logicalWidth >= WIDE_MIN ? Width.WIDE : logicalWidth >= MEDIUM_MIN ? Width.MEDIUM : Width.COMPACT;
        boolean tall = logicalHeight > logicalWidth * TALL_RATIO;
        return new SizeClass(w, tall, logicalHeight < SHORT_HEIGHT);
    }

    /** 이 컴포넌트의 크기를 지켜본다. 크기가 바뀌면 한 번에 모아서 다시 계산한다 */
    public void attach(JComponent component) {
        watched = component;
        component.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                schedule();
            }
        });
        update();
    }

    /** 창이 아직 화면에 없을 때, 창 전체 크기(물리 좌표)로 처음 등급을 어림잡는다 */
    public void prime(int width, int height) {
        if (width > 0 && height > 0) {
            current = classify(UIScale.unscale(width), UIScale.unscale(height));
        }
    }

    public SizeClass current() {
        return current;
    }

    /** 등급이 바뀔 때 부를 구독자를 등록한다 (등록 즉시 한 번 부르지는 않는다) */
    public void onChange(Consumer<SizeClass> listener) {
        listeners.add(listener);
    }

    /** 창 크기가 연달아 바뀌어도 이벤트 한 번에 한 번만 계산하도록 invokeLater로 모은다 (pending 깃발). */
    private void schedule() {
        if (!pending) {
            pending = true;
            SwingUtilities.invokeLater(this::update);
        }
    }

    /** 지금 크기로 바로 다시 계산한다 (캡처 도구 등) */
    public void update() {
        pending = false;
        if (watched == null || watched.getWidth() <= 0 || watched.getHeight() <= 0) {
            return;
        }
        SizeClass next = classify(UIScale.unscale(watched.getWidth()), UIScale.unscale(watched.getHeight()));
        if (!next.equals(current)) {
            current = next;
            new ArrayList<>(listeners).forEach(l -> l.accept(next));
        }
    }
}
