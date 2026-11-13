package proto.ui.dialog;

import proto.ui.component.Buttons;
import proto.ui.component.TokenLabel;
import proto.ui.theme.Fonts;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.List;

/**
 * [경계 boundary] 질문 하나에 버튼 몇 개로 답하는 확인 대화상자 (삭제 확인, 반복 범위 선택, 겹침 경고에 쓴다).
 */
public class ConfirmDialog extends BaseDialog {

    /** 버튼 모양 */
    public enum Kind { PRIMARY, DANGER, SECONDARY, GHOST }

    /** 버튼 한 개: 이름과 모양 */
    public record Choice(String label, Kind kind) {
    }

    private int result = -1;

    public ConfirmDialog(Window owner, String title, String message, String detail, JComponent extra,
                         List<Choice> choices) {
        super(owner, title);
        JPanel body = new proto.ui.component.WidthPanel(new BorderLayout(), 400);
        body.add(texts(message, detail, extra), BorderLayout.CENTER);
        setBody(body, buttons(choices), 24);
    }

    /** 대화상자를 열고 누른 버튼의 번호를 돌려준다. Esc나 닫기는 -1 */
    public int ask() {
        showDialog();
        return result;
    }

    private static JPanel texts(String message, String detail, JComponent extra) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        TokenLabel title = new TokenLabel("<html><body style='width:" + Ui.px(352) + "px'>" + html(message)
                + "</body></html>", Fonts.semibold(Fonts.LEAD), Tokens.TEXT);
        title.setAlignmentX(LEFT_ALIGNMENT);
        p.add(title);
        if (detail != null && !detail.isBlank()) {
            TokenLabel d = new TokenLabel("<html><body style='width:" + Ui.px(352) + "px'>" + html(detail)
                    + "</body></html>", Fonts.regular(Fonts.BODY), Tokens.MUTED);
            d.setAlignmentX(LEFT_ALIGNMENT);
            d.setBorder(BorderFactory.createEmptyBorder(Ui.px(8), 0, 0, 0));
            p.add(d);
        }
        if (extra != null) {
            extra.setAlignmentX(LEFT_ALIGNMENT);
            p.add(javax.swing.Box.createVerticalStrut(Ui.px(12)));
            p.add(extra);
        }
        return p;
    }

    private JPanel buttons(List<Choice> choices) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, Ui.px(8), 0));
        p.setOpaque(false);
        for (int i = 0; i < choices.size(); i++) {
            Choice c = choices.get(i);
            JButton b = switch (c.kind()) {
                case PRIMARY -> Buttons.primary(c.label());
                case DANGER -> Buttons.dangerFill(c.label());
                case SECONDARY -> Buttons.secondary(c.label());
                case GHOST -> Buttons.ghost(c.label());
            };
            int index = i;
            b.addActionListener(e -> {
                result = index;
                close(false);
            });
            p.add(b);
            if (c.kind() == Kind.PRIMARY || c.kind() == Kind.DANGER) {
                getRootPane().setDefaultButton(b);
            }
        }
        return p;
    }

    private static String html(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace("\n", "<br>");
    }
}
