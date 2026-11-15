package proto.ui.component;

import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * [경계 boundary] 들린 카드 밑에 깔리는 부드러운 그림자를 그린다 (그리기만 하고 상태를 갖지 않는다).
 * DragCard(끌고 있는 카드)와 TimelineSettle(놓은 뒤 내려앉는 블록)이 같은 그림자를 쓴다 (F3). 번짐 정도(blur)와 세기(strength)로 "얼마나 들렸나"를 나타낸다.
 * 가장자리로 갈수록 옅어지는 둥근 사각형을 겹쳐 번짐을 흉내 내고, 카드 자리는 오려 내서 반투명한 카드 아래가 어두워 보이지 않게 한다.
 */
final class LiftShadow {

    private static final float PEAK = 0.30f;

    private LiftShadow() {
    }

    /**
     * @param card     그림자를 드리울 카드 모양 (시간표·달력 좌표)
     * @param arc      카드 모서리 지름
     * @param blur     번짐 폭(논리 px)
     * @param strength 0~1. 0이면 그리지 않는다
     */
    static void paint(Graphics2D g2, Rectangle2D card, int arc, float blur, float strength) {
        float k = Math.max(0f, Math.min(1f, strength));
        if (k < 0.01f) {
            return;
        }
        float spread = Ui.px(blur);
        int layers = Math.max(2, Math.round(spread / 2f));
        Shape old = g2.getClip();
        g2.clip(outside(card, arc, spread));
        for (int i = layers; i >= 1; i--) {
            g2.setColor(Tokens.alpha(Color.BLACK, PEAK * k / layers));
            g2.fill(layer(card, arc, spread, spread * i / layers));
        }
        g2.setClip(old);
    }

    /** 카드 둘레 spread만큼 넓힌 영역에서 카드 자리를 뺀 모양 (그림자를 그려도 되는 곳) */
    private static Shape outside(Rectangle2D card, int arc, float spread) {
        Area area = new Area(new Rectangle2D.Float((float) card.getX() - spread * 2, (float) card.getY() - spread * 2,
                (float) card.getWidth() + spread * 4, (float) card.getHeight() + spread * 4));
        area.subtract(new Area(new RoundRectangle2D.Float((float) card.getX(), (float) card.getY(),
                (float) card.getWidth(), (float) card.getHeight(), arc, arc)));
        return area;
    }

    /** 카드를 d만큼 키운 둥근 사각형. 번짐이 클수록 그림자가 아래로 더 처진다 */
    private static RoundRectangle2D layer(Rectangle2D card, int arc, float spread, float d) {
        float drop = spread * 0.35f;
        return new RoundRectangle2D.Float((float) card.getX() - d, (float) card.getY() - d + drop,
                (float) card.getWidth() + d * 2, (float) card.getHeight() + d * 2, arc + d * 2, arc + d * 2);
    }
}
