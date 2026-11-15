package proto.ui.component;

import proto.ui.theme.CategoryColors;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * [경계 boundary] 색 견본을 격자로 늘어놓고 하나를 고르게 하는 컴포넌트 (고른 것은 체크 표시, 화살표 키로 이동, Space/Enter로 선택).
 * 설정의 "강조색" 줄(동그라미 견본)과 카테고리 색 고르기 팝오버(ColorSwatchPopover, 둥근 네모 견본)가 같이 쓴다 (F7).
 * 색은 Supplier로 받아 그릴 때마다 읽으므로 테마가 바뀌면 견본도 바로 따라 바뀐다.
 */
public class SwatchGrid extends JComponent {

    /** 견본 하나: 저장에 쓰는 id, 화면·툴팁에 쓰는 이름, 지금 색을 알려 주는 함수 */
    public record Swatch(String id, String label, Supplier<Color> color) {
    }

    private static final int CELL = 32;
    private static final int SIZE = 24;

    private final List<Swatch> swatches;
    private final int columns;
    private final boolean round;
    private final List<Consumer<String>> listeners = new ArrayList<>();
    private int selected;
    private int focused;
    private int hovered = -1;

    public SwatchGrid(List<Swatch> swatches, int columns, boolean round, String selectedId) {
        this.swatches = List.copyOf(swatches);
        this.columns = Math.max(1, columns);
        this.round = round;
        this.selected = Math.max(0, indexOf(selectedId));
        this.focused = selected;
        setOpaque(false);
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText("");
        installInput();
        Listeners.repaintOnFocus(this);
    }

    private void installInput() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int i = indexAt(e.getX(), e.getY());
                if (i >= 0) {
                    focused = i;
                    choose(i);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setHovered(-1);
            }
        });
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                setHovered(indexAt(e.getX(), e.getY()));
            }
        });
        Listeners.onKey(this, this::onKey);
    }

    /** 화살표·Home·End로 포커스를 옮기고, Space/Enter로 고른다. 줄 끝에서는 반대쪽으로 넘어가지 않고 멈춘다 */
    private void onKey(int code) {
        int n = swatches.size();
        switch (code) {
            case KeyEvent.VK_LEFT -> moveFocus(Math.max(0, focused - 1));
            case KeyEvent.VK_RIGHT -> moveFocus(Math.min(n - 1, focused + 1));
            case KeyEvent.VK_UP -> moveFocus(focused - columns >= 0 ? focused - columns : focused);
            case KeyEvent.VK_DOWN -> moveFocus(focused + columns < n ? focused + columns : focused);
            case KeyEvent.VK_HOME -> moveFocus(0);
            case KeyEvent.VK_END -> moveFocus(n - 1);
            case KeyEvent.VK_SPACE, KeyEvent.VK_ENTER -> choose(focused);
            default -> { }
        }
    }

    private void moveFocus(int index) {
        focused = index;
        repaint();
    }

    private void setHovered(int index) {
        if (hovered != index) {
            hovered = index;
            repaint();
        }
    }

    private void choose(int index) {
        selected = index;
        repaint();
        listeners.forEach(l -> l.accept(swatches.get(index).id()));
    }

    /** 고른 견본이 바뀔 때 id를 받는다 (같은 것을 다시 눌러도 불린다) */
    public void addChangeListener(Consumer<String> listener) {
        listeners.add(listener);
    }

    /** 고른 견본을 id로 정한다 (리스너는 부르지 않는다) */
    public void setSelected(String id) {
        int i = indexOf(id);
        if (i >= 0) {
            selected = i;
            focused = i;
            repaint();
        }
    }

    public String selectedId() {
        return swatches.get(selected).id();
    }

    private int indexOf(String id) {
        for (int i = 0; i < swatches.size(); i++) {
            if (swatches.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    // ---- 모양 ----

    private int cellX(int i) {
        return (i % columns) * Ui.px(CELL);
    }

    private int cellY(int i) {
        return (i / columns) * Ui.px(CELL);
    }

    private int indexAt(int x, int y) {
        int col = x / Ui.px(CELL);
        int row = y / Ui.px(CELL);
        int i = row * columns + col;
        return x >= 0 && y >= 0 && col < columns && i < swatches.size() ? i : -1;
    }

    @Override
    public Dimension getPreferredSize() {
        int rows = (swatches.size() + columns - 1) / columns;
        return new Dimension(Ui.px(CELL) * Math.min(columns, swatches.size()), Ui.px(CELL) * rows);
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        int i = indexAt(e.getX(), e.getY());
        return i < 0 ? null : swatches.get(i).label();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.smooth(g2);
        for (int i = 0; i < swatches.size(); i++) {
            paintCell(g2, i);
        }
        g2.dispose();
    }

    private void paintCell(Graphics2D g2, int i) {
        int cell = Ui.px(CELL);
        int d = Ui.px(SIZE);
        int x = cellX(i) + (cell - d) / 2;
        int y = cellY(i) + (cell - d) / 2;
        Color color = swatches.get(i).color().get();
        if (i == hovered) {
            g2.setColor(Tokens.alpha(Tokens.get(Tokens.TEXT), 0.10f));
            fillShape(g2, x - Ui.px(3), y - Ui.px(3), d + Ui.px(6));
        }
        g2.setColor(color);
        fillShape(g2, x, y, d);
        if (i == selected) {
            paintCheck(g2, x, y, d, CategoryColors.onColor(color));
        }
        if (i == focused && isFocusOwner()) {
            paintFocusRing(g2, x, y, d);
        }
    }

    private void fillShape(Graphics2D g2, int x, int y, int d) {
        if (round) {
            g2.fillOval(x, y, d, d);
        } else {
            int arc = Math.round(d * 0.3f);
            g2.fillRoundRect(x, y, d, d, arc, arc);
        }
    }

    private void paintFocusRing(Graphics2D g2, int x, int y, int d) {
        int gap = Ui.px(3);
        g2.setColor(Tokens.get(Tokens.ACCENT));
        g2.setStroke(new BasicStroke(Ui.px(2f)));
        if (round) {
            g2.drawOval(x - gap, y - gap, d + gap * 2, d + gap * 2);
        } else {
            int arc = Math.round((d + gap * 2) * 0.3f);
            g2.drawRoundRect(x - gap, y - gap, d + gap * 2, d + gap * 2, arc, arc);
        }
    }

    /** 견본 가운데에 체크 모양(✓)을 글자색으로 그린다 */
    private void paintCheck(Graphics2D g2, int x, int y, int d, Color color) {
        Path2D check = new Path2D.Float();
        check.moveTo(x + d * 0.27, y + d * 0.52);
        check.lineTo(x + d * 0.43, y + d * 0.68);
        check.lineTo(x + d * 0.74, y + d * 0.34);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(Ui.px(2f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(check);
    }

    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {
                @Override
                public AccessibleRole getAccessibleRole() {
                    return AccessibleRole.LIST;
                }

                @Override
                public String getAccessibleName() {
                    return swatches.get(focused).label() + (focused == selected ? " (선택됨)" : "");
                }
            };
        }
        return accessibleContext;
    }
}
