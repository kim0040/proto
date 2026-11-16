package proto.ui.component;

import proto.ui.layout.ColumnLayout;
import proto.ui.motion.Tween;
import proto.ui.theme.CategoryColors;
import proto.ui.theme.Fonts;
import proto.ui.theme.Glyphs;
import proto.ui.theme.Icons;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.ToolTipManager;
import java.awt.BasicStroke;
import java.awt.Color;
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
 * [경계 boundary] 사이드바의 선택 목록. 선택된 항목 뒤의 둥근 알약이 새 항목으로 스프링으로 미끄러져 가며 살짝 튄다 (옆 막대 없음).
 * Sidebar와 SidebarRail의 선택 목록이다 (보기 이동, 카테고리 필터 F7). 항목마다 따로 선택 배경을 그리지 않고, 목록 뒤에 알약 하나를 그려서 선택이 바뀌면 그 알약을 새 위치로 움직인다.
 */
public class PillList extends JPanel {

    private static final int ITEM_HEIGHT = 34;
    private static final int GAP = 2;
    private static final int SPRING_MS = 340;
    private static final int GLIDE_MS = 260;

    private final List<Item> items = new ArrayList<>();
    private final Tween pillY = new Tween(0f, this::repaint);
    private final Tween pillH = new Tween(0f, this::repaint);
    private final Tween pillA = new Tween(0f, this::repaint);
    private String selectedKey;
    private boolean pillPlaced;
    private boolean glide;
    /** 방금 선택이 바뀌었으면 다음 syncPill 한 번은 스프링으로 움직인다 (창 크기 변화로 다시 배치될 때는 부드러운 ease-out) */
    private boolean springNext;
    private boolean iconOnly;

    public PillList() {
        setLayout(ColumnLayout.fill());
        setOpaque(false);
    }

    /** 항목 하나를 맨 끝에 더한다. 아이콘 이름 또는 카테고리 색 칩 중 하나로 앞 표시를 정한다 */
    public void addItem(String key, String iconName, String dotCategoryId, String text, Runnable onClick) {
        Item item = new Item(key, iconName, dotCategoryId, text, onClick);
        items.add(item);
        add(item);
        add(Box.createVerticalStrut(Ui.px(GAP)));
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
        springNext = animate;
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

    @Override
    protected void paintComponent(Graphics g) {
        if (pillA.value() > 0.01f) {
            Graphics2D g2 = (Graphics2D) g.create();
            Ui.smooth(g2);
            g2.setColor(Tokens.alpha(Tokens.get(Tokens.ACCENT_SOFT), pillA.value()));
            int arc = Ui.px(10);
            // 스프링이 목록 위·아래로 넘쳐 잘려 보이지 않게, 알약을 목록 안으로 가둔다. 높이는 0 밑으로 내려가지 않는다.
            int h = Math.max(0, Math.round(pillH.value()));
            int y = Math.max(0, Math.min(Math.round(pillY.value()), getHeight() - h));
            g2.fillRoundRect(0, y, getWidth(), h, arc, arc);
            g2.dispose();
        }
    }

    /**
     * 알약을 선택된 항목의 위치·높이에 맞춘다. 처음 놓을 때나 애니메이션이 필요 없을 때는 바로 놓고, 그 뒤로는 미끄러진다
     * (선택이 바뀐 때는 스프링 340ms, 그 밖에는 ease-out 260ms). 선택된 항목이 아직 배치되지 않았으면(높이 0) 알약을 숨긴다.
     */
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
        } else if (springNext) {
            pillY.springTo(target.getY(), SPRING_MS);
            pillH.springTo(target.getHeight(), SPRING_MS);
        } else {
            pillY.to(target.getY(), GLIDE_MS);
            pillH.to(target.getHeight(), GLIDE_MS);
        }
        springNext = false;
        glide = true;
    }

    /** 목록의 한 줄 */
    private final class Item extends JComponent {
        private final String key;
        private final Icon icon;
        private final String dotCategory;
        private final String text;
        private final Tween hover = new Tween(0f, this::repaint);
        private final Tween sel = new Tween(0f, this::repaint);
        private boolean selected;

        Item(String key, String iconName, String dotCategory, String text, Runnable onClick) {
            this.key = key;
            this.icon = iconName == null ? null : Icons.of(iconName, 16, () -> Tokens.blend(
                    Tokens.get(Tokens.MUTED), Tokens.get(Tokens.ACCENT), sel.value()));
            this.dotCategory = dotCategory;
            this.text = text;
            setFont(Fonts.medium(Fonts.BODY));
            ToolTipManager.sharedInstance().registerComponent(this);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFocusable(true);
            setRequestFocusEnabled(false);
            getAccessibleContext().setAccessibleName(text);
            installInput(onClick);
        }

        void setSelectedState(boolean value, boolean animate) {
            selected = value;
            if (animate) {
                sel.to(value ? 1f : 0f, 200);
            } else {
                sel.set(value ? 1f : 0f);
            }
        }

        @Override
        public AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) {
                accessibleContext = new AccessibleJComponent() {
                    @Override
                    public AccessibleRole getAccessibleRole() {
                        return AccessibleRole.PUSH_BUTTON;
                    }
                };
            }
            return accessibleContext;
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
            Color fg = Tokens.blend(Tokens.get(Tokens.TEXT), Tokens.get(Tokens.ACCENT), sel.value());
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

                /** 사이드바 항목은 macOS 사이드바처럼 누르는 순간 고른다 (떼기를 기다리지 않아 반응이 바로 보인다) */
                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON1 && !e.isPopupTrigger()) {
                        onClick.run();
                    }
                }
            });
            Listeners.onKey(this, code -> {
                if (code == KeyEvent.VK_SPACE || code == KeyEvent.VK_ENTER) {
                    onClick.run();
                }
            });
            Listeners.repaintOnFocus(this);
        }

        private void paintFocus(Graphics2D g2, int arc) {
            if (isFocusOwner()) {
                g2.setColor(Tokens.alpha(Tokens.get(Tokens.ACCENT), 0.6f));
                g2.setStroke(new BasicStroke(Ui.px(1.5f)));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, arc, arc);
            }
        }

        private void paintCentered(Graphics2D g2) {
            if (icon != null) {
                icon.paintIcon(this, g2, (getWidth() - icon.getIconWidth()) / 2,
                        (getHeight() - icon.getIconHeight()) / 2);
            } else if (dotCategory != null) {
                int d = Ui.px(10);
                CategoryDot.paint(g2, (getWidth() - d) / 2, (getHeight() - d) / 2, d,
                        CategoryColors.of(dotCategory));
            }
        }

        private int paintLead(Graphics2D g2, int x, Color fg) {
            if (icon != null) {
                icon.paintIcon(this, g2, x, (getHeight() - icon.getIconHeight()) / 2);
                return x + icon.getIconWidth() + Ui.px(10);
            }
            if (dotCategory != null) {
                int d = Ui.px(10);
                CategoryDot.paint(g2, x + Ui.px(2), (getHeight() - d) / 2, d, CategoryColors.of(dotCategory));
                return x + Ui.px(2) + d + Ui.px(11);
            }
            return x;
        }
    }
}
