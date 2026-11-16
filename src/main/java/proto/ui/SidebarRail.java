package proto.ui;

import com.formdev.flatlaf.FlatClientProperties;
import proto.ui.AppState.Mode;
import proto.ui.component.Buttons;
import proto.ui.component.PillList;
import proto.ui.component.TokenPanel;
import proto.ui.layout.ColumnLayout;
import proto.ui.theme.Icons;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.function.Consumer;

/**
 * [경계 boundary] 좁은 화면에서 Sidebar 대신 보이는 아이콘 띠 (MainFrame이 만들어 SidebarHost 안에 둔다).
 * 맨 위 메뉴 버튼은 전체 사이드바를 서랍으로 펼치고, 둥근 + 버튼은 새 일정을 연다.
 * 아래쪽에는 사이드바 숨기기와 설정이 있다. 글자가 없으므로 모든 항목에 툴팁과 접근성 이름을 붙인다. 모든 버튼은 띠의 가운데 한 줄에 맞춰 선다.
 */
public final class SidebarRail extends TokenPanel {

    private final UiContext ctx;
    private final PillList nav = new PillList();
    private final PillList tools = new PillList();
    private final PillList settings = new PillList();
    private final JButton menu = Buttons.icon("menu", "메뉴 열기", Tokens.TEXT);

    /** onExpand는 메뉴·카테고리 버튼(전체 사이드바를 서랍으로 펼친다), onHide는 아래쪽 숨기기 버튼이 부른다 */
    public SidebarRail(UiContext ctx, Consumer<Mode> onNav, Runnable onExpand, Runnable onHide) {
        super(new BorderLayout(), Tokens.SIDEBAR);
        this.ctx = ctx;
        setBorder(BorderFactory.createEmptyBorder(Ui.px(Ui.isMac() ? 44 : 16), Ui.px(10), Ui.px(14), Ui.px(10)));
        for (PillList list : new PillList[] {nav, tools, settings}) {
            list.setIconOnly(true);
        }
        nav.addItem(Mode.TODAY.name(), "calendar-check", null, "오늘", () -> onNav.accept(Mode.TODAY));
        nav.addItem(Mode.WEEK.name(), "calendar-range", null, "이번 주", () -> onNav.accept(Mode.WEEK));
        nav.addItem(Mode.MONTH.name(), "calendar-days", null, "달력", () -> onNav.accept(Mode.MONTH));
        nav.addItem(Mode.STATS.name(), "chart", null, "주간 통계", () -> onNav.accept(Mode.STATS));
        tools.addItem("free", "hourglass", null, "빈 시간 찾기", () -> ctx.actions().openFreeSlots());
        tools.addItem("tag", "tag", null, "카테고리", onExpand);
        settings.addItem("hide", "panel-left", null, "사이드바 숨기기 (" + Ui.shortcut("\\") + ")", onHide);
        settings.addItem("settings", "settings", null, "설정", () -> ctx.actions().openSettings());
        nav.setSelected(Mode.TODAY.name(), false);
        tools.setSelected("", false);
        settings.setSelected("", false);
        menu.setToolTipText("메뉴 열기");
        menu.addActionListener(e -> onExpand.run());
        add(top(), BorderLayout.NORTH);
        add(settings, BorderLayout.SOUTH);
    }

    /** 보기 선택 표시를 맞춘다 */
    public void setMode(Mode mode, boolean animate) {
        nav.setSelected(mode.name(), animate);
    }

    /** 메뉴 버튼으로 포커스를 돌려준다 (서랍이 닫힌 뒤) */
    public void focusMenu() {
        menu.requestFocusInWindow();
    }

    private JPanel top() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(ColumnLayout.centered());
        JButton add = roundPlus();
        p.add(menu);
        p.add(Box.createVerticalStrut(Ui.px(14)));
        p.add(add);
        p.add(Box.createVerticalStrut(Ui.px(16)));
        p.add(nav);
        p.add(Box.createVerticalStrut(Ui.px(4)));
        p.add(tools);
        return p;
    }

    /** 새 일정을 여는 둥근 + 버튼. FlatLaf 스타일 문자열에서 arc를 크게(999) 주면 완전한 원이 되고, 40px 정사각형으로 크기를 고정한다. */
    private JButton roundPlus() {
        JButton b = Buttons.primary(null);
        b.setIcon(Icons.of("plus", 18, Tokens.ON_ACCENT));
        b.putClientProperty(FlatClientProperties.STYLE, "background:$proto.accent; foreground:$proto.onAccent;"
                + " borderWidth:0; arc:999; margin:0,0,0,0; focusWidth:1; minimumWidth:0;"
                + " hoverBackground:darken($proto.accent,6%); pressedBackground:darken($proto.accent,12%);"
                + " focusedBackground:$proto.accent");
        Dimension d = new Dimension(Ui.px(40), Ui.px(40));
        b.setPreferredSize(d);
        b.setMaximumSize(d);
        b.setMinimumSize(d);
        b.setToolTipText("새 일정 (" + Ui.shortcut("N") + ")");
        b.getAccessibleContext().setAccessibleName("새 일정");
        b.addActionListener(e -> ctx.actions().newScheduleHere());
        return b;
    }
}
