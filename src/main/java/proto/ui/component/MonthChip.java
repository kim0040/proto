package proto.ui.component;

import proto.entity.Occurrence;
import proto.ui.theme.Ui;

import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * [경계 boundary] 달력에서 끌고 다니는 일정 알약 하나의 모양: 칸 안의 일정 줄과 똑같은 알약(MonthPill)을 그대로 쓴다.
 * MonthDrag가 일정 줄을 끌기 시작할 때 만들어 DragCard의 Content로 넘긴다 (F3). 들림(그림자·살짝 커짐)은 DragCard가 맡고, 모양은 칸 안의 줄과 같아서
 * 칸에 내려앉는 순간 튀지 않는다. 알약은 일정 줄보다 위아래 1px씩 작게 잡아 줄 사이에 틈이 생기게 한다.
 */
final class MonthChip implements DragCard.Content {

    /** 알약이 줄 위아래에서 비워 두는 간격 (논리 px) */
    static final int GAP_Y = 1;

    private final Occurrence item;

    MonthChip(Occurrence item) {
        this.item = item;
    }

    /** 일정 줄(row) 위에 놓이는 알약의 처음 모양: 줄과 같은 너비 */
    static Rectangle startRect(Rectangle row) {
        return landRect(row);
    }

    /** 일정 줄(row)에 내려앉았을 때 알약이 차지할 모양 */
    static Rectangle landRect(Rectangle row) {
        return new Rectangle(row.x, row.y + Ui.px(GAP_Y), row.width, row.height - Ui.px(GAP_Y) * 2);
    }

    @Override
    public void paint(Graphics2D g2, int w, int h) {
        MonthPill.paint(g2, item, 0, 0, w, h);
    }
}
