package proto.ui.component;

import proto.ui.Fmt;
import proto.ui.theme.Fonts;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.function.Consumer;

/**
 * [경계 boundary] 작은 월 달력 (월요일 시작). 마우스뿐 아니라 키보드로도 고른다 (화살표·PageUp/PageDown·Home·Enter·Esc).
 * 두 곳에서 쓴다. (1) DatePickerField의 팝업 안 날짜 선택(기본 모양: 36px 정사각 칸, 고른 날이 진한 원, 오늘은 테두리 원).
 * (2) 사이드바의 월 달력(sidebar 모양: 칸이 너비를 따라 늘어나고, 오늘이 진한 원, 고른 날은 옅은 원, 보고 있는 주는 옅은 띠,
 * 일정이 있는 날은 숫자 아래 작은 점). 사이드바 모양은 달을 넘길 때마다 onMonth로 알려서 점을 새로 읽게 한다.
 * 날짜 칸은 항상 6주(42칸)를 그려서 달마다 높이가 변하지 않는다.
 */
public class MiniCalendar extends JPanel {

    private static final int CELL = 36;
    private static final int SIDEBAR_ROW = 28;

    private LocalDate month;
    private LocalDate selected;
    private LocalDate today;
    private final boolean sidebar;
    private final Consumer<LocalDate> onPick;
    private Consumer<LocalDate> onMonth = m -> { };
    private final TokenLabel title;
    private final Grid grid = new Grid();
    private Runnable onEscape = () -> { };
    private Set<LocalDate> marked = Set.of();
    private LocalDate rangeFrom;
    private LocalDate rangeTo;

    /** 날짜 고르기용 (기본 모양). 오늘 날짜는 호출한 쪽이 controller의 시계에서 받아 넘겨 준다. 컴포넌트가 직접 LocalDate.now()를 부르지 않는다. */
    public MiniCalendar(LocalDate selected, LocalDate today, Consumer<LocalDate> onPick) {
        this(selected, today, onPick, false);
    }

    private MiniCalendar(LocalDate selected, LocalDate today, Consumer<LocalDate> onPick, boolean sidebar) {
        super(new BorderLayout(0, Ui.px(sidebar ? 4 : 8)));
        this.selected = selected;
        this.onPick = onPick;
        this.sidebar = sidebar;
        this.month = selected.withDayOfMonth(1);
        this.today = today;
        this.title = new TokenLabel("", sidebar ? Fonts.semibold(Fonts.SMALL) : Fonts.semibold(Fonts.BODY), Tokens.TEXT);
        setOpaque(false);
        int pad = sidebar ? 0 : 12;
        setBorder(javax.swing.BorderFactory.createEmptyBorder(Ui.px(pad), Ui.px(pad), Ui.px(pad), Ui.px(pad)));
        add(header(), BorderLayout.NORTH);
        add(grid, BorderLayout.CENTER);
        refreshTitle();
        grid.cursor = selected;
    }

    /** 사이드바용 월 달력. 날짜를 누르면 onPick, 달을 넘기면 onMonth(그 달 1일)를 부른다 */
    public static MiniCalendar forSidebar(LocalDate today, Consumer<LocalDate> onPick, Consumer<LocalDate> onMonth) {
        MiniCalendar c = new MiniCalendar(today, today, onPick, true);
        c.onMonth = onMonth;
        return c;
    }

    /** Esc를 눌렀을 때 부를 동작 (팝업 닫기) */
    public void setOnEscape(Runnable action) {
        this.onEscape = action;
    }

    /** 날짜 칸에 키보드 포커스를 준다 */
    public void focusGrid() {
        grid.requestFocusInWindow();
    }

    /** 지금 보여 주는 달의 1일 */
    public LocalDate shownMonth() {
        return month;
    }

    /** 그 날이 들어 있는 달을 보여 준다 (onMonth는 부르지 않는다: 호출한 쪽이 이미 알고 있다) */
    public void showMonth(LocalDate day) {
        month = day.withDayOfMonth(1);
        refreshTitle();
        grid.repaint();
    }

    /** 고른 날(진한 원 또는 옅은 원)을 바꾼다 */
    public void setSelected(LocalDate day) {
        this.selected = day;
        grid.repaint();
    }

    /** 오늘 날짜가 바뀌었을 때(자정을 넘김) */
    public void setToday(LocalDate day) {
        this.today = day;
        grid.repaint();
    }

    /** 일정이 있는 날 (숫자 아래 작은 점) */
    public void setMarked(Set<LocalDate> days) {
        this.marked = days;
        grid.repaint();
    }

    /** 보고 있는 날짜 범위(from~to)를 옅은 띠로 보여 준다. 없으면 null */
    public void setRange(LocalDate from, LocalDate to) {
        this.rangeFrom = from;
        this.rangeTo = to;
        grid.repaint();
    }

    private JPanel header() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        JButton prev = Buttons.icon("chevron-left", "이전 달");
        JButton next = Buttons.icon("chevron-right", "다음 달");
        prev.addActionListener(e -> shift(-1));
        next.addActionListener(e -> shift(1));
        title.setHorizontalAlignment(sidebar ? TokenLabel.LEFT : TokenLabel.CENTER);
        if (sidebar) {
            title.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, Ui.px(8), 0, 0));
            JPanel arrows = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));
            arrows.setOpaque(false);
            arrows.add(prev);
            arrows.add(next);
            p.add(title, BorderLayout.CENTER);
            p.add(arrows, BorderLayout.EAST);
            return p;
        }
        p.add(prev, BorderLayout.WEST);
        p.add(title, BorderLayout.CENTER);
        p.add(next, BorderLayout.EAST);
        return p;
    }

    private void shift(int months) {
        month = month.plusMonths(months);
        refreshTitle();
        grid.repaint();
        onMonth.accept(month);
    }

    private void refreshTitle() {
        title.setText(Fmt.yearMonth(month));
    }

    /** 그리드: 요일 머리글 + 6주 × 7일 */
    private final class Grid extends JComponent {
        private int hoverIndex = -1;
        private LocalDate cursor;

        Grid() {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFocusable(true);
            Listeners.repaintOnFocus(this);
            bindKeys();
            bindMouse();
        }

        /** 누르면 그 날짜를 고르고, 마우스가 올라간 칸은 강조한다 (메서드 30줄 규칙 때문에 생성자에서 나눔) */
        private void bindMouse() {
            Listeners.onClick(this, e -> {
                int i = indexAt(e.getX(), e.getY());
                if (i >= 0) {
                    LocalDate d = dateAt(i);
                    MiniCalendar.this.selected = d;
                    cursor = d;
                    onPick.accept(d);
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    hoverIndex = -1;
                    repaint();
                }
            });
            addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int i = indexAt(e.getX(), e.getY());
                    if (i != hoverIndex) {
                        hoverIndex = i;
                        repaint();
                    }
                }
            });
        }

        /** 화살표(±1일, ±7일), PageUp/PageDown(±1달), Home(오늘), Enter/Space(고르기), Esc(닫기) */
        private void bindKeys() {
            bind("left", KeyEvent.VK_LEFT, () -> move(cursor.minusDays(1)));
            bind("right", KeyEvent.VK_RIGHT, () -> move(cursor.plusDays(1)));
            bind("up", KeyEvent.VK_UP, () -> move(cursor.minusDays(7)));
            bind("down", KeyEvent.VK_DOWN, () -> move(cursor.plusDays(7)));
            bind("pgup", KeyEvent.VK_PAGE_UP, () -> move(cursor.minusMonths(1)));
            bind("pgdn", KeyEvent.VK_PAGE_DOWN, () -> move(cursor.plusMonths(1)));
            bind("home", KeyEvent.VK_HOME, () -> move(today));
            bind("pick", KeyEvent.VK_ENTER, this::pickCursor);
            bind("pick2", KeyEvent.VK_SPACE, this::pickCursor);
            bind("esc", KeyEvent.VK_ESCAPE, () -> onEscape.run());
        }

        private void bind(String name, int key, Runnable action) {
            getInputMap(WHEN_FOCUSED).put(javax.swing.KeyStroke.getKeyStroke(key, 0), "proto-" + name);
            getActionMap().put("proto-" + name, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    action.run();
                }
            });
        }

        private void move(LocalDate next) {
            cursor = next;
            if (!YearMonthCheck.sameMonth(next, month)) {
                month = next.withDayOfMonth(1);
                refreshTitle();
                onMonth.accept(month);
            }
            repaint();
        }

        private void pickCursor() {
            MiniCalendar.this.selected = cursor;
            onPick.accept(cursor);
        }

        @Override
        public javax.accessibility.AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) {
                accessibleContext = new AccessibleJComponent() {
                    @Override
                    public String getAccessibleName() {
                        return cursor == null ? "날짜 선택" : Fmt.dateLong(cursor);
                    }

                    @Override
                    public javax.accessibility.AccessibleRole getAccessibleRole() {
                        return javax.accessibility.AccessibleRole.TABLE;
                    }
                };
            }
            return accessibleContext;
        }

        @Override
        public Dimension getPreferredSize() {
            int row = rowHeight();
            return new Dimension(sidebar ? Ui.px(SIDEBAR_ROW) * 7 : row * 7, row * 7);
        }

        /** 칸 높이. 기본 모양은 정사각 칸(36), 사이드바 모양은 28 */
        private int rowHeight() {
            return Ui.px(sidebar ? SIDEBAR_ROW : CELL);
        }

        /** 칸 너비. 기본 모양은 정사각이고, 사이드바 모양은 너비를 7등분한다 */
        private int colWidth() {
            return sidebar ? Math.max(1, getWidth() / 7) : Ui.px(CELL);
        }

        private int indexAt(int x, int y) {
            int row = y / rowHeight() - 1;
            int col = x / colWidth();
            return row < 0 || row > 5 || col > 6 ? -1 : row * 7 + col;
        }

        private LocalDate dateAt(int index) {
            LocalDate first = month.withDayOfMonth(1);
            LocalDate gridStart = first.minusDays(first.getDayOfWeek().getValue() - 1L);
            return gridStart.plusDays(index);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Ui.smooth(g2);
            int w = colWidth();
            int h = rowHeight();
            g2.setFont(Fonts.medium(Fonts.CAPTION));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Tokens.get(Tokens.MUTED));
            for (int col = 0; col < 7; col++) {
                String s = Fmt.weekdayShort(DayOfWeek.of(col + 1));
                g2.drawString(s, col * w + (w - fm.stringWidth(s)) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            }
            paintRangeBand(g2, w, h);
            for (int i = 0; i < 42; i++) {
                paintDay(g2, i, (i % 7) * w, (i / 7 + 1) * h, w, h);
            }
            g2.dispose();
        }

        /** 보고 있는 날짜 범위를 줄마다 옅은 둥근 띠로 깐다 (날짜 칸 아래에 그린다) */
        private void paintRangeBand(Graphics2D g2, int w, int h) {
            if (rangeFrom == null || rangeTo == null) {
                return;
            }
            g2.setColor(Tokens.get(Tokens.HOVER));
            int inset = Ui.px(2);
            for (int row = 0; row < 6; row++) {
                int first = -1;
                int last = -1;
                for (int col = 0; col < 7; col++) {
                    LocalDate d = dateAt(row * 7 + col);
                    if (!d.isBefore(rangeFrom) && !d.isAfter(rangeTo)) {
                        first = first < 0 ? col : first;
                        last = col;
                    }
                }
                if (first >= 0) {
                    int y = (row + 1) * h + inset;
                    g2.fillRoundRect(first * w + inset, y, (last - first + 1) * w - inset * 2, h - inset * 2, h, h);
                }
            }
        }

        private void paintDay(Graphics2D g2, int index, int x, int y, int w, int h) {
            LocalDate d = dateAt(index);
            boolean inMonth = d.getMonth() == month.getMonth();
            boolean isToday = d.equals(today);
            boolean isSelected = d.equals(selected);
            int dia = Math.min(w, h) - Ui.px(sidebar ? 4 : 6);
            int cx = x + w / 2;
            int cy = y + h / 2;
            paintDisc(g2, index, isToday, isSelected, cx, cy, dia);
            g2.setFont(isToday || isSelected ? Fonts.semibold(Fonts.SMALL) : Fonts.regular(Fonts.SMALL));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Tokens.get(textToken(inMonth, isToday, isSelected)));
            boolean dot = sidebar && marked.contains(d);
            String s = String.valueOf(d.getDayOfMonth());
            int ty = cy - fm.getHeight() / 2 + fm.getAscent() - Ui.px(sidebar ? 1 : 0);
            g2.drawString(s, cx - fm.stringWidth(s) / 2, ty);
            if (dot) {
                // 점은 숫자 아래 (글자 바닥선 + 3px, 숫자 줄은 점이 있든 없든 같은 높이). 원 밖으로 나가지 않게 원 아래쪽 안에서 멈춘다
                int r = Ui.px(3);
                int top = Math.min(ty + Ui.px(3), cy + dia / 2 - r);
                g2.setColor(Tokens.get(isToday ? Tokens.ON_ACCENT : Tokens.ACCENT));
                g2.fillOval(cx - r / 2, top, r, r);
            }
        }

        /** 글자 색: 기본 모양은 고른 날, 사이드바 모양은 오늘이 진한 원이라 흰 글자 */
        private String textToken(boolean inMonth, boolean isToday, boolean isSelected) {
            if (sidebar ? isToday : isSelected) {
                return Tokens.ON_ACCENT;
            }
            if (sidebar && isSelected) {
                return Tokens.ACCENT;
            }
            // 사이드바 달력의 이웃 달 날짜는 글자 대비 4.5:1 이상을 지키려고 MUTED로 (본문 TEXT와는 색 굵기로 구분)
            return inMonth ? Tokens.TEXT : sidebar ? Tokens.MUTED : Tokens.FAINT;
        }

        /** 날짜 칸 뒤의 원: 고른 날·오늘·마우스 올림·키보드 커서 */
        private void paintDisc(Graphics2D g2, int index, boolean isToday, boolean isSelected, int cx, int cy, int dia) {
            int left = cx - dia / 2;
            int top = cy - dia / 2;
            boolean filled = sidebar ? isToday : isSelected;
            if (filled) {
                g2.setColor(Tokens.get(Tokens.ACCENT));
                g2.fillOval(left, top, dia, dia);
            } else if (sidebar && isSelected) {
                g2.setColor(Tokens.get(Tokens.ACCENT_SOFT));
                g2.fillOval(left, top, dia, dia);
            } else if (index == hoverIndex) {
                g2.setColor(Tokens.get(Tokens.HOVER));
                g2.fillOval(left, top, dia, dia);
            }
            boolean cursorHere = isFocusOwner() && dateAt(index).equals(cursor);
            if (cursorHere) {
                g2.setColor(Tokens.alpha(Tokens.get(Tokens.ACCENT), 0.7f));
                g2.setStroke(new java.awt.BasicStroke(Ui.px(2f)));
                g2.drawOval(left - 1, top - 1, dia + 1, dia + 1);
            } else if (!sidebar && isToday && !isSelected) {
                g2.setColor(Tokens.get(Tokens.ACCENT));
                g2.setStroke(new java.awt.BasicStroke(Ui.px(1.4f)));
                g2.drawOval(left, top, dia - 1, dia - 1);
            }
        }
    }
}

/** 같은 달인지 비교하는 작은 도우미 */
final class YearMonthCheck {
    private YearMonthCheck() {
    }

    static boolean sameMonth(LocalDate a, LocalDate b) {
        return a.getYear() == b.getYear() && a.getMonth() == b.getMonth();
    }
}
