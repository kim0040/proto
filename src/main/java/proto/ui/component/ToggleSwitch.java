package proto.ui.component;

import proto.ui.motion.Tween;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;
import javax.swing.JComponent;
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

/**
 * [경계 boundary] 켜기/끄기 스위치. 동그란 손잡이가 눌렀을 때 스프링으로 미끄러지며 살짝 튀고 색이 섞인다 (애니메이션이 꺼져 있으면 바로 바뀐다). 코드로 값을 바꿀 때는 튀지 않는다.
 * SettingsView의 켜기/끄기 설정(알림 소리 등)에 쓴다. 접근성 도구가 켜짐 상태를 읽도록 AccessibleState.CHECKED를 직접 알려 준다.
 */
public class ToggleSwitch extends JComponent {

    private static final int W = 42;
    private static final int H = 24;
    private static final int PAD = 3;
    private static final int SPRING_MS = 320;

    private final List<Consumer<Boolean>> listeners = new ArrayList<>();
    private final Tween position = new Tween(0f, this::repaint);
    private boolean on;

    public ToggleSwitch(boolean initial, String accessibleName) {
        this.on = initial;
        position.set(initial ? 1f : 0f);
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        getAccessibleContext().setAccessibleName(accessibleName);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    toggle();
                }
            }
        });
        Listeners.onKey(this, code -> {
            if (code == KeyEvent.VK_SPACE || code == KeyEvent.VK_ENTER) {
                toggle();
            }
        });
        Listeners.repaintOnFocus(this);
    }

    public boolean isOn() {
        return on;
    }

    /** 값을 정한다 (리스너는 부르지 않는다) */
    public void setOn(boolean value) {
        on = value;
        position.to(value ? 1f : 0f, 150);
    }

    /** 사용자가 눌러서 바꿀 때: 스프링으로 살짝 넘었다 돌아온다 */
    private void flip(boolean value) {
        on = value;
        position.springTo(value ? 1f : 0f, SPRING_MS);
    }

    public void addChangeListener(Consumer<Boolean> listener) {
        listeners.add(listener);
    }

    private void toggle() {
        flip(!on);
        listeners.forEach(l -> l.accept(on));
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Ui.px(W), Ui.px(H));
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.smooth(g2);
        int w = Ui.px(W);
        int h = Ui.px(H);
        float pos = position.value();
        float t = Math.max(0f, Math.min(1f, pos));
        g2.setColor(Tokens.blend(Tokens.get(Tokens.BORDER_STRONG), Tokens.get(Tokens.ACCENT), t));
        g2.fillRoundRect(0, 0, w, h, h, h);
        int knob = h - Ui.px(PAD) * 2;
        int x = Math.round(Ui.px(PAD) + (w - knob - Ui.px(PAD) * 2) * pos);
        g2.setColor(new Color(0, 0, 0, 40));
        g2.fillOval(x, Ui.px(PAD) + 1, knob, knob);
        g2.setColor(Color.WHITE);
        g2.fillOval(x, Ui.px(PAD), knob, knob);
        if (isFocusOwner()) {
            g2.setColor(Tokens.alpha(Tokens.get(Tokens.ACCENT), 0.5f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, h, h);
        }
        g2.dispose();
    }

    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {
                @Override
                public AccessibleRole getAccessibleRole() {
                    return AccessibleRole.TOGGLE_BUTTON;
                }

                @Override
                public AccessibleStateSet getAccessibleStateSet() {
                    AccessibleStateSet set = super.getAccessibleStateSet();
                    if (on) {
                        set.add(AccessibleState.CHECKED);
                    }
                    return set;
                }
            };
        }
        return accessibleContext;
    }
}
