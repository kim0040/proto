package proto.ui.component;

import javax.swing.JComponent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * [경계 boundary] 자주 쓰는 리스너 묶음을 한 줄로 달아 주는 도우미 (포커스 때 다시 그리기, 글자 바뀜, 키 입력).
 * 컴포넌트마다 반복되는 리스너 코드를 한 줄로 줄이려는 도우미이다 (OccurrenceRow, MonthGrid, PillList 등이 쓴다).
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

    /**
     * 왼쪽 버튼으로 이 컴포넌트를 눌렀다가 그 안에서 떼면 action을 부른다.
     * 왜 mouseClicked를 쓰지 않는가: 자바의 mouseClicked는 누르고 떼는 사이에 포인터가 1px만 움직여도 오지 않아서,
     * 트랙패드나 빠른 클릭에서 "한 번 더 눌러야 반응하는" 것처럼 느껴진다. 일반 버튼처럼 "안에서 뗐는가"로만 판단한다.
     */
    public static void onClick(JComponent c, Consumer<MouseEvent> action) {
        c.addMouseListener(new MouseAdapter() {
            private boolean pressed;

            @Override
            public void mousePressed(MouseEvent e) {
                pressed = e.getButton() == MouseEvent.BUTTON1 && !e.isPopupTrigger();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                boolean inside = c.contains(e.getPoint());
                if (pressed && inside && e.getButton() == MouseEvent.BUTTON1 && !e.isPopupTrigger()) {
                    action.accept(e);
                }
                pressed = false;
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
