package proto.ui;

import proto.entity.Occurrence;
import proto.ui.component.OccurrenceHolder;
import proto.ui.theme.Ui;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.function.BooleanSupplier;

/**
 * [경계 boundary] 메인 창의 편집 단축키: 되돌리기(Ctrl/⌘+Z), 다시 실행(Ctrl/⌘+Shift+Z, Windows는 Ctrl+Y), 복사(Ctrl/⌘+C), 붙여넣기(Ctrl/⌘+V).
 * MainFrame이 창 뿌리(rootPane)에 한 번 달고, 실제 동작은 UiActions가 한다 (F1, F3). 복사는 포커스가 일정 조각(OccurrenceHolder) 위에 있을 때만 일정을 복사한다.
 * 글자 입력칸(JTextComponent)에 포커스가 있으면 그 칸이 키를 쓰도록 아무것도 하지 않는다.
 * 시트나 명령 메뉴(⌘K)가 열려 있는 동안에는 꺼진다: 같은 창 안에 뜨므로, 막지 않으면 뒤 화면의 일정을 되돌리거나 붙여 넣게 된다.
 * 꺼질지는 MainFrame의 keysFree와 같은 판단(free)을 받아, 창 단축키·한 글자 단축키와 한 곳의 규칙을 함께 쓴다.
 */
final class EditShortcuts {

    private final UiContext ctx;
    private final JRootPane root;
    private final BooleanSupplier free;

    /**
     * @param free 지금 창 단축키가 동작해도 되는지 (시트도 명령 메뉴도 열려 있지 않을 때 true)
     */
    EditShortcuts(UiContext ctx, JRootPane root, BooleanSupplier free) {
        this.ctx = ctx;
        this.root = root;
        this.free = free;
    }

    /** 단축키를 창에 단다 */
    void install() {
        install(Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
    }

    /** 단축키 수정 키(⌘ 또는 Ctrl)를 직접 정해 단다 (헤드리스 시험에서는 Toolkit이 마스크를 주지 못한다) */
    void install(int mask) {
        bind("undo", KeyStroke.getKeyStroke(KeyEvent.VK_Z, mask), () -> ctx.actions().undo());
        bind("redo", KeyStroke.getKeyStroke(KeyEvent.VK_Z, mask | InputEvent.SHIFT_DOWN_MASK), () -> ctx.actions().redo());
        if (!Ui.isMac()) {
            bind("redo-y", KeyStroke.getKeyStroke(KeyEvent.VK_Y, mask), () -> ctx.actions().redo());
        }
        bind("copy", KeyStroke.getKeyStroke(KeyEvent.VK_C, mask), this::copyFocused);
        bind("paste", KeyStroke.getKeyStroke(KeyEvent.VK_V, mask), () -> ctx.actions().paste());
    }

    /** 지금 키 입력을 받는 컴포넌트가 글자 입력칸인지 */
    static boolean typingInTextField() {
        Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        return owner instanceof JTextComponent;
    }

    private void copyFocused() {
        Occurrence o = OccurrenceHolder.find(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
        if (o != null) {
            ctx.actions().copy(o);
        }
    }

    private void bind(String name, KeyStroke key, Runnable action) {
        String id = "proto-edit-" + name;
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(key, id);
        root.getActionMap().put(id, new AbstractAction() {
            @Override
            public boolean isEnabled() {
                return free.getAsBoolean();
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                if (!typingInTextField()) {
                    action.run();
                }
            }
        });
    }
}
