package proto.ui.component;

import proto.ui.theme.Icons;
import proto.ui.theme.Tokens;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import java.awt.BorderLayout;

/**
 * [경계 boundary] 버튼이 달린 확인 토스트("일정을 삭제했어요 [되돌리기]")와 중립 안내 토스트("되돌릴 작업이 없어요").
 * HistoryActions가 만들어 Overlay에 올린다 (되돌리기·다시 실행). 성공 토스트(ToastCard) 모양에 오른쪽 버튼 하나만 더한다.
 * 안내 토스트는 같은 모양에서 초록 체크 대신 회색 알림 아이콘을 써서 성공으로 읽히지 않게 한다. 버튼은 setActionEnabled(false)로 끌 수 있다(되돌릴 수 없게 된 때).
 */
public class ActionToastCard extends ToastCard {

    private JButton action;

    private ActionToastCard(String message) {
        super(message, false, () -> { });
    }

    /** 오른쪽에 label 버튼이 붙은 토스트. 버튼을 누르면 onAction이 불린다 */
    public static ActionToastCard withAction(String message, String label, Runnable onAction) {
        ActionToastCard card = new ActionToastCard(message);
        card.action = Buttons.compact(Buttons.secondary(label));
        card.action.addActionListener(e -> onAction.run());
        card.setBorder(BorderFactory.createEmptyBorder(MARGIN + 6, MARGIN + 16, MARGIN + 6, MARGIN + 8));
        card.add(card.action, BorderLayout.EAST);
        return card;
    }

    /** 버튼 없이 회색 아이콘만 쓰는 안내 토스트 */
    public static ActionToastCard notice(String message) {
        ActionToastCard card = new ActionToastCard(message);
        Object west = ((BorderLayout) card.getLayout()).getLayoutComponent(BorderLayout.WEST);
        if (west instanceof JLabel icon) {
            icon.setIcon(Icons.of("circle-alert", 18, Tokens.MUTED));
        }
        return card;
    }

    /** 버튼을 켜거나 끈다. 꺼진 버튼은 흐리게 보이고 눌리지 않는다 */
    public void setActionEnabled(boolean enabled) {
        if (action != null) {
            action.setEnabled(enabled);
        }
    }

    /** 버튼이 지금 눌릴 수 있는지 (버튼이 없으면 false) */
    public boolean isActionEnabled() {
        return action != null && action.isEnabled();
    }

    /** 점검·테스트용: 버튼을 누른 것처럼 동작시킨다 */
    public void clickAction() {
        if (action != null) {
            action.doClick(0);
        }
    }
}
