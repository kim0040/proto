package proto.ui.component;

import proto.ui.theme.Fonts;
import proto.ui.theme.Glyphs;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.ToolTipManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.MouseEvent;

/**
 * [경계 boundary] 글자색을 토큰 이름으로 정하는 라벨. 테마가 바뀌면 자동으로 새 색을 읽는다.
 * 화면의 거의 모든 글자 라벨이다. "토큰"은 색에 붙인 이름("proto.text" 같은)으로, getForeground를 다시 정의해 그릴 때마다 현재 테마의 색을 찾아 쓴다.
 * 잘려서 "…"로 보이면 마우스를 올렸을 때 전체 글을 툴팁으로 보여 주고, 글꼴에 없는 글자(이모지)는 대체 글꼴로 그린다.
 */
public class TokenLabel extends JLabel {

    private String colorToken;

    public TokenLabel(String text, Font font, String colorToken) {
        super(text);
        this.colorToken = colorToken;
        setFont(font);
        ToolTipManager.sharedInstance().registerComponent(this);
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

    @Override
    public String getToolTipText(MouseEvent e) {
        String explicit = getToolTipText();
        if (explicit != null || !plainText()) {
            return explicit;
        }
        Insets in = getInsets();
        FontMetrics fm = getFontMetrics(getFont());
        int room = getWidth() - in.left - in.right - iconSpace();
        return getWidth() > 0 && Glyphs.width(fm, getText()) > room ? TextUtil.tip(getText()) : null;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        if (!usesFallback()) {
            return d;
        }
        Insets in = getInsets();
        return new Dimension(in.left + in.right + iconSpace() + Glyphs.width(getFontMetrics(getFont()), getText()),
                d.height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (!usesFallback()) {
            super.paintComponent(g);
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.smooth(g2);
        g2.setFont(getFont());
        g2.setColor(getForeground());
        Insets in = getInsets();
        int x = in.left;
        int room = getWidth() - in.left - in.right - iconSpace();
        Icon icon = getIcon();
        if (icon != null) {
            icon.paintIcon(this, g2, x, (getHeight() - icon.getIconHeight()) / 2);
            x += iconSpace();
        }
        FontMetrics fm = g2.getFontMetrics();
        String shown = TextUtil.ellipsize(getText(), fm, room);
        int tw = Glyphs.width(fm, shown);
        int tx = switch (getHorizontalAlignment()) {
            case CENTER -> x + (room - tw) / 2;
            case RIGHT -> x + room - tw;
            default -> x;
        };
        Glyphs.draw(g2, shown, tx, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }

    private boolean plainText() {
        String t = getText();
        return t != null && !t.isEmpty() && !t.regionMatches(true, 0, "<html", 0, 5);
    }

    /** 글꼴에 없는 글자(이모지 등)가 있으면 JLabel의 기본 그리기 대신 Glyphs로 대체 글꼴을 섞어 직접 그린다. 폭을 재는 것도 같은 방법으로 해서 말줄임과 크기가 맞게 한다. */
    private boolean usesFallback() {
        return plainText() && Glyphs.needsFallback(getFont(), getText());
    }

    private int iconSpace() {
        Icon icon = getIcon();
        return icon == null ? 0 : icon.getIconWidth() + getIconTextGap();
    }
}
