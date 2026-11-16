package proto.ui.sheet;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * [경계 boundary] 메인 창 안에 시트(대화상자)를 겹쳐 올리는 층. 열린 시트를 쌓아 두고, 시트가 열려 있는 동안 뒤 화면을 막는다.
 * MainFrame이 레이어드 팬의 MODAL 층(본문·서랍 위, 토스트·알림 카드 아래)에 하나 달아 두고, BaseDialog가 열릴 때 of(부모)로 찾아 push한다.
 * 시트마다 스크림과 카드를 가진 SheetLayer를 하나씩 만들어 위에 쌓는다(시트에서 연 확인 창은 그 위에 자기 스크림과 함께 뜬다).
 * 닫힐 때는 포커스를 열기 전 자리로, Enter 기본 버튼을 아래 시트의 것으로 돌려준다. 메인 창의 단축키는 isOpen으로 막는다.
 */
public final class SheetHost extends JComponent {

    private static final String KEY = "proto.sheetHost";

    private final List<SheetLayer> stack = new ArrayList<>();
    private final SheetFocusGuard guard = new SheetFocusGuard(this);
    private JButton baseDefault;

    private SheetHost() {
        setLayout(null);
        setOpaque(false);
        setFocusable(false);
    }

    /** 레이어드 팬에 시트 층을 단다 (이미 있으면 그것을 돌려준다). 창 크기를 따라간다 */
    public static SheetHost install(JLayeredPane layers) {
        if (layers.getClientProperty(KEY) instanceof SheetHost existing) {
            return existing;
        }
        SheetHost host = new SheetHost();
        layers.putClientProperty(KEY, host);
        layers.add(host, JLayeredPane.MODAL_LAYER);
        host.setBounds(0, 0, layers.getWidth(), layers.getHeight());
        layers.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                host.setBounds(0, 0, layers.getWidth(), layers.getHeight());
                host.revalidate();
            }
        });
        return host;
    }

    /** c가 들어 있는 창의 시트 층. 창(JFrame)이나 시트 안의 컴포넌트를 넘겨도 된다. 아직 없으면 달아 주고, 창에 붙어 있지 않으면 null */
    public static SheetHost of(Component c) {
        SheetHost found = find(c);
        if (found != null) {
            return found;
        }
        JRootPane root = c instanceof RootPaneContainer r ? r.getRootPane() : SwingUtilities.getRootPane(c);
        return root == null ? null : install(root.getLayeredPane());
    }

    /** c가 들어 있는 창에 지금 열린 시트가 있는지 (메인 창 단축키가 확인한다) */
    public static boolean isOpen(Component c) {
        SheetHost host = find(c);
        return host != null && host.isOpen();
    }

    /** 닫히는 중이 아닌 시트가 하나라도 있는지 */
    public boolean isOpen() {
        return topLayer() != null;
    }

    /** 닫히는 중이 아닌 시트 수 */
    public int depth() {
        return (int) stack.stream().filter(l -> !l.closing()).count();
    }

    /** 맨 위의(닫히는 중이 아닌) 시트 */
    SheetLayer topLayer() {
        for (int i = stack.size() - 1; i >= 0; i--) {
            if (!stack.get(i).closing()) {
                return stack.get(i);
            }
        }
        return null;
    }

    /** 시트를 맨 위에 올리고 나타나게 한다. 포커스는 시트 안으로 옮긴다 */
    public void push(Sheet sheet) {
        JRootPane root = SwingUtilities.getRootPane(this);
        if (!isOpen() && root != null) {
            baseDefault = root.getDefaultButton();
        }
        Component before = focusBehind(KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner());
        SheetLayer layer = new SheetLayer(sheet, before);
        stack.add(layer);
        add(layer, 0);
        layer.setBounds(0, 0, getWidth(), getHeight());
        layer.validate();
        guard.update(true);
        applyDefaultButton();
        layer.playIn();
        layer.focusInside();
    }

    /** 시트를 닫는다. 바로 클릭을 받지 않게 되고, 포커스와 기본 버튼을 돌려준 뒤 사라지는 모션이 끝나면 떼어 낸다 */
    public void pop(Sheet sheet) {
        SheetLayer layer = layerOf(sheet);
        if (layer == null || layer.closing()) {
            return;
        }
        layer.playOut(() -> {
            stack.remove(layer);
            remove(layer);
            repaint();
            guard.update(!stack.isEmpty());
        });
        applyDefaultButton();
        restoreFocus(layer);
    }

    /** 시트 내용이 바뀌어 크기를 다시 맞춘다 (검증 오류가 나타나는 등) */
    public void relayout(Sheet sheet) {
        SheetLayer layer = layerOf(sheet);
        if (layer != null) {
            layer.revalidate();
            layer.repaint();
        }
    }

    /** 시트를 좌우로 짧게 흔든다 (지금은 닫을 수 없다는 신호) */
    public void shake(Sheet sheet) {
        SheetLayer layer = layerOf(sheet);
        if (layer != null) {
            layer.shake();
        }
    }

    /** 시트가 기본 버튼을 바꿨을 때 다시 적용한다 */
    public void defaultButtonChanged() {
        applyDefaultButton();
    }

    /** 시트가 있을 때만 마우스를 받는다. 비어 있으면 투명한 층이라 클릭이 아래로 지나간다 */
    @Override
    public boolean contains(int x, int y) {
        return !stack.isEmpty() && super.contains(x, y);
    }

    @Override
    public void doLayout() {
        for (SheetLayer layer : stack) {
            layer.setBounds(0, 0, getWidth(), getHeight());
        }
    }

    private static SheetHost find(Component c) {
        Component p = c instanceof RootPaneContainer r ? r.getLayeredPane() : c;
        for (; p != null; p = p.getParent()) {
            if (p instanceof JComponent jc && jc.getClientProperty(KEY) instanceof SheetHost h) {
                return h;
            }
            if (p instanceof JRootPane root && root.getLayeredPane().getClientProperty(KEY) instanceof SheetHost h) {
                return h;
            }
        }
        return null;
    }

    /**
     * 닫힌 뒤 돌려줄 포커스 자리. 지금 포커스가 사라지는 중인 시트 안에 있으면(빈 시간을 고르자마자 새 일정 시트가 열릴 때)
     * 그 시트가 돌려주려던 자리를 대신 쓴다.
     */
    private Component focusBehind(Component owner) {
        for (int i = stack.size() - 1; i >= 0; i--) {
            SheetLayer l = stack.get(i);
            if (l.closing() && owner != null && SwingUtilities.isDescendingFrom(owner, l)) {
                owner = l.previousFocus();
            }
        }
        return owner;
    }

    private SheetLayer layerOf(Sheet sheet) {
        return stack.stream().filter(l -> l.sheet() == sheet).findFirst().orElse(null);
    }

    private void applyDefaultButton() {
        JRootPane root = SwingUtilities.getRootPane(this);
        if (root != null) {
            SheetLayer top = topLayer();
            root.setDefaultButton(top != null ? top.sheet().defaultButton() : baseDefault);
        }
    }

    /** 열기 전에 포커스가 있던 곳으로 돌려준다. 그곳이 사라졌으면 아래 시트 안으로 */
    private void restoreFocus(SheetLayer closed) {
        Component back = closed.previousFocus();
        SheetLayer top = topLayer();
        boolean usable = back != null && back.isShowing() && back.isFocusable()
                && (top == null || SwingUtilities.isDescendingFrom(back, top));
        if (usable) {
            back.requestFocusInWindow();
        } else if (top != null) {
            top.focusInside();
        }
    }
}
