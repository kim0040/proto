package proto.ui.component;

import proto.ui.motion.Tween;
import proto.ui.theme.Fonts;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.JComponent;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * [경계 boundary] 여러 선택지 중 하나를 고르는 분할 버튼. 선택 표시가 옆으로 미끄러진다 (반복 선택 등).
 * ScheduleDialog의 반복 선택(F9)과 설정·빈 시간 찾기 화면에서 쓴다. 여러 값(enum 등) 중 하나를 고르며, 값을 글자로 바꾸는 방법은 생성자로 받는다.
 * 칸 너비는 글자 길이에 맞춘다.
 */
public class Segmented<T> extends JComponent {

    private static final int PAD = 3;
    private static final int TEXT_PAD = 14;

    private final List<T> values;
    private final Function<T, String> label;
    private final List<Consumer<T>> listeners = new ArrayList<>();
    private int selected;
    private final Tween thumb = new Tween(0f, this::repaint);

    public Segmented(List<T> values, Function<T, String> label) {
        this.values = values;
        this.label = label;
        setFont(Fonts.medium(Fonts.SMALL));
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                select(indexAt(e.getX()), true);
            }
        });
        Listeners.onKey(this, code -> {
            if (code == KeyEvent.VK_LEFT) {
                select(Math.max(0, selected - 1), true);
            } else if (code == KeyEvent.VK_RIGHT) {
                select(Math.min(values.size() - 1, selected + 1), true);
            }
        });
        Listeners.repaintOnFocus(this);
    }

    @Override
    public javax.accessibility.AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {
                @Override
                public javax.accessibility.AccessibleRole getAccessibleRole() {
                    return javax.accessibility.AccessibleRole.PUSH_BUTTON;
                }
            };
        }
        return accessibleContext;
    }

    public void addChangeListener(Consumer<T> l) {
        listeners.add(l);
    }

    public T value() {
        return values.get(selected);
    }

    /** 값을 정한다 (리스너는 부르지 않는다) */
    public void setValue(T value, boolean animate) {
        int i = values.indexOf(value);
        if (i >= 0) {
            moveThumb(i, animate);
        }
    }

    private void select(int index, boolean animate) {
        if (index == selected) {
            return;
        }
        moveThumb(index, animate);
        listeners.forEach(l -> l.accept(value()));
    }

    private void moveThumb(int index, boolean animate) {
        selected = index;
        if (animate) {
            thumb.to(index, 220);
        } else {
            thumb.set(index);
        }
        repaint();
    }

    private int segWidth(int i) {
        return getFontMetrics(getFont()).stringWidth(label.apply(values.get(i))) + Ui.px(TEXT_PAD) * 2;
    }

    private int left(int i) {
        int x = Ui.px(PAD);
        for (int k = 0; k < i; k++) {
            x += segWidth(k);
        }
        return x;
    }

    private int indexAt(int x) {
        for (int i = 0; i < values.size(); i++) {
            if (x < left(i) + segWidth(i)) {
                return i;
            }
        }
        return values.size() - 1;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(left(values.size()) + Ui.px(PAD), Ui.px(36));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.smooth(g2);
        int arc = Ui.px(10);
        int w = left(values.size()) + Ui.px(PAD);
        g2.setColor(Tokens.get("proto.track"));
        g2.fillRoundRect(0, 0, w, getHeight(), arc, arc);
        paintThumb(g2, arc);
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        for (int i = 0; i < values.size(); i++) {
            String text = label.apply(values.get(i));
            g2.setColor(Tokens.get(i == selected ? Tokens.TEXT : Tokens.MUTED));
            int tx = left(i) + (segWidth(i) - fm.stringWidth(text)) / 2;
            g2.drawString(text, tx, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        }
        if (isFocusOwner()) {
            g2.setColor(Tokens.alpha(Tokens.get(Tokens.ACCENT), 0.6f));
            g2.setStroke(new java.awt.BasicStroke(Ui.px(1.5f)));
            g2.drawRoundRect(1, 1, w - 3, getHeight() - 3, arc, arc);
        }
        g2.dispose();
    }

    /** 선택 표시(thumb)가 두 칸 사이에 있을 때의 위치·너비를 앞뒤 칸의 값에서 비율로 섞어(보간) 계산한다. 그래서 칸 너비가 달라도 매끄럽게 미끄러진다. */
    private void paintThumb(Graphics2D g2, int arc) {
        int i0 = (int) Math.floor(thumb.value());
        int i1 = Math.min(values.size() - 1, i0 + 1);
        float f = thumb.value() - i0;
        int x = Math.round(left(i0) + (left(i1) - left(i0)) * f);
        int w = Math.round(segWidth(i0) + (segWidth(i1) - segWidth(i0)) * f);
        int pad = Ui.px(PAD);
        int inner = arc - Ui.px(2);
        g2.setColor(Tokens.get("proto.thumb"));
        g2.fillRoundRect(x, pad, w, getHeight() - pad * 2, inner, inner);
        g2.setColor(Tokens.alpha(Tokens.get(Tokens.BORDER_STRONG), 0.6f));
        g2.drawRoundRect(x, pad, w - 1, getHeight() - pad * 2 - 1, inner, inner);
    }
}
