package proto.ui.component;

import proto.ui.motion.Spring;
import proto.ui.motion.Tween;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/**
 * [경계 boundary] 완료 처리(F5)용 동그란 체크박스. 체크하면 색이 차오르며 작게 시작해 스프링으로 톡 튀어 오른다.
 * OccurrenceRow가 일정 줄 앞에 둔다. 눌리면 리스너를 부를 뿐 완료 처리는 직접 하지 않는다. OccurrenceRow가 RowHandler#toggleDone으로 controller에 요청하고,
 * 그 결과로 목록이 다시 그려질 때 setChecked로 모양이 바뀐다 ("화면은 요청만, 상태는 controller가 가진다").
 */
public class CompletionCheck extends JComponent {

    private static final int SIZE = 20;
    /** 체크 톡: 70% 크기에서 시작해 스프링(오버슈트 8%)으로 제 크기를 살짝 넘었다 돌아온다 */
    private static final Spring POP = Spring.of(0.08);
    private static final int POP_MS = 340;
    private static final float POP_FROM = 0.7f;

    private boolean checked;
    private boolean hover;
    private final Tween fill = new Tween(0f, this::repaint);
    private final Tween pulse = new Tween(1f, this::repaint);
    private final List<ActionListener> listeners = new ArrayList<>();

    public CompletionCheck() {
        setOpaque(false);
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new CheckMouse());
        Listeners.onClick(this, e -> fire());
        Listeners.onKey(this, code -> {
            if (code == KeyEvent.VK_SPACE || code == KeyEvent.VK_ENTER) {
                fire();
            }
        });
        Listeners.repaintOnFocus(this);
    }

    /** 체크 동그라미 위의 마우스 동작 */
    private final class CheckMouse extends MouseAdapter {
        @Override
        public void mouseEntered(MouseEvent e) {
            hover = true;
            repaint();
        }

        @Override
        public void mouseExited(MouseEvent e) {
            hover = false;
            repaint();
        }
    }

    public void addActionListener(ActionListener l) {
        listeners.add(l);
    }

    private void fire() {
        ActionEvent ev = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "toggle");
        listeners.forEach(l -> l.actionPerformed(ev));
    }

    public boolean isChecked() {
        return checked;
    }

    /** 체크 상태를 바꾼다. animate면 채움과 살짝 튀는 모션을 보여 준다 */
    public void setChecked(boolean value, boolean animate) {
        if (value == checked) {
            return;
        }
        checked = value;
        if (animate) {
            fill.to(value ? 1f : 0f, 160);
            if (value) {
                pulse.set(0f);
                pulse.to(1f, POP_MS, POP);
            }
        } else {
            fill.set(value ? 1f : 0f);
        }
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Ui.px(SIZE), Ui.px(SIZE));
    }

    /** 좁은 줄에서도 체크가 0×0으로 사라지지 않게 최소 크기를 원래 크기로 둔다 */
    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.smooth(g2);
        float d = Ui.px(SIZE - 4);
        // 체크할 때의 "톡" 효과: pulse가 스프링으로 0→1(중간에 1을 살짝 넘음)이라 크기가 70%→약 102%→100%가 된다. 0 밑으로는 내려가지 않게 자른다.
        float scale = Math.max(0f, POP_FROM + (1f - POP_FROM) * pulse.value());
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = d / 2f * scale;
        float f = fill.value();
        Color ring = Tokens.blend(hover ? Tokens.get(Tokens.ACCENT) : Tokens.get(Tokens.BORDER_STRONG),
                Tokens.get(Tokens.ACCENT), f);
        g2.setColor(Tokens.alpha(Tokens.get(Tokens.ACCENT), f));
        g2.fill(new java.awt.geom.Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));
        g2.setStroke(new BasicStroke(Ui.px(1.6f)));
        g2.setColor(ring);
        float inset = Ui.px(0.8f);
        g2.draw(new java.awt.geom.Ellipse2D.Float(cx - r + inset, cy - r + inset, (r - inset) * 2, (r - inset) * 2));
        paintCheck(g2, cx, cy, d * scale, f);
        if (isFocusOwner()) {
            g2.setColor(Tokens.alpha(Tokens.get(Tokens.ACCENT), 0.5f));
            g2.setStroke(new BasicStroke(Ui.px(2f)));
            g2.drawOval(Ui.px(1), Ui.px(1), getWidth() - Ui.px(2), getHeight() - Ui.px(2));
        }
        g2.dispose();
    }

    private void paintCheck(Graphics2D g2, float cx, float cy, float d, float f) {
        if (f <= 0.02f) {
            return;
        }
        Path2D p = new Path2D.Float();
        p.moveTo(cx - d * 0.24f, cy + d * 0.02f);
        p.lineTo(cx - d * 0.07f, cy + d * 0.19f);
        p.lineTo(cx + d * 0.26f, cy - d * 0.16f);
        g2.setStroke(new BasicStroke(Ui.px(1.9f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(Tokens.alpha(Tokens.get(Tokens.ON_ACCENT), f));
        g2.draw(p);
    }
}
