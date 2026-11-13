package proto.ui.component;

import proto.ui.motion.Tween;
import proto.ui.theme.Glyphs;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;

/**
 * [경계 boundary] 한 줄 제목 라벨. 완료되면 글자색이 흐려지며 취소선이 그어진다 (애니메이션 포함).
 */
public class StrikeLabel extends JComponent {

    private String text = "";
    private String colorToken = Tokens.TEXT;
    private final Tween done = new Tween(0f, this::repaint);

    public StrikeLabel(java.awt.Font font) {
        setFont(font);
        setOpaque(false);
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    public void setText(String rawValue) {
        String value = TextUtil.clean(rawValue);
        if (!value.equals(text)) {
            text = value;
            revalidate();
            repaint();
        }
    }

    public boolean isEmpty() {
        return text.isEmpty();
    }

    public void setColorToken(String token) {
        colorToken = token;
        repaint();
    }

    /** 완료 모양 여부. animate면 글자색과 취소선이 부드럽게 바뀐다 */
    public void setDone(boolean value, boolean animate) {
        float target = value ? 1f : 0f;
        if (animate) {
            done.to(target, 220);
        } else {
            done.set(target);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(Glyphs.width(fm, text), fm.getHeight());
    }

    /** 잘려서 "…"로 보일 때만 전체 글을 툴팁으로 보여 준다 */
    @Override
    public String getToolTipText(MouseEvent e) {
        boolean clipped = getWidth() > 0 && Glyphs.width(getFontMetrics(getFont()), text) > getWidth();
        return clipped ? TextUtil.tip(text) : null;
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(Ui.px(24), getPreferredSize().height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.smooth(g2);
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        String shown = TextUtil.ellipsize(text, fm, getWidth());
        g2.setColor(Tokens.blend(Tokens.get(colorToken), Tokens.get(Tokens.MUTED), done.value()));
        int baseline = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
        Glyphs.draw(g2, shown, 0, baseline);
        if (done.value() > 0.01f) {
            int w = Math.round(Glyphs.width(fm, shown) * done.value());
            int y = baseline - fm.getAscent() / 3;
            g2.fillRect(0, y, w, Math.max(1, Ui.px(1)));
        }
        g2.dispose();
    }
}
