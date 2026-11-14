package proto.ui.component;

import proto.entity.Occurrence;
import proto.ui.Fmt;
import proto.ui.theme.CategoryColors;
import proto.ui.theme.Fonts;
import proto.ui.theme.Glyphs;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.Cursor;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * [경계 boundary] 월간 달력 칸 (6주 × 7일, 월요일 시작). 날짜 숫자, 일정 이름 또는 점(칸이 좁을 때), "+N"을 그린다.
 * MonthView가 달력 본문으로 쓴다 (F2). 일정이 들어 있는 날짜별 목록(items)을 받아서 그리기만 하고, 일정을 직접 읽지 않는다.
 * 칸을 누르면 onSelect, 두 번 누르거나 Enter를 누르면 onCreate가 불려 MonthView가 선택·새 일정 동작을 맡는다 (F1).
 * 오늘은 강조 동그라미, 고른 날은 옅은 채움. 세로로 길거나 낮은 화면에서는 이름 대신 점과 개수만 보여 준다.
 * 키보드: 화살표(±1일, ±7일), PageUp/PageDown(±1달), Home(오늘), Space(고르기), Enter(그 날에 새 일정).
 */
public class MonthGrid extends JComponent {

    private static final int HEADER_H = 30;
    private static final int LABEL_MIN_CELL = 84;

    private LocalDate month = LocalDate.now().withDayOfMonth(1);
    private LocalDate today = LocalDate.now();
    private LocalDate selected = LocalDate.now();
    private Map<LocalDate, List<Occurrence>> items = Map.of();
    private int hoverIndex = -1;
    private boolean dotMode;
    private final Consumer<LocalDate> onSelect;
    private final Consumer<LocalDate> onCreate;

    public MonthGrid(Consumer<LocalDate> onSelect, Consumer<LocalDate> onCreate) {
        this.onSelect = onSelect;
        this.onCreate = onCreate;
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int i = indexAt(e.getX(), e.getY());
                if (i >= 0) {
                    (e.getClickCount() >= 2 ? onCreate : onSelect).accept(dateAt(i));
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                setHover(indexAt(e.getX(), e.getY()));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setHover(-1);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        setFocusable(true);
        Listeners.repaintOnFocus(this);
        bindKeys();
    }

    /** 글자 대신 점과 개수만 보여 줄지 (세로로 길거나 낮은 화면) */
    public void setDotMode(boolean on) {
        if (on != dotMode) {
            dotMode = on;
            repaint();
        }
    }

    private void bindKeys() {
        bind("left", KeyEvent.VK_LEFT, () -> step(selected.minusDays(1)));
        bind("right", KeyEvent.VK_RIGHT, () -> step(selected.plusDays(1)));
        bind("up", KeyEvent.VK_UP, () -> step(selected.minusDays(7)));
        bind("down", KeyEvent.VK_DOWN, () -> step(selected.plusDays(7)));
        bind("pgup", KeyEvent.VK_PAGE_UP, () -> step(selected.minusMonths(1)));
        bind("pgdn", KeyEvent.VK_PAGE_DOWN, () -> step(selected.plusMonths(1)));
        bind("home", KeyEvent.VK_HOME, () -> step(today));
        bind("create", KeyEvent.VK_ENTER, () -> onCreate.accept(selected));
        bind("pick", KeyEvent.VK_SPACE, () -> onSelect.accept(selected));
    }

    private void bind(String name, int key, Runnable action) {
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key, 0), "proto-" + name);
        getActionMap().put("proto-" + name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private void step(LocalDate next) {
        onSelect.accept(next);
    }

    @Override
    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {
                @Override
                public String getAccessibleName() {
                    int n = items.getOrDefault(selected, List.of()).size();
                    return Fmt.dateLong(selected) + ", " + (n == 0 ? "일정 없음" : "일정 " + n + "개");
                }

                @Override
                public javax.accessibility.AccessibleRole getAccessibleRole() {
                    return javax.accessibility.AccessibleRole.TABLE;
                }
            };
        }
        return accessibleContext;
    }

    /** 보여 줄 달·오늘·고른 날·날짜별 일정을 정한다 */
    public void setData(LocalDate month, LocalDate today, LocalDate selected, Map<LocalDate, List<Occurrence>> items) {
        this.month = month.withDayOfMonth(1);
        this.today = today;
        this.selected = selected;
        this.items = items;
        repaint();
    }

    public void setSelected(LocalDate day) {
        this.selected = day;
        repaint();
    }

    /** 칸 맨 앞(첫 줄 월요일) 날짜 */
    public LocalDate gridStart() {
        return month.minusDays(month.getDayOfWeek().getValue() - 1L);
    }

    private LocalDate dateAt(int index) {
        return gridStart().plusDays(index);
    }

    private void setHover(int i) {
        if (i != hoverIndex) {
            hoverIndex = i;
            repaint();
        }
    }

    /** 마우스 위치(x, y)를 칸 번호(0~41)로 바꾼다. 요일 머리글 위이거나 칸 밖이면 -1이다. 칸 크기가 창 크기에 따라 달라지므로 비율로 계산한다. */
    private int indexAt(int x, int y) {
        int header = Ui.px(HEADER_H);
        if (y < header || x < 0 || x >= getWidth() || y >= getHeight()) {
            return -1;
        }
        int col = x * 7 / getWidth();
        int row = (y - header) * 6 / Math.max(1, getHeight() - header);
        return Math.min(row, 5) * 7 + Math.min(col, 6);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.smooth(g2);
        paintWeekdays(g2);
        int header = Ui.px(HEADER_H);
        float cw = getWidth() / 7f;
        float ch = (getHeight() - header) / 6f;
        g2.setColor(Tokens.get(Tokens.BORDER));
        for (int row = 0; row < 6; row++) {
            g2.fillRect(0, header + Math.round(row * ch), getWidth(), 1);
        }
        for (int i = 0; i < 42; i++) {
            paintCell(g2, i, Math.round((i % 7) * cw), header + Math.round((i / 7) * ch), Math.round(cw), Math.round(ch));
        }
        g2.dispose();
    }

    private void paintWeekdays(Graphics2D g2) {
        g2.setFont(Fonts.medium(Fonts.CAPTION));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(Tokens.get(Tokens.MUTED));
        float cw = getWidth() / 7f;
        for (int i = 0; i < 7; i++) {
            String s = Fmt.weekdayShort(DayOfWeek.of(i + 1));
            g2.drawString(s, Math.round(i * cw + Ui.px(8)), (Ui.px(HEADER_H) - fm.getHeight()) / 2 + fm.getAscent());
        }
    }

    private void paintCell(Graphics2D g2, int index, int x, int y, int w, int h) {
        LocalDate d = dateAt(index);
        int inset = Ui.px(2);
        int arc = Ui.px(10);
        if (d.equals(selected)) {
            g2.setColor(Tokens.get(Tokens.HOVER));
            g2.fillRoundRect(x + inset, y + inset, w - inset * 2, h - inset * 2, arc, arc);
            if (isFocusOwner()) {
                g2.setColor(Tokens.alpha(Tokens.get(Tokens.ACCENT), 0.7f));
                g2.setStroke(new java.awt.BasicStroke(Ui.px(1.5f)));
                g2.drawRoundRect(x + inset, y + inset, w - inset * 2 - 1, h - inset * 2 - 1, arc, arc);
            }
        } else if (index == hoverIndex) {
            g2.setColor(Tokens.get(Tokens.SUBTLE));
            g2.fillRoundRect(x + inset, y + inset, w - inset * 2, h - inset * 2, arc, arc);
        }
        boolean inMonth = d.getMonth() == month.getMonth();
        paintNumber(g2, d, x + Ui.px(8), y + Ui.px(6), inMonth);
        List<Occurrence> list = items.getOrDefault(d, List.of());
        if (list.isEmpty() || !inMonth) {
            return;
        }
        if (dotMode || h < Ui.px(64)) {
            paintSummary(g2, list, x, y, w, h);
        } else {
            paintItems(g2, list, x + Ui.px(8), y + Ui.px(34), w - Ui.px(14), h - Ui.px(36));
        }
    }

    private void paintNumber(Graphics2D g2, LocalDate d, int x, int y, boolean inMonth) {
        int dia = Ui.px(24);
        boolean isToday = d.equals(today);
        if (isToday) {
            g2.setColor(Tokens.get(Tokens.ACCENT));
            g2.fillOval(x - Ui.px(5), y - Ui.px(1), dia, dia);
        }
        g2.setFont(isToday ? Fonts.semibold(Fonts.SMALL) : Fonts.medium(Fonts.SMALL));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(Tokens.get(isToday ? Tokens.ON_ACCENT : inMonth ? Tokens.TEXT : Tokens.FAINT));
        String s = String.valueOf(d.getDayOfMonth());
        int cx = x - Ui.px(5) + dia / 2;
        g2.drawString(s, cx - fm.stringWidth(s) / 2, y - Ui.px(1) + (dia - fm.getHeight()) / 2 + fm.getAscent());
    }

    /** 칸이 충분히 크면 일정 이름을 줄마다 보여 준다. 줄이 모자라 일정이 더 있으면 마지막 줄을 "+N"으로 바꾼다. 칸이 좁으면(LABEL_MIN_CELL 미만) 이름 대신 점만 보여 준다. */
    private void paintItems(Graphics2D g2, List<Occurrence> list, int x, int y, int w, int availH) {
        int rowH = Ui.px(18);
        int maxRows = Math.max(1, Math.min(3, availH / rowH));
        boolean labels = getWidth() / 7 >= Ui.px(LABEL_MIN_CELL);
        int shown = list.size() <= maxRows ? list.size() : maxRows - 1;
        if (!labels) {
            paintDots(g2, list, x, y, w, shown);
            return;
        }
        for (int i = 0; i < shown; i++) {
            paintLabel(g2, list.get(i), x, y + i * rowH, w, rowH);
        }
        if (list.size() > shown) {
            paintMore(g2, list.size() - shown, x, y + shown * rowH, rowH);
        }
    }

    /** 점(최대 3개)과 전체 개수. 칸이 낮으면 숫자 줄 오른쪽에, 넉넉하면 숫자 아래에 놓는다 */
    private void paintSummary(Graphics2D g2, List<Occurrence> list, int x, int y, int w, int h) {
        int d = Ui.px(7);
        int gap = Ui.px(3);
        int dots = Math.min(3, list.size());
        g2.setFont(Fonts.medium(Fonts.CAPTION));
        FontMetrics fm = g2.getFontMetrics();
        String count = String.valueOf(list.size());
        int total = dots * d + (dots - 1) * gap + Ui.px(5) + fm.stringWidth(count);
        boolean inline = h < Ui.px(52);
        // 낮은 칸: 날짜 숫자 바로 오른쪽에 붙여 다음 칸 숫자와 헷갈리지 않게 한다. 자리가 모자라면 점을 하나로 줄인다
        int cx = inline ? x + Ui.px(36) : x + Ui.px(8);
        if (inline && cx + total > x + w - Ui.px(2)) {
            dots = 1;
        }
        int cy = inline ? y + Ui.px(6) + Ui.px(10) : y + Ui.px(34) + Ui.px(8);
        for (int i = 0; i < dots; i++) {
            g2.setColor(CategoryColors.of(list.get(i).categoryId()));
            g2.fillOval(cx, cy - d / 2, d, d);
            cx += d + gap;
        }
        g2.setColor(Tokens.get(Tokens.MUTED));
        g2.drawString(count, cx + Ui.px(2), cy - fm.getHeight() / 2 + fm.getAscent());
    }

    private void paintLabel(Graphics2D g2, Occurrence o, int x, int y, int w, int rowH) {
        int d = Ui.px(6);
        g2.setColor(CategoryColors.of(o.categoryId()));
        g2.fillOval(x, y + (rowH - d) / 2, d, d);
        g2.setFont(Fonts.regular(Fonts.CAPTION));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(Tokens.get(o.completed() ? Tokens.MUTED : Tokens.TEXT));
        int tx = x + d + Ui.px(5);
        String text = TextUtil.ellipsize(o.title(), fm, w - d - Ui.px(5));
        int base = y + (rowH - fm.getHeight()) / 2 + fm.getAscent();
        Glyphs.draw(g2, text, tx, base);
        if (o.completed()) {
            g2.fillRect(tx, base - fm.getAscent() / 3, Glyphs.width(fm, text), Math.max(1, Ui.px(1)));
        }
    }

    private void paintDots(Graphics2D g2, List<Occurrence> list, int x, int y, int w, int shown) {
        int d = Ui.px(7);
        int gap = Ui.px(4);
        int cx = x;
        for (int i = 0; i < Math.min(list.size(), 3); i++) {
            g2.setColor(CategoryColors.of(list.get(i).categoryId()));
            g2.fillOval(cx, y + Ui.px(4), d, d);
            cx += d + gap;
        }
        if (list.size() > 3) {
            paintMore(g2, list.size() - 3, cx, y, Ui.px(16));
        }
    }

    private void paintMore(Graphics2D g2, int n, int x, int y, int rowH) {
        g2.setFont(Fonts.medium(Fonts.CAPTION));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(Tokens.get(Tokens.MUTED));
        g2.drawString("+" + n, x, y + (rowH - fm.getHeight()) / 2 + fm.getAscent());
    }
}
