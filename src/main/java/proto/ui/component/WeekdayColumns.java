package proto.ui.component;

import proto.ui.Fmt;
import proto.ui.motion.Tween;
import proto.ui.theme.Fonts;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.JComponent;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.Map;

/**
 * [경계 boundary] 요일별 일정 수 세로 막대 그래프. 월~일 7칸, 열릴 때 아래에서 자라난다.
 * StatsView가 요일별 일정 수(F14)를 보여 줄 때 쓴다. 숫자는 WeeklyStatistics#countByDay에서 온다.
 */
public class WeekdayColumns extends JComponent {

    private static final int DAYS = 7;
    private static final int GROW_MS = 420;

    private final Tween grow = new Tween(1f, this::repaint);
    private Map<DayOfWeek, Integer> counts = new HashMap<>();
    private DayOfWeek highlight;

    public WeekdayColumns() {
        setOpaque(false);
    }

    /** 데이터를 바꾼다. highlight는 이번 주에 들어 있는 오늘의 요일(없으면 null) */
    public void setCounts(Map<DayOfWeek, Integer> data, DayOfWeek today, boolean animate) {
        counts = new HashMap<>(data);
        highlight = today;
        if (animate) {
            grow.set(0f);
            grow.to(1f, GROW_MS);
        } else {
            grow.set(1f);
        }
        repaint();
    }

    /** 막대를 0에서 다시 자라게 한다 */
    public void replay() {
        grow.set(0f);
        grow.to(1f, GROW_MS);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Ui.px(400), Ui.px(168));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.smooth(g2);
        int max = Math.max(1, counts.values().stream().mapToInt(Integer::intValue).max().orElse(1));
        int colW = getWidth() / DAYS;
        int labelH = Ui.px(24);
        int top = Ui.px(22);
        int base = getHeight() - labelH;
        int barW = Math.min(Ui.px(28), colW - Ui.px(16));
        for (int i = 0; i < DAYS; i++) {
            DayOfWeek d = DayOfWeek.of(i + 1);
            int n = counts.getOrDefault(d, 0);
            float local = Math.max(0f, Math.min(1f, grow.value() * 1.3f - i * 0.05f));
            int h = n == 0 ? Ui.px(3) : Math.max(Ui.px(6), Math.round((base - top) * (n / (float) max) * local));
            int x = i * colW + (colW - barW) / 2;
            boolean today = d == highlight;
            g2.setColor(n == 0 ? Tokens.get(Tokens.BORDER) : Tokens.get(Tokens.ACCENT));
            if (n > 0 && !today) {
                g2.setColor(Tokens.blend(Tokens.get(Tokens.ACCENT), Tokens.get(Tokens.BG), 0.35f));
            }
            g2.fillRoundRect(x, base - h, barW, h, Ui.px(6), Ui.px(6));
            paintLabels(g2, d, n, x + barW / 2, base - h, i * colW + colW / 2, today);
        }
        g2.dispose();
    }

    private void paintLabels(Graphics2D g2, DayOfWeek d, int n, int barCenter, int barTop, int colCenter,
                             boolean today) {
        g2.setFont(Fonts.semibold(Fonts.SMALL));
        FontMetrics fm = g2.getFontMetrics();
        if (n > 0) {
            g2.setColor(Tokens.get(Tokens.TEXT));
            String s = String.valueOf(n);
            g2.drawString(s, barCenter - fm.stringWidth(s) / 2, barTop - Ui.px(6));
        }
        g2.setFont(today ? Fonts.semibold(Fonts.SMALL) : Fonts.regular(Fonts.SMALL));
        fm = g2.getFontMetrics();
        g2.setColor(Tokens.get(today ? Tokens.ACCENT : Tokens.MUTED));
        String w = Fmt.weekdayShort(d);
        g2.drawString(w, colCenter - fm.stringWidth(w) / 2, getHeight() - Ui.px(5));
    }
}
