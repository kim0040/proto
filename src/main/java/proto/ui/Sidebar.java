package proto.ui;

import proto.entity.Category;
import proto.ui.AppState.Mode;
import proto.ui.component.Buttons;
import proto.ui.component.PillList;
import proto.ui.component.ScrollPanel;
import proto.ui.component.TokenLabel;
import proto.ui.component.TokenPanel;
import proto.ui.motion.Motion;
import proto.ui.theme.Fonts;
import proto.ui.theme.Icons;
import proto.ui.theme.ThemeManager;
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
import java.util.function.Consumer;

/**
 * [경계 boundary] 왼쪽 사이드바: 앱 이름, 새 일정, 보기 이동, 빈 시간 찾기, 카테고리 필터, 테마·애니메이션 설정.
 * 선택된 항목은 옆 막대 대신 둥근 알약이 미끄러지며 표시한다.
 */
public final class Sidebar extends TokenPanel {

    private static final String ALL = "all";

    private final UiContext ctx;
    private final Consumer<Mode> onNav;
    private final Consumer<String> onCategory;
    private final PillList nav = new PillList();
    private final PillList categories = new PillList();
    private final PillList settings = new PillList();

    public Sidebar(UiContext ctx, Consumer<Mode> onNav, Consumer<String> onCategory) {
        super(new BorderLayout(), Tokens.SIDEBAR);
        this.ctx = ctx;
        this.onNav = onNav;
        this.onCategory = onCategory;
        setPreferredSize(new Dimension(Ui.px(232), 10));
        setBorder(BorderFactory.createEmptyBorder(Ui.px(Ui.isMac() ? 44 : 22), Ui.px(14), Ui.px(14), Ui.px(14)));
        add(top(), BorderLayout.NORTH);
        add(categorySection(), BorderLayout.CENTER);
        add(bottom(), BorderLayout.SOUTH);
        buildNav();
        refreshCategories();
        rebuildSettings();
        Motion.onToggle(this::rebuildSettings);
    }

    private JPanel top() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        TokenLabel brand = new TokenLabel("일정 관리", Fonts.bold(Fonts.TITLE), Tokens.TEXT);
        brand.setBorder(BorderFactory.createEmptyBorder(0, Ui.px(8), Ui.px(18), 0));
        brand.setAlignmentX(0f);
        JButton add = Buttons.primary("새 일정");
        add.setIcon(Icons.of("plus", 16, Tokens.ON_ACCENT));
        add.setIconTextGap(8);
        add.setAlignmentX(0f);
        add.setMaximumSize(new Dimension(Integer.MAX_VALUE, Ui.px(38)));
        add.addActionListener(e -> ctx.actions().newSchedule(ctx.state().mode() == Mode.MONTH
                ? ctx.state().selectedDay() : ctx.schedules().today()));
        nav.setAlignmentX(0f);
        PillList tools = new PillList();
        tools.addItem("free", "hourglass", null, "빈 시간 찾기", () -> ctx.actions().openFreeSlots());
        tools.setAlignmentX(0f);
        p.add(brand);
        p.add(add);
        p.add(Box.createVerticalStrut(Ui.px(16)));
        p.add(nav);
        p.add(Box.createVerticalStrut(Ui.px(4)));
        p.add(tools);
        return p;
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
        edit.addActionListener(e -> ctx.actions().openCategories());
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

    /** 카테고리 목록을 controller에서 다시 읽어 만든다 */
    public void refreshCategories() {
        String selected = ctx.state().categoryId();
        categories.clear();
        categories.addItem(ALL, "tag", null, "전체", () -> onCategory.accept(null));
        for (Category c : ctx.categories().getCategories()) {
            String id = c.getId();
            categories.addItem(id, null, id, c.getName(), () -> onCategory.accept(id));
        }
        categories.setSelected(selected == null ? ALL : selected, false);
        categories.revalidate();
        categories.repaint();
    }

    private void rebuildSettings() {
        settings.clear();
        boolean dark = ThemeManager.isDark();
        settings.addItem("theme", dark ? "sun" : "moon", null, dark ? "밝은 테마" : "어두운 테마", () -> {
            ThemeManager.toggle();
            rebuildSettings();
        });
        settings.addItem("anim", "zap", null, Motion.enabled() ? "애니메이션 켜짐" : "애니메이션 꺼짐",
                () -> Motion.setEnabled(!Motion.enabled()));
        settings.revalidate();
        settings.repaint();
    }

    /** 보기 선택 표시를 맞춘다 */
    public void setMode(Mode mode, boolean animate) {
        nav.setSelected(mode.name(), animate);
    }

    /** 카테고리 선택 표시를 맞춘다 */
    public void setCategory(String id, boolean animate) {
        categories.setSelected(id == null ? ALL : id, animate);
    }
}
