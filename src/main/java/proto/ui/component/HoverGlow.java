package proto.ui.component;

import proto.ui.motion.Tween;
import proto.ui.theme.Tokens;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Objects;

/**
 * [경계 boundary] 빽빽한 칸 위에서 마우스가 가리키는 칸 하나를 옅게 밝히는 강조 (시간표 빈 15분 칸, 달력 날짜 칸, 끌 때 놓일 날짜 칸).
 * TimelineGrid와 MonthGrid·MonthDrag가 하나씩 갖고 moveTo로 칸을 알리고 paint로 그린다 (F3). 새 칸은 짧게(기본 40ms) 나타나고, 떠난 칸은 천천히(110ms) 사라진다.
 * 떠난 칸의 흔적은 하나만 남긴다: 마우스를 빨리 움직여도 여러 칸이 한꺼번에 흐릿하게 남아 지저분해지지 않는다.
 * Motion을 끄면 나타나고 사라지는 것이 바로 일어난다.
 */
final class HoverGlow {

    private final Tween appear;
    private final Tween vanish;
    private final int inMs;
    private final int outMs;
    private Rectangle current;
    private Rectangle leaving;

    HoverGlow(Runnable onChange, int inMs, int outMs) {
        this.appear = new Tween(0f, onChange);
        this.vanish = new Tween(0f, onChange);
        this.inMs = inMs;
        this.outMs = outMs;
    }

    /** 강조할 칸을 알린다. null이면 강조를 거둔다 */
    void moveTo(Rectangle next) {
        if (Objects.equals(next, current)) {
            return;
        }
        if (current != null) {
            leaving = current;
            vanish.set(appear.value());
            vanish.to(0f, outMs, DragMotion.IN);
        }
        current = next == null ? null : new Rectangle(next);
        appear.set(0f);
        if (current != null) {
            appear.to(1f, inMs, DragMotion.OUT);
        }
    }

    /** 지금 강조 중인 칸 (없으면 null) */
    Rectangle current() {
        return current;
    }

    /** 화면에 아직 남은 강조가 있는지 */
    boolean visible() {
        return (current != null && appear.value() > 0.01f) || (leaving != null && vanish.value() > 0.01f);
    }

    /** 떠나는 칸과 새 칸을 그린다. border가 null이면 테두리 없이 채우기만 한다 */
    void paint(Graphics2D g2, Color fill, Color border, int arc) {
        if (leaving != null && vanish.value() > 0.01f) {
            paintOne(g2, leaving, fill, border, arc, vanish.value());
        }
        if (current != null && appear.value() > 0.01f) {
            paintOne(g2, current, fill, border, arc, appear.value());
        }
    }

    private static void paintOne(Graphics2D g2, Rectangle r, Color fill, Color border, int arc, float a) {
        g2.setColor(Tokens.alpha(fill, a));
        g2.fillRoundRect(r.x, r.y, r.width, r.height, arc, arc);
        if (border != null) {
            g2.setColor(Tokens.alpha(border, a));
            g2.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, arc, arc);
        }
    }
}
