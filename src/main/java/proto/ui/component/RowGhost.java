package proto.ui.component;

import proto.ui.motion.Motion;

import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;

/**
 * [경계 boundary] 목록에서 지워진 일정 행이 사라지기 전 잠깐(약 100ms) 제자리에서 흐려지며 남는 그림자.
 * OccurrenceList가 행을 지우기 직전의 모습을 찍어 만든다. 진짜 행이 아니므로 마우스·키보드를 받지 않는다 (지워진 일정이 눌려 열리는 일이 없다).
 */
final class RowGhost extends JComponent {

    private static final int FADE_MS = 100;

    private final BufferedImage image;
    private float alpha = 1f;
    private Motion.Handle handle;

    private RowGhost(BufferedImage image, Rectangle bounds) {
        this.image = image;
        setBounds(bounds);
        setOpaque(false);
        setFocusable(false);
    }

    /** row의 지금 모습과 자리를 찍는다 */
    static RowGhost of(OccurrenceRow row) {
        GraphicsConfiguration gc = row.getGraphicsConfiguration();
        double scale = gc == null ? 1.0 : gc.getDefaultTransform().getScaleX();
        BufferedImage img = new BufferedImage(Math.max(1, (int) Math.ceil(row.getWidth() * scale)),
                Math.max(1, (int) Math.ceil(row.getHeight() * scale)), BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = img.createGraphics();
        g.scale(scale, scale);
        row.printAll(g);
        g.dispose();
        return new RowGhost(img, row.getBounds());
    }

    /** 흐려지며 사라진 뒤 whenGone을 부른다 */
    void fadeOut(Runnable whenGone) {
        handle = Motion.run(FADE_MS, Motion.EXIT, f -> {
            alpha = 1f - f;
            repaint();
        }, whenGone);
    }

    /** 흐려지기를 멈춘다 (다른 변화가 이어질 때) */
    void cancel() {
        if (handle != null) {
            handle.cancel();
            handle = null;
        }
    }

    /** 어떤 마우스 동작도 받지 않는다 */
    @Override
    public boolean contains(int x, int y) {
        return false;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, Math.min(1f, alpha))));
        g2.drawImage(image, 0, 0, getWidth(), getHeight(), null);
        g2.dispose();
    }
}
