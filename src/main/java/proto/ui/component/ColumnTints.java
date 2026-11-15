package proto.ui.component;

import proto.ui.theme.Tokens;

import java.awt.Graphics2D;

/**
 * [경계 boundary] 주 시간표의 날짜 칸 바탕 칠: 오늘 칸은 옅은 회색, 토·일 칸은 아주 옅게(2%) 덮는다.
 * TimelineGrid(시간 본문)와 AllDayRow(하루 종일 줄)가 같은 칸 모양을 쓰도록 따로 두었다 (F2). 그리기만 하고 상태를 갖지 않는다.
 */
final class ColumnTints {

    private static final int SATURDAY = 5;

    private ColumnTints() {
    }

    /** height 높이의 칸들에 바탕을 칠한다. today는 오늘 칸 번호(0~6, 이번 주가 아니면 -1) */
    static void paint(Graphics2D g2, TimelineMetrics m, int height, int today) {
        if (today >= 0 && today < TimelineMetrics.DAYS) {
            g2.setColor(Tokens.alpha(Tokens.get(Tokens.SUBTLE), 0.22f));
            g2.fillRect(m.columnX(today), 0, m.columnWidth(today), height);
        }
        g2.setColor(Tokens.alpha(Tokens.get(Tokens.TEXT), 0.025f));
        int x = m.columnX(SATURDAY);
        g2.fillRect(x, 0, Math.max(0, m.columnX(TimelineMetrics.DAYS) - x), height);
    }
}
