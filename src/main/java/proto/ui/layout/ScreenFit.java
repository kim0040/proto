package proto.ui.layout;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

/**
 * [경계 boundary] 창을 화면 안에 맞추는 도우미: 화면의 쓸 수 있는 영역(작업 표시줄·메뉴 막대 제외),
 * 여러 모니터에서 창이 있는 화면 찾기, 창을 화면 안으로 밀어 넣기.
 * BaseDialog(대화상자 위치·크기), WindowState(저장된 창 위치 복원), MainFrame이 쓴다. 모니터를 빼거나 해상도가 바뀌어도 창이 화면 밖에서 열리지 않게 한다.
 */
public final class ScreenFit {

    private ScreenFit() {
    }

    /** 화면에서 작업 표시줄·메뉴 막대·독을 뺀 영역 */
    public static Rectangle usable(GraphicsConfiguration gc) {
        Rectangle b = gc.getBounds();
        Insets in = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        return new Rectangle(b.x + in.left, b.y + in.top, b.width - in.left - in.right, b.height - in.top - in.bottom);
    }

    /** 연결된 모든 화면의 쓸 수 있는 영역 */
    public static List<Rectangle> allUsable() {
        List<Rectangle> list = new ArrayList<>();
        for (GraphicsDevice d : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            list.add(usable(d.getDefaultConfiguration()));
        }
        return list;
    }

    /** 이 창이 놓인 화면. 창이 아직 없거나 알 수 없으면 마우스가 있는 화면, 그것도 없으면 기본 화면 */
    public static Rectangle usableOf(Window window) {
        if (window != null && window.isShowing() && window.getGraphicsConfiguration() != null) {
            return usable(window.getGraphicsConfiguration());
        }
        return pointerScreen();
    }

    /** 마우스 포인터가 있는 화면의 쓸 수 있는 영역 */
    public static Rectangle pointerScreen() {
        PointerInfo p = MouseInfo.getPointerInfo();
        GraphicsConfiguration gc = p != null ? p.getDevice().getDefaultConfiguration()
                : GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
        return usable(gc);
    }

    /** r을 area 안으로 (필요하면 줄이고) 밀어 넣는다 */
    public static Rectangle clampInto(Rectangle r, Rectangle area) {
        int w = Math.min(r.width, area.width);
        int h = Math.min(r.height, area.height);
        int x = Math.max(area.x, Math.min(r.x, area.x + area.width - w));
        int y = Math.max(area.y, Math.min(r.y, area.y + area.height - h));
        return new Rectangle(x, y, w, h);
    }

    /** 창의 크기를 화면 쓸 수 있는 영역(바깥 여유 margin) 안으로 줄이고, 부모 가운데에 놓되 화면 밖으로 나가지 않게 한다 */
    public static void fitAndCenter(Window w, Window owner, int margin) {
        Rectangle area = usableOf(owner != null && owner.isShowing() ? owner : w);
        area = new Rectangle(area.x + margin, area.y + margin, area.width - margin * 2, area.height - margin * 2);
        int width = Math.min(w.getWidth(), area.width);
        int height = Math.min(w.getHeight(), area.height);
        Rectangle ref = owner != null && owner.isShowing() ? owner.getBounds() : area;
        Rectangle r = new Rectangle(ref.x + (ref.width - width) / 2, ref.y + (ref.height - height) / 2, width, height);
        w.setBounds(clampInto(r, area));
    }

    /** 이미 놓인 창을 크기는 유지한 채 화면 안으로 밀어 넣는다 (내용이 바뀌어 커졌을 때) */
    public static void keepOnScreen(Window w, int margin) {
        Rectangle area = usableOf(w);
        area = new Rectangle(area.x + margin, area.y + margin, area.width - margin * 2, area.height - margin * 2);
        w.setBounds(clampInto(w.getBounds(), area));
    }

    /** r이 어느 화면에서든 넓이의 절반 이상 보이고 제목 줄 쪽도 보이는지 */
    public static boolean mostlyVisible(Rectangle r) {
        if (r.width <= 0 || r.height <= 0) {
            return false;
        }
        long seen = 0;
        boolean titleSeen = false;
        Rectangle title = new Rectangle(r.x, r.y, r.width, Math.min(r.height, 48));
        for (Rectangle s : allUsable()) {
            Rectangle i = s.intersection(r);
            if (!i.isEmpty()) {
                seen += (long) i.width * i.height;
            }
            Rectangle t = s.intersection(title);
            titleSeen |= !t.isEmpty() && t.width >= title.width / 3 && t.height >= Math.min(title.height, 24);
        }
        return titleSeen && seen * 2 >= (long) r.width * r.height;
    }
}
