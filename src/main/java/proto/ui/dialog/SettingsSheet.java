package proto.ui.dialog;

import proto.ui.UiContext;
import proto.ui.component.Buttons;
import proto.ui.component.TokenLabel;
import proto.ui.component.WidthPanel;
import proto.ui.theme.Fonts;
import proto.ui.theme.Tokens;
import proto.ui.view.SettingsView;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

/**
 * [경계 boundary] 설정 시트: 지금 보던 화면 위에 설정 내용(SettingsView)을 띄운다. 바꾸는 즉시 적용·저장되므로 "저장" 버튼이 없다.
 * 사이드바의 "설정"과 Ctrl/⌘+, 로 UiActions#openSettings가 연다. 테마·애니메이션을 바꾸면 스크림 뒤의 화면이 바로 바뀌고,
 * 바깥 클릭·Esc·닫기 버튼으로 닫으면 뒤 화면은 열기 전 그대로이다.
 */
public class SettingsSheet extends BaseDialog {

    private static final int WIDTH = 600;

    public SettingsSheet(UiContext ctx) {
        super(ctx.window(), "설정");
        JButton close = Buttons.secondary("닫기");
        close.addActionListener(e -> onCancel());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        south.setOpaque(false);
        south.add(close);
        JPanel body = new WidthPanel(new BorderLayout(), WIDTH);
        body.add(heading(), BorderLayout.NORTH);
        body.add(new SettingsView(ctx).component(), BorderLayout.CENTER);
        setBody(body, south, 28);
    }

    private JPanel heading() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        TokenLabel title = new TokenLabel("설정", Fonts.semibold(Fonts.TITLE), Tokens.TEXT);
        TokenLabel subtitle = new TokenLabel("화면과 알림을 내 방식대로 맞춰요", Fonts.regular(Fonts.SMALL), Tokens.MUTED);
        title.setAlignmentX(LEFT_ALIGNMENT);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);
        p.add(title);
        p.add(javax.swing.Box.createVerticalStrut(proto.ui.theme.Ui.px(4)));
        p.add(subtitle);
        return p;
    }
}
