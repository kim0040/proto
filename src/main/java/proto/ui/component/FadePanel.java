package proto.ui.component;

import proto.ui.motion.Tween;
import proto.ui.theme.Tokens;

import javax.swing.JPanel;
import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;

/**
 * [경계 boundary] 열릴 때 투명도와 크기(97%→100%)가 부드럽게 변하는 대화상자 내용 판.
 * Window.setOpacity를 쓰지 않고 직접 그려서 장식된 창에서도 잘 동작한다.
 */
public class FadePanel extends JPanel {

    private final Tween intro = new Tween(1f, this::repaint);

    public FadePanel(LayoutManager layout) {
        super(layout);
        setOpaque(true);
    }

    /** 열릴 때 한 번 부른다. 애니메이션이 꺼져 있으면 바로 보인다 */
    public void playIntro() {
        intro.set(0f);
        intro.to(1f, 200);
    }

    @Override
    public java.awt.Color getBackground() {
        return Tokens.get(Tokens.SURFACE);
    }

    @Override
    public void paint(Graphics g) {
        float p = intro.value();
        if (p >= 0.999f) {
            super.paint(g);
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(getBackground());
        g2.fillRect(0, 0, getWidth(), getHeight());
        float s = 0.97f + 0.03f * p;
        g2.translate(getWidth() / 2.0, getHeight() / 2.0);
        g2.scale(s, s);
        g2.translate(-getWidth() / 2.0, -getHeight() / 2.0);
        g2.setComposite(AlphaComposite.SrcOver.derive(p));
        super.paint(g2);
        g2.dispose();
    }
}
