package proto.ui.component;

import proto.ui.motion.Tween;
import proto.ui.theme.Fonts;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import java.awt.BasicStroke;
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
 * [경계 boundary] 하루 종일 줄을 펼치고 접는 작은 버튼: 날짜 칸 안의 "+N"(숨은 일정 개수)과 시각 글자 영역의 ▾/▴ 화살표 두 모양이 있다.
 * AllDayRow가 만들어 일정이 많은 날과 왼쪽 칸에 놓는다 (F2). 눌리거나 Enter/Space를 누르면 주어진 동작을 부른다. 올리면 옅은 바탕이 50ms에 켜지고 120ms에 꺼진다.
 */
final class AllDayButton extends JComponent {

    private final String text;
    private final boolean chevronUp;
    private final Tween hover = new Tween(0f, this::repaint);

    private AllDayButton(String text, boolean chevronUp, String accessibleName, Runnable action) {
        this.text = text;
        this.chevronUp = chevronUp;
        setOpaque(false);
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText(accessibleName);
        getAccessibleContext().setAccessibleName(accessibleName);
        Listeners.repaintOnFocus(this);
        install(action);
    }

    /** 숨은 일정이 n개라는 "+n" 버튼 */
    static AllDayButton more(int n, Runnable action) {
        return new AllDayButton("+" + n, false, "하루 종일 일정 " + n + "개 더 보기", action);
    }

    /** 펼치기(▾) / 접기(▴) 화살표 버튼 */
    static AllDayButton chevron(boolean expanded, Runnable action) {
        return new AllDayButton(null, expanded, expanded ? "하루 종일 줄 접기" : "하루 종일 줄 펼치기", action);
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
        return new Dimension(Ui.px(text == null ? 22 : 30), Ui.px(AllDayChip.HEIGHT));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.smooth(g2);
        int arc = Ui.px(4);
        if (hover.value() > 0.01f) {
            g2.setColor(Tokens.alpha(Tokens.get(Tokens.HOVER), hover.value()));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
        }
        g2.setColor(Tokens.get(Tokens.MUTED));
        if (text != null) {
            g2.setFont(Fonts.medium(11f));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, Ui.px(6), (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        } else {
            paintChevron(g2);
        }
        if (isFocusOwner()) {
            g2.setColor(Tokens.alpha(Tokens.get(Tokens.ACCENT), 0.8f));
            g2.setStroke(new BasicStroke(Ui.px(1.5f)));
            g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, arc, arc);
        }
        g2.dispose();
    }

    /** 마우스(올림 강조, 놓으면 동작)와 키(Enter·Space) 연결 */
    private void install(Runnable action) {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hover.to(1f, 50);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover.to(0f, 120);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (contains(e.getPoint()) && e.getButton() == MouseEvent.BUTTON1) {
                    action.run();
                }
            }
        });
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_SPACE) {
                    action.run();
                }
            }
        });
    }

    private void paintChevron(Graphics2D g2) {
        g2.setStroke(new BasicStroke(Ui.px(1.4f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int cx = getWidth() / 2;
        int cy = getHeight() / 2;
        int a = Math.round(Ui.px(3.5f));
        int dir = chevronUp ? 1 : -1;
        g2.drawLine(cx - a, cy + dir * a / 2, cx, cy - dir * a / 2);
        g2.drawLine(cx, cy - dir * a / 2, cx + a, cy + dir * a / 2);
    }
}
