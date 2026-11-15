package proto.ui;

import com.formdev.flatlaf.FlatClientProperties;
import proto.ui.component.Buttons;
import proto.ui.component.Ime;
import proto.ui.component.Listeners;
import proto.ui.layout.SizeClass;
import proto.ui.motion.Motion;
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
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

/**
 * [경계 boundary] 본문 위쪽 줄: 보기 제목과 날짜, 이전/오늘/다음 버튼, 검색창(입력 후 150ms 뒤 적용).
 * MainFrame이 만들어 본문 위쪽에 붙인다. 이전/오늘/다음 버튼은 MainFrame의 shift·goToday로, 검색어는 MainFrame#search로 전달된다 (F2 조회 이동, F8 검색).
 * 좁은 화면(SizeClass.compact)에서는 검색창을 아이콘 하나로 접었다가 눌렀을 때 펼쳐서 자리를 아낀다.
 * 검색창 오른쪽의 "⌘K" 작은 버튼은 명령 메뉴를 연다 (넓은 화면에서만 보인다. 좁은 화면에서도 ⌘K 키는 그대로 된다).
 */
public final class HeaderBar extends JPanel {

    private static final int DEBOUNCE_MS = 150;

    private final TokenLabel title = new TokenLabel("", Fonts.semibold(Fonts.DISPLAY), Tokens.TEXT);
    private final TokenLabel subtitle = new TokenLabel("", Fonts.regular(Fonts.SMALL), Tokens.MUTED);
    private final JPanel navigator = new JPanel(new FlowLayout(FlowLayout.RIGHT, Ui.px(4), 0));
    private final JTextField search = new JTextField();
    private final JButton searchIcon = Buttons.icon("search", "검색 (" + Ui.shortcut("F") + ")", Tokens.TEXT);
    private final Timer debounce;
    private final Consumer<String> onSearch;
    private final JButton commandButton = Buttons.compact(Buttons.secondary(Ui.shortcut("K")));
    private boolean compact;
    private boolean searchAllowed = true;
    private boolean expanded;
    private Motion.Handle expandMotion;

    public HeaderBar(Runnable onPrev, Runnable onToday, Runnable onNext, Consumer<String> onSearch,
                     Runnable onCommand) {
        super(new BorderLayout(Ui.px(16), 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(Ui.px(Ui.isMac() ? 34 : 22), Ui.px(32), Ui.px(16), Ui.px(32)));
        // 검색창에 글자를 칠 때마다 곧바로 검색하면 한 글자마다 화면을 다시 그리므로, 마지막 입력 뒤 150ms 동안 더 입력이 없을 때만 검색한다.
        debounce = new Timer(DEBOUNCE_MS, e -> onSearch.accept(search.getText()));
        debounce.setRepeats(false);
        this.onSearch = onSearch;
        commandButton.setToolTipText("명령 메뉴 (" + Ui.shortcut("K") + ")");
        commandButton.getAccessibleContext().setAccessibleName("명령 메뉴 열기");
        commandButton.addActionListener(e -> onCommand.run());
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
        p.add(searchIcon);
        p.add(search);
        p.add(commandButton);
        searchIcon.setVisible(false);
        searchIcon.addActionListener(e -> expandSearch());
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
        search.getAccessibleContext().setAccessibleName("일정 검색");
        Ime.track(search);
        Listeners.onTextChange(search, debounce::restart);
        // Enter를 누르면 기다리지 않고 바로 검색한다. 그 전에 한글 조합 중인 마지막 글자를 확정해야 검색어에서 빠지지 않는다.
        search.addActionListener(e -> {
            Ime.commit(search);
            debounce.stop();
            onSearch.accept(search.getText());
        });
        bindSearchEscape();
        search.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if (compact && search.getText().isEmpty() && !e.isTemporary()) {
                    collapseSearch(false);
                }
            }
        });
    }

    private void bindSearchEscape() {
        search.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "proto-esc");
        search.getActionMap().put("proto-esc", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                escapeSearch();
            }
        });
    }

    /** Esc: 글이 있으면 먼저 지우고, 비어 있으면 (좁은 화면에서) 검색창을 접는다 */
    private void escapeSearch() {
        if (!search.getText().isEmpty()) {
            search.setText("");
        } else if (compact) {
            collapseSearch(true);
        }
    }

    /** 화면 크기 등급에 맞춰 여백과 검색창 모양(좁으면 아이콘 버튼)을 바꾼다 */
    public void apply(SizeClass sc) {
        compact = sc.compact();
        int trim = Ui.px(sc.verticalTrim());
        setBorder(BorderFactory.createEmptyBorder(Ui.px(Ui.isMac() ? 34 : 22) - trim, Ui.px(sc.gutter()),
                Ui.px(16) - trim, Ui.px(sc.gutter())));
        title.setFont(Fonts.semibold(sc.dense() ? Fonts.TITLE + 2 : Fonts.DISPLAY));
        search.setPreferredSize(new Dimension(Ui.px(compact ? 200 : 260), Ui.px(36)));
        expanded = !compact || !search.getText().isEmpty();
        commandButton.setVisible(!compact);
        updateSearchMode();
        revalidate();
    }

    private void updateSearchMode() {
        search.setVisible(searchAllowed && (!compact || expanded));
        searchIcon.setVisible(searchAllowed && compact && !expanded);
    }

    private void expandSearch() {
        if (expanded) {
            return;
        }
        expanded = true;
        updateSearchMode();
        int full = Ui.px(200);
        if (expandMotion != null) {
            expandMotion.cancel();
        }
        expandMotion = Motion.run(160, f -> {
            search.setPreferredSize(new Dimension(Math.max(Ui.px(40), Math.round(full * f)), Ui.px(36)));
            search.getParent().revalidate();
        }, () -> search.setPreferredSize(new Dimension(full, Ui.px(36))));
        search.requestFocusInWindow();
    }

    private void collapseSearch(boolean restoreFocus) {
        if (!expanded || !compact) {
            return;
        }
        expanded = false;
        updateSearchMode();
        revalidate();
        repaint();
        if (restoreFocus && searchIcon.isShowing()) {
            searchIcon.requestFocusInWindow();
        }
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
        search.getAccessibleContext().setAccessibleDescription(enabled ? "" : "통계에는 적용되지 않아요");
        searchIcon.setEnabled(enabled);
        search.setEnabled(enabled);
        search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, enabled ? "제목이나 메모 검색" : "통계에는 검색이 적용되지 않아요");
    }

    /** 검색창 자체를 보이거나 숨긴다 */
    public void setSearchVisible(boolean visible) {
        searchAllowed = visible;
        updateSearchMode();
    }

    /** 검색창에 포커스를 준다. 접혀 있으면 먼저 펼친다 */
    public void focusSearch() {
        if (!searchAllowed || !search.isEnabled()) {
            return;
        }
        expandSearch();
        search.requestFocusInWindow();
        search.selectAll();
    }

    /** 창을 닫을 때 남은 타이머를 멈춘다 */
    public void dispose() {
        debounce.stop();
        if (expandMotion != null) {
            expandMotion.cancel();
        }
    }

    /** 검색어를 지운다 */
    public void clearSearch() {
        search.setText("");
    }
}
