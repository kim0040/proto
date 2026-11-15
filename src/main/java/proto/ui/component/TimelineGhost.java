package proto.ui.component;

import proto.ui.theme.Fonts;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import java.awt.BasicStroke;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * [경계 boundary] 끌고 있는 동안 시간표에 그려지는 "놓일 자리" 표시: 반투명한 강조색 자리 틀과 바뀔 시간 범위 알약 (그리기만 하고 상태를 갖지 않는다).
 * TimelineDrag와 TimelineSettle이 TimelineGrid 위에 그린다 (F3). 일정 카드 자체는 DragCard가 마우스를 따라다니며 그리므로, 여기서는 카드가 놓일 15분·하루 단위 자리만 보여 준다.
 * 자리 틀은 일정 블록처럼 색을 채우지 않고 강조색 테두리와 아주 옅은 바탕만 쓰고, alpha로 서서히 나타나고 사라진다.
 * 블록이 낮아도 시간이 읽히도록 "화 09:15 – 10:15" 알약은 자리 틀 바로 위(맨 위쪽이면 아래)에 따로 그리며, 자리 틀이 움직이면 같이 움직인다.
 */
final class TimelineGhost {

    private TimelineGhost() {
    }

    /** 자리 틀의 테두리와 옅은 바탕만 alpha(0~1)만큼 그린다 (놓은 뒤 서서히 지울 때도 쓴다) */
    static void paintFrame(Graphics2D g2, Rectangle box, float alpha) {
        Ui.smooth(g2);
        int arc = Ui.px(6);
        g2.setColor(Tokens.alpha(Tokens.get(Tokens.ACCENT), 0.10f * alpha));
        g2.fillRoundRect(box.x, box.y, box.width, box.height, arc, arc);
        g2.setColor(Tokens.alpha(Tokens.get(Tokens.ACCENT), 0.70f * alpha));
        g2.setStroke(new BasicStroke(Ui.px(1.5f)));
        g2.drawRoundRect(box.x, box.y, box.width - 1, box.height - 1, arc, arc);
    }

    /** 틀 위(자리가 없으면 아래)에 시간 범위 알약을 그린다. 시간표 밖으로 나가지 않게 가둔다 */
    static void paintLabel(Graphics2D g2, Rectangle box, Rectangle bounds, String label) {
        g2.setFont(Fonts.semibold(Fonts.CAPTION));
        FontMetrics fm = g2.getFontMetrics();
        int w = fm.stringWidth(label) + Ui.px(16);
        int h = fm.getHeight() + Ui.px(6);
        int x = Math.max(bounds.x, Math.min(box.x, bounds.x + bounds.width - w));
        int y = box.y - h - Ui.px(3);
        if (y < bounds.y) {
            y = box.y + box.height + Ui.px(3);
        }
        g2.setColor(Tokens.get(Tokens.SURFACE));
        g2.fillRoundRect(x, y, w, h, h, h);
        g2.setColor(Tokens.alpha(Tokens.get(Tokens.ACCENT), 0.85f));
        g2.setStroke(new BasicStroke(Ui.px(1f)));
        g2.drawRoundRect(x, y, w - 1, h - 1, h, h);
        g2.setColor(Tokens.get(Tokens.ACCENT));
        g2.drawString(label, x + Ui.px(8), y + (h - fm.getHeight()) / 2 + fm.getAscent());
    }
}
