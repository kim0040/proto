package proto.ui.component;

import proto.entity.ScheduleState;
import proto.ui.motion.ColorTween;
import proto.ui.theme.Fonts;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * [경계 boundary] 일정 상태(예정/진행 중/지연/완료)를 보여 주는 작은 글자 알약. 상태가 바뀌면 색이 부드럽게 섞인다.
 */
public class StatePill extends JComponent {

    private static final int FADE_MS = 240;
    private static final int FIXED_CHARS_WIDTH = 46;

    private ScheduleState state;
    private final ColorTween fg = new ColorTween(Color.GRAY, this::repaint);
    private final ColorTween bg = new ColorTween(Color.LIGHT_GRAY, this::repaint);

    public StatePill(ScheduleState state) {
        this.state = state;
        setOpaque(false);
        setFont(Fonts.medium(Fonts.CAPTION));
        fg.set(stateColor(state, "fg"));
        bg.set(stateColor(state, "bg"));
    }

    /** 상태를 바꾼다. animate가 true면 색이 크로스페이드로 바뀐다 */
    public void setState(ScheduleState next, boolean animate) {
        boolean changed = next != state;
        state = next;
        Color nfg = stateColor(next, "fg");
        Color nbg = stateColor(next, "bg");
        if (animate && changed) {
            fg.to(nfg, FADE_MS);
            bg.to(nbg, FADE_MS);
        } else {
            fg.set(nfg);
            bg.set(nbg);
        }
        repaint();
    }

    /** 테마가 바뀌었을 때 새 색으로 맞춘다 */
    @Override
    public void updateUI() {
        super.updateUI();
        if (state != null) {
            fg.set(stateColor(state, "fg"));
            bg.set(stateColor(state, "bg"));
        }
    }

    static Color stateColor(ScheduleState s, String part) {
        String name = switch (s) {
            case SCHEDULED -> "scheduled";
            case IN_PROGRESS -> "progress";
            case DELAYED -> "delayed";
            case DONE -> "done";
        };
        return Tokens.get("proto.state." + name + "." + part);
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        int w = Math.max(fm.stringWidth(state.label()) + Ui.px(16), Ui.px(FIXED_CHARS_WIDTH));
        return new Dimension(w, fm.getHeight() + Ui.px(6));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.smooth(g2);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(bg.current());
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
        g2.setFont(getFont());
        g2.setColor(fg.current());
        FontMetrics fm = g2.getFontMetrics();
        int x = (getWidth() - fm.stringWidth(state.label())) / 2;
        int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(state.label(), x, y);
        g2.dispose();
    }
}
