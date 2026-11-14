package proto.ui.component;

import com.formdev.flatlaf.FlatClientProperties;
import proto.ui.theme.Fonts;
import proto.ui.theme.Icons;
import proto.ui.theme.Tokens;

import javax.swing.Icon;
import javax.swing.JButton;
import java.awt.Cursor;

/**
 * [경계 boundary] 버튼 종류(주 버튼·보조·투명·위험·아이콘)를 만들어 주는 공장. 색은 모두 토큰을 따른다.
 * 거의 모든 화면(사이드바, 헤더, 대화상자, 보기)이 버튼을 만들 때 쓴다. 모양(색·둥글기·여백)을 FlatLaf 스타일 문자열 한 줄로 정하고,
 * 색은 이름(토큰)으로만 적는다. 그래서 어두운 테마로 바뀌어도 코드를 고치지 않아도 된다.
 */
public final class Buttons {

    private static final String BASE = "arc:10; focusWidth:1; minimumWidth:0; ";

    private Buttons() {
    }

    /** 가장 중요한 한 가지 행동 (강조색 채움) */
    public static JButton primary(String text) {
        return make(text, "background:$proto.accent; foreground:$proto.onAccent; borderWidth:0;"
                + " hoverBackground:darken($proto.accent,6%); pressedBackground:darken($proto.accent,12%);"
                + " focusedBackground:$proto.accent; margin:6,16,6,16; " + BASE);
    }

    /** 평범한 보조 행동 (얇은 테두리) */
    public static JButton secondary(String text) {
        return make(text, "background:$proto.surface; foreground:$proto.text; borderColor:$proto.borderStrong;"
                + " hoverBackground:$proto.subtle; pressedBackground:$proto.hover;"
                + " focusedBackground:$proto.surface; margin:6,16,6,16; " + BASE);
    }

    /** 배경 없는 글자 버튼 (마우스를 올리면 옅은 배경) */
    public static JButton ghost(String text) {
        JButton b = make(text, "foreground:$proto.text; toolbar.hoverBackground:$proto.hover;"
                + " toolbar.pressedBackground:$proto.hover; toolbar.margin:4,10,4,10;"
                + " toolbar.spacingInsets:0,0,0,0; " + BASE);
        b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        return b;
    }

    /** 삭제처럼 되돌리기 어려운 행동 (글자만 위험색) */
    public static JButton danger(String text) {
        return make(text, "background:$proto.surface; foreground:$proto.danger; borderColor:$proto.borderStrong;"
                + " hoverBackground:$proto.dangerSoft; pressedBackground:$proto.dangerSoft;"
                + " focusedBackground:$proto.surface; margin:6,16,6,16; " + BASE);
    }

    /** 확인 대화상자의 위험 행동 (위험색 채움) */
    public static JButton dangerFill(String text) {
        return make(text, "background:$proto.danger; foreground:$proto.bg; borderWidth:0;"
                + " hoverBackground:darken($proto.danger,6%); pressedBackground:darken($proto.danger,12%);"
                + " focusedBackground:$proto.danger; margin:6,16,6,16; " + BASE);
    }

    /** 아이콘만 있는 작은 투명 버튼 */
    public static JButton icon(String iconName, String tooltip) {
        return icon(iconName, tooltip, Tokens.MUTED);
    }

    public static JButton icon(String iconName, String tooltip, String colorToken) {
        JButton b = ghost(null);
        Icon icon = Icons.of(iconName, 16, colorToken);
        b.setIcon(icon);
        b.setToolTipText(tooltip);
        b.putClientProperty(FlatClientProperties.STYLE, "toolbar.margin:6,6,6,6; toolbar.hoverBackground:$proto.hover;"
                + " toolbar.pressedBackground:$proto.hover; toolbar.spacingInsets:0,0,0,0; arc:8");
        b.getAccessibleContext().setAccessibleName(tooltip);
        return b;
    }

    /** 글자 + 앞 아이콘이 있는 투명 버튼 */
    public static JButton ghostWithIcon(String text, String iconName) {
        JButton b = ghost(text);
        b.setIcon(Icons.of(iconName, 16, Tokens.MUTED));
        b.setIconTextGap(8);
        return b;
    }

    /** 좁은 카드 안에서 쓰도록 버튼을 작게 줄인다 */
    public static JButton compact(JButton b) {
        Object style = b.getClientProperty(FlatClientProperties.STYLE);
        b.putClientProperty(FlatClientProperties.STYLE, style + "; margin:4,12,4,12");
        b.setFont(Fonts.medium(Fonts.SMALL));
        return b;
    }

    private static JButton make(String text, String style) {
        JButton b = new JButton(text);
        b.setFont(Fonts.medium(Fonts.BODY));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.putClientProperty(FlatClientProperties.STYLE, style);
        b.setFocusable(true);
        return b;
    }
}
