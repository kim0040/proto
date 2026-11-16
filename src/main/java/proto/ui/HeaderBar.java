package proto.ui;

import com.formdev.flatlaf.FlatClientProperties;
import proto.ui.component.Buttons;
import proto.ui.component.Ime;
import proto.ui.component.Listeners;
import proto.ui.component.TokenLabel;
import proto.ui.layout.SizeClass;
import proto.ui.motion.Motion;
import proto.ui.theme.Fonts;
import proto.ui.theme.Icons;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

/**
 * [경계 boundary] 본문 위쪽 줄: 보기 제목과 날짜, 이전/오늘/다음 버튼, 검색창(입력 후 150ms 뒤 적용), 명령 메뉴를 여는 "⌘K" 버튼.
 * 사이드바를 숨기면 제목 왼쪽에 "사이드바 보이기" 버튼이 나타난다.
 * MainFrame이 만들어 본문 위쪽에 붙이고, 버튼과 검색어는 MainFrame이 넘겨 준 동작(shift·goToday·search)으로 전달된다 (F2 조회 이동, F8 검색).
 * 좁은 화면(SizeClass.compact)에서는 자리를 아끼려고 검색창을 아이콘 하나로 접었다가 눌렀을 때 펼치고, "⌘K" 버튼은 숨긴다 (⌘K 키는 그대로 동작한다).
 */
public final class HeaderBar extends JPanel {

    private static final int DEBOUNCE_MS = 150;
    private static final int EXPAND_MS = 160;
    private static final int SEARCH_WIDTH = 260;
    private static final int SEARCH_WIDTH_COMPACT = 200;
    private static final int SEARCH_HEIGHT = 36;

    private final TokenLabel title = new TokenLabel("", Fonts.semibold(Fonts.DISPLAY), Tokens.TEXT);
    private final TokenLabel subtitle = new TokenLabel("", Fonts.regular(Fonts.SMALL), Tokens.MUTED);
    private final JPanel navigator = new JPanel(new FlowLayout(FlowLayout.RIGHT, Ui.px(4), 0));
    private final JTextField search = new JTextField();
    private final JButton searchIcon = Buttons.icon("search", "검색 (" + Ui.shortcut("F") + ")", Tokens.TEXT);
    private final Timer debounce;
    private final Consumer<String> onSearch;
    private final JButton commandButton = Buttons.compact(Buttons.secondary(Ui.shortcut("K")));
    /** 사이드바를 숨겼을 때만 제목 왼쪽에 보이는 "사이드바 보이기" 버튼 */
    private final JButton sidebarButton = Buttons.icon("panel-left", "사이드바 보이기 (" + Ui.shortcut("\\") + ")", Tokens.TEXT);
    private boolean compact;
    private boolean searchAllowed = true;
    private boolean expanded;
    private Motion.Handle expandMotion;

    public HeaderBar(Runnable onPrev, Runnable onToday, Runnable onNext, Consumer<String> onSearch,
                     Runnable onCommand, Runnable onShowSidebar) {
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
        sidebarButton.addActionListener(e -> onShowSidebar.run());
        add(sidebarSlot(), BorderLayout.WEST);
        add(titles(), BorderLayout.CENTER);
        add(right(onPrev, onToday, onNext), BorderLayout.EAST);
    }

    /** 사이드바를 숨겼으면 제목 왼쪽에 "사이드바 보이기" 버튼을 보인다 */
    public void setSidebarHidden(boolean hidden) {
        // 버튼을 감싼 칸째로 숨겨야 BorderLayout이 제목 앞의 간격도 함께 뺀다
        sidebarButton.getParent().setVisible(hidden);
        revalidate();
        repaint();
    }

    /** "사이드바 보이기" 버튼으로 포커스를 옮긴다 (사이드바 안에 있던 포커스가 사이드바와 함께 사라지지 않게) */
    public void focusSidebarButton() {
        sidebarButton.requestFocusInWindow();
    }

    /** 화면 크기 등급에 맞춰 여백과 검색창 모양(좁으면 아이콘 버튼)을 바꾼다 */
    public void apply(SizeClass sc) {
        compact = sc.compact();
        int trim = Ui.px(sc.verticalTrim());
        setBorder(BorderFactory.createEmptyBorder(Ui.px(Ui.isMac() ? 34 : 22) - trim, Ui.px(sc.gutter()),
                Ui.px(16) - trim, Ui.px(sc.gutter())));
        title.setFont(Fonts.semibold(sc.dense() ? Fonts.TITLE + 2 : Fonts.DISPLAY));
        search.setPreferredSize(new Dimension(Ui.px(compact ? SEARCH_WIDTH_COMPACT : SEARCH_WIDTH), Ui.px(SEARCH_HEIGHT)));
        expanded = !compact || !search.getText().isEmpty();
        commandButton.setVisible(!compact);
        updateSearchMode();
        revalidate();
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

    /** 버튼을 제목 줄 높이에 맞춰 위쪽에 둔다 (BorderLayout의 WEST는 높이를 꽉 채우므로 한 번 감싼다). 처음에는 숨겨 둔다 */
    private JPanel sidebarSlot() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(sidebarButton, BorderLayout.NORTH);
        p.setVisible(false);
        return p;
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
        search.setPreferredSize(new Dimension(Ui.px(SEARCH_WIDTH), Ui.px(SEARCH_HEIGHT)));
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
            public void actionPerformed(ActionEvent e) {
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
        int full = Ui.px(SEARCH_WIDTH_COMPACT);
        if (expandMotion != null) {
            expandMotion.cancel();
        }
        expandMotion = Motion.run(EXPAND_MS, f -> {
            search.setPreferredSize(new Dimension(Math.max(Ui.px(40), Math.round(full * f)), Ui.px(SEARCH_HEIGHT)));
            search.getParent().revalidate();
        }, () -> search.setPreferredSize(new Dimension(full, Ui.px(SEARCH_HEIGHT))));
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
}
