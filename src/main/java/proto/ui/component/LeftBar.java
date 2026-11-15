package proto.ui.component;

import proto.ui.theme.Ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;

/**
 * [경계 boundary] 시간이 있는 일정 블록·알약 왼쪽 가장자리의 꽉 찬 색 막대 (노션 캘린더 모양): 둥근 모서리 모양으로 잘라서 막대가 왼쪽 모서리를 따라간다.
 * TimelineBlock과 MonthPill이 연한 바탕을 칠한 뒤 이어서 그린다 (F2, F7). 그리기만 하고 상태를 갖지 않는다.
 */
final class LeftBar {

    /** 막대 너비 (논리 px) */
    static final int WIDTH = 3;

    private LeftBar() {
    }

    /** 막대가 차지하는 실제 너비 */
    static int width() {
        return Ui.px(WIDTH);
    }

    /** (x, y)에서 w×h인 둥근 모서리(arc) 블록의 왼쪽 가장자리에 막대를 칠한다 */
    static void paint(Graphics2D g2, Color color, int x, int y, int w, int h, int arc) {
        Shape saved = g2.getClip();
        g2.clip(new RoundRectangle2D.Float(x, y, w, h, arc, arc));
        g2.setColor(color);
        g2.fillRect(x, y, width(), h);
        g2.setClip(saved);
    }
}
