package proto.ui.command;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * [경계 boundary] 메인 창의 한 글자 단축키(C, T, W, M, D, ←, →, ?)를 받아 알맞은 동작을 부른다.
 * MainFrame이 창을 만들 때 달고(install) 닫을 때 뗀다(uninstall). 일반 단축키(InputMap)와 달리 키가 포커스 칸에 닿기 전에 보므로,
 * 포커스가 스크롤 영역 안에 있어도 ←/→가 먹혀 버리지 않는다. 대신 비켜 줘야 할 때를 스스로 가린다:
 * 시트·명령 메뉴가 열려 있을 때(enabled가 false), 글자 입력칸·콤보 상자·목록·슬라이더에 포커스가 있을 때(글이 먼저다),
 * 팝업 메뉴가 열려 있을 때, 방향키는 포커스 칸이 스스로 방향키를 쓸 때(달력 칸 등: 그 칸의 입력 맵에 방향키가 있을 때).
 * 이 창에 속하지 않은 키 입력(다른 창)은 건드리지 않는다.
 */
public final class PlainKeys implements KeyEventDispatcher {

    private final Window window;
    private final BooleanSupplier enabled;
    private final Map<PlainKey, Runnable> actions;
    private char typedToSwallow = KeyEvent.CHAR_UNDEFINED;
    private int pendingCode;
    private Runnable pendingAction;

    public PlainKeys(Window window, BooleanSupplier enabled, Map<PlainKey, Runnable> actions) {
        this.window = window;
        this.enabled = enabled;
        this.actions = actions;
    }

    public void install() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
    }

    public void uninstall() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (swallowTyped(e)) {
            return true;
        }
        if (runPending(e)) {
            return false;
        }
        if (e.getID() != KeyEvent.KEY_PRESSED || e.isConsumed() || !inThisWindow(e)) {
            return false;
        }
        Optional<PlainKey> key = keyOf(e);
        if (key.isEmpty() || !actions.containsKey(key.get())) {
            return false;
        }
        Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (!enabled.getAsBoolean() || yieldsTo(owner, key.get())) {
            return false;
        }
        e.consume();
        swallowNextTyped(e.getKeyChar());
        Runnable action = actions.get(key.get());
        if (key.get().isArrow()) {
            // 방향키는 누르고 있는 동안 계속 넘어가야 하므로 바로 실행한다
            SwingUtilities.invokeLater(action);
        } else {
            pendingCode = e.getKeyCode();
            pendingAction = action;
        }
        return true;
    }

    /** 눌린 키를 단축키로 읽는다 (Ctrl·Alt·⌘가 함께 눌렸으면 한 글자 단축키가 아니다) */
    private static Optional<PlainKey> keyOf(KeyEvent e) {
        int others = InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK
                | InputEvent.ALT_GRAPH_DOWN_MASK;
        return PlainKey.of(e.getKeyCode(), e.getKeyChar(), e.isShiftDown(), (e.getModifiersEx() & others) != 0);
    }

    /**
     * 글자 단축키는 키를 놓을 때 실행한다. 한글 입력기를 켜 둔 채 C를 누르면 'ㅊ'이 입력기에서 조금 늦게 도착해서, 누르자마자 새 일정 창을 열면
     * 그 'ㅊ'이 새 창의 제목 칸에 들어가 버린다 (Esc도 글자 확정에 쓰여 창이 안 닫힌다). 놓을 때 열면 글자는 이미 지나간 뒤이다.
     * 누르고 있는 동안 반복해서 오는 같은 키는 무시한다. 실행했거나 기다리던 키를 놓았으면 true.
     */
    private static void endComposition() {
        Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (owner != null && owner.getInputContext() != null) {
            owner.getInputContext().endComposition();
        }
    }

    private boolean runPending(KeyEvent e) {
        if (pendingAction == null || e.getKeyCode() != pendingCode) {
            return false;
        }
        if (e.getID() == KeyEvent.KEY_RELEASED) {
            Runnable action = pendingAction;
            pendingAction = null;
            SwingUtilities.invokeLater(() -> {
                endComposition();
                action.run();
            });
            return true;
        }
        if (e.getID() == KeyEvent.KEY_PRESSED) {
            e.consume();
        }
        return false;
    }

    /**
     * 단축키로 쓴 글자 키는 곧이어 같은 글자의 "입력(KEY_TYPED)" 이벤트가 뒤따른다. 단축키가 입력칸이 있는 창(새 일정 등)을 열면 그 글자가
     * 새 칸에 들어가 버리므로, 뒤따르는 입력 이벤트 하나를 삼킨다. 키를 놓으면(KEY_RELEASED) 더는 기다리지 않는다.
     */
    void swallowNextTyped(char c) {
        typedToSwallow = c;
    }

    boolean swallowTyped(KeyEvent e) {
        if (typedToSwallow == KeyEvent.CHAR_UNDEFINED) {
            return false;
        }
        if (e.getID() == KeyEvent.KEY_TYPED && e.getKeyChar() == typedToSwallow) {
            typedToSwallow = KeyEvent.CHAR_UNDEFINED;
            e.consume();
            return true;
        }
        if (e.getID() == KeyEvent.KEY_RELEASED) {
            typedToSwallow = KeyEvent.CHAR_UNDEFINED;
        }
        return false;
    }

    private boolean inThisWindow(KeyEvent e) {
        Component c = e.getComponent();
        return c == window || (c != null && SwingUtilities.getWindowAncestor(c) == window);
    }

    /** 이 키를 단축키로 쓰지 말고 포커스 칸에 넘겨야 하는 경우인지 */
    static boolean yieldsTo(Component owner, PlainKey key) {
        if (MenuSelectionManager.defaultManager().getSelectedPath().length > 0) {
            return true;
        }
        if (owner instanceof JTextComponent || owner instanceof JComboBox || owner instanceof JSpinner
                || owner instanceof JSlider || owner instanceof JList || owner instanceof JTable
                || owner instanceof JTree) {
            return true;
        }
        return key.isArrow() && usesArrow(owner, key.arrowCode());
    }

    /** 포커스 칸이 입력 맵에 이 방향키를 이미 갖고 있는지 (달력 칸의 날짜 이동 등) */
    private static boolean usesArrow(Component owner, int code) {
        if (!(owner instanceof JComponent c)) {
            return false;
        }
        return c.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(code, 0)) != null;
    }
}
