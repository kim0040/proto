package proto.ui.component;

import proto.ui.theme.Fonts;
import proto.ui.theme.Icons;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

/**
 * [경계 boundary] 짧은 확인 알림 한 장("일정을 저장했어요"). 오류 알림은 닫기 버튼이 있고 계속 남는다.
 * Overlay가 UiActions#toast, #error, #storageError 요청마다 만든다 (F1, F3~F5, F11 결과 알림). 오류 토스트는 사용자가 닫을 때까지 남는다.
 */
public class ToastCard extends FloatingCard {

    private final boolean error;

    public ToastCard(String message, boolean error, Runnable onClose) {
        this(message, error, onClose, null, null);
    }

    /** actionLabel이 있으면 오른쪽에 그 이름의 버튼이 붙는다 (예: 저장 오류의 "자세히") */
    public ToastCard(String message, boolean error, Runnable onClose, String actionLabel, Runnable onAction) {
        super(new BorderLayout(Ui.px(10), 0));
        this.error = error;
        setBorder(BorderFactory.createEmptyBorder(MARGIN + 10, MARGIN + 16, MARGIN + 10, MARGIN + (error ? 8 : 18)));
        JLabel icon = new JLabel(Icons.of(error ? "circle-alert" : "circle-check", 18,
                error ? Tokens.DANGER : Tokens.SUCCESS));
        add(icon, BorderLayout.WEST);
        TokenLabel text = new TokenLabel(TextUtil.plain(message), Fonts.medium(Fonts.BODY), Tokens.TEXT);
        add(text, BorderLayout.CENTER);
        if (error) {
            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, Ui.px(4), 0));
            right.setOpaque(false);
            if (actionLabel != null) {
                JButton action = Buttons.compact(Buttons.secondary(actionLabel));
                action.addActionListener(e -> onAction.run());
                right.add(action);
            }
            JButton close = Buttons.icon("x", "닫기");
            close.addActionListener(e -> onClose.run());
            right.add(close);
            add(right, BorderLayout.EAST);
        }
    }

    public boolean isError() {
        return error;
    }
}
