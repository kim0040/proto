package proto.ui.dialog;

import proto.ui.component.Listeners;
import proto.ui.theme.CategoryColors;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * [경계 boundary] 일정 하나만의 색을 고르는 한 줄 색 점 목록: 맨 앞 "카테고리 색"(이 일정만의 색 없음) + 팔레트 색 12개.
 * ScheduleDialog의 "색" 줄이다. 한 칸에 포커스가 있고 ←/→/Home/End로 고르면 바로 그 색이 선택된다(라디오 버튼 묶음처럼). 마우스로 눌러도 되고, 점에 마우스를 올리면 색 이름이 툴팁으로 뜬다.
 * 고른 점에는 체크 표시가 붙는다(색 위에서 글자색 대비가 맞는 색으로). "카테고리 색" 점은 지금 고른 카테고리의 색 테두리로 그려서, 카테고리를 바꾸면 따라 바뀐다.
 */
public final class ColorSwatchRow extends JComponent {

    private static final int DOT = 22;
    private static final int GAP = 6;
    /** 고른 점의 바깥 고리가 잘리지 않도록 줄 양쪽에 두는 여백 */
    private static final int PAD = 4;

    private final List<String> keys = new ArrayList<>();
    private final Supplier<Color> categoryColor;
    private Consumer<String> onChange = k -> { };
    private int selected;
    private int hover = -1;

    /** palette는 팔레트 색 키들. value는 처음 고른 색 키 (null이면 카테고리 색) */
    public ColorSwatchRow(List<String> palette, Supplier<Color> categoryColor, String value) {
        this.categoryColor = categoryColor;
        keys.add(null);
        keys.addAll(palette);
        this.selected = Math.max(0, keys.indexOf(value));
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        ToolTipManager.sharedInstance().registerComponent(this);
        Listeners.repaintOnFocus(this);
        Listeners.onKey(this, this::key);
        installMouse();
    }

    /** 색을 고를 때마다 부른다 (인자: 색 키, 카테고리 색이면 null) */
    public void onChange(Consumer<String> listener) {
        this.onChange = listener;
    }

    /** 지금 고른 색 키 (카테고리 색이면 null) */
    public String value() {
        return keys.get(selected);
    }

    /** 카테고리가 바뀌어 "카테고리 색" 점의 색이 달라졌을 때 */
    public void categoryChanged() {
        repaint();
    }

    private void installMouse() {
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int i = indexAt(e.getX());
                if (i >= 0) {
                    requestFocusInWindow();
                    choose(i);
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                hover = indexAt(e.getX());
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = -1;
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    private void key(int code) {
        int n = keys.size();
        switch (code) {
            case KeyEvent.VK_LEFT -> choose(Math.max(0, selected - 1));
            case KeyEvent.VK_RIGHT -> choose(Math.min(n - 1, selected + 1));
            case KeyEvent.VK_HOME -> choose(0);
            case KeyEvent.VK_END -> choose(n - 1);
            default -> { }
        }
    }

    private void choose(int index) {
        if (index == selected) {
            return;
        }
        selected = index;
        getAccessibleContext().setAccessibleDescription(ColorMenu.nameOf(value()));
        repaint();
        onChange.accept(value());
    }

    private int indexAt(int x) {
        int step = Ui.px(DOT + GAP);
        int rel = x - Ui.px(PAD);
        int i = rel / step;
        boolean onDot = rel >= 0 && rel - i * step < Ui.px(DOT);
        return onDot && i < keys.size() ? i : -1;
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        int i = indexAt(e.getX());
        return i < 0 ? null : ColorMenu.nameOf(keys.get(i));
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(keys.size() * Ui.px(DOT + GAP) - Ui.px(GAP) + Ui.px(PAD) * 2, Ui.px(DOT + 8));
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.smooth(g2);
        int d = Ui.px(DOT);
        int y = (getHeight() - d) / 2;
        for (int i = 0; i < keys.size(); i++) {
            paintDot(g2, i, Ui.px(PAD) + i * Ui.px(DOT + GAP), y, d);
        }
        g2.dispose();
    }

    private void paintDot(Graphics2D g2, int index, int x, int y, int d) {
        String key = keys.get(index);
        Color color = key == null ? categoryColor.get() : CategoryColors.ofKey(key);
        if (key == null) {
            g2.setColor(Tokens.alpha(color, 0.45f));
            g2.setStroke(new BasicStroke(Ui.px(1.6f)));
            g2.drawOval(x + 1, y + 1, d - 2, d - 2);
            int inner = Ui.px(8);
            g2.setColor(color);
            g2.fillOval(x + (d - inner) / 2, y + (d - inner) / 2, inner, inner);
        } else {
            g2.setColor(color);
            g2.fillOval(x, y, d, d);
        }
        boolean on = index == selected;
        if (on) {
            paintCheck(g2, key == null ? color : checkColor(color), x, y, d);
            paintRing(g2, x, y, d, isFocusOwner() ? Tokens.ACCENT : Tokens.TEXT);
        } else if (index == hover) {
            paintRing(g2, x, y, d, Tokens.BORDER_STRONG);
        }
    }

    private void paintRing(Graphics2D g2, int x, int y, int d, String token) {
        int off = Ui.px(3);
        g2.setColor(Tokens.get(token));
        g2.setStroke(new BasicStroke(Ui.px(1.6f)));
        g2.drawOval(x - off, y - off, d + off * 2 - 1, d + off * 2 - 1);
    }

    /** 색 점 위의 체크 표시: 흰색과 진한 회색 중 그 색과 대비가 큰 쪽 */
    private static Color checkColor(Color bg) {
        Color dark = new Color(0x15, 0x17, 0x1A);
        return contrast(Color.WHITE, bg) >= contrast(dark, bg) ? Color.WHITE : dark;
    }

    private static double contrast(Color a, Color b) {
        double x = luminance(a);
        double y = luminance(b);
        return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
    }

    private static double luminance(Color c) {
        double[] v = {c.getRed() / 255.0, c.getGreen() / 255.0, c.getBlue() / 255.0};
        for (int i = 0; i < 3; i++) {
            v[i] = v[i] <= 0.03928 ? v[i] / 12.92 : Math.pow((v[i] + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
    }

    private void paintCheck(Graphics2D g2, Color color, int x, int y, int d) {
        g2.setColor(color);
        g2.setStroke(new BasicStroke(Ui.px(2f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int cx = x + d / 2;
        int cy = y + d / 2;
        int u = Ui.px(3);
        g2.drawLine(cx - u - 1, cy, cx - 1, cy + u - 1);
        g2.drawLine(cx - 1, cy + u - 1, cx + u + 1, cy - u + 1);
    }

    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {
                @Override
                public AccessibleRole getAccessibleRole() {
                    return AccessibleRole.LIST;
                }
            };
            accessibleContext.setAccessibleName("일정 색");
            accessibleContext.setAccessibleDescription(ColorMenu.nameOf(value()));
        }
        return accessibleContext;
    }
}
