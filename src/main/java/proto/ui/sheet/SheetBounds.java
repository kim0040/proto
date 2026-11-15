package proto.ui.sheet;

import java.awt.Dimension;
import java.awt.Rectangle;

/**
 * [경계 boundary] 창 크기에서 시트 카드가 놓일 자리를 계산하는 규칙: 넓은 창에서는 창의 90%까지, 좁은 창에서는 양옆 16px만 남기고 거의 꽉 차게,
 * 그리고 가운데에 놓는다. SheetLayer가 배치할 때 부른다. 값은 모두 실제 px이고 여백은 호출하는 쪽이 Ui.px로 바꿔 넘긴다.
 */
final class SheetBounds {

    /** 이보다 좁은(낮은) 창은 "좁은 창"으로 보고 여백만 남긴다 (실제 px로 바꿔 쓴다) */
    static final int NARROW = 900;
    static final int SHORT = 700;
    static final float MAX_RATIO = 0.9f;

    private SheetBounds() {
    }

    /** 카드가 쓸 수 있는 가장 큰 크기. narrow·short·margin·top은 실제 px */
    static Dimension max(int width, int height, int narrow, int shortH, int margin, int top) {
        int w = width < narrow ? width - margin * 2 : Math.round(width * MAX_RATIO);
        int h = height < shortH ? height - margin - top : Math.round(height * MAX_RATIO);
        return new Dimension(Math.max(1, Math.min(w, width - margin * 2)), Math.max(1, Math.min(h, height - margin - top)));
    }

    /** card 크기의 카드를 창 가운데에 놓는다. 다만 위쪽 top만큼(맥의 제목 줄 버튼 자리)은 피한다 */
    static Rectangle center(int width, int height, Dimension card, int top) {
        int x = (width - card.width) / 2;
        int y = Math.max(top, (height - card.height) / 2);
        return new Rectangle(x, y, card.width, card.height);
    }
}
