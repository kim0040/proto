package proto.ui.component;

import proto.entity.Occurrence;

import javax.swing.JComponent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.function.Supplier;

/**
 * [경계 boundary] Alt+방향키로 일정을 옮기는 키 연결 (←/→ 하루, ↑/↓ 15분). 키 → (일수, 분) 계산도 여기에 있다.
 * OccurrenceRow와 WeekChip이 시간표 블록(TimelineBlock)과 같은 키를 쓰도록 한 줄로 달아 준다 (F3). 실제 옮기기는 RowHandler#nudge에 맡긴다.
 */
public final class NudgeKeys {

    private NudgeKeys() {
    }

    /** 방향키 코드를 (일수, 분)으로 바꾼다. 방향키가 아니면 null */
    public static int[] delta(int keyCode) {
        return switch (keyCode) {
            case KeyEvent.VK_LEFT -> new int[]{-1, 0};
            case KeyEvent.VK_RIGHT -> new int[]{1, 0};
            case KeyEvent.VK_UP -> new int[]{0, -TimeSnap.STEP};
            case KeyEvent.VK_DOWN -> new int[]{0, TimeSnap.STEP};
            default -> null;
        };
    }

    /** component에서 Alt+방향키를 누르면 occurrence가 알려 주는 일정을 handler로 옮긴다 */
    public static void install(JComponent component, RowHandler handler, Supplier<Occurrence> occurrence) {
        component.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int[] d = e.isAltDown() ? delta(e.getKeyCode()) : null;
                Occurrence o = occurrence.get();
                if (d != null && o != null) {
                    e.consume();
                    handler.nudge(o, d[0], d[1]);
                }
            }
        });
    }
}
