package proto.ui;

import proto.entity.Category;
import proto.ui.AppState.Mode;
import proto.ui.component.Buttons;
import proto.ui.component.PillList;
import proto.ui.component.ScrollPanel;
import proto.ui.component.TextUtil;
import proto.ui.component.TokenLabel;
import proto.ui.component.TokenPanel;
import proto.ui.theme.AppIcon;
import proto.ui.theme.Fonts;
import proto.ui.theme.Icons;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.time.LocalDate;
import java.util.function.Consumer;

/**
 * [경계 boundary] 왼쪽 사이드바: 앱 이름, 새 일정, 보기 이동, 빈 시간 찾기, 작은 월 달력(SidebarMonth), 카테고리 필터, 설정 열기.
 * MainFrame이 만들고, 넓은 화면에서는 SidebarHost가 본문 왼쪽에 붙이며 좁은 화면에서는 SidebarDrawer 안에 담겨 펼쳐진다.
 * 앱 이름 오른쪽의 버튼(⌘\)은 사이드바를 숨긴다.
 * 버튼은 직접 일하지 않고 생성자로 받은 동작(onNav, onCategory, onDate, onHide)이나 UiActions에 맡긴다 (F1 새 일정, F7 카테고리 필터·편집, F12 빈 시간 찾기).
 * 선택된 항목은 옆 막대 대신 둥근 알약이 미끄러지며 표시한다. 세로 공간이 모자라 카테고리 목록이 눌리면 달력을 숨긴다.
 */
public final class Sidebar extends TokenPanel {

    private static final String ALL = "all";
    private static final String NONE = "";
    /** 달력을 보여 주는 사이드바 최소 높이 (논리 px). 이보다 낮으면 카테고리 목록이 눌린다 */
    private static final int MONTH_MIN_HEIGHT = 760;

    private final UiContext ctx;
    private final Consumer<Mode> onNav;
    private final Consumer<String> onCategory;
    private final Consumer<LocalDate> onDate;
    private final Runnable beforeAction;
    private final Runnable onHide;
    private final SidebarMonth month;
    private final PillList nav = new PillList();
    private final PillList categories = new PillList();
    private final PillList settings = new PillList();

    /**
     * beforeAction은 새 일정·빈 시간·카테고리 편집 같은 동작 직전에 불린다 (서랍으로 열려 있으면 닫으려고).
     * onHide는 사이드바 숨기기 버튼을 눌렀을 때 불린다
     */
    public Sidebar(UiContext ctx, Consumer<Mode> onNav, Consumer<String> onCategory, Consumer<LocalDate> onDate,
                   Runnable beforeAction, Runnable onHide) {
        super(new BorderLayout(), Tokens.SIDEBAR);
        this.ctx = ctx;
        this.onNav = onNav;
        this.onCategory = onCategory;
        this.onDate = onDate;
        this.beforeAction = beforeAction;
        this.onHide = onHide;
        this.month = new SidebarMonth(ctx, day -> {
            beforeAction.run();
            onDate.accept(day);
        });
        setLogicalWidth(232);
        setBorder(BorderFactory.createEmptyBorder(Ui.px(Ui.isMac() ? 44 : 22), Ui.px(14), Ui.px(14), Ui.px(14)));
        add(top(), BorderLayout.NORTH);
        add(categorySection(), BorderLayout.CENTER);
        add(bottom(), BorderLayout.SOUTH);
        buildNav();
        refreshCategories();
        buildSettings();
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateMonthVisibility();
            }
        });
    }

    /** 일정이 바뀌었을 때: 달력의 점을 다시 읽는다 */
    public void refreshMonth() {
        month.refreshMarks();
    }

    /** 화면(보기·날짜)이 바뀌었을 때: 달력의 오늘·고른 날·주 띠를 맞춘다 */
    public void syncMonth() {
        month.sync();
    }

    /** 사이드바 너비를 논리 px로 정한다 */
    public void setLogicalWidth(int logicalPx) {
        setPreferredSize(new Dimension(Ui.px(logicalPx), 10));
    }

    /** 카테고리 목록을 controller에서 다시 읽어 만든다 */
    public void refreshCategories() {
        String selected = ctx.state().categoryId();
        categories.clear();
        categories.addItem(ALL, "tag", null, "전체", () -> onCategory.accept(null));
        for (Category c : ctx.categories().getCategories()) {
            String id = c.getId();
            categories.addItem(id, null, id, TextUtil.clean(c.getName()), () -> onCategory.accept(id));
        }
        categories.setSelected(selected == null ? ALL : selected, false);
        categories.revalidate();
        categories.repaint();
    }

    /** 보기 선택 표시를 맞춘다 */
    public void setMode(Mode mode, boolean animate) {
        nav.setSelected(mode.name(), animate);
    }

    /** 카테고리 선택 표시를 맞춘다 */
    public void setCategory(String id, boolean animate) {
        categories.setSelected(id == null ? ALL : id, animate);
    }

    /** 달력이 자리를 잡아도 카테고리 목록이 눌리지 않을 만큼 세로로 클 때만 보인다 */
    private void updateMonthVisibility() {
        boolean fits = getHeight() >= Ui.px(MONTH_MIN_HEIGHT);
        if (fits != month.isVisible()) {
            month.setVisible(fits);
            revalidate();
        }
    }

    /** "새 일정" 버튼. 달력 보기에서는 고른 날짜로, 다른 보기에서는 오늘 날짜로 새 일정 창을 연다. */
    private JButton newButton() {
        JButton add = Buttons.primary("새 일정");
        add.setIcon(Icons.of("plus", 16, Tokens.ON_ACCENT));
        add.setIconTextGap(8);
        add.setAlignmentX(0f);
        add.setMaximumSize(new Dimension(Integer.MAX_VALUE, Ui.px(38)));
        add.getAccessibleContext().setAccessibleName("새 일정");
        add.addActionListener(e -> {
            beforeAction.run();
            ctx.actions().newScheduleHere();
        });
        return add;
    }

    private JPanel top() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JPanel brand = brandRow();
        JButton add = newButton();
        nav.setAlignmentX(0f);
        month.setAlignmentX(0f);
        month.setBorder(BorderFactory.createEmptyBorder(Ui.px(14), Ui.px(2), 0, Ui.px(2)));
        PillList tools = new PillList();
        tools.addItem("free", "hourglass", null, "빈 시간 찾기", () -> {
            beforeAction.run();
            ctx.actions().openFreeSlots();
        });
        tools.setAlignmentX(0f);
        p.add(brand);
        p.add(add);
        p.add(Box.createVerticalStrut(Ui.px(16)));
        p.add(nav);
        p.add(Box.createVerticalStrut(Ui.px(4)));
        p.add(tools);
        p.add(month);
        return p;
    }

    /** 앱 아이콘과 이름, 그 오른쪽 끝의 사이드바 숨기기 버튼 */
    private JPanel brandRow() {
        TokenLabel name = new TokenLabel("일정 관리", Fonts.bold(Fonts.TITLE), Tokens.TEXT);
        name.setBorder(BorderFactory.createEmptyBorder(0, Ui.px(8), 0, 0));
        name.setIcon(AppIcon.icon(26));
        name.setIconTextGap(Ui.px(10));
        JButton hide = Buttons.icon("panel-left", "사이드바 숨기기 (" + Ui.shortcut("\\") + ")");
        hide.addActionListener(e -> onHide.run());
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(0, 0, Ui.px(18), 0));
        row.add(name, BorderLayout.CENTER);
        row.add(hide, BorderLayout.EAST);
        row.setAlignmentX(0f);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    private void buildNav() {
        nav.addItem(Mode.TODAY.name(), "calendar-check", null, "오늘", () -> onNav.accept(Mode.TODAY));
        nav.addItem(Mode.WEEK.name(), "calendar-range", null, "이번 주", () -> onNav.accept(Mode.WEEK));
        nav.addItem(Mode.MONTH.name(), "calendar-days", null, "달력", () -> onNav.accept(Mode.MONTH));
        nav.addItem(Mode.STATS.name(), "chart", null, "주간 통계", () -> onNav.accept(Mode.STATS));
        nav.setSelected(Mode.TODAY.name(), false);
    }

    private JPanel categorySection() {
        JPanel p = new JPanel(new BorderLayout(0, Ui.px(4)));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(Ui.px(18), 0, 0, 0));
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setBorder(BorderFactory.createEmptyBorder(0, Ui.px(8), 0, 0));
        head.add(new TokenLabel("카테고리", Fonts.semibold(Fonts.CAPTION), Tokens.MUTED), BorderLayout.WEST);
        JButton edit = Buttons.ghost("편집");
        edit.setFont(Fonts.medium(Fonts.CAPTION));
        edit.getAccessibleContext().setAccessibleName("카테고리 편집");
        edit.addActionListener(e -> {
            beforeAction.run();
            ctx.actions().openCategories();
        });
        head.add(edit, BorderLayout.EAST);
        ScrollPanel holder = new ScrollPanel(new BorderLayout());
        holder.add(categories, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(holder);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        p.add(head, BorderLayout.NORTH);
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    private JPanel bottom() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(Ui.px(8), 0, 0, 0));
        p.add(settings, BorderLayout.CENTER);
        return p;
    }

    /** "설정"은 보기 이동이 아니라 지금 화면 위에 설정 시트를 연다 (그래서 선택 표시가 남지 않는다) */
    private void buildSettings() {
        settings.addItem("settings", "settings", null, "설정", () -> {
            beforeAction.run();
            ctx.actions().openSettings();
        });
        settings.setSelected(NONE, false);
    }
}
