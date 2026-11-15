package proto.ui.component;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import proto.entity.CategoryPalette;
import proto.entity.Occurrence;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.ui.theme.CategoryColors;
import proto.ui.theme.ThemeManager;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 일정 색 규칙: 12색 팔레트 전부가 밝은/어두운 테마의 연한 바탕·진한 바탕 위에서 본문 대비 4.5:1 이상이다 */
class EventPaintTest {

    @BeforeAll
    static void theme() {
        ThemeManager.install();
        CategoryColors.setResolver(id -> id);
    }

    @AfterAll
    static void restore() {
        CategoryColors.setResolver(null);
    }

    private static Occurrence occ(String key, boolean allDay, boolean done) {
        return new Occurrence("s", LocalDate.of(2026, 11, 12), "제목", LocalTime.of(9, 0), LocalTime.of(10, 0), key, "",
                RepeatRule.NONE, ReminderOption.NONE, done, allDay);
    }

    /** 테마 하나에서 모든 팔레트 색의 가장 낮은 대비를 [연한 제목, 연한 시간, 경고, 진한 글자]로 모은다 */
    private static double[] worst(boolean dark) throws Exception {
        SwingUtilities.invokeAndWait(() -> ThemeManager.apply(dark, false, false));
        double[] min = {99, 99, 99, 99};
        for (String key : CategoryPalette.KEYS) {
            Occurrence timed = occ(key, false, false);
            Occurrence solid = occ(key, true, false);
            for (float hover : new float[] {0f, 1f}) {
                Color tint = EventPaint.tintFill(timed, hover);
                min[0] = Math.min(min[0], EventPaint.contrast(EventPaint.tintTitle(timed, tint), tint));
                min[1] = Math.min(min[1], EventPaint.contrast(EventPaint.tintSub(timed, tint), tint));
                min[2] = Math.min(min[2], EventPaint.contrast(EventPaint.warning(tint), tint));
                Color fill = EventPaint.solidFill(solid, hover);
                min[3] = Math.min(min[3], EventPaint.contrast(EventPaint.solidText(solid, fill), fill));
            }
        }
        return min;
    }

    @Test
    void 밝은_테마에서_모든_색의_본문_대비가_4_5_이상이다() throws Exception {
        for (double c : worst(false)) {
            assertTrue(c >= EventPaint.MIN_CONTRAST, "밝은 테마 최저 대비 " + java.util.Arrays.toString(worst(false)));
        }
    }

    @Test
    void 어두운_테마에서_모든_색의_본문_대비가_4_5_이상이다() throws Exception {
        for (double c : worst(true)) {
            assertTrue(c >= EventPaint.MIN_CONTRAST, "어두운 테마 최저 대비 " + c);
        }
        SwingUtilities.invokeAndWait(() -> ThemeManager.apply(false, false, false));
    }

    @Test
    void 완료한_일정은_바탕과_글자가_더_옅다() {
        Occurrence open = occ("blue", false, false);
        Occurrence done = occ("blue", false, true);
        Color openFill = EventPaint.tintFill(open, 0f);
        Color doneFill = EventPaint.tintFill(done, 0f);
        assertTrue(EventPaint.contrast(EventPaint.tintTitle(done, doneFill), doneFill)
                < EventPaint.contrast(EventPaint.tintTitle(open, openFill), openFill));
    }

    @Test
    void 대비는_흰색_검정이_21이다() {
        assertEquals(21.0, EventPaint.contrast(Color.WHITE, Color.BLACK), 0.001);
    }
}
