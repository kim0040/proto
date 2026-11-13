package proto.ui.component;

import proto.ui.motion.Motion;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.image.BufferedImage;

/**
 * [경계 boundary] 화면 내용을 바꿀 때 이전·이후 모습을 사진으로 찍어 크로스페이드 + 살짝 미끄러지게 보여 주는 틀.
 * 보기 전환은 위로 12px, 주/월 이동은 좌우로 미끄러진다. 애니메이션이 꺼져 있으면 즉시 바꾼다.
 */
public class TransitionHost extends JPanel {

    /** 새 내용이 들어오는 방향 */
    public enum Dir { UP, FROM_RIGHT, FROM_LEFT, NONE }

    private static final int DURATION_MS = 240;

    private BufferedImage before;
    private BufferedImage after;
    private Dir dir = Dir.NONE;
    private float progress;
    private boolean running;
    private Motion.Handle handle;

    public TransitionHost() {
        super(new BorderLayout());
        setOpaque(true);
    }

    /** 애니메이션 없이 내용을 정한다 */
    public void setContent(JComponent content) {
        removeAll();
        add(content, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    /** change로 내용을 바꾸면서 dir 방향의 전환 효과를 보여 준다 */
    public void transition(Dir direction, Runnable change) {
        transition(direction, change, () -> { });
    }

    /** 전환이 끝난 뒤(애니메이션이 없으면 바로) onDone을 한 번 부른다 */
    public void transition(Dir direction, Runnable change, Runnable onDone) {
        if (running && handle != null) {
            handle.finish();
        }
        if (!Motion.enabled() || !isShowing() || getWidth() <= 0 || getHeight() <= 0
                || direction == Dir.NONE) {
            change.run();
            revalidate();
            repaint();
            onDone.run();
            return;
        }
        before = snapshot();
        change.run();
        validate();
        after = snapshot();
        dir = direction;
        progress = 0f;
        running = true;
        handle = Motion.run(DURATION_MS, f -> {
            progress = f;
            repaint();
        }, () -> {
            finishTransition();
            onDone.run();
        });
    }

    private void finishTransition() {
        running = false;
        before = null;
        after = null;
        repaint();
    }

    private BufferedImage snapshot() {
        GraphicsConfiguration gc = getGraphicsConfiguration();
        double scale = gc == null ? 1.0 : gc.getDefaultTransform().getScaleX();
        BufferedImage img = new BufferedImage(Math.max(1, (int) Math.ceil(getWidth() * scale)),
                Math.max(1, (int) Math.ceil(getHeight() * scale)), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.scale(scale, scale);
        printAll(g);
        g.dispose();
        return img;
    }

    @Override
    protected void paintChildren(Graphics g) {
        if (!running) {
            super.paintChildren(g);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(Tokens.get(Tokens.BG));
        g2.fillRect(0, 0, getWidth(), getHeight());
        if (running && before != null && after != null) {
            paintFrame(g2);
        }
        g2.dispose();
    }

    private void paintFrame(Graphics2D g2) {
        int dist = dir == Dir.UP ? Ui.px(12) : Ui.px(36);
        float rest = 1f - progress;
        int dx = 0;
        int dy = 0;
        int oldDx = 0;
        if (dir == Dir.UP) {
            dy = Math.round(dist * rest);
        } else {
            int sign = dir == Dir.FROM_RIGHT ? 1 : -1;
            dx = Math.round(sign * dist * rest);
            oldDx = Math.round(-sign * dist * 0.5f * progress);
        }
        g2.drawImage(before, oldDx, 0, getWidth(), getHeight(), null);
        g2.setComposite(AlphaComposite.SrcOver.derive(Math.min(1f, progress * 1.15f)));
        g2.drawImage(after, dx, dy, getWidth(), getHeight(), null);
    }
}
