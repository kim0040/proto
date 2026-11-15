package proto.ui.component;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager;

/**
 * [경계 boundary] 일정 행을 위에서 아래로 쌓되, 행마다 RowMotion이 정한 어긋남(offset)만큼 그려 새 자리로 미끄러지는 모습을 만드는 배치.
 * OccurrenceList가 쓴다. 행의 진짜 자리(쌓은 순서)는 그대로이고 그리는 자리만 어긋나 있다가 0으로 돌아오므로, 마우스가 맞는 행은 곧 새 자리의 행이다.
 * 사라지는 중인 그림자(RowGhost)는 자기 자리에 그대로 두고, 그 아래까지는 목록 높이에 포함해 잘리지 않게 한다.
 */
final class FlipLayout implements LayoutManager {

    @Override
    public void addLayoutComponent(String name, Component comp) {
    }

    @Override
    public void removeLayoutComponent(Component comp) {
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        int w = 0;
        int h = 0;
        int ghostBottom = 0;
        for (Component c : parent.getComponents()) {
            if (c instanceof OccurrenceRow) {
                Dimension d = c.getPreferredSize();
                w = Math.max(w, d.width);
                h += d.height;
            } else {
                ghostBottom = Math.max(ghostBottom, c.getY() + c.getHeight());
            }
        }
        return new Dimension(w, Math.max(h, ghostBottom));
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return preferredLayoutSize(parent);
    }

    @Override
    public void layoutContainer(Container parent) {
        int y = 0;
        int width = parent.getWidth();
        for (Component c : parent.getComponents()) {
            if (c instanceof OccurrenceRow row) {
                int h = row.getPreferredSize().height;
                row.setBounds(0, y + row.motion().offset(), width, h);
                y += h;
            }
        }
    }
}
