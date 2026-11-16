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
 * OccurrenceRow가 일정 줄 오른쪽에 둔다. 상태(F6) 자체는 StateCalculator가 계산한 값을 받아(RowHandler#stateOf 경유) 이 클래스는 그 상태에 맞는 색과 글자만 보여 준다.
 */
public class StatePill extends JComponent {

    private static final int FADE_MS = 240;
    private static final int FIXED_CHARS_WIDTH = 46;

    private final ColorTween fg = new ColorTween(Color.GRAY, this::repaint);
    private final ColorTween bg = new ColorTween(Color.LIGHT_GRAY, this::repaint);
    private ScheduleState state;

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

    /** 테마가 바뀌면(밝게↔어둡게) 상태 색 토큰을 다시 읽어, 애니메이션 없이 바로 새 색으로 맞춘다. */
    @Override
    public void updateUI() {
        super.updateUI();
        if (state != null) {
            fg.set(stateColor(state, "fg"));
            bg.set(stateColor(state, "bg"));
        }
    }

    /** 좁은 줄에서도 알약이 0×0으로 사라지지 않게 최소 크기를 원래 크기로 둔다 */
    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
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

    static Color stateColor(ScheduleState s, String part) {
        String name = switch (s) {
            case SCHEDULED -> "scheduled";
            case IN_PROGRESS -> "progress";
            case DELAYED -> "delayed";
            case DONE -> "done";
        };
        return Tokens.get("proto.state." + name + "." + part);
    }
}
