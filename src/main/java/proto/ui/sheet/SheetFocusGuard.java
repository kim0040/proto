package proto.ui.sheet;

import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * [경계 boundary] 시트가 열려 있는 동안 키보드 포커스가 뒤 화면으로 새어 나가지 않게 지킨다.
 * SheetHost가 첫 시트를 열 때 켜고 마지막 시트가 닫히면 끈다. 토스트·알림 카드의 버튼을 눌러 포커스가 시트 밖으로 나가면 맨 위 시트의 마지막 자리로 되돌린다.
 * 팝업 메뉴(날짜 고르기, 콤보 목록)와 다른 창은 건드리지 않는다.
 */
final class SheetFocusGuard implements PropertyChangeListener {

    private static final String PROPERTY = "focusOwner";

    private final SheetHost host;
    private boolean installed;

    SheetFocusGuard(SheetHost host) {
        this.host = host;
    }

    /** 시트가 하나라도 있으면 켜고, 없으면 끈다 */
    void update(boolean any) {
        KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        if (any && !installed) {
            kfm.addPropertyChangeListener(PROPERTY, this);
        } else if (!any && installed) {
            kfm.removePropertyChangeListener(PROPERTY, this);
        }
        installed = any;
    }

    @Override
    public void propertyChange(PropertyChangeEvent e) {
        if (!(e.getNewValue() instanceof Component c)) {
            return;
        }
        SheetLayer top = host.topLayer();
        if (top == null || SwingUtilities.getRootPane(c) != SwingUtilities.getRootPane(host)) {
            return;
        }
        if (SwingUtilities.isDescendingFrom(c, top)) {
            top.rememberFocus(c);
        } else if (SwingUtilities.getAncestorOfClass(JPopupMenu.class, c) == null) {
            SwingUtilities.invokeLater(() -> {
                if (host.topLayer() == top) {
                    top.focusInside();
                }
            });
        }
    }
}
