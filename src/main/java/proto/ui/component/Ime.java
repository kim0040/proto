package proto.ui.component;

import javax.swing.JComboBox;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.event.InputMethodEvent;
import java.awt.event.InputMethodListener;
import java.awt.im.InputContext;
import java.text.AttributedCharacterIterator;

/**
 * [경계 boundary] 한글처럼 조합 중인 글자(IME)를 다루는 도우미: 입력칸의 값을 읽기 전에 조합을 끝내 마지막 글자가 빠지지 않게 하고,
 * 조합 중의 Enter로는 저장되지 않게 상태를 알려 준다.
 * HeaderBar의 검색창, ScheduleDialog, CategoryDialog, TimeCombo가 쓴다 (F1 제목 입력, F8 검색).
 * 한글은 확정되지 않은 글자가 입력칸에 남은 채 값을 읽으면 마지막 글자가 빠지기 때문이다.
 */
public final class Ime {

    private static final String COMPOSING = "proto.ime.composing";

    private Ime() {
    }

    /** 이 입력칸의 조합 상태를 추적한다 */
    public static void track(JTextComponent field) {
        field.addInputMethodListener(new InputMethodListener() {
            @Override
            public void inputMethodTextChanged(InputMethodEvent e) {
                AttributedCharacterIterator it = e.getText();
                int total = it == null ? 0 : it.getEndIndex() - it.getBeginIndex();
                field.putClientProperty(COMPOSING, total > e.getCommittedCharacterCount());
            }

            @Override
            public void caretPositionChanged(InputMethodEvent e) {
                // 캐럿 위치는 쓰지 않는다
            }
        });
    }

    /** 콤보의 편집칸에도 조합 추적을 붙인다 */
    public static void track(JComboBox<?> combo) {
        if (combo.getEditor().getEditorComponent() instanceof JTextComponent t) {
            track(t);
        }
    }

    /** 조합 중인 글자가 있는지 (track을 붙인 입력칸만 알 수 있다) */
    public static boolean composing(Component c) {
        return c instanceof JTextComponent t && Boolean.TRUE.equals(t.getClientProperty(COMPOSING));
    }

    /** 조합 중인 글자를 확정한다. 입력칸이 아닌 곳이면 아무 일도 하지 않는다 */
    public static void commit(Component c) {
        if (c == null) {
            return;
        }
        InputContext ic = c.getInputContext();
        if (ic != null) {
            ic.endComposition();
        }
        if (c instanceof JTextComponent t) {
            t.putClientProperty(COMPOSING, false);
        }
    }

    /** 지금 포커스가 있는 입력칸의 조합을 확정한다 (저장·검색 직전에 부른다) */
    public static void commitFocused() {
        Component f = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        commit(f);
    }

    /** 지금 포커스가 있는 곳이 조합 중인지. 조합 중이면 확정만 하고 true를 돌려준다 (그 Enter는 저장으로 치지 않는다) */
    public static boolean swallowIfComposing() {
        Component f = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (composing(f)) {
            commit(f);
            return true;
        }
        return false;
    }
}
