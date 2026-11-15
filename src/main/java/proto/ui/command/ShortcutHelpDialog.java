package proto.ui.command;

import proto.ui.UiContext;
import proto.ui.component.Buttons;
import proto.ui.component.TokenLabel;
import proto.ui.component.WidthPanel;
import proto.ui.dialog.BaseDialog;
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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Dimension;
import java.awt.FontMetrics;

/**
 * [경계 boundary] 단축키 안내 시트: 묶음별로 "하는 일 ····· 키" 목록을 보여 준다 (? 키, 명령 메뉴의 "단축키 보기").
 * 내용은 ShortcutCatalog가 정하고, 이 시트는 그리기만 한다. BaseDialog를 쓰므로 스크림·바깥 클릭/Esc 닫기·포커스 복귀가 다른 시트와 같다.
 * 입력이 없는 시트라 바깥 클릭도 바로 닫힌다. 닫기 버튼이 기본 버튼이다.
 */
public final class ShortcutHelpDialog extends BaseDialog {

    public ShortcutHelpDialog(UiContext ctx) {
        this(ctx.window());
    }

    /** 시트를 띄울 창(또는 그 안의 컴포넌트)을 직접 정한다 (테스트) */
    ShortcutHelpDialog(java.awt.Component owner) {
        super(owner, "단축키");
        JButton close = Buttons.primary("닫기");
        close.addActionListener(e -> onCancel());
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.setOpaque(false);
        footer.add(close);
        setBody(body(), footer, 28);
        setDefaultButton(close);
        setInitialFocus(close);
    }

    private JPanel body() {
        JPanel root = new WidthPanel(new BorderLayout(), 480);
        TokenLabel heading = new TokenLabel("단축키", Fonts.semibold(Fonts.TITLE), Tokens.TEXT);
        heading.setBorder(BorderFactory.createEmptyBorder(0, 0, Ui.px(14), 0));
        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        for (ShortcutCatalog.Group g : ShortcutCatalog.groups()) {
            list.add(groupTitle(g.title()));
            g.entries().forEach(e -> list.add(new EntryRow(e)));
        }
        root.add(heading, BorderLayout.NORTH);
        root.add(list, BorderLayout.CENTER);
        return root;
    }

    private static JComponent groupTitle(String text) {
        TokenLabel l = new TokenLabel(text, Fonts.semibold(Fonts.CAPTION), Tokens.MUTED);
        l.setBorder(BorderFactory.createEmptyBorder(Ui.px(14), 0, Ui.px(6), 0));
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    /** 한 줄: 왼쪽에 하는 일, 오른쪽 끝에 키 칸들 */
    private static final class EntryRow extends JComponent {
        private static final int H = 32;
        private final ShortcutCatalog.Entry entry;

        EntryRow(ShortcutCatalog.Entry entry) {
            this.entry = entry;
            setAlignmentX(LEFT_ALIGNMENT);
        }

        @Override
        public javax.accessibility.AccessibleContext getAccessibleContext() {
            if (accessibleContext == null) {
                accessibleContext = new AccessibleJComponent() { };
                accessibleContext.setAccessibleName(entry.what() + ": " + String.join(" ", entry.keys()));
            }
            return accessibleContext;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(Ui.px(100), Ui.px(H));
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, Ui.px(H));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Ui.smooth(g2);
            int cy = getHeight() / 2;
            int right = getWidth();
            for (int i = entry.keys().size() - 1; i >= 0; i--) {
                right = paintKey(g2, entry.keys().get(i), right, cy) - Ui.px(6);
            }
            g2.setFont(Fonts.regular(Fonts.BODY));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Tokens.get(Tokens.TEXT));
            g2.drawString(entry.what(), 0, cy - fm.getHeight() / 2 + fm.getAscent());
            g2.setColor(Tokens.get(Tokens.BORDER));
            g2.fillRect(0, getHeight() - 1, getWidth(), 1);
            g2.dispose();
        }

        /** 키 칸 하나를 right에서 왼쪽으로 그리고 그 왼쪽 끝을 돌려준다 */
        private int paintKey(Graphics2D g2, String text, int right, int cy) {
            g2.setFont(Fonts.medium(Fonts.CAPTION));
            FontMetrics fm = g2.getFontMetrics();
            int w = Math.max(Ui.px(24), fm.stringWidth(text) + Ui.px(14));
            int h = Ui.px(22);
            int x = right - w;
            g2.setColor(Tokens.get(Tokens.SUBTLE));
            g2.fillRoundRect(x, cy - h / 2, w, h, Ui.px(6), Ui.px(6));
            g2.setColor(Tokens.get(Tokens.BORDER_STRONG));
            g2.drawRoundRect(x, cy - h / 2, w - 1, h - 1, Ui.px(6), Ui.px(6));
            g2.setColor(Tokens.get(Tokens.TEXT));
            g2.drawString(text, x + (w - fm.stringWidth(text)) / 2, cy - fm.getHeight() / 2 + fm.getAscent());
            return x;
        }
    }
}
