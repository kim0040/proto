package proto.ui.view;

import proto.ui.AppState.Mode;
import proto.ui.UiContext;
import proto.ui.UiSettings;
import proto.ui.component.Buttons;
import proto.ui.component.Hairline;
import proto.ui.component.PathLabel;
import proto.ui.component.Segmented;
import proto.ui.component.SwatchGrid;
import proto.ui.component.TimeCombo;
import proto.ui.component.ToggleSwitch;
import proto.ui.component.TokenLabel;
import proto.ui.motion.Motion;
import proto.ui.theme.Accent;
import proto.ui.theme.AppIcon;
import proto.ui.theme.Fonts;
import proto.ui.theme.ThemeManager;
import proto.ui.theme.ThemeManager.Choice;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

/**
 * [경계 boundary] 설정 내용: 화면(테마·강조색·애니메이션·시작 화면), 알림, 빈 시간 찾기, 데이터 폴더, 앱 정보를 묶음별로 보여 주고 바꾸는 즉시 적용·저장한다.
 * 설정 시트(SettingsSheet)가 열릴 때마다 하나 만들어 담는다. 테마·강조색·애니메이션을 바꾸면 시트 뒤의 화면에 바로 반영된다.
 * 보기가 아니라 지금 화면 위에 뜨는 시트의 내용이라, 스크롤은 시트가 맡는다.
 */
public class SettingsView {

    private static final int MAX_WIDTH = 720;
    private static final int TIME_FIELD_WIDTH = 104;
    private static final int TIME_FIELD_HEIGHT = 36;
    private static final String HOURS_HINT = "빈 시간 찾기를 열면 이 시간대가 먼저 채워져요";
    /** 묶여 나온 jar의 매니페스트(Implementation-Version)에서 읽는다. gradle run·테스트처럼 jar가 아니면 "개발판" */
    private static final String VERSION = versionText();

    private final UiContext ctx;
    private final JPanel root;
    private final Segmented<Choice> theme = new Segmented<>(List.of(Choice.values()), SettingsView::themeLabel);
    private final SwatchGrid accent = new SwatchGrid(accentSwatches(), Accent.values().length, true,
            ThemeManager.accent().key());
    private final ToggleSwitch animation = new ToggleSwitch(Motion.enabled(), "애니메이션");
    private final Segmented<Mode> start = new Segmented<>(List.of(Mode.TODAY, Mode.WEEK, Mode.MONTH),
            SettingsView::modeLabel);
    private final ToggleSwitch sound = new ToggleSwitch(UiSettings.reminderSound(), "알림 소리");
    private final TimeCombo from = new TimeCombo(UiSettings.freeFrom()).named("활동 시작 시각");
    private final TimeCombo to = new TimeCombo(UiSettings.freeTo()).named("활동 끝 시각");
    private final TokenLabel hoursHint = new TokenLabel(HOURS_HINT, Fonts.regular(Fonts.SMALL), Tokens.MUTED);

    public SettingsView(UiContext ctx) {
        this.ctx = ctx;
        this.root = build();
        wire();
        refresh();
    }

    /** 시트에 담을 내용 (스크롤은 시트가 한다) */
    public JComponent component() {
        return root;
    }

    /** 저장된 값으로 스위치·선택을 다시 맞춘다 */
    public void refresh() {
        theme.setValue(ThemeManager.choice(), false);
        accent.setSelected(ThemeManager.accent().key());
        start.setValue(UiSettings.startMode(), false);
        animation.setOn(Motion.enabled());
        sound.setOn(UiSettings.reminderSound());
    }

    /** 강조색 7가지를 지금 테마에 맞는 색으로 보여 주는 견본 목록 */
    private static List<SwatchGrid.Swatch> accentSwatches() {
        return Arrays.stream(Accent.values())
                .map(a -> new SwatchGrid.Swatch(a.key(), a.label(), () -> a.color(ThemeManager.isDark())))
                .toList();
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

    private JPanel build() {
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(section("화면", List.of(
                row("테마", "시스템을 고르면 컴퓨터의 밝기 설정을 따라가요", theme),
                row("강조색", "버튼, 선택 표시, 오늘 표시에 쓰는 색이에요", accent),
                row("애니메이션", "화면 전환과 효과를 부드럽게 보여 줘요", animation),
                row("시작 화면", "앱을 열 때 가장 먼저 보여 줄 화면이에요", start))));
        body.add(section("알림", List.of(
                row("알림 소리", "알림이 뜰 때 소리를 내요", sound),
                row("알림 카드", "닫거나 완료 처리하거나 다시 알림을 고를 때까지 화면에 남아요",
                        info("직접 닫을 때까지")))));
        body.add(section("빈 시간 찾기", List.of(row("기본 활동 시간대", hoursHint, hours()))));
        body.add(section("데이터", List.of(dataRow())));
        body.add(section("정보", List.of(about())));
        return body;
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
                new Hairline(),
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
        from.setPreferredSize(new Dimension(Ui.px(TIME_FIELD_WIDTH), Ui.px(TIME_FIELD_HEIGHT)));
        to.setPreferredSize(new Dimension(Ui.px(TIME_FIELD_WIDTH), Ui.px(TIME_FIELD_HEIGHT)));
        p.add(from);
        p.add(Box.createHorizontalStrut(Ui.px(10)));
        p.add(new TokenLabel("–", Fonts.regular(Fonts.BODY), Tokens.MUTED));
        p.add(Box.createHorizontalStrut(Ui.px(10)));
        p.add(to);
        return p;
    }

    private static String versionText() {
        String version = SettingsView.class.getPackage().getImplementationVersion();
        return version == null || version.isBlank() ? "개발판" : version;
    }

    private JPanel dataRow() {
        Path dir = ctx.dataDir().toAbsolutePath();
        PathLabel path = new PathLabel(dir.toString());
        JButton open = Buttons.secondary("폴더 열기");
        open.setEnabled(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN));
        open.addActionListener(e -> openFolder(dir));
        return row("데이터 폴더", path, open);
    }

    /** 폴더를 연다. 폴더가 없거나 운영체제가 열지 못하면 토스트로 알리고 앱은 그대로 계속 쓴다 */
    private void openFolder(Path dir) {
        try {
            if (!Files.isDirectory(dir)) {
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
        text.add(new TokenLabel(VERSION.equals("개발판") ? "개발판" : "버전 " + VERSION, Fonts.regular(Fonts.SMALL), Tokens.MUTED));
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        GridBagConstraints at = new GridBagConstraints();
        at.weightx = 1;
        at.anchor = GridBagConstraints.WEST;
        center.add(text, at);
        Icon icon = AppIcon.icon(64);
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
        accent.addChangeListener(key -> ThemeManager.setAccent(Accent.of(key), true));
        animation.addChangeListener(Motion::setEnabled);
        start.addChangeListener(UiSettings::setStartMode);
        sound.addChangeListener(UiSettings::setReminderSound);
        for (TimeCombo c : List.of(from, to)) {
            c.addActionListener(e -> commitHours());
            ((JTextField) c.getEditor().getEditorComponent()).addFocusListener(
                    new FocusAdapter() {
                        @Override
                        public void focusLost(FocusEvent e) {
                            commitHours();
                        }
                    });
        }
    }

    private void commitHours() {
        LocalTime windowStart = from.time();
        LocalTime windowEnd = to.time();
        if (windowStart == null || windowEnd == null || !windowStart.isBefore(windowEnd)) {
            hoursHint.setText("시작은 끝보다 앞서야 해요 (예: 09:00 – 22:00)");
            hoursHint.setColorToken(Tokens.DANGER);
            return;
        }
        UiSettings.setFreeRange(windowStart, windowEnd);
        hoursHint.setText(HOURS_HINT);
        hoursHint.setColorToken(Tokens.MUTED);
    }
}
