package proto.ui.component;

import javax.swing.JPanel;
import javax.swing.Scrollable;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Rectangle;

/**
 * [경계 boundary] 스크롤 영역 안에서 너비는 창에 맞추고 높이만 늘어나는 패널.
 * 스크롤되는 목록의 바탕이다 (OccurrenceList, 대화상자 본문 등). 너비를 보이는 영역에 맞추면(getScrollableTracksViewportWidth) 가로 스크롤 없이 줄바꿈·말줄임이 잘 된다.
 */
public class ScrollPanel extends JPanel implements Scrollable {

    public ScrollPanel(LayoutManager layout) {
        super(layout);
        setOpaque(false);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return 28;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return Math.max(visible.height - 48, 48);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
