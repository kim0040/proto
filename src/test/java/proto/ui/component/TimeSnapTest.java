package proto.ui.component;

import org.junit.jupiter.api.Test;
import proto.ui.component.TimeSnap.Range;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 끌기 계산: 15분 맞춤, 하루 안으로 가두기, 23:59 처리 */
class TimeSnapTest {

    @Test
    void 옮기기는_길이를_지키고_15분_단위로_맞춘다() {
        assertEquals(new Range(10 * 60, 11 * 60), TimeSnap.moved(9 * 60, 10 * 60, 62));
        assertEquals(new Range(9 * 60 + 15, 10 * 60 + 15), TimeSnap.moved(9 * 60, 10 * 60, 8));
        assertEquals(new Range(9 * 60, 10 * 60), TimeSnap.moved(9 * 60, 10 * 60, 7));
    }

    @Test
    void 옮기기는_하루_안에서_멈춘다() {
        assertEquals(new Range(0, 60), TimeSnap.moved(60, 120, -600));
        assertEquals(new Range(23 * 60, 24 * 60), TimeSnap.moved(21 * 60, 22 * 60, 600));
        // 23:00~23:59(끝은 1440으로 센다)을 아래로 끌어도 그대로
        assertEquals(new Range(23 * 60, 1440), TimeSnap.moved(23 * 60, 1440, 120));
    }

    @Test
    void 원래_15분_단위가_아닌_시작은_움직이지_않으면_그대로다() {
        assertEquals(new Range(9 * 60 + 5, 10 * 60 + 5), TimeSnap.moved(9 * 60 + 5, 10 * 60 + 5, 3));
    }

    @Test
    void 크기_바꾸기는_끝만_옮기고_최소_15분이다() {
        assertEquals(new Range(9 * 60, 11 * 60), TimeSnap.resized(9 * 60, 10 * 60, 60));
        assertEquals(new Range(9 * 60, 9 * 60 + 15), TimeSnap.resized(9 * 60, 10 * 60, -500));
        assertEquals(new Range(9 * 60, 1440), TimeSnap.resized(9 * 60, 10 * 60, 5000));
    }

    @Test
    void 빈곳_끌기는_시작은_내림_끝은_올림() {
        assertEquals(new Range(9 * 60, 10 * 60 + 30), TimeSnap.created(9 * 60 + 7, 10 * 60 + 20));
        assertEquals(new Range(9 * 60, 10 * 60 + 30), TimeSnap.created(10 * 60 + 20, 9 * 60 + 7));
        assertEquals(new Range(9 * 60, 9 * 60 + 15), TimeSnap.created(9 * 60 + 3, 9 * 60 + 3));
        assertEquals(new Range(1440 - 15, 1440), TimeSnap.created(1440, 1440));
    }

    @Test
    void 두번_누르기는_한시간이고_하루를_넘으면_끝에_맞춘다() {
        assertEquals(new Range(14 * 60, 15 * 60), TimeSnap.hourAt(14 * 60 + 10));
        assertEquals(new Range(23 * 60, 1440), TimeSnap.hourAt(23 * 60 + 40));
    }

    @Test
    void 키보드_이동은_맞춤없이_하루_안에서_멈춘다() {
        assertEquals(new Range(9 * 60 + 15, 10 * 60 + 15), TimeSnap.shifted(9 * 60, 10 * 60, 15));
        assertEquals(new Range(0, 60), TimeSnap.shifted(0, 60, -15));
        assertEquals(new Range(23 * 60, 1440), TimeSnap.shifted(23 * 60, 1440, 15));
    }

    @Test
    void 분과_시각_바꾸기() {
        assertEquals(1440, TimeSnap.endMinuteOf(LocalTime.of(23, 59)));
        assertEquals(10 * 60 + 30, TimeSnap.endMinuteOf(LocalTime.of(10, 30)));
        assertEquals(LocalTime.of(23, 59), TimeSnap.timeOf(1440));
        assertEquals(LocalTime.of(9, 15), TimeSnap.timeOf(9 * 60 + 15));
        assertEquals(LocalTime.MIDNIGHT, TimeSnap.timeOf(-5));
    }
}
