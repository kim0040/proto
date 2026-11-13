package proto.ui.theme;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * [경계 boundary] 글자색을 따라 색이 바뀌는 작은 SVG 아이콘 (Lucide 모양을 참고해 다시 그린 선 아이콘).
 */
public final class Icons {

    private static final Map<String, FlatSVGIcon> CACHE = new HashMap<>();

    private Icons() {
    }

    /** 토큰 색으로 칠하는 아이콘. 테마가 바뀌면 다음 그릴 때 새 색을 읽는다 */
    public static Icon of(String name, int size, String colorToken) {
        return new TintIcon(name, size, () -> Tokens.get(colorToken));
    }

    /** 임의의 색(그릴 때마다 읽음)으로 칠하는 아이콘 */
    public static Icon of(String name, int size, Supplier<Color> color) {
        return new TintIcon(name, size, color);
    }

    private static FlatSVGIcon svg(String name, int size, Color color) {
        String key = name + ":" + size + ":" + color.getRGB();
        return CACHE.computeIfAbsent(key, k -> {
            FlatSVGIcon icon = new FlatSVGIcon("proto/ui/icons/" + name + ".svg", size, size,
                    Icons.class.getClassLoader());
            icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> color));
            return icon;
        });
    }

    private static final class TintIcon implements Icon {
        private final String name;
        private final int size;
        private final Supplier<Color> color;

        TintIcon(String name, int size, Supplier<Color> color) {
            this.name = name;
            this.size = size;
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            svg(name, size, color.get()).paintIcon(c, g, x, y);
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
