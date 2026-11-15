package proto.ui.component;

import proto.entity.Occurrence;
import proto.ui.Fmt;
import proto.ui.theme.Glyphs;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.JComponent;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;

/**
 * [경계 boundary] 목록 줄의 시간 자리에 쓰는 "하루 종일" 표시: 카테고리 색의 작은 꽉 찬 네모(10px, 둥근 모서리) + "하루 종일" 글자.
 * OccurrenceRow(오늘 목록·달력 옆 목록)가 하루 종일 일정이면 시간 범위 대신 이 표시를 넣는다 (F2, F7). 줄에는 색 바탕을 깔지 않고(목록은 차분하게) 이 네모만 색을 가진다.
 * 시간 문구가 필요한 곳(툴팁, 접근성 이름)은 when으로 같은 규칙("하루 종일" 또는 "09:00 – 10:30")을 쓴다.
 */
final class AllDayTag extends JComponent {

    static final String LABEL = "하루 종일";
    private static final int SQUARE = 10;
    private static final int GAP = 6;

    private final Font font;
    private final String colorToken;
    private Occurrence occurrence;

    AllDayTag(Font font, String colorToken) {
        this.font = font;
        this.colorToken = colorToken;
        setOpaque(false);
    }

    /** 일정의 시간 문구: 하루 종일이면 "하루 종일", 아니면 "09:00 – 10:30" */
    static String when(Occurrence o) {
        return o.allDay() ? LABEL : Fmt.range(o);
    }

    void setOccurrence(Occurrence o) {
        this.occurrence = o;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(font);
        return new Dimension(Ui.px(SQUARE + GAP) + Glyphs.width(fm, LABEL), Math.max(Ui.px(20), fm.getHeight()));
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.smooth(g2);
        int s = Ui.px(SQUARE);
        g2.setColor(occurrence == null ? Tokens.get(Tokens.FAINT) : EventPaint.base(occurrence));
        g2.fillRoundRect(0, (getHeight() - s) / 2, s, s, Ui.px(3), Ui.px(3));
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(Tokens.get(colorToken));
        Glyphs.draw(g2, LABEL, s + Ui.px(GAP), (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }
}
