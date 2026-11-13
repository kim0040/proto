package proto.ui.theme;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.JFrame;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Taskbar;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * [경계 boundary] 앱 아이콘 PNG(여러 크기)를 읽어 창, 작업 표시줄/독, 화면 안 작은 로고에 연결한다.
 * 파일이 없으면 아무것도 하지 않고 넘어간다.
 */
public final class AppIcon {

    private static final int[] SIZES = {16, 32, 48, 64, 128, 256, 512, 1024};
    private static final String PATH = "/proto/ui/appicon/icon-%d.png";

    private static TreeMap<Integer, Image> cache;

    private AppIcon() {
    }

    private static synchronized TreeMap<Integer, Image> load() {
        if (cache == null) {
            cache = new TreeMap<>();
            for (int size : SIZES) {
                try (InputStream in = AppIcon.class.getResourceAsStream(String.format(PATH, size))) {
                    if (in != null) {
                        Image image = ImageIO.read(in);
                        if (image != null) {
                            cache.put(size, image);
                        }
                    }
                } catch (IOException e) {
                    // 읽을 수 없는 크기는 건너뛴다
                }
            }
        }
        return cache;
    }

    /** 읽을 수 있었던 모든 크기 (없으면 빈 목록) */
    public static List<Image> images() {
        return new ArrayList<>(load().values());
    }

    /** 창 제목줄(Windows)과 독/작업 표시줄(macOS 등)에 아이콘을 연결한다 */
    public static void apply(JFrame frame) {
        List<Image> all = images();
        if (all.isEmpty()) {
            return;
        }
        frame.setIconImages(all);
        try {
            if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
                Taskbar.getTaskbar().setIconImage(all.get(all.size() - 1));
            }
        } catch (SecurityException | UnsupportedOperationException e) {
            // 독 아이콘을 바꿀 수 없는 환경이면 창 아이콘만 쓴다
        }
    }

    /** 화면 안에 그릴 px 크기의 로고. 아이콘 파일이 없으면 null */
    public static Icon icon(int px) {
        return load().isEmpty() ? null : new Logo(px);
    }

    /** 부드럽게 줄여 그리는 로고 (HiDPI에서는 두 배 크기 원본을 고른다) */
    private static final class Logo implements Icon {
        private final int size;

        Logo(int size) {
            this.size = size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            double scale = c == null || c.getGraphicsConfiguration() == null ? 1
                    : c.getGraphicsConfiguration().getDefaultTransform().getScaleX();
            int wanted = (int) Math.ceil(Ui.px(size) * Math.max(1, scale));
            Image image = load().ceilingEntry(wanted) != null ? load().ceilingEntry(wanted).getValue()
                    : load().lastEntry().getValue();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.drawImage(image, x, y, Ui.px(size), Ui.px(size), null);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return Ui.px(size);
        }

        @Override
        public int getIconHeight() {
            return Ui.px(size);
        }
    }
}
