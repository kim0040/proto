package proto.ui.sheet;

import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 시트 카드 자리: 넓은 창은 90%까지, 좁은 창은 양옆 여백만 남기고, 가운데에 놓되 위쪽 제목 줄 자리는 피한다 */
class SheetBoundsTest {

    @Test
    void 넓은_창에서는_90퍼센트까지_쓴다() {
        assertEquals(new Dimension(1296, 810), SheetBounds.max(1440, 900, 900, 700, 16, 16));
    }

    @Test
    void 좁은_창에서는_양옆_여백만_남긴다() {
        assertEquals(new Dimension(728, 484), SheetBounds.max(760, 540, 900, 700, 16, 40));
    }

    @Test
    void 가운데에_놓되_위쪽_자리는_피한다() {
        assertEquals(new Rectangle(340, 200, 600, 400), SheetBounds.center(1280, 800, new Dimension(600, 400), 40));
        assertEquals(new Rectangle(16, 40, 728, 484), SheetBounds.center(760, 540, new Dimension(728, 484), 40));
    }
}
