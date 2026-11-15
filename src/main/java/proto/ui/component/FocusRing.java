package proto.ui.component;

import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import java.awt.BasicStroke;
import java.awt.Graphics2D;

/**
 * [경계 boundary] 색으로 꽉 찬 블록·칩에 키보드 포커스가 있을 때 두르는 링: 강조색 바깥 선 + 안쪽의 바탕색 가는 선 (어떤 색 위에서도 보인다).
 * TimelineBlock, AllDayChip, MonthPill이 같은 모양을 쓰게 하려고 따로 두었다 (F2). 그리기만 하고 상태를 갖지 않는다.
 */
final class FocusRing {

    private FocusRing() {
    }

    /** 0,0에서 w×h인 둥근 모서리(arc) 안쪽에 링을 그린다 */
    static void paint(Graphics2D g2, int w, int h, int arc) {
        g2.setStroke(new BasicStroke(Ui.px(1.5f)));
        g2.setColor(Tokens.get(Tokens.ACCENT));
        g2.drawRoundRect(1, 1, w - 3, h - 3, arc, arc);
        g2.setStroke(new BasicStroke(Ui.px(1f)));
        g2.setColor(Tokens.alpha(Tokens.get(Tokens.BG), 0.9f));
        int in = Ui.px(3);
        g2.drawRoundRect(in, in, w - in * 2 - 1, h - in * 2 - 1, Math.max(2, arc - 2), Math.max(2, arc - 2));
    }
}
