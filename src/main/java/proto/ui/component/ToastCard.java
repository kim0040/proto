package proto.ui.component;

import proto.ui.theme.Fonts;
import proto.ui.theme.Icons;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import java.awt.BorderLayout;

/**
 * [경계 boundary] 짧은 확인 알림 한 장("일정을 저장했어요"). 오류 알림은 닫기 버튼이 있고 계속 남는다.
 */
public class ToastCard extends FloatingCard {

    private final boolean error;

    public ToastCard(String message, boolean error, Runnable onClose) {
        super(new BorderLayout(Ui.px(10), 0));
        this.error = error;
        setBorder(BorderFactory.createEmptyBorder(MARGIN + 10, MARGIN + 16, MARGIN + 10, MARGIN + (error ? 8 : 18)));
        JLabel icon = new JLabel(Icons.of(error ? "circle-alert" : "circle-check", 18,
                error ? Tokens.DANGER : Tokens.SUCCESS));
        add(icon, BorderLayout.WEST);
        TokenLabel text = new TokenLabel(message.replace('\n', ' '), Fonts.medium(Fonts.BODY), Tokens.TEXT);
        add(text, BorderLayout.CENTER);
        if (error) {
            JButton close = Buttons.icon("x", "닫기");
            close.addActionListener(e -> onClose.run());
            add(close, BorderLayout.EAST);
        }
    }

    public boolean isError() {
        return error;
    }
}
