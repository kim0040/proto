package proto.ui.component;

import proto.ui.motion.Tween;
import proto.ui.theme.Fonts;
import proto.ui.theme.Glyphs;
import proto.ui.theme.Icons;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * [경계 boundary] 사이드바의 선택 목록. 선택된 항목 뒤의 둥근 알약이 새 항목으로 미끄러져 간다 (옆 막대 없음).
 */
public class PillList extends JPanel {

    private static final int ITEM_HEIGHT = 34;
    private static final int GAP = 2;

    private final List<Item> items = new ArrayList<>();
    private final Tween pillY = new Tween(0f, this::repaint);
    private final Tween pillH = new Tween(0f, this::repaint);
    private final Tween pillA = new Tween(0f, this::repaint);
    private String selectedKey;
    private boolean pillPlaced;
    private boolean glide;
    private boolean iconOnly;

    public PillList() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
    }

    /** 항목 하나를 맨 끝에 더한다. 아이콘 이름 또는 카테고리 점 중 하나로 앞 표시를 정한다 */
    public void addItem(String key, String iconName, String dotCategoryId, String text, Runnable onClick) {
        Item item = new Item(key, iconName, dotCategoryId, text, onClick);
        items.add(item);
        add(item);
        add(javax.swing.Box.createVerticalStrut(Ui.px(GAP)));
    }

    /** true면 글자 없이 아이콘(또는 점)만 가운데에 보여 준다 (좁은 사이드바 띠). 이름은 툴팁과 접근성 이름으로 남는다 */
    public void setIconOnly(boolean value) {
        iconOnly = value;
        revalidate();
        repaint();
    }

    /** 모든 항목을 지운다 (카테고리 목록을 다시 만들 때) */
    public void clear() {
        items.clear();
        removeAll();
        pillPlaced = false;
    }

    /** 선택을 바꾼다. animate가 true면 알약이 미끄러진다 */
    public void setSelected(String key, boolean animate) {
        selectedKey = key;
        glide = animate;
        for (Item i : items) {
            i.setSelectedState(i.key.equals(key), animate);
        }
        syncPill();
        repaint();
    }

    public String selectedKey() {
        return selectedKey;
    }

    @Override
    public void doLayout() {
        super.doLayout();
        syncPill();
    }

    private void syncPill() {
        Item target = null;
        for (Item i : items) {
            if (i.key.equals(selectedKey)) {
                target = i;
            }
        }
        if (target == null || target.getHeight() == 0) {
            pillA.set(0f);
            pillPlaced = false;
            return;
        }
        if (!pillPlaced || !glide) {
            pillY.set(target.getY());
            pillH.set(target.getHeight());
            pillA.set(1f);
            pillPlaced = true;
        } else {
            pillY.to(target.getY(), 260);
            pillH.to(target.getHeight(), 260);
        }
        glide = true;
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (pillA.value() > 0.01f) {
            Graphics2D g2 = (Graphics2D) g.create();
            Ui.smooth(g2);
            g2.setColor(Tokens.alpha(Tokens.get(Tokens.ACCENT_SOFT), pillA.value()));
            int arc = Ui.px(10);
            g2.fillRoundRect(0, Math.round(pillY.value()), getWidth(), Math.round(pillH.value()), arc, arc);
            g2.dispose();
        }
    }

    /** 목록의 한 줄 */
    private final class Item extends JComponent {
        private final String key;
        private Icon icon;
        private final String dotCategory;
        private final String text;
        private boolean selected;
        private final Tween hover = new Tween(0f, this::repaint);
        private final Tween sel = new Tween(0f, this::repaint);

        Item(String key, String iconName, String dotCategory, String text, Runnable onClick) {
            this.key = key;
            this.icon = iconName == null ? null : Icons.of(iconName, 16, () -> Tokens.blend(
                    Tokens.get(Tokens.MUTED), Tokens.get(Tokens.ACCENT), sel.value()));
            this.dotCategory = dotCategory;
            this.text = text;
            setFont(Fonts.medium(Fonts.BODY));
            javax.swing.ToolTipManager.sharedInstance().registerComponent(this);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFocusable(true);
            setRequestFocusEnabled(false);
            getAccessibleContext().setAccessibleName(text);
            installInput(onClick);
        }

        private void installInput(Runnable onClick) {
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover.to(1f, 120);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover.to(0f, 160);
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    onClick.run();
                }
            });
            Listeners.onKey(this, code -> {
                if (code == KeyEvent.VK_SPACE || code == KeyEvent.VK_ENTER) {
                    onClick.run();
                }
            });
            Listeners.repaintOnFocus(this);
        }

        @Override
        public javax.accessibility.AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) {
                accessibleContext = new AccessibleJComponent() {
                    @Override
                    public javax.accessibility.AccessibleRole getAccessibleRole() {
                        return javax.accessibility.AccessibleRole.PUSH_BUTTON;
                    }
                };
            }
            return accessibleContext;
        }

        void setSelectedState(boolean value, boolean animate) {
            selected = value;
            if (animate) {
                sel.to(value ? 1f : 0f, 200);
            } else {
                sel.set(value ? 1f : 0f);
            }
        }

        /** 아이콘 띠에서는 항상, 목록에서는 이름이 잘렸을 때 이름을 툴팁으로 보여 준다 */
        @Override
        public String getToolTipText(MouseEvent e) {
            FontMetrics fm = getFontMetrics(getFont());
            boolean clipped = Glyphs.width(fm, text) > getWidth() - Ui.px(12) - Ui.px(36);
            return iconOnly || clipped ? TextUtil.tip(text) : null;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(Ui.px(iconOnly ? 44 : 100), Ui.px(iconOnly ? 40 : ITEM_HEIGHT));
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, Ui.px(iconOnly ? 40 : ITEM_HEIGHT));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Ui.smooth(g2);
            int arc = Ui.px(10);
            if (!selected && hover.value() > 0.01f) {
                g2.setColor(Tokens.alpha(Tokens.get(Tokens.HOVER), hover.value() * 0.7f));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            }
            java.awt.Color fg = Tokens.blend(Tokens.get(Tokens.TEXT), Tokens.get(Tokens.ACCENT), sel.value());
            if (iconOnly) {
                paintCentered(g2);
                paintFocus(g2, arc);
                g2.dispose();
                return;
            }
            int x = Ui.px(12);
            x = paintLead(g2, x, fg);
            g2.setFont(getFont());
            g2.setColor(fg);
            FontMetrics fm = g2.getFontMetrics();
            String shown = TextUtil.ellipsize(text, fm, getWidth() - x - Ui.px(10));
            Glyphs.draw(g2, shown, x, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            paintFocus(g2, arc);
            g2.dispose();
        }

        private void paintFocus(Graphics2D g2, int arc) {
            if (isFocusOwner()) {
                g2.setColor(Tokens.alpha(Tokens.get(Tokens.ACCENT), 0.6f));
                g2.setStroke(new java.awt.BasicStroke(Ui.px(1.5f)));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, arc, arc);
            }
        }

        private void paintCentered(Graphics2D g2) {
            if (icon != null) {
                icon.paintIcon(this, g2, (getWidth() - icon.getIconWidth()) / 2,
                        (getHeight() - icon.getIconHeight()) / 2);
            } else if (dotCategory != null) {
                int d = Ui.px(10);
                g2.setColor(proto.ui.theme.CategoryColors.of(dotCategory));
                g2.fillOval((getWidth() - d) / 2, (getHeight() - d) / 2, d, d);
            }
        }

        private int paintLead(Graphics2D g2, int x, java.awt.Color fg) {
            if (icon != null) {
                icon.paintIcon(this, g2, x, (getHeight() - icon.getIconHeight()) / 2);
                return x + icon.getIconWidth() + Ui.px(10);
            }
            if (dotCategory != null) {
                int d = Ui.px(8);
                g2.setColor(proto.ui.theme.CategoryColors.of(dotCategory));
                g2.fillOval(x + Ui.px(3), (getHeight() - d) / 2, d, d);
                return x + Ui.px(3) + d + Ui.px(12);
            }
            return x;
        }
    }
}
