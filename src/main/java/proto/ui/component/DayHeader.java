package proto.ui.component;

import proto.ui.Fmt;
import proto.ui.theme.Fonts;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.time.LocalDate;

/**
 * [경계 boundary] 이번 주 보기의 날짜 머리글: 요일 글자 + 날짜 숫자. 오늘이면 숫자 뒤에 강조색 동그라미를 깐다 (막대 아님).
 */
public class DayHeader extends JPanel {

    public DayHeader(LocalDate day, boolean today) {
        super(new FlowLayout(FlowLayout.LEFT, Ui.px(6), 0));
        setOpaque(false);
        setAlignmentX(0f);
        add(new TokenLabel(Fmt.weekdayShort(day.getDayOfWeek()), Fonts.medium(Fonts.SMALL),
                today ? Tokens.ACCENT : Tokens.MUTED));
        add(new DayNumber(day.getDayOfMonth(), today));
        getAccessibleContext().setAccessibleName(Fmt.dateLong(day) + (today ? ", 오늘" : ""));
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    /** 날짜 숫자. 오늘이면 뒤에 강조색 동그라미를 깐다 */
    private static final class DayNumber extends JComponent {
        private final int number;
        private final boolean today;

        DayNumber(int number, boolean today) {
            this.number = number;
            this.today = today;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(Ui.px(28), Ui.px(28));
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Ui.smooth(g2);
            if (today) {
                g2.setColor(Tokens.get(Tokens.ACCENT));
                g2.fillOval(0, 0, getWidth(), getHeight());
            }
            g2.setFont(Fonts.semibold(Fonts.BODY));
            FontMetrics fm = g2.getFontMetrics();
            Color c = Tokens.get(today ? Tokens.ON_ACCENT : Tokens.TEXT);
            g2.setColor(c);
            String s = String.valueOf(number);
            g2.drawString(s, (getWidth() - fm.stringWidth(s)) / 2, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }
}
