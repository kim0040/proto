package proto.ui.component;

import proto.ui.theme.Tokens;

import javax.swing.border.AbstractBorder;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;

/**
 * [경계 boundary] 아래쪽에만 그리는 1px 옅은 구분선 테두리 (목록 행 사이 선, 세로 장식선 아님).
 */
public class Hairline extends AbstractBorder {

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        g.setColor(Tokens.get(Tokens.BORDER));
        g.fillRect(x, y + height - 1, width, 1);
    }

    @Override
    public Insets getBorderInsets(Component c) {
        return new Insets(0, 0, 1, 0);
    }
}
