package proto.ui.component;

import proto.ui.theme.Tokens;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.LayoutManager;

/**
 * [경계 boundary] 배경색을 토큰 이름으로 정하는 패널. 테마가 바뀌면 자동으로 새 색을 읽는다.
 * MainFrame, 사이드바 등 배경색이 있는 모든 패널의 부모이다. getBackground를 다시 정의해서 테마가 바뀌면 다음 그리기에서 새 색을 쓴다.
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
