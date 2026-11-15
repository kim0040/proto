package proto.ui.component;

import com.formdev.flatlaf.util.Animator;
import proto.ui.motion.Motion;
import proto.ui.motion.Tween;
import proto.ui.theme.Ui;

import javax.swing.JComponent;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;

/**
 * [경계 boundary] 끌고 있는 "들린 카드" 하나: 마우스를 소수점 단위까지 그대로 따라다니고, 들릴 때 살짝 커지며 그림자가 짙어진다.
 * TimelineDrag(시간표 블록)와 MonthDrag(달력 일정 칩)가 하나씩 만들고, 놓은 뒤에는 TimelineSettle·MonthSettle이 이어받아 제자리로 보낸다 (F3).
 * 카드의 모습은 Content가 그리고(블록 복사본·칩), 이 클래스는 위치·크기·들림 정도(0~1)와 그림자·투명도(5% 줄임)만 맡는다.
 * 잡은 자리(grab)를 기억해 카드가 마우스 밑에서 튀지 않는다. 들림은 110ms 동안 올라가며 Motion을 끄면 바로 다 들린 상태가 된다.
 */
final class DragCard {

    /** 카드 안쪽 모습을 그리는 쪽. 좌표는 0,0에서 w×h */
    interface Content {
        void paint(Graphics2D g2, int w, int h);
    }

    private final JComponent host;
    private final Content content;
    private final Rectangle2D.Float base;
    private final Point grab;
    private final Tween lift;
    private final int arc;
    private Motion.Handle flight;

    /**
     * @param start 카드의 처음 자리(호스트 좌표). 들리기 전이라 원래 블록과 같다
     * @param press 마우스를 누른 자리(호스트 좌표). start 안쪽이라 잡은 자리가 정해진다
     */
    DragCard(JComponent host, Content content, Rectangle start, Point press, int arc) {
        this.host = host;
        this.content = content;
        this.base = new Rectangle2D.Float(start.x, start.y, start.width, start.height);
        this.grab = new Point(press.x - start.x, press.y - start.y);
        this.arc = arc;
        this.lift = new Tween(0f, host::repaint);
    }

    /** 들리기 시작한다 (110ms, 끄면 바로 다 들림) */
    void lift() {
        lift.set(0f);
        lift.to(1f, DragMotion.LIFT_MS, DragMotion.OUT);
    }

    /** 0(내려놓음)~1(다 들림) */
    float liftValue() {
        return lift.value();
    }

    /** 마우스(호스트 좌표)가 있는 곳에 맞춰 카드를 옮긴다. 잡은 자리는 그대로 지키고 호스트 밖으로는 나가지 않는다 */
    void follow(double x, double y) {
        base.x = clamp((float) x - grab.x, 0f, Math.max(0f, host.getWidth() - base.width));
        base.y = clamp((float) y - grab.y, 0f, Math.max(0f, host.getHeight() - base.height));
    }

    /** 위쪽 모서리는 두고 높이만 바꾼다 (크기 바꾸기: 아래 모서리가 마우스를 따라간다) */
    void setHeight(float h) {
        base.height = Math.max(1f, h);
    }

    /** 들림 효과를 빼고 본 지금 모양 */
    Rectangle2D.Float rest() {
        return new Rectangle2D.Float(base.x, base.y, base.width, base.height);
    }

    /** 들림(2% 확대)까지 반영한 지금 모양 */
    Rectangle shown() {
        Rectangle2D.Float r = scaled(Math.max(0f, lift.value()));
        return new Rectangle(Math.round(r.x), Math.round(r.y), Math.round(r.width), Math.round(r.height));
    }

    /** 가운데를 기준으로 들림 정도 k에 맞춰 키운 모양 */
    private Rectangle2D.Float scaled(float k) {
        float s = 1f + DragMotion.LIFT_SCALE * k;
        float w = base.width * s;
        float h = base.height * s;
        return new Rectangle2D.Float(base.x - (w - base.width) / 2f, base.y - (h - base.height) / 2f, w, h);
    }

    /**
     * 카드를 end 자리로 날려 보내며 들림을 푼다 (그림자가 옅어지고 크기가 제자리로). done은 도착한 뒤 한 번 부른다.
     * ease가 스프링이면 도착 자리를 아주 조금 넘었다 돌아온다.
     */
    void flyTo(Rectangle end, int ms, Animator.Interpolator ease, Runnable done) {
        cancelFlight();
        Rectangle2D.Float from = rest();
        lift.to(0f, ms, DragMotion.OUT);
        flight = Motion.run(ms, ease, f -> {
            base.x = from.x + (end.x - from.x) * f;
            base.y = from.y + (end.y - from.y) * f;
            base.width = Math.max(1f, from.width + (end.width - from.width) * f);
            base.height = Math.max(1f, from.height + (end.height - from.height) * f);
            host.repaint();
        }, done);
    }

    /** 날아가던 모션을 도착 없이 멈춘다 */
    void cancelFlight() {
        if (flight != null) {
            flight.cancel();
            flight = null;
        }
    }

    /** 날아가던 모션을 도착 자리까지 바로 건너뛰고 도착 동작을 부른다 */
    void finishFlight() {
        if (flight != null) {
            Motion.Handle h = flight;
            flight = null;
            h.finish();
        }
    }

    /** 그림자·투명도·확대를 적용해 카드를 그린다 */
    void paint(Graphics2D g) {
        float k = Math.max(0f, Math.min(1f, lift.value()));
        Rectangle2D.Float r = scaled(k);
        float blur = DragMotion.BLUR_REST + (DragMotion.BLUR_LIFT - DragMotion.BLUR_REST) * k;
        LiftShadow.paint(g, r, arc, blur, k);
        int w = Math.max(1, Math.round(base.width));
        int h = Math.max(1, Math.round(base.height));
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.smooth(g2);
        g2.setComposite(AlphaComposite.SrcOver.derive(1f - DragMotion.LIFT_FADE * k));
        g2.translate(r.x, r.y);
        g2.scale(r.width / w, r.height / h);
        content.paint(g2, w, h);
        g2.dispose();
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
