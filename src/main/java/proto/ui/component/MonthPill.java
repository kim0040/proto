package proto.ui.component;

import proto.entity.Occurrence;
import proto.ui.theme.Fonts;
import proto.ui.theme.Glyphs;
import proto.ui.theme.Ui;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

/**
 * [경계 boundary] 달력 칸 안의 일정 알약 하나를 그린다: 시간이 있는 일정은 연한 바탕 + 왼쪽 색 막대에 "9:00 회의", 하루 종일 일정은 진한 바탕에 제목만 (그리기만 하고 상태를 갖지 않는다).
 * MonthGrid가 칸 안의 줄마다, MonthChip이 끌어 올린 일정을 그릴 때 같은 모양을 쓰도록 따로 두었다 (F2, F3, F7). 색은 EventPaint가 정한다.
 * 완료한 일정은 흐린 색 + 취소선이다. 글자는 칸이 좁으면 제목이 먼저 "…"로 줄고, 시간은 자리가 있을 때만 앞에 붙는다.
 */
final class MonthPill {

    private static final int ARC = 4;
    private static final int PAD_X = 4;
    private static final float TIME_SIZE = 11f;

    private MonthPill() {
    }

    /** 알약의 모양(둥근 모서리)에 쓰는 반지름 (논리 px) */
    static int arc() {
        return Ui.px(ARC);
    }

    /** (x, y)에서 w×h인 알약을 그린다. 호버나 선택 강조는 없다 (칸이 맡는다) */
    static void paint(Graphics2D g2, Occurrence o, int x, int y, int w, int h) {
        boolean solid = o.allDay();
        Color fill = solid ? EventPaint.solidFill(o, 0f) : EventPaint.tintFill(o, 0f);
        g2.setColor(fill);
        g2.fillRoundRect(x, y, w, h, arc(), arc());
        int bar = solid ? 0 : LeftBar.width();
        if (!solid) {
            LeftBar.paint(g2, EventPaint.barColor(o), x, y, w, h, arc());
        }
        int tx = x + bar + Ui.px(PAD_X);
        int room = w - bar - Ui.px(PAD_X) * 2;
        if (!solid) {
            int used = paintTime(g2, o, fill, tx, y, h, room);
            tx += used;
            room -= used;
        }
        paintTitle(g2, o, solid ? EventPaint.solidText(o, fill) : EventPaint.tintTitle(o, fill), tx, y, h, room);
    }

    /** 앞에 붙는 시작 시간("9:00"). 자리가 모자라면 그리지 않는다. 쓴 너비(뒤 간격 포함)를 돌려준다 */
    private static int paintTime(Graphics2D g2, Occurrence o, Color fill, int x, int y, int h, int room) {
        g2.setFont(Fonts.regular(TIME_SIZE));
        FontMetrics fm = g2.getFontMetrics();
        String time = shortTime(o);
        int used = fm.stringWidth(time) + Ui.px(4);
        if (used > room - Ui.px(40)) {
            return 0;
        }
        g2.setColor(EventPaint.tintSub(o, fill));
        g2.drawString(time, x, y + (h - fm.getHeight()) / 2 + fm.getAscent());
        return used;
    }

    private static void paintTitle(Graphics2D g2, Occurrence o, Color color, int x, int y, int h, int room) {
        g2.setFont(Fonts.medium(Fonts.CAPTION));
        FontMetrics fm = g2.getFontMetrics();
        String text = TextUtil.ellipsize(TextUtil.clean(o.title()), fm, room);
        int base = y + (h - fm.getHeight()) / 2 + fm.getAscent();
        g2.setColor(color);
        Glyphs.draw(g2, text, x, base);
        if (o.completed()) {
            g2.fillRect(x, base - fm.getAscent() / 3, Glyphs.width(fm, text), Math.max(1, Ui.px(1)));
        }
    }

    /** 앞 0을 뗀 시작 시간 ("9:00", "14:30") */
    static String shortTime(Occurrence o) {
        return o.startTime().getHour() + ":" + String.format("%02d", o.startTime().getMinute());
    }
}
