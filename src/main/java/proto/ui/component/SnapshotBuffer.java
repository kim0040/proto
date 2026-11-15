package proto.ui.component;

import javax.swing.JComponent;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.image.BufferedImage;

/**
 * [경계 boundary] 컴포넌트 모습을 사진으로 찍어 두는 다시 쓰는 그림판.
 * TransitionHost가 나가는 화면을 찍어 둘 때 쓴다. 전환마다 큰 그림(고해상도에서 15MB 안팎)을 새로 만들지 않도록 크기가 같으면 같은 그림을 다시 쓴다.
 * 화면 배율(2배 등)만큼 크게 그려서 고해상도에서도 흐려지지 않는다.
 */
final class SnapshotBuffer {

    private BufferedImage image;
    private double scale = 1.0;

    /** 지금 모습을 이 그림판에 찍는다 (printAll이라 보이지 않는 부분도 평소대로 그려진다) */
    void capture(JComponent source) {
        Graphics2D g = begin(source);
        source.printAll(g);
        g.dispose();
    }

    /** 그림판을 source 크기에 맞추고, 그릴 수 있는 Graphics를 돌려준다 (논리 좌표로 그리면 된다) */
    Graphics2D begin(JComponent source) {
        GraphicsConfiguration gc = source.getGraphicsConfiguration();
        scale = gc == null ? 1.0 : gc.getDefaultTransform().getScaleX();
        int w = Math.max(1, (int) Math.ceil(source.getWidth() * scale));
        int h = Math.max(1, (int) Math.ceil(source.getHeight() * scale));
        if (image == null || image.getWidth() != w || image.getHeight() != h) {
            image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
        }
        Graphics2D g = image.createGraphics();
        g.scale(scale, scale);
        return g;
    }

    /** 찍어 둔 모습을 alpha(0~1) 투명도로 w×h(논리 크기) 칸에 그린다 */
    void draw(Graphics2D g, int w, int h, float alpha) {
        if (image == null || alpha <= 0.002f) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.SrcOver.derive(Math.min(1f, alpha)));
        g2.drawImage(image, 0, 0, w, h, null);
        g2.dispose();
    }

    /** 큰 그림을 놓아 준다 */
    void release() {
        image = null;
    }
}
