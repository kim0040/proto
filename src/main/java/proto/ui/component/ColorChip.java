package proto.ui.component;

import proto.ui.theme.CategoryColors;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * [경계 boundary] 카테고리의 지금 색을 보여 주는 눌러서 쓰는 둥근 네모 칩 (누르면 색 고르기가 열린다, F7).
 * CategoryDialog의 카테고리 줄 맨 앞에 놓인다. 마우스를 올리면 테두리가 생기고, 키보드로는 Tab으로 와서 Space/Enter로 연다.
 */
public class ColorChip extends JComponent {

    private static final int SIZE = 20;

    private final String categoryId;
    private boolean hover;

    public ColorChip(String categoryId, String name, Consumer<ColorChip> onClick) {
        this.categoryId = categoryId;
        setOpaque(false);
        setFocusable(true);
        setRequestFocusEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText("색 바꾸기");
        getAccessibleContext().setAccessibleName(name + " 색 바꾸기");
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                setHover(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setHover(false);
            }
        });
        Listeners.onClick(this, e -> onClick.accept(this));
        Listeners.onKey(this, code -> {
            if (code == KeyEvent.VK_SPACE || code == KeyEvent.VK_ENTER) {
                onClick.accept(ColorChip.this);
            }
        });
        Listeners.repaintOnFocus(this);
    }

    /** 이 칩이 보여 주는 카테고리 id */
    public String categoryId() {
        return categoryId;
    }

    private void setHover(boolean value) {
        hover = value;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Ui.px(SIZE + 8), Ui.px(SIZE + 8));
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.smooth(g2);
        int d = Ui.px(SIZE);
        int x = (getWidth() - d) / 2;
        int y = (getHeight() - d) / 2;
        CategoryDot.paint(g2, x, y, d, CategoryColors.of(categoryId));
        if (hover || isFocusOwner()) {
            int gap = Ui.px(3);
            int arc = Math.round((d + gap * 2) * 0.6f);
            g2.setColor(isFocusOwner() ? Tokens.get(Tokens.ACCENT) : Tokens.get(Tokens.BORDER_STRONG));
            g2.setStroke(new BasicStroke(Ui.px(isFocusOwner() ? 2f : 1.5f)));
            g2.drawRoundRect(x - gap, y - gap, d + gap * 2, d + gap * 2, arc, arc);
        }
        g2.dispose();
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
}
