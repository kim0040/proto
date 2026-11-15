package proto.ui.dialog;

import proto.ui.component.ColorLabels;

import proto.entity.CategoryPalette;
import proto.ui.theme.CategoryColors;
import proto.ui.theme.Ui;

import javax.swing.Icon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * [경계 boundary] 일정 색 고르기에 쓰는 한국어 색 이름(카테고리 색과 같은 ColorLabels 이름)과, 일정 오른쪽 클릭 메뉴의 "색 바꾸기" 하위 메뉴.
 * ScheduleDialog의 색 줄(ColorSwatchRow), UiActions의 일정 메뉴, 명령 메뉴(CommandCatalog)가 같은 이름을 쓰도록 한곳에 둔다 (F3 일정 수정).
 * 하위 메뉴는 맨 위 "카테고리 색"(일정만의 색 없음) 다음에 팔레트 색 12개를 색 점과 함께 보여 주고, 지금 색에 체크를 건다.
 */
public final class ColorMenu {

    /** 일정만의 색이 없을 때(카테고리 색을 따를 때)의 이름 */
    public static final String FOLLOW_CATEGORY = "카테고리 색";

    private ColorMenu() {
    }

    /** 색 키의 한국어 이름 (null이면 "카테고리 색", 모르는 키는 그대로) */
    public static String nameOf(String key) {
        return key == null ? FOLLOW_CATEGORY : ColorLabels.of(key);
    }

    /**
     * 일정 메뉴에 넣을 "색 바꾸기" 하위 메뉴.
     *
     * @param current       지금 일정만의 색 키 (없으면 null)
     * @param categoryColor "카테고리 색" 항목에 칠할 지금 카테고리의 색
     * @param onPick        색을 고르면 부른다 (null이면 카테고리 색으로 되돌리기)
     */
    public static JMenu submenu(String current, Supplier<Color> categoryColor, Consumer<String> onPick) {
        JMenu menu = new JMenu("색 바꾸기");
        menu.add(item(FOLLOW_CATEGORY, categoryColor, current == null, () -> onPick.accept(null)));
        for (String key : CategoryPalette.KEYS) {
            menu.add(item(nameOf(key), () -> CategoryColors.ofKey(key), key.equals(current), () -> onPick.accept(key)));
        }
        return menu;
    }

    private static JCheckBoxMenuItem item(String text, Supplier<Color> color, boolean checked, Runnable action) {
        JCheckBoxMenuItem i = new JCheckBoxMenuItem(text, new DotIcon(color), checked);
        i.setIconTextGap(Ui.px(10));
        i.addActionListener(e -> action.run());
        return i;
    }

    /** 메뉴 항목 앞의 색 점 */
    private record DotIcon(Supplier<Color> color) implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            Ui.smooth(g2);
            g2.setColor(color.get());
            int d = Ui.px(12);
            g2.fillOval(x, y + (getIconHeight() - d) / 2, d, d);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return Ui.px(14);
        }

        @Override
        public int getIconHeight() {
            return Ui.px(16);
        }
    }
}
