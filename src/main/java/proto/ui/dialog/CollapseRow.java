package proto.ui.dialog;

import proto.ui.motion.Motion;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.AlphaComposite;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

/**
 * [경계 boundary] 내용이 부드럽게 접히고 펴지는 행: 높이가 줄어들며 내용이 흐려진다 (160ms, 애니메이션을 끄면 바로 바뀐다).
 * ScheduleDialog가 "하루 종일"을 켜면 시간·알림 줄을 접는 데 쓴다. 접힌 내용은 보이지 않을 뿐 아니라 키보드 포커스도 받지 않는다.
 * 높이가 바뀌는 동안 onResize를 불러 시트가 크기를 다시 맞추게 한다.
 */
final class CollapseRow extends JPanel {

    private static final int DURATION_MS = 160;

    private final JComponent content;
    private final Runnable onResize;
    private float open = 1f;
    private Motion.Handle running;

    CollapseRow(JComponent content, Runnable onResize) {
        super(null);
        this.content = content;
        this.onResize = onResize;
        setOpaque(false);
        add(content);
    }

    /** 접거나 편다. animate가 false이거나 애니메이션이 꺼져 있으면 바로 바뀐다 */
    void setCollapsed(boolean collapsed, boolean animate) {
        float to = collapsed ? 0f : 1f;
        if (running != null) {
            running.cancel();
            running = null;
        }
        if (!animate || !Motion.enabled()) {
            apply(to);
            return;
        }
        float from = open;
        content.setVisible(true);
        running = Motion.run(DURATION_MS, f -> apply(from + (to - from) * f), () -> apply(to));
    }

    private void apply(float value) {
        open = Math.max(0f, Math.min(1f, value));
        content.setVisible(open > 0.001f);
        revalidate();
        repaint();
        onResize.run();
    }

    boolean isCollapsed() {
        return open <= 0.001f;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = content.getPreferredSize();
        return new Dimension(d.width, Math.round(d.height * open));
    }

    @Override
    public void doLayout() {
        content.setBounds(0, 0, getWidth(), content.getPreferredSize().height);
    }

    /** 접히는 동안 내용이 서서히 흐려진다 (그림만 바꾸고, 평소에는 그대로 그린다) */
    @Override
    protected void paintChildren(Graphics g) {
        if (open >= 0.999f) {
            super.paintChildren(g);
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, open)));
        super.paintChildren(g2);
        g2.dispose();
    }
}
