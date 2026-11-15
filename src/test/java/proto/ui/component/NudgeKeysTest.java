package proto.ui.component;

import org.junit.jupiter.api.Test;
import proto.entity.Occurrence;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static proto.TestData.t;

/** Alt+방향키 계산과 포커스에서 일정 찾기 */
class NudgeKeysTest {

    @Test
    void 좌우는_하루_상하는_십오_분이다() {
        assertArrayEquals(new int[]{-1, 0}, NudgeKeys.delta(java.awt.event.KeyEvent.VK_LEFT));
        assertArrayEquals(new int[]{1, 0}, NudgeKeys.delta(java.awt.event.KeyEvent.VK_RIGHT));
        assertArrayEquals(new int[]{0, -15}, NudgeKeys.delta(java.awt.event.KeyEvent.VK_UP));
        assertArrayEquals(new int[]{0, 15}, NudgeKeys.delta(java.awt.event.KeyEvent.VK_DOWN));
        assertNull(NudgeKeys.delta(java.awt.event.KeyEvent.VK_A));
    }

    /** 일정을 들고 있는 조각 */
    private static final class Holder extends JPanel implements OccurrenceHolder {
        private final Occurrence occurrence;

        Holder(Occurrence occurrence) {
            this.occurrence = occurrence;
        }

        @Override
        public Occurrence occurrence() {
            return occurrence;
        }
    }

    @Test
    void 포커스가_조각_안쪽에_있어도_위쪽_조각의_일정을_찾는다() {
        Occurrence o = new Occurrence("id", LocalDate.of(2026, 11, 16), "회의", t("09:00"), t("10:00"), "class", "",
                RepeatRule.NONE, ReminderOption.NONE, false);
        Holder holder = new Holder(o);
        JPanel inner = new JPanel();
        JButton button = new JButton();
        inner.add(button);
        holder.add(inner);
        assertSame(o, OccurrenceHolder.find(button));
        assertSame(o, OccurrenceHolder.find(holder));
    }

    @Test
    void 일정_조각_밖이거나_포커스가_없으면_없다() {
        assertNull(OccurrenceHolder.find(new JButton()));
        assertNull(OccurrenceHolder.find(null));
    }
}
