package proto.ui.command;

import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 한 글자 단축키: 키 판별과 "글자를 치는 곳·방향키를 쓰는 곳에서는 비켜 준다" 규칙 */
class PlainKeysTest {

    private static Optional<PlainKey> key(int code) {
        return PlainKey.of(code, KeyEvent.CHAR_UNDEFINED, false, false);
    }

    @Test
    void 알파벳과_방향키를_단축키로_읽는다() {
        assertEquals(Optional.of(PlainKey.NEW), key(KeyEvent.VK_C));
        assertEquals(Optional.of(PlainKey.TODAY), key(KeyEvent.VK_T));
        assertEquals(Optional.of(PlainKey.WEEK), key(KeyEvent.VK_W));
        assertEquals(Optional.of(PlainKey.MONTH), key(KeyEvent.VK_M));
        assertEquals(Optional.of(PlainKey.DAY), key(KeyEvent.VK_D));
        assertEquals(Optional.of(PlainKey.PREVIOUS), key(KeyEvent.VK_LEFT));
        assertEquals(Optional.of(PlainKey.NEXT), key(KeyEvent.VK_RIGHT));
        assertTrue(key(KeyEvent.VK_X).isEmpty());
    }

    @Test
    void 물음표는_Shift와_슬래시() {
        assertEquals(Optional.of(PlainKey.HELP), PlainKey.of(KeyEvent.VK_SLASH, '?', true, false));
        assertEquals(Optional.of(PlainKey.HELP), PlainKey.of(KeyEvent.VK_UNDEFINED, '?', false, false));
        assertTrue(PlainKey.of(KeyEvent.VK_SLASH, '/', false, false).isEmpty());
    }

    @Test
    void 보조_키가_함께_눌리면_단축키가_아니다() {
        assertTrue(PlainKey.of(KeyEvent.VK_C, 'c', false, true).isEmpty());
        assertTrue(PlainKey.of(KeyEvent.VK_LEFT, KeyEvent.CHAR_UNDEFINED, false, true).isEmpty());
        assertTrue(PlainKey.of(KeyEvent.VK_C, 'C', true, false).isEmpty());
        assertTrue(PlainKey.of(KeyEvent.VK_LEFT, KeyEvent.CHAR_UNDEFINED, true, false).isEmpty());
    }

    @Test
    void 한글_두벌식_같은_자리() {
        assertEquals(Optional.of(PlainKey.NEW), PlainKey.of(KeyEvent.VK_UNDEFINED, 'ㅊ', false, false));
        assertEquals(Optional.of(PlainKey.TODAY), PlainKey.of(KeyEvent.VK_UNDEFINED, 'ㅅ', false, false));
        assertEquals(Optional.of(PlainKey.WEEK), PlainKey.of(KeyEvent.VK_UNDEFINED, 'ㅈ', false, false));
        assertEquals(Optional.of(PlainKey.MONTH), PlainKey.of(KeyEvent.VK_UNDEFINED, 'ㅡ', false, false));
        assertEquals(Optional.of(PlainKey.DAY), PlainKey.of(KeyEvent.VK_UNDEFINED, 'ㅇ', false, false));
    }

    @Test
    void 글자_입력칸과_콤보에서는_비켜_준다() {
        assertTrue(PlainKeys.yieldsTo(new JTextField(), PlainKey.NEW));
        assertTrue(PlainKeys.yieldsTo(new JTextField(), PlainKey.PREVIOUS));
        assertTrue(PlainKeys.yieldsTo(new JComboBox<>(new String[]{"a"}), PlainKey.WEEK));
    }

    @Test
    void 버튼_패널_스크롤_영역에서는_단축키가_동작한다() {
        assertFalse(PlainKeys.yieldsTo(new JButton("x"), PlainKey.NEW));
        assertFalse(PlainKeys.yieldsTo(new JButton("x"), PlainKey.NEXT));
        assertFalse(PlainKeys.yieldsTo(new JPanel(), PlainKey.PREVIOUS));
        assertFalse(PlainKeys.yieldsTo(new JScrollPane(), PlainKey.NEXT));
        assertFalse(PlainKeys.yieldsTo(null, PlainKey.TODAY));
    }

    @Test
    void 방향키를_자기_입력_맵에_가진_칸에서는_방향키만_비켜_준다() {
        JComponent grid = new JPanel();
        grid.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "left");
        assertTrue(PlainKeys.yieldsTo(grid, PlainKey.PREVIOUS));
        assertFalse(PlainKeys.yieldsTo(grid, PlainKey.NEXT));
        assertFalse(PlainKeys.yieldsTo(grid, PlainKey.NEW));
    }

    @Test
    void 단축키로_쓴_글자의_입력_이벤트는_한_번_삼킨다() {
        PlainKeys keys = new PlainKeys(null, () -> true, java.util.Map.of());
        JPanel source = new JPanel();
        KeyEvent typedA = new KeyEvent(source, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'c');
        assertFalse(keys.swallowTyped(typedA), "단축키를 쓰기 전에는 그대로 둔다");
        keys.swallowNextTyped('c');
        KeyEvent typedB = new KeyEvent(source, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'c');
        assertTrue(keys.swallowTyped(typedB));
        assertTrue(typedB.isConsumed());
        assertFalse(keys.swallowTyped(new KeyEvent(source, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'c')),
                "한 번만 삼킨다");
    }

    @Test
    void 키를_놓으면_삼킬_입력을_더_기다리지_않는다() {
        PlainKeys keys = new PlainKeys(null, () -> true, java.util.Map.of());
        JPanel source = new JPanel();
        keys.swallowNextTyped('c');
        keys.swallowTyped(new KeyEvent(source, KeyEvent.KEY_RELEASED, 0, 0, KeyEvent.VK_C, 'c'));
        assertFalse(keys.swallowTyped(new KeyEvent(source, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'c')));
    }

    @Test
    void 다른_글자의_입력은_삼키지_않는다() {
        PlainKeys keys = new PlainKeys(null, () -> true, java.util.Map.of());
        keys.swallowNextTyped('c');
        assertFalse(keys.swallowTyped(new KeyEvent(new JPanel(), KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'x')));
    }
}
