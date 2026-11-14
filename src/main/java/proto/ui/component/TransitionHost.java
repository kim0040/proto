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
 * MainFrame이 본문 영역으로 쓴다 (보기 이동, 이전/다음 이동). 전환 방법: 바꾸기 전 모습(before)을 그림으로 찍고, 내용을 바꾼 뒤 모습(after)도 찍어서,
 * 두 그림을 겹쳐 그리는 방식이라 실제 컴포넌트가 움직이지 않는다. 그래서 어떤 보기든 같은 코드로 전환 효과를 줄 수 있다.
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

    /** 지금 모습을 그림으로 찍는다. 고해상도 화면(배율 2배 등)에서도 흐려지지 않도록 화면 배율만큼 크게 그린다. */
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

    /** 전환 중에는 진짜 내용을 그리지 않는다. 두 그림(before, after)이 그 자리를 대신해야 하기 때문이다. */
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
