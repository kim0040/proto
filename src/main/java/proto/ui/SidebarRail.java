package proto.ui;

import com.formdev.flatlaf.FlatClientProperties;
import proto.ui.AppState.Mode;
import proto.ui.component.Buttons;
import proto.ui.component.PillList;
import proto.ui.component.TokenPanel;
import proto.ui.theme.Icons;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.function.Consumer;

/**
 * [경계 boundary] 좁은 화면의 사이드바: 아이콘만 있는 얇은 띠. 맨 위 메뉴 버튼이 전체 사이드바를 서랍으로 펼치고,
 * 둥근 + 버튼이 새 일정을 연다. 모든 버튼은 툴팁과 접근성 이름을 가진다.
 */
public final class SidebarRail extends TokenPanel {

    private final UiContext ctx;
    private final PillList nav = new PillList();
    private final PillList tools = new PillList();
    private final PillList settings = new PillList();
    private final JButton menu = Buttons.icon("menu", "메뉴 열기", Tokens.TEXT);

    public SidebarRail(UiContext ctx, Consumer<Mode> onNav, Runnable onExpand) {
        super(new BorderLayout(), Tokens.SIDEBAR);
        this.ctx = ctx;
        setBorder(BorderFactory.createEmptyBorder(Ui.px(Ui.isMac() ? 44 : 16), Ui.px(10), Ui.px(14), Ui.px(10)));
        nav.setIconOnly(true);
        tools.setIconOnly(true);
        settings.setIconOnly(true);
        nav.addItem(Mode.TODAY.name(), "calendar-check", null, "오늘", () -> onNav.accept(Mode.TODAY));
        nav.addItem(Mode.WEEK.name(), "calendar-range", null, "이번 주", () -> onNav.accept(Mode.WEEK));
        nav.addItem(Mode.MONTH.name(), "calendar-days", null, "달력", () -> onNav.accept(Mode.MONTH));
        nav.addItem(Mode.STATS.name(), "chart", null, "주간 통계", () -> onNav.accept(Mode.STATS));
        tools.addItem("free", "hourglass", null, "빈 시간 찾기", () -> ctx.actions().openFreeSlots());
        tools.addItem("tag", "tag", null, "카테고리", onExpand);
        settings.addItem(Mode.SETTINGS.name(), "settings", null, "설정", () -> onNav.accept(Mode.SETTINGS));
        nav.setSelected(Mode.TODAY.name(), false);
        tools.setSelected("", false);
        settings.setSelected("", false);
        menu.setToolTipText("메뉴 열기");
        menu.addActionListener(e -> onExpand.run());
        add(top(), BorderLayout.NORTH);
        add(settings, BorderLayout.SOUTH);
    }

    private JPanel top() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        menu.setAlignmentX(CENTER_ALIGNMENT);
        JButton add = roundPlus();
        add.setAlignmentX(CENTER_ALIGNMENT);
        p.add(menu);
        p.add(Box.createVerticalStrut(Ui.px(14)));
        p.add(add);
        p.add(Box.createVerticalStrut(Ui.px(16)));
        p.add(nav);
        p.add(Box.createVerticalStrut(Ui.px(4)));
        p.add(tools);
        return p;
    }

    /** 새 일정을 여는 둥근 + 버튼 */
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
        b.addActionListener(e -> ctx.actions().newSchedule(ctx.state().mode() == Mode.MONTH
                ? ctx.state().selectedDay() : ctx.schedules().today()));
        return b;
    }

    /** 보기 선택 표시를 맞춘다 */
    public void setMode(Mode mode, boolean animate) {
        boolean inSettings = mode == Mode.SETTINGS;
        nav.setSelected(inSettings ? "" : mode.name(), animate);
        settings.setSelected(inSettings ? mode.name() : "", animate);
    }

    /** 메뉴 버튼으로 포커스를 돌려준다 (서랍이 닫힌 뒤) */
    public void focusMenu() {
        menu.requestFocusInWindow();
    }
}
