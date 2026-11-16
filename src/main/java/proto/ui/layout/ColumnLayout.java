package proto.ui.layout;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

/**
 * [경계 boundary] 자식을 위에서 아래로 한 줄씩 쌓는 단순한 배치. 각 자식은 자기 선호 높이를 쓰고, 가로는 둘 중 하나다:
 * 꽉 채우기(목록 줄) 또는 선호 너비로 가운데 맞추기(아이콘 띠의 버튼).
 * 왜 BoxLayout을 쓰지 않는가: BoxLayout은 자식마다 alignmentX와 최대 크기를 섞어 위치를 계산해서, 최대 너비가 아주 큰 자식이 섞이면
 * 같은 줄에 있어야 할 아이콘들이 서로 몇 픽셀씩 어긋났다. 이 배치는 규칙이 하나뿐이라 결과를 바로 예측할 수 있다.
 */
public final class ColumnLayout implements LayoutManager {

    private final boolean centered;

    private ColumnLayout(boolean centered) {
        this.centered = centered;
    }

    /** 자식을 컨테이너 너비만큼 늘린다 */
    public static ColumnLayout fill() {
        return new ColumnLayout(false);
    }

    /** 자식을 선호 너비로 두고 가로 가운데에 맞춘다 (컨테이너보다 넓으면 컨테이너 너비로 줄인다) */
    public static ColumnLayout centered() {
        return new ColumnLayout(true);
    }

    @Override
    public void layoutContainer(Container parent) {
        Insets in = parent.getInsets();
        int width = parent.getWidth() - in.left - in.right;
        int y = in.top;
        for (Component c : parent.getComponents()) {
            if (!c.isVisible()) {
                continue;
            }
            Dimension pref = c.getPreferredSize();
            int w = centered ? Math.min(pref.width, width) : width;
            int x = in.left + (width - w) / 2;
            c.setBounds(x, y, w, pref.height);
            y += pref.height;
        }
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        Insets in = parent.getInsets();
        int w = 0;
        int h = 0;
        for (Component c : parent.getComponents()) {
            if (c.isVisible()) {
                Dimension pref = c.getPreferredSize();
                w = Math.max(w, pref.width);
                h += pref.height;
            }
        }
        return new Dimension(w + in.left + in.right, h + in.top + in.bottom);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return preferredLayoutSize(parent);
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
        // 이름으로 자리를 정하지 않는다
    }

    @Override
    public void removeLayoutComponent(Component comp) {
        // 따로 기억하는 자리가 없다
    }
}
