package proto.ui.view;

import proto.ui.AppState.Mode;
import proto.ui.UiContext;
import proto.ui.UiSettings;
import proto.ui.component.Buttons;
import proto.ui.component.Listeners;
import proto.ui.component.PathLabel;
import proto.ui.layout.SizeClass;
import proto.ui.component.ScrollPanel;
import proto.ui.component.Segmented;
import proto.ui.component.TimeCombo;
import proto.ui.component.ToggleSwitch;
import proto.ui.component.TokenLabel;
import proto.ui.motion.Motion;
import proto.ui.theme.AppIcon;
import proto.ui.theme.Fonts;
import proto.ui.theme.ThemeManager;
import proto.ui.theme.ThemeManager.Choice;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.List;

/**
 * [경계 boundary] 설정 보기: 화면(테마·애니메이션·시작 화면), 알림, 빈 시간 찾기, 데이터 폴더, 앱 정보를 묶음별로 보여 주고 바꾸는 즉시 적용·저장한다.
 */
public class SettingsView implements ContentView {

    private static final int MAX_WIDTH = 720;
    private static final String VERSION = "0.1.0";

    private final UiContext ctx;
    private final JScrollPane root;
    private final Segmented<Choice> theme = new Segmented<>(List.of(Choice.values()), SettingsView::themeLabel);
    private final ToggleSwitch animation = new ToggleSwitch(Motion.enabled(), "애니메이션");
    private final Segmented<Mode> start = new Segmented<>(List.of(Mode.TODAY, Mode.WEEK, Mode.MONTH),
            SettingsView::modeLabel);
    private final ToggleSwitch sound = new ToggleSwitch(UiSettings.reminderSound(), "알림 소리");
    private final TimeCombo from = new TimeCombo(UiSettings.freeFrom()).named("활동 시작 시각");
    private final TimeCombo to = new TimeCombo(UiSettings.freeTo()).named("활동 끝 시각");
    private ScrollPanel body;
    private final TokenLabel hoursHint = new TokenLabel("빈 시간 찾기를 열면 이 시간대가 먼저 채워져요",
            Fonts.regular(Fonts.SMALL), Tokens.MUTED);

    public SettingsView(UiContext ctx) {
        this.ctx = ctx;
        this.root = build();
        wire();
        refresh(false);
    }

    private static String themeLabel(Choice c) {
        return switch (c) {
            case SYSTEM -> "시스템";
            case LIGHT -> "라이트";
            case DARK -> "다크";
        };
    }

    private static String modeLabel(Mode m) {
        return switch (m) {
            case TODAY -> "오늘";
            case WEEK -> "이번 주";
            default -> "달력";
        };
    }

    // ---- 조립 ----

    private JScrollPane build() {
        body = new ScrollPanel(null);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        applySize(ctx.size());
        body.add(section("화면", List.of(
                row("테마", "시스템을 고르면 컴퓨터의 밝기 설정을 따라가요", theme),
                row("애니메이션", "화면 전환과 효과를 부드럽게 보여 줘요", animation),
                row("시작 화면", "앱을 열 때 가장 먼저 보여 줄 화면이에요", start))));
        body.add(section("알림", List.of(
                row("알림 소리", "알림이 뜰 때 소리를 내요", sound),
                row("알림 카드", "닫거나 완료 처리하거나 다시 알림을 고를 때까지 화면에 남아요",
                        info("직접 닫을 때까지")))));
        body.add(section("빈 시간 찾기", List.of(row("기본 활동 시간대", hoursHint, hours()))));
        body.add(section("데이터", List.of(dataRow())));
        body.add(section("정보", List.of(about())));
        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        return scroll;
    }

    /** 좁은 화면에서는 가로 여백을 줄인다 */
    @Override
    public void applySize(SizeClass sc) {
        if (body != null) {
            int g = Ui.px(sc.gutter());
            body.setBorder(BorderFactory.createEmptyBorder(Ui.px(4), g, Ui.px(40 - sc.verticalTrim()), g));
        }
    }

    private JPanel section(String heading, List<JComponent> rows) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setAlignmentX(0f);
        p.setMaximumSize(new Dimension(Ui.px(MAX_WIDTH), Integer.MAX_VALUE));
        TokenLabel title = new TokenLabel(heading, Fonts.semibold(Fonts.LEAD), Tokens.TEXT);
        title.setBorder(BorderFactory.createEmptyBorder(Ui.px(24), 0, Ui.px(6), 0));
        title.setAlignmentX(0f);
        p.add(title);
        rows.forEach(p::add);
        return p;
    }

    private JPanel row(String label, String description, JComponent control) {
        return row(label, new TokenLabel(description, Fonts.regular(Fonts.SMALL), Tokens.MUTED), control);
    }

    private JPanel row(String label, JLabel description, JComponent control) {
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        TokenLabel name = new TokenLabel(label, Fonts.medium(Fonts.BODY), Tokens.TEXT);
        name.setAlignmentX(0f);
        description.setAlignmentX(0f);
        text.add(name);
        text.add(Box.createVerticalStrut(Ui.px(2)));
        text.add(description);
        JPanel right = new JPanel(new GridBagLayout());
        right.setOpaque(false);
        right.add(control);
        JPanel p = new JPanel(new BorderLayout(Ui.px(24), 0));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createCompoundBorder(
                new proto.ui.component.Hairline(),
                BorderFactory.createEmptyBorder(Ui.px(14), 0, Ui.px(14), 0)));
        p.add(text, BorderLayout.CENTER);
        p.add(right, BorderLayout.EAST);
        p.setAlignmentX(0f);
        p.setMaximumSize(new Dimension(Ui.px(MAX_WIDTH), p.getPreferredSize().height));
        return p;
    }

    private static TokenLabel info(String text) {
        return new TokenLabel(text, Fonts.medium(Fonts.SMALL), Tokens.MUTED);
    }

    private JComponent hours() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        p.setOpaque(false);
        from.setPreferredSize(new Dimension(Ui.px(104), Ui.px(36)));
        to.setPreferredSize(new Dimension(Ui.px(104), Ui.px(36)));
        p.add(from);
        p.add(Box.createHorizontalStrut(Ui.px(10)));
        p.add(new TokenLabel("–", Fonts.regular(Fonts.BODY), Tokens.MUTED));
        p.add(Box.createHorizontalStrut(Ui.px(10)));
        p.add(to);
        return p;
    }

    private JPanel dataRow() {
        Path dir = Path.of("data").toAbsolutePath();
        PathLabel path = new PathLabel(dir.toString());
        JButton open = Buttons.secondary("폴더 열기");
        open.setEnabled(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN));
        open.addActionListener(e -> openFolder(dir));
        return row("데이터 폴더", path, open);
    }

    /** 폴더를 연다. 폴더가 없거나 운영체제가 열지 못하면 토스트로 알리고 앱은 그대로 계속 쓴다 */
    private void openFolder(Path dir) {
        try {
            if (!java.nio.file.Files.isDirectory(dir)) {
                ctx.actions().error("데이터 폴더가 아직 없어요");
                return;
            }
            Desktop.getDesktop().open(dir.toFile());
        } catch (IOException | RuntimeException e) {
            ctx.actions().error("폴더를 열 수 없어요");
        }
    }

    private JPanel about() {
        JPanel p = new JPanel(new BorderLayout(Ui.px(16), 0));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(Ui.px(16), 0, 0, 0));
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(new TokenLabel("일정 관리", Fonts.semibold(Fonts.LEAD), Tokens.TEXT));
        text.add(Box.createVerticalStrut(Ui.px(2)));
        text.add(new TokenLabel("버전 " + VERSION, Fonts.regular(Fonts.SMALL), Tokens.MUTED));
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        java.awt.GridBagConstraints at = new java.awt.GridBagConstraints();
        at.weightx = 1;
        at.anchor = java.awt.GridBagConstraints.WEST;
        center.add(text, at);
        javax.swing.Icon icon = AppIcon.icon(64);
        if (icon != null) {
            p.add(new JLabel(icon), BorderLayout.WEST);
        }
        p.add(center, BorderLayout.CENTER);
        p.setAlignmentX(0f);
        p.setMaximumSize(new Dimension(Ui.px(MAX_WIDTH), p.getPreferredSize().height));
        return p;
    }

    // ---- 동작 ----

    private void wire() {
        theme.addChangeListener(ThemeManager::setChoice);
        animation.addChangeListener(Motion::setEnabled);
        start.addChangeListener(UiSettings::setStartMode);
        sound.addChangeListener(UiSettings::setReminderSound);
        for (TimeCombo c : List.of(from, to)) {
            c.addActionListener(e -> commitHours());
            ((javax.swing.JTextField) c.getEditor().getEditorComponent()).addFocusListener(
                    new java.awt.event.FocusAdapter() {
                        @Override
                        public void focusLost(java.awt.event.FocusEvent e) {
                            commitHours();
                        }
                    });
        }
    }

    private void commitHours() {
        LocalTime f = from.time();
        LocalTime t = to.time();
        if (f == null || t == null || !f.isBefore(t)) {
            hoursHint.setText("시작은 끝보다 앞서야 해요 (예: 09:00 – 22:00)");
            hoursHint.setColorToken(Tokens.DANGER);
            return;
        }
        UiSettings.setFreeRange(f, t);
        hoursHint.setText("빈 시간 찾기를 열면 이 시간대가 먼저 채워져요");
        hoursHint.setColorToken(Tokens.MUTED);
    }

    // ---- ContentView ----

    @Override
    public JComponent component() {
        return root;
    }

    @Override
    public String title() {
        return "설정";
    }

    @Override
    public String subtitle() {
        return "화면과 알림을 내 방식대로 맞춰요";
    }

    @Override
    public boolean hasNavigator() {
        return false;
    }

    @Override
    public boolean usesFilters() {
        return false;
    }

    @Override
    public boolean hasSearch() {
        return false;
    }

    @Override
    public void refresh(boolean animate) {
        theme.setValue(ThemeManager.choice(), false);
        start.setValue(UiSettings.startMode(), false);
        animation.setOn(Motion.enabled());
        sound.setOn(UiSettings.reminderSound());
    }
}
