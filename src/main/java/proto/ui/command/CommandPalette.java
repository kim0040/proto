package proto.ui.command;

import com.formdev.flatlaf.FlatClientProperties;
import proto.ui.component.Ime;
import proto.ui.component.Listeners;
import proto.ui.component.TokenLabel;
import proto.ui.theme.Fonts;
import proto.ui.theme.Icons;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.border.AbstractBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.Function;

/**
 * [경계 boundary] 명령 메뉴(⌘K)의 카드 안쪽: 맨 위 입력칸, 결과 목록, 아래 키 안내.
 * PaletteLayer가 카드로 띄운다. 글을 입력할 때마다 source(CommandCatalog#results)로 목록을 다시 만들고, ↑/↓로 고르고 Enter로 실행하고 Esc·⌘K로 닫는다.
 * 한글 입력 중(IME 조합)에 누른 Enter는 글자를 확정하기만 하고 실행하지 않는다. 입력칸은 늘 포커스를 갖고, 목록은 포커스를 받지 않는다.
 * 목록 높이가 바뀌면(결과가 줄면) onResize로 알려서 카드가 맞춰 커지거나 작아진다.
 */
final class CommandPalette extends JPanel {

    private final Function<String, List<Command>> source;
    private final JTextField field = new JTextField();
    private final PaletteResults results;
    private final JScrollPane scroll;
    private final TokenLabel empty = new TokenLabel("맞는 명령이 없어요", Fonts.regular(Fonts.BODY), Tokens.MUTED);
    private final Runnable onClose;
    private final Runnable onResize;

    CommandPalette(Function<String, List<Command>> source, java.util.function.Consumer<Command> onRun, Runnable onClose,
                   Runnable onResize) {
        super(new BorderLayout());
        this.source = source;
        this.onClose = onClose;
        this.onResize = onResize;
        this.results = new PaletteResults(onRun);
        setOpaque(false);
        scroll = new JScrollPane(results, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder(Ui.px(6), 0, Ui.px(6), 0));
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setFocusable(false);
        configureField();
        add(field, BorderLayout.NORTH);
        add(middle(), BorderLayout.CENTER);
        add(footer(), BorderLayout.SOUTH);
        refresh();
    }

    private void configureField() {
        field.setFont(Fonts.regular(Fonts.LEAD));
        field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "명령이나 날짜를 입력하세요  (예: 내일, 11월 20일, 설정)");
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, Icons.of("search", 18, Tokens.MUTED));
        field.putClientProperty(FlatClientProperties.STYLE, "background:$proto.surface; borderWidth:0; focusWidth:0;"
                + " innerFocusWidth:0; margin:2,18,2,18");
        field.setBorder(BorderFactory.createEmptyBorder(Ui.px(14), Ui.px(18), Ui.px(14), Ui.px(18)));
        field.setPreferredSize(new Dimension(Ui.px(300), Ui.px(54)));
        field.getAccessibleContext().setAccessibleName("명령 검색");
        // Tab·Shift+Tab은 결과 이동으로 쓴다. 끄지 않으면 Swing이 먼저 포커스를 뒤 화면으로 넘겨 버린다
        field.setFocusTraversalKeysEnabled(false);
        Ime.track(field);
        Listeners.onTextChange(field, this::refresh);
        field.addActionListener(e -> runSelected());
        bindKeys();
    }

    private JPanel middle() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(new TopLine());
        empty.setHorizontalAlignment(TokenLabel.CENTER);
        empty.setBorder(BorderFactory.createEmptyBorder(Ui.px(18), 0, Ui.px(18), 0));
        p.add(scroll, BorderLayout.CENTER);
        p.add(empty, BorderLayout.SOUTH);
        return p;
    }

    private JPanel footer() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(Ui.px(8), Ui.px(18), Ui.px(10), Ui.px(18)));
        p.add(new TokenLabel("↑↓ 고르기     Enter 실행     Esc 닫기", Fonts.regular(Fonts.CAPTION), Tokens.MUTED),
                BorderLayout.WEST);
        return p;
    }

    /** 입력칸 키: ↑/↓ 고르기, PageUp/PageDown 5칸, Tab 아래로, Esc·⌘K 닫기 (Enter는 입력칸의 기본 동작으로 실행) */
    private void bindKeys() {
        int mask = Ui.isMac() ? KeyEvent.META_DOWN_MASK : KeyEvent.CTRL_DOWN_MASK;
        bind("up", KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), () -> results.move(-1));
        bind("down", KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), () -> results.move(1));
        bind("tab", KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), () -> results.move(1));
        bind("shift-tab", KeyStroke.getKeyStroke(KeyEvent.VK_TAB, KeyEvent.SHIFT_DOWN_MASK), () -> results.move(-1));
        bind("pgup", KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), () -> results.move(-5));
        bind("pgdn", KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), () -> results.move(5));
        bind("esc", KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), this::closeUnlessComposing);
        bind("close", KeyStroke.getKeyStroke(KeyEvent.VK_K, mask), onClose);
    }

    private void bind(String name, KeyStroke key, Runnable action) {
        field.getInputMap(JComponent.WHEN_FOCUSED).put(key, "proto-palette-" + name);
        field.getActionMap().put("proto-palette-" + name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    /** 한글을 조합하는 중의 Esc는 글자 확정으로만 쓴다 */
    private void closeUnlessComposing() {
        if (!Ime.swallowIfComposing()) {
            onClose.run();
        }
    }

    /** Enter: 한글 조합 중이면 글자만 확정하고, 아니면 고른 줄을 실행한다 */
    private void runSelected() {
        if (Ime.swallowIfComposing()) {
            return;
        }
        results.chooseSelected();
    }

    /** 입력한 글로 목록을 다시 만든다 (글이 비어 있으면 묶음 제목과 함께 전체) */
    private void refresh() {
        String q = field.getText();
        List<Command> found = source.apply(q);
        results.setCommands(found, q.strip().isEmpty());
        boolean none = found.isEmpty();
        empty.setVisible(none);
        scroll.setVisible(!none);
        field.getAccessibleContext().setAccessibleDescription(none ? "결과 없음" : "결과 " + found.size() + "개");
        scroll.setPreferredSize(new Dimension(Ui.px(300), results.viewportHeight() + Ui.px(12)));
        revalidate();
        onResize.run();
    }

    /** 열릴 때 입력칸에 포커스를 준다 */
    void focusField() {
        field.requestFocusInWindow();
    }

    /** 입력칸 (테스트와 화면 캡처가 쓴다) */
    JTextField field() {
        return field;
    }

    PaletteResults results() {
        return results;
    }

    /** 입력칸과 목록 사이의 위쪽 1px 옅은 선 (테마가 바뀌면 새 색으로 그린다) */
    private static final class TopLine extends AbstractBorder {
        @Override
        public void paintBorder(java.awt.Component c, java.awt.Graphics g, int x, int y, int width, int height) {
            g.setColor(Tokens.get(Tokens.BORDER));
            g.fillRect(x, y, width, 1);
        }

        @Override
        public java.awt.Insets getBorderInsets(java.awt.Component c) {
            return new java.awt.Insets(1, 0, 0, 0);
        }
    }
}
