package proto.ui.component;

import proto.entity.Occurrence;
import proto.entity.ScheduleState;
import proto.ui.Fmt;
import proto.ui.motion.Tween;
import proto.ui.theme.Fonts;
import proto.ui.theme.Glyphs;
import proto.ui.theme.Ui;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * [경계 boundary] 하루 종일 일정 하나의 칩: 카테고리 색으로 꽉 채운 둥근 막대(20px) 위에 제목만 쓴다 (시간 없음).
 * 주 시간표의 하루 종일 줄(AllDayRow)과 오늘 보기의 하루 종일 구역(AllDayStrip)이 일정마다 하나씩 만든다 (F2, F3, F7).
 * 클릭·Enter는 열기, 오른쪽 클릭은 메뉴, Delete는 삭제를 RowHandler에 요청하고, Alt+←/→는 하루씩 옮기기를 요청한다.
 * 가로로 끌어 다른 날로 옮기는 동작은 Host(AllDayRow)에 넘긴다. Host가 없으면(오늘 보기) 끌기 없이 클릭만 받는다.
 * 완료한 일정은 흐린 색 + 취소선이고, 마우스를 올리면 바탕이 조금 더 진해진다 (들어올 때 50ms, 나갈 때 120ms).
 */
public class AllDayChip extends JComponent implements OccurrenceHolder {

    /** 칩의 높이 (논리 px) */
    public static final int HEIGHT = 20;
    private static final int ARC = 4;
    private static final int PAD_X = 6;

    private final RowHandler handler;
    private final Occurrence occurrence;
    private final ScheduleState state;
    private final Host host;
    private final Tween hover = new Tween(0f, this::repaint);
    private boolean dimmed;
    private boolean ghosted;

    AllDayChip(RowHandler handler, Occurrence occurrence, Host host) {
        this.handler = handler;
        this.occurrence = occurrence;
        this.host = host;
        this.state = handler.stateOf(occurrence);
        setOpaque(false);
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText(TextUtil.tip(occurrence.title(), "하루 종일 · "
                + handler.categoryName(occurrence.categoryId()) + " · " + state.label()));
        getAccessibleContext().setAccessibleName(TextUtil.clean(occurrence.title()) + ", 하루 종일, " + state.label()
                + (occurrence.isRepeating() ? ", 반복" : ""));
        Listeners.repaintOnFocus(this);
        addKeyListener(new ChipKeys());
        ChipMouse mouse = new ChipMouse();
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    @Override
    public Occurrence occurrence() {
        return occurrence;
    }

    /** 끌고 있는 동안 원래 자리의 칩을 흐리게 한다 */
    void setDimmed(boolean on) {
        if (dimmed != on) {
            dimmed = on;
            repaint();
        }
    }

    /** 끌어 놓은 카드가 내려앉는 동안 이 칩을 그리지 않는다 (카드가 대신 보인다) */
    void setGhosted(boolean on) {
        if (ghosted != on) {
            ghosted = on;
            repaint();
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

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Ui.px(120), Ui.px(HEIGHT));
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, Ui.px(HEIGHT));
    }

    /** 칩이 끌기·키보드 이동을 넘기는 쪽 (AllDayRow) */
    interface Host {
        void press(AllDayChip chip, MouseEvent e);

        void drag(MouseEvent e);

        /** 끌기를 끝냈다. 끌기였으면 true (그러면 칩은 클릭으로 열지 않는다) */
        boolean release(MouseEvent e);

        void nudge(AllDayChip chip, int days);
    }

    /** Enter 열기, Delete 삭제, Alt+←/→ 하루씩 옮기기 */
    private final class ChipKeys extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            int code = e.getKeyCode();
            if (e.isAltDown() && (code == KeyEvent.VK_LEFT || code == KeyEvent.VK_RIGHT)) {
                e.consume();
                int days = code == KeyEvent.VK_LEFT ? -1 : 1;
                if (host != null) {
                    host.nudge(AllDayChip.this, days);
                } else {
                    handler.nudge(occurrence, days, 0);
                }
            } else if (code == KeyEvent.VK_ENTER) {
                handler.open(occurrence);
            } else if (code == KeyEvent.VK_DELETE || code == KeyEvent.VK_BACK_SPACE) {
                handler.delete(occurrence);
            }
        }
    }

    /** 칩 위의 마우스 동작: 올림 강조, 메뉴, 눌러서 끌기(Host), 끌지 않았으면 열기 */
    private final class ChipMouse extends MouseAdapter {
        @Override
        public void mouseEntered(MouseEvent e) {
            hover.to(1f, 50);
        }

        @Override
        public void mouseExited(MouseEvent e) {
            hover.to(0f, 120);
        }

        @Override
        public void mousePressed(MouseEvent e) {
            if (menu(e) || !SwingUtilities.isLeftMouseButton(e)) {
                return;
            }
            requestFocusInWindow();
            if (host != null) {
                host.press(AllDayChip.this, e);
            }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (host != null) {
                host.drag(e);
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (menu(e) || !SwingUtilities.isLeftMouseButton(e)) {
                return;
            }
            boolean dragged = host != null && host.release(e);
            if (!dragged && contains(e.getPoint())) {
                handler.open(occurrence);
            }
        }

        private boolean menu(MouseEvent e) {
            if (e.isPopupTrigger()) {
                handler.showMenu(AllDayChip.this, e.getPoint(), occurrence);
            }
            return e.isPopupTrigger();
        }
    }

    // ---- 그리기 ----

    @Override
    protected void paintComponent(Graphics g) {
        if (ghosted) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.smooth(g2);
        if (dimmed) {
            g2.setComposite(AlphaComposite.SrcOver.derive(0.45f));
        }
        paintBody(g2, getWidth(), getHeight(), hover.value());
        if (isFocusOwner()) {
            FocusRing.paint(g2, getWidth(), getHeight(), Ui.px(ARC));
        }
        g2.dispose();
    }

    /** 칩 모양 하나(바탕 + 제목)를 w×h에 그린다. 끌어 올린 카드가 이 모양을 그대로 쓴다 */
    void paintBody(Graphics2D g2, int w, int h, float hoverValue) {
        Color fill = EventPaint.solidFill(occurrence, hoverValue);
        int arc = Ui.px(ARC);
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, w, h, arc, arc);
        g2.setFont(Fonts.medium(Fonts.CAPTION));
        FontMetrics fm = g2.getFontMetrics();
        int x = Ui.px(PAD_X);
        String text = TextUtil.ellipsize(TextUtil.clean(occurrence.title()), fm, w - x * 2);
        int base = (h - fm.getHeight()) / 2 + fm.getAscent();
        g2.setColor(EventPaint.solidText(occurrence, fill));
        Glyphs.draw(g2, text, x, base);
        if (state == ScheduleState.DONE) {
            g2.fillRect(x, base - fm.getAscent() / 3, Glyphs.width(fm, text), Math.max(1, Ui.px(1)));
        }
    }

    /** 점검용: 읽기 쉬운 설명 */
    @Override
    public String toString() {
        return "AllDayChip[" + occurrence.title() + " " + Fmt.dateShort(occurrence.date()) + "]";
    }
}
