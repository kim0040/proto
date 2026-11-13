package proto.ui.component;

import proto.ui.theme.Tokens;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.LayoutManager;

/**
 * [경계 boundary] 배경색을 토큰 이름으로 정하는 패널. 테마가 바뀌면 자동으로 새 색을 읽는다.
 */
public class TokenPanel extends JPanel {

    private final String bgToken;

    public TokenPanel(LayoutManager layout, String bgToken) {
        super(layout);
        this.bgToken = bgToken;
        setOpaque(bgToken != null);
    }

    @Override
    public Color getBackground() {
        return bgToken == null ? super.getBackground() : Tokens.get(bgToken);
    }
}
