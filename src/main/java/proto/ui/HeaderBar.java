package proto.ui;

import com.formdev.flatlaf.FlatClientProperties;
import proto.ui.component.Buttons;
import proto.ui.component.Listeners;
import proto.ui.component.TokenLabel;
import proto.ui.theme.Fonts;
import proto.ui.theme.Icons;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.function.Consumer;

/**
 * [경계 boundary] 본문 위쪽 줄: 보기 제목과 날짜, 이전/오늘/다음 버튼, 검색창(입력 후 150ms 뒤 적용).
 */
public final class HeaderBar extends JPanel {

    private static final int DEBOUNCE_MS = 150;

    private final TokenLabel title = new TokenLabel("", Fonts.semibold(Fonts.DISPLAY), Tokens.TEXT);
    private final TokenLabel subtitle = new TokenLabel("", Fonts.regular(Fonts.SMALL), Tokens.MUTED);
    private final JPanel navigator = new JPanel(new FlowLayout(FlowLayout.RIGHT, Ui.px(4), 0));
    private final JTextField search = new JTextField();
    private final Timer debounce;

    public HeaderBar(Runnable onPrev, Runnable onToday, Runnable onNext, Consumer<String> onSearch) {
        super(new BorderLayout(Ui.px(16), 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(Ui.px(Ui.isMac() ? 34 : 22), Ui.px(32), Ui.px(16), Ui.px(32)));
        debounce = new Timer(DEBOUNCE_MS, e -> onSearch.accept(search.getText()));
        debounce.setRepeats(false);
        add(titles(), BorderLayout.CENTER);
        add(right(onPrev, onToday, onNext), BorderLayout.EAST);
    }

    private JPanel titles() {
        JPanel p = new JPanel(new BorderLayout(0, Ui.px(2)));
        p.setOpaque(false);
        p.add(title, BorderLayout.NORTH);
        p.add(subtitle, BorderLayout.CENTER);
        return p;
    }

    private JPanel right(Runnable onPrev, Runnable onToday, Runnable onNext) {
        buildNavigator(onPrev, onToday, onNext);
        configureSearch();
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, Ui.px(12), 0));
        p.setOpaque(false);
        p.add(navigator);
        p.add(search);
        return p;
    }

    private void buildNavigator(Runnable onPrev, Runnable onToday, Runnable onNext) {
        navigator.setOpaque(false);
        JButton prev = Buttons.icon("chevron-left", "이전", Tokens.TEXT);
        JButton today = Buttons.compact(Buttons.secondary("오늘"));
        JButton next = Buttons.icon("chevron-right", "다음", Tokens.TEXT);
        prev.addActionListener(e -> onPrev.run());
        today.addActionListener(e -> onToday.run());
        next.addActionListener(e -> onNext.run());
        navigator.add(prev);
        navigator.add(today);
        navigator.add(next);
    }

    private void configureSearch() {
        search.setFont(Fonts.regular(Fonts.BODY));
        search.setPreferredSize(new Dimension(Ui.px(260), Ui.px(36)));
        search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "제목이나 메모 검색");
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, Icons.of("search", 16, Tokens.MUTED));
        search.putClientProperty(FlatClientProperties.STYLE, "arc:10; margin:4,8,4,8; background:$proto.surface;"
                + " borderColor:$proto.borderStrong");
        Listeners.onTextChange(search, debounce::restart);
    }

    /** 제목과 작은 글씨를 바꾼다 */
    public void setTitles(String titleText, String subtitleText) {
        title.setText(titleText);
        subtitle.setText(subtitleText);
    }

    /** 이전/오늘/다음 버튼을 보이거나 숨긴다 */
    public void setNavigatorVisible(boolean visible) {
        navigator.setVisible(visible);
    }

    /** 검색창을 켜거나 끈다 (통계 보기는 검색을 쓰지 않는다) */
    public void setSearchEnabled(boolean enabled) {
        search.setEnabled(enabled);
        search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, enabled ? "제목이나 메모 검색" : "통계에는 검색이 적용되지 않아요");
    }

    /** 검색창 자체를 보이거나 숨긴다 */
    public void setSearchVisible(boolean visible) {
        search.setVisible(visible);
    }

    public void focusSearch() {
        search.requestFocusInWindow();
        search.selectAll();
    }

    /** 검색어를 지운다 */
    public void clearSearch() {
        search.setText("");
    }
}
