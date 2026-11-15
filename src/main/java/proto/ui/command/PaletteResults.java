package proto.ui.command;

import proto.ui.theme.CategoryColors;
import proto.ui.theme.Fonts;
import proto.ui.theme.Icons;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import javax.swing.Scrollable;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * [경계 boundary] 명령 메뉴의 결과 목록: 줄마다 아이콘(또는 카테고리 점), 이름, 옅은 보조 글, 단축키 칸을 그리고 고른 줄을 강조한다.
 * CommandPalette가 스크롤 영역에 넣어 쓴다. 키보드 포커스는 받지 않는다(포커스는 입력칸에 그대로 있다). 방향키는 입력칸이 move로 넘겨 주고,
 * 마우스를 올리면 그 줄이 곧바로 고른 줄이 되며 누르면 onChoose를 부른다. 글을 입력하기 전에는 묶음 제목 줄이 들어간다.
 */
final class PaletteResults extends JComponent implements Scrollable {

    private static final int ITEM_H = 38;
    private static final int HEAD_H = 30;
    private static final int PAD = 6;

    /** 한 줄: 묶음 제목이거나 명령 */
    private record Row(String header, Command command) {
    }

    private final List<Row> rows = new ArrayList<>();
    private final Consumer<Command> onChoose;
    private int selected = -1;

    PaletteResults(Consumer<Command> onChoose) {
        this.onChoose = onChoose;
        setFocusable(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int i = rowAt(e.getY());
                if (i >= 0 && rows.get(i).command() != null && i != selected) {
                    select(i);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                int i = rowAt(e.getY());
                if (i >= 0 && rows.get(i).command() != null && e.getButton() == MouseEvent.BUTTON1) {
                    onChoose.accept(rows.get(i).command());
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    /** 항목들을 채운다. grouped가 true면 묶음마다 제목 줄을 넣는다. 첫 항목을 고른 줄로 둔다 */
    void setCommands(List<Command> commands, boolean grouped) {
        rows.clear();
        String lastGroup = null;
        for (Command c : commands) {
            if (grouped && !c.group().equals(lastGroup)) {
                rows.add(new Row(c.group(), null));
                lastGroup = c.group();
            }
            rows.add(new Row(null, c));
        }
        selected = -1;
        select(nextCommand(-1, 1));
        revalidate();
        repaint();
    }

    /** 고른 줄의 명령 (없으면 null) */
    Command selectedCommand() {
        return selected < 0 ? null : rows.get(selected).command();
    }

    /** 고른 줄을 실행한다 (Enter) */
    void chooseSelected() {
        Command c = selectedCommand();
        if (c != null) {
            onChoose.accept(c);
        }
    }

    int commandCount() {
        return (int) rows.stream().filter(r -> r.command() != null).count();
    }

    /** 고른 줄을 delta칸 옮긴다 (제목 줄은 건너뛰고, 끝에서 반대쪽 끝으로 이어진다) */
    void move(int delta) {
        if (commandCount() == 0) {
            return;
        }
        int step = delta > 0 ? 1 : -1;
        int i = selected;
        for (int n = 0; n < Math.abs(delta); n++) {
            i = nextCommand(i, step);
        }
        select(i);
    }

    /** from에서 step 방향으로 다음 명령 줄 (끝을 넘으면 반대쪽 끝) */
    private int nextCommand(int from, int step) {
        int n = rows.size();
        for (int k = 1; k <= n; k++) {
            int i = Math.floorMod(from + step * k, n);
            if (n > 0 && rows.get(i).command() != null) {
                return i;
            }
        }
        return -1;
    }

    private void select(int index) {
        selected = index;
        if (index >= 0) {
            Rectangle r = rowBounds(index);
            if (index > 0 && rows.get(index - 1).command() == null) {
                // 묶음 제목 바로 아래 줄이면 제목까지 함께 보이게 한다 (맨 위 줄을 고르면 제목도 보인다)
                Rectangle head = rowBounds(index - 1);
                r = head.union(r);
            }
            scrollRectToVisible(r);
        }
        repaint();
    }

    private Rectangle rowBounds(int index) {
        int y = 0;
        for (int i = 0; i < index; i++) {
            y += heightOf(i);
        }
        return new Rectangle(0, y, Math.max(1, getWidth()), heightOf(index));
    }

    private int heightOf(int index) {
        return Ui.px(rows.get(index).command() == null ? HEAD_H : ITEM_H);
    }

    private int rowAt(int y) {
        int top = 0;
        for (int i = 0; i < rows.size(); i++) {
            top += heightOf(i);
            if (y < top) {
                return i;
            }
        }
        return -1;
    }

    private int totalHeight() {
        int h = 0;
        for (int i = 0; i < rows.size(); i++) {
            h += heightOf(i);
        }
        return h;
    }

    /** 스크롤 없이 보여 줄 높이: 항목 8줄까지 */
    int viewportHeight() {
        return Math.min(totalHeight(), Ui.px(ITEM_H * 8 + HEAD_H));
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Ui.px(300), totalHeight());
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.smooth(g2);
        int y = 0;
        for (int i = 0; i < rows.size(); i++) {
            int h = heightOf(i);
            Row r = rows.get(i);
            if (r.command() == null) {
                paintHeader(g2, r.header(), y, h);
            } else {
                paintItem(g2, r.command(), i == selected, y, h);
            }
            y += h;
        }
        g2.dispose();
    }

    private void paintHeader(Graphics2D g2, String text, int y, int h) {
        g2.setFont(Fonts.semibold(Fonts.CAPTION));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(Tokens.get(Tokens.MUTED));
        g2.drawString(text, Ui.px(PAD + 12), y + h - Ui.px(8) - fm.getDescent());
    }

    private void paintItem(Graphics2D g2, Command c, boolean active, int y, int h) {
        int pad = Ui.px(PAD);
        if (active) {
            g2.setColor(Tokens.get(Tokens.HOVER));
            g2.fillRoundRect(pad, y + Ui.px(1), getWidth() - pad * 2, h - Ui.px(2), Ui.px(8), Ui.px(8));
        }
        int x = pad + Ui.px(10);
        int cy = y + h / 2;
        paintLead(g2, c, x, cy);
        x += Ui.px(16 + 12);
        g2.setFont(Fonts.regular(Fonts.BODY));
        FontMetrics fm = g2.getFontMetrics();
        int right = getWidth() - pad - Ui.px(10);
        right = paintShortcut(g2, c.shortcut(), right, cy);
        right = paintDetail(g2, c.detail(), right, cy);
        g2.setFont(Fonts.regular(Fonts.BODY));
        g2.setColor(Tokens.get(Tokens.TEXT));
        g2.drawString(clip(fm, c.title(), right - x - Ui.px(8)), x, cy - fm.getHeight() / 2 + fm.getAscent());
    }

    private void paintLead(Graphics2D g2, Command c, int x, int cy) {
        int size = Ui.px(16);
        if (c.dotColorKey() != null || c.dotCategory() != null) {
            int d = Ui.px(10);
            g2.setColor(c.dotColorKey() != null ? CategoryColors.ofKey(c.dotColorKey())
                    : CategoryColors.of(c.dotCategory()));
            g2.fillOval(x + (size - d) / 2, cy - d / 2, d, d);
        } else if (c.icon() != null) {
            Icons.of(c.icon(), 16, Tokens.MUTED).paintIcon(this, g2, x, cy - size / 2);
        }
    }

    /** 오른쪽 끝에 단축키 칸을 그리고, 그 왼쪽 끝 위치를 돌려준다 */
    private int paintShortcut(Graphics2D g2, String text, int right, int cy) {
        if (text.isEmpty()) {
            return right;
        }
        g2.setFont(Fonts.medium(Fonts.CAPTION));
        FontMetrics fm = g2.getFontMetrics();
        int w = fm.stringWidth(text) + Ui.px(12);
        int h = Ui.px(20);
        int x = right - w;
        g2.setColor(Tokens.get(Tokens.SUBTLE));
        g2.fillRoundRect(x, cy - h / 2, w, h, Ui.px(6), Ui.px(6));
        g2.setColor(Tokens.get(Tokens.BORDER));
        g2.drawRoundRect(x, cy - h / 2, w - 1, h - 1, Ui.px(6), Ui.px(6));
        g2.setColor(Tokens.get(Tokens.MUTED));
        g2.drawString(text, x + Ui.px(6), cy - fm.getHeight() / 2 + fm.getAscent());
        return x - Ui.px(10);
    }

    private int paintDetail(Graphics2D g2, String text, int right, int cy) {
        if (text.isEmpty()) {
            return right;
        }
        g2.setFont(Fonts.regular(Fonts.SMALL));
        FontMetrics fm = g2.getFontMetrics();
        String shown = clip(fm, text, Ui.px(150));
        int x = right - fm.stringWidth(shown);
        g2.setColor(Tokens.get(Tokens.MUTED));
        g2.drawString(shown, x, cy - fm.getHeight() / 2 + fm.getAscent());
        return x - Ui.px(10);
    }

    private static String clip(FontMetrics fm, String text, int maxWidth) {
        if (fm.stringWidth(text) <= maxWidth) {
            return text;
        }
        String s = text;
        while (s.length() > 1 && fm.stringWidth(s + "…") > maxWidth) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "…";
    }

    // ---- Scrollable ----

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return new Dimension(Ui.px(300), viewportHeight());
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
        return Ui.px(ITEM_H);
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return Math.max(visible.height - Ui.px(ITEM_H), Ui.px(ITEM_H));
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
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
            accessibleContext.setAccessibleName("명령 목록");
        }
        return accessibleContext;
    }
}
