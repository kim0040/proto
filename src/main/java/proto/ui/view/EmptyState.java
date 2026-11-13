package proto.ui.view;

import proto.ui.component.Buttons;
import proto.ui.component.TokenLabel;
import proto.ui.theme.Fonts;
import proto.ui.theme.Icons;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.GridBagLayout;

/**
 * [경계 boundary] 보여 줄 일정이 없을 때의 안내 화면 (문장 + 선택적으로 새 일정 버튼).
 */
public class EmptyState extends JPanel {

    private final TokenLabel title = new TokenLabel("", Fonts.semibold(Fonts.TITLE), Tokens.TEXT);
    private final TokenLabel hint = new TokenLabel("", Fonts.regular(Fonts.BODY), Tokens.MUTED);
    private final JButton action;

    public EmptyState(String actionText, Runnable onAction) {
        super(new GridBagLayout());
        setOpaque(false);
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        JLabel icon = new JLabel(Icons.of("calendar-check", 36, Tokens.FAINT));
        icon.setAlignmentX(CENTER_ALIGNMENT);
        title.setAlignmentX(CENTER_ALIGNMENT);
        hint.setAlignmentX(CENTER_ALIGNMENT);
        action = Buttons.primary(actionText);
        action.setAlignmentX(CENTER_ALIGNMENT);
        action.addActionListener(e -> onAction.run());
        box.add(icon);
        box.add(Box.createVerticalStrut(Ui.px(14)));
        box.add(title);
        box.add(Box.createVerticalStrut(Ui.px(6)));
        box.add(hint);
        box.add(Box.createVerticalStrut(Ui.px(20)));
        box.add(action);
        add(box);
    }

    /** 문구를 정한다. showAction이 false면 버튼을 숨긴다 */
    public void set(String titleText, String hintText, boolean showAction) {
        title.setText(titleText);
        hint.setText(hintText);
        action.setVisible(showAction);
    }
}
