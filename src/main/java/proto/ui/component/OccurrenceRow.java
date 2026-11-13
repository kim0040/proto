package proto.ui.component;

import proto.entity.Occurrence;
import proto.entity.ReminderOption;
import proto.entity.ScheduleState;
import proto.ui.Fmt;
import proto.ui.motion.Tween;
import proto.ui.theme.Fonts;
import proto.ui.theme.Icons;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * [경계 boundary] 일정 한 줄(시간·체크·제목·카테고리·상태). 오늘 목록과 달력 옆 목록이 함께 쓴다.
 * 마우스를 올리면 배경이 서서히 켜지고, 완료하면 체크와 제목이 부드럽게 바뀐다.
 */
public class OccurrenceRow extends JPanel {

    private final RowHandler handler;
    private final boolean compact;
    private Occurrence occurrence;

    private final TokenLabel time = new TokenLabel("", Fonts.medium(Fonts.SMALL), Tokens.MUTED);
    private final CompletionCheck check = new CompletionCheck();
    private final StrikeLabel title = new StrikeLabel(Fonts.medium(Fonts.BODY));
    private final StrikeLabel memo = new StrikeLabel(Fonts.regular(Fonts.SMALL));
    private final TokenLabel metaTime = new TokenLabel("", Fonts.regular(Fonts.CAPTION), Tokens.MUTED);
    private final CategoryDot dot = new CategoryDot(null, 8);
    private final TokenLabel category = new TokenLabel("", Fonts.regular(Fonts.SMALL), Tokens.MUTED);
    private final StatePill pill = new StatePill(ScheduleState.SCHEDULED);
    private final JLabel repeatIcon = new JLabel(Icons.of("repeat", 14, Tokens.FAINT));
    private final JLabel bellIcon = new JLabel(Icons.of("bell", 14, Tokens.FAINT));
    private final Tween hover = new Tween(0f, this::repaint);

    public OccurrenceRow(RowHandler handler, boolean compact) {
        super(new GridBagLayout());
        this.handler = handler;
        this.compact = compact;
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFocusable(true);
        setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
        buildLayout();
        installMouse();
    }

    /** 이 행이 보여 주는 하루치 일정 */
    public Occurrence occurrence() {
        return occurrence;
    }

    /** 새 값을 반영한다. 같은 일정이면 animate에 따라 체크·색이 부드럽게 바뀐다 */
    public void update(Occurrence o, ScheduleState state, boolean animate) {
        this.occurrence = o;
        time.setText(Fmt.range(o));
        metaTime.setText(Fmt.range(o));
        title.setText(o.title());
        title.setDone(o.completed(), animate);
        memo.setText(TextUtil.firstLine(o.memo()));
        memo.setDone(o.completed(), animate);
        memo.setVisible(!compact && !memo.isEmpty());
        check.setChecked(o.completed(), animate);
        pill.setState(state, animate);
        dot.setCategoryId(o.categoryId());
        category.setText(handler.categoryName(o.categoryId()));
        repeatIcon.setVisible(o.isRepeating());
        bellIcon.setVisible(o.reminder() != ReminderOption.NONE);
        time.setColorToken(o.completed() ? Tokens.FAINT : Tokens.MUTED);
        getAccessibleContext().setAccessibleName(o.title() + ", " + Fmt.range(o) + ", " + state.label());
        revalidate();
    }

    private void buildLayout() {
        if (compact) {
            buildCompact();
        } else {
            buildWide();
        }
    }

    private void buildWide() {
        time.setPreferredSize(new Dimension(Ui.px(116), Ui.px(20)));
        JPanel texts = textColumn(title, memo);
        add(time, cell(0, 0, 0, new Insets(0, 4, 0, 4)));
        add(check, cell(1, 0, 0, new Insets(0, 4, 0, 12)));
        add(texts, cell(2, 1, 1, new Insets(0, 0, 0, 12)));
        JPanel cat = categoryChip();
        cat.setPreferredSize(new Dimension(Ui.px(112), Ui.px(20)));
        add(cat, cell(3, 0, 0, new Insets(0, 0, 0, 12)));
        add(pill, cell(4, 0, 0, new Insets(0, 0, 0, 12)));
        JPanel icons = new JPanel(new FlowLayout(FlowLayout.RIGHT, Ui.px(4), 0));
        icons.setOpaque(false);
        icons.add(repeatIcon);
        icons.add(bellIcon);
        icons.setPreferredSize(new Dimension(Ui.px(40), Ui.px(20)));
        add(icons, cell(5, 0, 0, new Insets(0, 0, 0, 8)));
    }

    private void buildCompact() {
        JPanel meta = new JPanel(new FlowLayout(FlowLayout.LEFT, Ui.px(6), 0));
        meta.setOpaque(false);
        meta.add(metaTime);
        meta.add(categoryChip());
        JPanel texts = textColumn(title, meta);
        add(check, cell(0, 0, 0, new Insets(0, 8, 0, 12)));
        add(texts, cell(1, 1, 1, new Insets(0, 0, 0, 8)));
        add(pill, cell(2, 0, 0, new Insets(0, 0, 0, 8)));
    }

    private JPanel categoryChip() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, Ui.px(6), 0));
        p.setOpaque(false);
        p.add(dot);
        p.add(category);
        return p;
    }

    private static JPanel textColumn(JComponent first, JComponent second) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        first.setAlignmentX(LEFT_ALIGNMENT);
        second.setAlignmentX(LEFT_ALIGNMENT);
        p.add(first);
        p.add(second);
        return p;
    }

    private static GridBagConstraints cell(int x, double weightX, int fill, Insets in) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.weightx = weightX;
        c.fill = fill == 1 ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(Ui.px(in.top), Ui.px(in.left), Ui.px(in.bottom), Ui.px(in.right));
        return c;
    }

    private void installMouse() {
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hover.to(1f, 140);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                java.awt.Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), OccurrenceRow.this);
                if (!contains(p)) {
                    hover.to(0f, 180);
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                maybeMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeMenu(e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && !e.isPopupTrigger()) {
                    handler.open(occurrence);
                }
            }
        };
        addMouseListener(adapter);
        check.addActionListener(e -> handler.toggleDone(occurrence));
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    handler.open(occurrence);
                }
            }
        });
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                repaint();
            }
        });
    }

    private void maybeMenu(MouseEvent e) {
        if (e.isPopupTrigger()) {
            handler.showMenu(this, e.getPoint(), occurrence);
        }
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(d.width, Math.max(d.height + Ui.px(compact ? 16 : 22), Ui.px(compact ? 56 : 58)));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.smooth(g2);
        int arc = Ui.px(10);
        if (hover.value() > 0.01f) {
            g2.setColor(Tokens.alpha(Tokens.get(Tokens.SUBTLE), hover.value()));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            g2.setColor(Tokens.alpha(Tokens.get(Tokens.HOVER), hover.value() * 0.5f));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
        }
        g2.setColor(Tokens.alpha(Tokens.get(Tokens.BORDER), 1f - hover.value()));
        g2.fillRect(Ui.px(12), getHeight() - 1, getWidth() - Ui.px(24), 1);
        if (isFocusOwner()) {
            g2.setColor(Tokens.alpha(Tokens.get(Tokens.ACCENT), 0.6f));
            g2.setStroke(new BasicStroke(Ui.px(1.5f)));
            g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, arc, arc);
        }
        g2.dispose();
    }
}
