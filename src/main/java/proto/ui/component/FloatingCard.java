package proto.ui.component;

import proto.ui.motion.Motion;
import proto.ui.motion.Tween;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;

/**
 * [경계 boundary] 토스트·알림 카드의 공통 바탕: 둥근 면 + 얇은 테두리 + 옅은 그림자.
 * ToastCard, ReminderCard, MoreCard의 부모 클래스이다. Overlay가 이 카드들을 창 위에 쌓는다. 카드 둘레에 투명 여백(MARGIN)을 두어 그림자가 잘리지 않게 한다.
 * 나타날 때 아래에서 22px 올라오며 스프링으로 살짝 넘었다 자리 잡고, 사라질 때는 튀지 않고 서서히 흐려진다.
 */
public abstract class FloatingCard extends JPanel {

    /** 그림자와 미끄러짐이 들어갈 투명 여백 */
    public static final int MARGIN = 20;
    /** 등장할 때 올라오는 거리 (논리 px) */
    private static final int SLIDE = 22;

    private final Tween appear = new Tween(0f, this::repaint);
    private boolean closing;

    protected FloatingCard(LayoutManager layout) {
        super(layout);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(MARGIN + 12, MARGIN + 16, MARGIN + 12, MARGIN + 16));
    }

    /** 나타나는 모션을 시작한다 */
    public void playIn() {
        appear.set(0f);
        appear.springTo(1f, 380);
    }

    /** 사라지는 모션 뒤에 after를 부른다 (한 번만) */
    public void playOut(Runnable after) {
        if (closing) {
            return;
        }
        closing = true;
        float from = appear.value();
        Motion.run(180, f -> appear.set(from * (1f - f)), after);
    }

    public boolean isClosing() {
        return closing;
    }

    @Override
    public void paint(Graphics g) {
        float a = appear.value();
        if (a <= 0.005f) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.translate(0, Math.round((1f - a) * Ui.px(SLIDE)));
        g2.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, Math.min(1f, a))));
        super.paint(g2);
        g2.dispose();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.smooth(g2);
        int m = Ui.px(MARGIN);
        int w = getWidth() - m * 2;
        int h = getHeight() - m * 2;
        int arc = Ui.px(14);
        for (int i = 3; i >= 1; i--) {
            g2.setColor(Tokens.alpha(java.awt.Color.BLACK, 0.035f));
            g2.fillRoundRect(m - i * 2, m - i * 2 + Ui.px(3), w + i * 4, h + i * 4, arc + i * 3, arc + i * 3);
        }
        g2.setColor(Tokens.get(Tokens.SURFACE));
        g2.fillRoundRect(m, m, w, h, arc, arc);
        g2.setColor(Tokens.get(Tokens.BORDER_STRONG));
        g2.drawRoundRect(m, m, w - 1, h - 1, arc, arc);
        g2.dispose();
    }

    /** 투명 여백은 카드가 아닌 것으로 친다. 그래야 카드 주변의 마우스가 아래 화면으로 통과한다. */
    @Override
    public boolean contains(int x, int y) {
        int m = Ui.px(MARGIN);
        return x >= m && y >= m && x < getWidth() - m && y < getHeight() - m;
    }
}
