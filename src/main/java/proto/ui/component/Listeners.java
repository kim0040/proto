package proto.ui.component;

import javax.swing.JComponent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.function.IntConsumer;

/**
 * [경계 boundary] 자주 쓰는 리스너 묶음을 한 줄로 달아 주는 도우미 (포커스 때 다시 그리기, 글자 바뀜, 키 입력).
 */
public final class Listeners {

    private Listeners() {
    }

    /** 포커스를 얻거나 잃을 때 컴포넌트를 다시 그린다 (포커스 테두리용) */
    public static void repaintOnFocus(JComponent c) {
        c.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                c.repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                c.repaint();
            }
        });
    }

    /** 글자가 바뀔 때마다 action을 부른다 */
    public static void onTextChange(JTextComponent c, Runnable action) {
        c.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                action.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                action.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                action.run();
            }
        });
    }

    /** 키를 눌렀을 때 키 코드를 넘겨 받는다 */
    public static void onKey(JComponent c, IntConsumer action) {
        c.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                action.accept(e.getKeyCode());
            }
        });
    }
}
