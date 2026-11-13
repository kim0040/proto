package proto.ui.component;

import proto.ui.theme.Ui;

import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.LayoutManager;

/**
 * [경계 boundary] 너비는 고정하고 높이는 내용에 맞춰 늘어나는 패널 (대화상자 본문용).
 */
public class WidthPanel extends JPanel {

    private final int width;

    public WidthPanel(LayoutManager layout, int width) {
        super(layout);
        this.width = width;
        setOpaque(false);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Ui.px(width), super.getPreferredSize().height);
    }
}
