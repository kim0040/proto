package proto.ui.dialog;

import com.formdev.flatlaf.FlatClientProperties;
import proto.entity.Category;
import proto.ui.component.TokenLabel;
import proto.ui.theme.CategoryColors;
import proto.ui.theme.Fonts;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.Function;

/**
 * [경계 boundary] 대화상자 입력 폼을 만드는 도우미: 라벨+입력+오류 묶음, 점이 있는 카테고리 콤보 등.
 * ScheduleDialog와 FreeSlotDialog가 입력 폼을 만들 때 쓴다 (F1, F12). 이름/입력/오류를 세로로 묶는 모양을 한곳에 두어 모든 입력칸이 같은 간격을 갖게 한다.
 */
final class FormKit {

    private FormKit() {
    }

    /** "라벨 / 입력 / 오류" 세로 묶음 */
    static JPanel block(String label, JComponent input, JLabel error) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        p.setAlignmentX(0f);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.gridy = 0;
        c.insets = new Insets(0, 0, Ui.px(6), 0);
        p.add(new TokenLabel(label, Fonts.medium(Fonts.CAPTION), Tokens.MUTED), c);
        c.gridy = 1;
        c.insets = new Insets(0, 0, 0, 0);
        p.add(input, c);
        c.gridy = 2;
        c.insets = new Insets(Ui.px(4), 0, 0, 0);
        p.add(error, c);
        return p;
    }

    /** 가로 폭이 넉넉한 기본 입력칸 스타일 */
    static void styleField(JTextField f) {
        f.setFont(Fonts.regular(Fonts.BODY));
        f.putClientProperty(FlatClientProperties.STYLE, "arc:10; margin:6,12,6,12;"
                + " borderColor:$proto.borderStrong; background:$proto.surface");
    }

    /** 카테고리를 점 + 이름으로 보여 주는 콤보 */
    static JComboBox<Category> categoryCombo(java.util.List<Category> categories) {
        JComboBox<Category> combo = new JComboBox<>(categories.toArray(new Category[0]));
        combo.setFont(Fonts.regular(Fonts.BODY));
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof Category c) {
                    l.setText(c.getName());
                    l.setIcon(new DotIcon(c.getId()));
                    l.setIconTextGap(Ui.px(10));
                }
                return l;
            }
        });
        return combo;
    }

    /** 일반 값을 글자로 보여 주는 콤보 */
    static <T> JComboBox<T> labelCombo(T[] items, Function<T, String> label) {
        JComboBox<T> combo = new JComboBox<>(items);
        combo.setFont(Fonts.regular(Fonts.BODY));
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            @SuppressWarnings("unchecked")
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean selected, boolean focus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
                l.setText(value == null ? "" : label.apply((T) value));
                return l;
            }
        });
        return combo;
    }

    /** 카테고리 색 점 아이콘 */
    static final class DotIcon implements Icon {
        private final String categoryId;

        DotIcon(String categoryId) {
            this.categoryId = categoryId;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            Ui.smooth(g2);
            g2.setColor(CategoryColors.of(categoryId));
            g2.fillOval(x, y + (getIconHeight() - Ui.px(10)) / 2, Ui.px(10), Ui.px(10));
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return Ui.px(10);
        }

        @Override
        public int getIconHeight() {
            return Ui.px(16);
        }
    }
}
