package proto.ui.component;

import proto.ui.theme.Fonts;
import proto.ui.theme.Tokens;

import javax.swing.JLabel;
import java.awt.Color;
import java.awt.Font;

/**
 * [경계 boundary] 글자색을 토큰 이름으로 정하는 라벨. 테마가 바뀌면 자동으로 새 색을 읽는다.
 */
public class TokenLabel extends JLabel {

    private String colorToken;

    public TokenLabel(String text, Font font, String colorToken) {
        super(text);
        this.colorToken = colorToken;
        setFont(font);
    }

    public TokenLabel(String text, float size, Fonts.Weight weight, String colorToken) {
        this(text, Fonts.of(size, weight), colorToken);
    }

    public void setColorToken(String token) {
        this.colorToken = token;
        repaint();
    }

    @Override
    public Color getForeground() {
        return colorToken == null ? super.getForeground() : Tokens.get(colorToken);
    }
}
