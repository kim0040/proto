package proto.ui.component;

import proto.ui.Fmt;
import proto.ui.theme.Fonts;
import proto.ui.theme.Icons;
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
import java.util.function.Consumer;

/**
 * [경계 boundary] 날짜 선택 팝업 안에 들어가는 작은 월 달력 (월요일 시작). 마우스뿐 아니라 키보드로도 고른다
 * (화살표·PageUp/PageDown·Home·Enter·Esc).
 * DatePickerField의 팝업 안에서만 쓴다. 날짜 칸은 항상 6주(42칸)를 그려서 달마다 높이가 변하지 않는다.
 */
public class MiniCalendar extends JPanel {

    private static final int CELL = 36;

    private LocalDate month;
    private LocalDate selected;
    private final Consumer<LocalDate> onPick;
    private final TokenLabel title = new TokenLabel("", Fonts.semibold(Fonts.BODY), Tokens.TEXT);
    private final Grid grid = new Grid();
    private Runnable onEscape = () -> { };

    public MiniCalendar(LocalDate selected, LocalDate today, Consumer<LocalDate> onPick) {
        super(new BorderLayout(0, Ui.px(8)));
        this.selected = selected;
        this.onPick = onPick;
        this.month = selected.withDayOfMonth(1);
        this.today = today;
        setOpaque(false);
        setBorder(javax.swing.BorderFactory.createEmptyBorder(Ui.px(12), Ui.px(12), Ui.px(12), Ui.px(12)));
        add(header(), BorderLayout.NORTH);
        add(grid, BorderLayout.CENTER);
        refreshTitle();
        grid.cursor = selected;
    }

    // 오늘 날짜는 호출한 쪽(대화상자)이 controller의 시계에서 받아 넘겨 준다. 컴포넌트가 직접 LocalDate.now()를 부르지 않는다.
    private final LocalDate today;

    /** Esc를 눌렀을 때 부를 동작 (팝업 닫기) */
    public void setOnEscape(Runnable action) {
        this.onEscape = action;
    }

    /** 날짜 칸에 키보드 포커스를 준다 */
    public void focusGrid() {
        grid.requestFocusInWindow();
    }

    private JPanel header() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        JButton prev = Buttons.icon("chevron-left", "이전 달");
        JButton next = Buttons.icon("chevron-right", "다음 달");
        prev.addActionListener(e -> shift(-1));
        next.addActionListener(e -> shift(1));
        title.setHorizontalAlignment(TokenLabel.CENTER);
        p.add(prev, BorderLayout.WEST);
        p.add(title, BorderLayout.CENTER);
        p.add(next, BorderLayout.EAST);
        return p;
    }

    private void shift(int months) {
        month = month.plusMonths(months);
        refreshTitle();
        grid.repaint();
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
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int i = indexAt(e.getX(), e.getY());
                    if (i >= 0) {
                        LocalDate d = dateAt(i);
                        MiniCalendar.this.selected = d;
                        cursor = d;
                        onPick.accept(d);
                    }
                }

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
            return new Dimension(Ui.px(CELL) * 7, Ui.px(CELL) * 7);
        }

        private int indexAt(int x, int y) {
            int c = Ui.px(CELL);
            int row = y / c - 1;
            int col = x / c;
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
            int c = Ui.px(CELL);
            g2.setFont(Fonts.medium(Fonts.CAPTION));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Tokens.get(Tokens.MUTED));
            for (int col = 0; col < 7; col++) {
                String s = Fmt.weekdayShort(DayOfWeek.of(col + 1));
                g2.drawString(s, col * c + (c - fm.stringWidth(s)) / 2, (c - fm.getHeight()) / 2 + fm.getAscent());
            }
            for (int i = 0; i < 42; i++) {
                paintDay(g2, i, (i % 7) * c, (i / 7 + 1) * c, c);
            }
            g2.dispose();
        }

        private void paintDay(Graphics2D g2, int index, int x, int y, int c) {
            LocalDate d = dateAt(index);
            boolean inMonth = d.getMonth() == month.getMonth();
            int pad = Ui.px(3);
            int dia = c - pad * 2;
            if (d.equals(selected)) {
                g2.setColor(Tokens.get(Tokens.ACCENT));
                g2.fillOval(x + pad, y + pad, dia, dia);
            } else if (index == hoverIndex) {
                g2.setColor(Tokens.get(Tokens.HOVER));
                g2.fillOval(x + pad, y + pad, dia, dia);
            }
            if (isFocusOwner() && d.equals(cursor)) {
                g2.setColor(Tokens.alpha(Tokens.get(Tokens.ACCENT), 0.7f));
                g2.setStroke(new java.awt.BasicStroke(Ui.px(2f)));
                g2.drawOval(x + pad - 1, y + pad - 1, dia + 1, dia + 1);
            } else if (d.equals(today) && !d.equals(selected)) {
                g2.setColor(Tokens.get(Tokens.ACCENT));
                g2.setStroke(new java.awt.BasicStroke(Ui.px(1.4f)));
                g2.drawOval(x + pad, y + pad, dia - 1, dia - 1);
            }
            g2.setFont(d.equals(today) || d.equals(selected) ? Fonts.semibold(Fonts.SMALL) : Fonts.regular(Fonts.SMALL));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Tokens.get(d.equals(selected) ? Tokens.ON_ACCENT : inMonth ? Tokens.TEXT : Tokens.FAINT));
            String s = String.valueOf(d.getDayOfMonth());
            g2.drawString(s, x + (c - fm.stringWidth(s)) / 2, y + (c - fm.getHeight()) / 2 + fm.getAscent());
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
