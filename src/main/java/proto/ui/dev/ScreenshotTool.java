package proto.ui.dev;

import proto.control.CategoryController;
import proto.control.ReminderTimer;
import proto.control.ScheduleController;
import proto.control.ScheduleDraft;
import proto.entity.Occurrence;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.ui.AppState.Mode;
import proto.ui.MainFrame;
import proto.ui.UiContext;
import proto.ui.UiLauncher;
import proto.ui.component.AllDayRow;
import proto.ui.component.ReminderCard;
import proto.ui.component.TimeCombo;
import proto.ui.dialog.BaseDialog;
import proto.ui.dialog.CategoryDialog;
import proto.ui.dialog.ConfirmDialog;
import proto.ui.dialog.Dialogs;
import proto.ui.dialog.FreeSlotDialog;
import proto.ui.dialog.ScheduleDialog;
import proto.ui.dialog.SettingsSheet;
import proto.ui.motion.Motion;
import proto.ui.theme.ThemeManager;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * [경계 boundary] 개발용 화면 캡처 도구. 화면을 2배 크기 PNG로 그려 저장한다 (OS 화면 캡처 권한 필요 없음).
 * 개발할 때만 proto.app.dev.ScreenshotMain이 부른다 (앱 실행에는 쓰이지 않는다). 가짜 데이터가 든 controller로 진짜 MainFrame을 띄우고 화면을 PNG로 그려
 * 저장한다.
 * Motion을 임시로 꺼서 캡처 중에 애니메이션이 섞이지 않게 하고, 설정 파일에는 저장하지 않는다.
 * 여러 창 크기(1440×900, 1100×720, 900×640, 760×540, 세로 800×1000)로 메인 보기 4개를 밝은/어두운 테마로 찍고,
 * 서랍·주간 아젠다·긴 글/이모지·시간 입력 오류·알림 더미 같은 경계 장면과, 메인 창 위에 뜬 시트(일정·빈 시간·카테고리·확인·설정)도 찍는다.
 */
public final class ScreenshotTool {

    private static final int SCALE = 2;
    private static final int[][] SIZES = {{1440, 900}, {1100, 720}, {900, 640}, {760, 540}, {800, 1000}};
    private static final String[] SIZE_NAMES = {"1440", "1100", "900", "760", "800p"};

    private ScreenshotTool() {
    }

    /** 이미 만들어진 controller로 모든 화면을 outDir에 PNG로 저장한다. EDT가 아닌 스레드에서 부른다 */
    public static void capture(ScheduleController schedules, CategoryController categories,
                               ReminderTimer reminders, Path outDir) throws IOException, InterruptedException,
            InvocationTargetException {
        Files.createDirectories(outDir);
        boolean motionBefore = Motion.enabled();
        MainFrame[] holder = new MainFrame[1];
        try {
            Motion.setEnabledTransient(false);
            SwingUtilities.invokeAndWait(() -> {
                holder[0] = UiLauncher.create(schedules, categories, reminders);
                holder[0].attachListeners();
                holder[0].setVisible(true);
            });
            seedAllDay(holder[0]);
            shootAll(holder[0], outDir);
        } finally {
            Motion.setEnabledTransient(motionBefore);
            if (holder[0] != null) {
                run(holder[0]::dispose);
            }
        }
    }

    private static void shootAll(MainFrame frame, Path out) throws IOException, InterruptedException,
            InvocationTargetException {
        for (boolean dark : new boolean[] {false, true}) {
            String theme = dark ? "dark" : "light";
            run(() -> ThemeManager.apply(dark, false, false));
            for (int i = 0; i < SIZES.length; i++) {
                captureMain(frame, out, theme, SIZES[i][0], SIZES[i][1], SIZE_NAMES[i]);
            }
            captureAllDayScenes(frame, out, theme);
            captureSheets(frame, out, theme);
            captureSettingsSheets(frame, out, theme);
            captureEdgeScenes(frame, out, theme);
        }
        seedLongText(frame);
        for (boolean dark : new boolean[] {false, true}) {
            run(() -> ThemeManager.apply(dark, false, false));
            captureLongText(frame, out, dark ? "dark" : "light");
        }
    }

    /** 창 크기를 바꾸고, 운영체제가 실제로 그 크기를 적용해 레이아웃이 끝날 때까지 기다린 뒤 크기 등급을 다시 읽는다 */
    private static void resize(MainFrame frame, int w, int h) throws InterruptedException, InvocationTargetException {
        run(() -> frame.setSize(w, h));
        for (int i = 0; i < 80 && !sized(frame, w); i++) {
            Thread.sleep(25);
            run(() -> { });
        }
        run(() -> {
            frame.getRootPane().validate();
            frame.context().breakpoints().update();
        });
        run(() -> { });
        run(() -> { });
    }

    private static boolean sized(MainFrame frame, int w) throws InterruptedException, InvocationTargetException {
        boolean[] ok = new boolean[1];
        SwingUtilities.invokeAndWait(() -> ok[0] = frame.getWidth() == w && frame.getRootPane().getWidth() > 0
                && Math.abs(frame.getRootPane().getWidth() - (w - frame.getInsets().left - frame.getInsets().right)) <= 1
                && frame.getRootPane().getLayeredPane().getWidth() == frame.getRootPane().getWidth());
        return ok[0];
    }

    private static void captureMain(MainFrame frame, Path out, String theme, int w, int h, String sizeName)
            throws InterruptedException, InvocationTargetException, IOException {
        resize(frame, w, h);
        for (Mode mode : Mode.values()) {
            run(() -> frame.showMode(mode));
            run(() -> { });
            save(frame.getRootPane(), out.resolve(theme + "-" + mode.name().toLowerCase() + "-" + sizeName + ".png"));
        }
        run(() -> frame.showMode(Mode.TODAY));
    }

    /** 설정 시트: 위쪽, 맨 아래(데이터·정보)로 내린 모습, 좁은 창(760×540) */
    private static void captureSettingsSheets(MainFrame frame, Path out, String theme)
            throws InterruptedException, InvocationTargetException, IOException {
        UiContext ctx = frame.context();
        resize(frame, 1200, 780);
        run(() -> frame.showMode(Mode.WEEK));
        shoot(frame, () -> new SettingsSheet(ctx), out, theme + "-sheet-settings");
        shoot(frame, () -> scrolledToBottom(new SettingsSheet(ctx)), out, theme + "-sheet-settings-bottom");
        resize(frame, 760, 540);
        shoot(frame, () -> new SettingsSheet(ctx), out, theme + "-sheet-settings-760");
        run(() -> frame.showMode(Mode.TODAY));
    }

    private static BaseDialog scrolledToBottom(BaseDialog d) {
        d.open();
        d.getParent().validate();
        javax.swing.JScrollBar bar = d.scrollPane().getVerticalScrollBar();
        bar.setValue(bar.getMaximum());
        return d;
    }

    /** 일정·빈 시간·카테고리 시트를 메인 창(오늘 보기) 위에 띄워 찍는다. 확인 시트는 일정 시트 위에 겹쳐 뜬 모습으로 찍는다 */
    private static void captureSheets(MainFrame frame, Path out, String theme)
            throws InterruptedException, InvocationTargetException, IOException {
        resize(frame, 1200, 780);
        run(() -> frame.showMode(Mode.TODAY));
        UiContext ctx = frame.context();
        LocalDate today = ctx.schedules().today();
        Occurrence any = firstOccurrence(ctx, today);
        shoot(frame, () -> new ScheduleDialog(ctx, null, ctx.schedules().newDraft(today)), out, theme + "-sheet-new");
        if (any != null) {
            shoot(frame, () -> new ScheduleDialog(ctx, any, ctx.schedules().draftOf(any)), out, theme + "-sheet-edit");
            shootStacked(frame, () -> new ScheduleDialog(ctx, any, ctx.schedules().draftOf(any)),
                    ScreenshotTool::scopeDialog, out, theme + "-sheet-scope");
        }
        shootStacked(frame, () -> dirtyNew(ctx, today), Dialogs::discardPrompt, out, theme + "-sheet-discard");
        shoot(frame, () -> invalidDialog(ctx, today, null), out, theme + "-sheet-errors");
        shoot(frame, () -> invalidDialog(ctx, today, "25:00"), out, theme + "-sheet-time-error");
        shoot(frame, () -> new FreeSlotDialog(ctx), out, theme + "-sheet-freeslots");
        shoot(frame, () -> new CategoryDialog(ctx), out, theme + "-sheet-categories");
        resize(frame, 760, 540);
        shoot(frame, () -> new ScheduleDialog(ctx, null, ctx.schedules().newDraft(today)), out, theme + "-sheet-new-760");
    }

    /** 제목을 써서 "바꾼 것이 있는" 새 일정 시트 */
    private static ScheduleDialog dirtyNew(UiContext ctx, LocalDate today) {
        ScheduleDialog d = new ScheduleDialog(ctx, null, ctx.schedules().newDraft(today));
        find(d, javax.swing.JTextField.class).setText("팀 회의 준비");
        return d;
    }

    private static ConfirmDialog scopeDialog(java.awt.Component owner) {
        return new ConfirmDialog(owner, "반복 일정 삭제", "반복 일정을 삭제할까요?",
                "‘이 일정만’은 이 날짜 하나만, ‘앞으로 전부’는 이 날짜부터 뒤의 모든 반복에 적용돼요.", null,
                List.of(new ConfirmDialog.Choice("취소", ConfirmDialog.Kind.GHOST),
                        new ConfirmDialog.Choice("이 일정만", ConfirmDialog.Kind.SECONDARY),
                        new ConfirmDialog.Choice("앞으로 전부", ConfirmDialog.Kind.DANGER)));
    }

    /** 저장을 눌러 오류가 보이는 새 일정 창. startText가 있으면 시작 시간 칸에 그 글을 먼저 넣는다 */
    private static BaseDialog invalidDialog(UiContext ctx, LocalDate today, String startText) {
        ScheduleDialog d = new ScheduleDialog(ctx, null, ctx.schedules().newDraft(today));
        if (startText != null) {
            find(d, TimeCombo.class).setSelectedItem(startText);
        }
        clickButton(d, "저장");
        return d;
    }

    /** root 안에서 type인 첫 컴포넌트 (화면 순서대로) */
    private static <T extends Component> T find(Container root, Class<T> type) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c)) {
                return type.cast(c);
            }
            if (c instanceof Container inner) {
                T found = find(inner, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static Occurrence firstOccurrence(UiContext ctx, LocalDate day) {
        List<Occurrence> list = ctx.schedules().getOccurrences(day, day, null, null);
        return list.isEmpty() ? null : list.get(0);
    }

    /** 서랍, 주간 아젠다, 알림 더미 같은 크기 등급에 따라 달라지는 장면 */
    private static void captureEdgeScenes(MainFrame frame, Path out, String theme)
            throws InterruptedException, InvocationTargetException, IOException {
        UiContext ctx = frame.context();
        resize(frame, 900, 640);
        run(() -> frame.showMode(Mode.TODAY));
        run(frame::openDrawerForCapture);
        run(() -> { });
        save(frame.getRootPane(), out.resolve(theme + "-drawer-900.png"));
        run(frame::closeDrawerForCapture);
        run(() -> frame.showMode(Mode.WEEK));
        save(frame.getRootPane(), out.resolve(theme + "-week-agenda-900.png"));
        Occurrence o = firstOccurrence(ctx, ctx.schedules().today());
        if (o != null) {
            captureReminders(frame, out, theme, o);
        }
        run(() -> frame.showMode(Mode.TODAY));
    }

    /** 알림 카드 5장: 최근 3장만 보이고 "+2개 더"로 줄어 있는 모습, 그리고 펼친 모습 (960×640) */
    private static void captureReminders(MainFrame frame, Path out, String theme, Occurrence o)
            throws InterruptedException, InvocationTargetException, IOException {
        UiContext ctx = frame.context();
        resize(frame, 960, 640);
        run(() -> {
            frame.showMode(Mode.TODAY);
            for (int i = 0; i < 5; i++) {
                frame.overlay().addReminder(o, i % 2 == 1, ctx.categories().nameOf(o.categoryId()), noopActions());
            }
            ctx.actions().toast("일정을 저장했어요");
        });
        run(() -> { });
        save(frame.getRootPane(), out.resolve(theme + "-reminder-stack-960.png"));
        run(() -> clickButton(frame.overlay(), "+2개 더"));
        run(() -> { });
        save(frame.getRootPane(), out.resolve(theme + "-reminder-stack-expanded-960.png"));
        run(() -> frame.overlay().clearAll());
    }

    private static ReminderCard.Actions noopActions() {
        return new ReminderCard.Actions() {
            @Override
            public boolean snooze(Occurrence occurrence) {
                return true;
            }

            @Override
            public boolean complete(Occurrence occurrence) {
                return true;
            }
        };
    }

    // ---- 하루 종일 ----

    /** 하루 종일 예시: 오늘 1개, 금요일 4개(펼침·"+N" 확인), 반복(매주 월요일) 1개, 다음 주 월요일 1개 */
    private static void seedAllDay(MainFrame frame) throws InterruptedException, InvocationTargetException {
        UiContext ctx = frame.context();
        run(() -> {
            LocalDate today = ctx.schedules().today();
            allDay(ctx, "팀플 중간 발표", today, "team", RepeatRule.NONE);
            allDay(ctx, "학과 행사", today.plusDays(1), "class", RepeatRule.NONE);
            allDay(ctx, "🎂 동생 생일", today.plusDays(1), "personal", RepeatRule.NONE);
            allDay(ctx, "공모전 마감", today.plusDays(1), "assignment", RepeatRule.NONE);
            allDay(ctx, "도서관 휴관", today.plusDays(1), "personal", RepeatRule.NONE);
            allDay(ctx, "근로 장학 신청", today.minusDays(3), "assignment", RepeatRule.WEEKLY);
            allDay(ctx, "동아리 MT", today.plusDays(4), "team", RepeatRule.NONE);
        });
    }

    /** 하루 종일 줄을 펼친 주 보기(1440, 900), 달력과 오늘 보기의 하루 종일 표시(1100) */
    private static void captureAllDayScenes(MainFrame frame, Path out, String theme)
            throws InterruptedException, InvocationTargetException, IOException {
        resize(frame, 1440, 900);
        run(() -> frame.showMode(Mode.WEEK));
        AllDayRow row = find(frame.getRootPane(), AllDayRow.class);
        run(() -> row.setExpanded(true));
        run(() -> frame.getRootPane().validate());
        save(frame.getRootPane(), out.resolve(theme + "-week-allday-expanded-1440.png"));
        run(() -> row.setExpanded(false));
        resize(frame, 1100, 720);
        run(() -> frame.showMode(Mode.TODAY));
        run(() -> frame.showMode(Mode.TODAY));
        save(frame.getRootPane(), out.resolve(theme + "-today-allday-1100.png"));
    }

    private static void allDay(UiContext ctx, String title, LocalDate d, String cat, RepeatRule repeat) {
        var r = ctx.schedules().create(new ScheduleDraft(title, d, LocalTime.of(0, 0), LocalTime.of(23, 59), cat, "",
                repeat, null, ReminderOption.NONE, true));
        if (!r.success()) {
            throw new IllegalStateException("하루 종일 예시 일정을 만들 수 없어요: " + r.message());
        }
    }

    // ---- 긴 글·이모지 ----

    /** 매우 긴 제목·메모·카테고리 이름, 이모지, 앞뒤 공백, 한/영/숫자 섞인 제목, 한 날 30개 일정을 controller로 만든다 */
    private static void seedLongText(MainFrame frame) throws InterruptedException, InvocationTargetException {
        UiContext ctx = frame.context();
        run(() -> {
            ctx.categories().add("아주 아주 긴 카테고리 이름 Long Category Name 12345");
            LocalDate today = ctx.schedules().today();
            String cat = ctx.categories().getCategories().get(ctx.categories().getCategories().size() - 1).getId();
            add(ctx, "  🎉 팀 회식 😀 예약 – Team Dinner Reservation ✅  ", today, 18, 0, 19, 0, cat,
                    "메모가 매우 길어요 ".repeat(12) + "\n둘째 줄 메모");
            add(ctx, "매우 긴 제목입니다 ".repeat(4), today, 8, 0, 8, 30, "personal", "");
            for (int i = 0; i < 30; i++) {
                add(ctx, "자잘한 일정 " + (i + 1), today.plusDays(2), 6 + i / 2, (i % 2) * 30, 6 + i / 2,
                        (i % 2) * 30 + 25, "class", "");
            }
        });
    }

    private static void add(UiContext ctx, String title, LocalDate d, int sh, int sm, int eh, int em, String cat,
                            String memo) {
        var r = ctx.schedules().create(new ScheduleDraft(title, d, LocalTime.of(sh, sm), LocalTime.of(eh, em), cat, memo,
                RepeatRule.NONE, null, ReminderOption.NONE));
        if (!r.success()) {
            throw new IllegalStateException("예시 일정을 만들 수 없어요: " + r.message());
        }
    }

    private static void captureLongText(MainFrame frame, Path out, String theme)
            throws InterruptedException, InvocationTargetException, IOException {
        for (int[] size : new int[][] {{1100, 720}, {760, 540}}) {
            resize(frame, size[0], size[1]);
            run(() -> frame.showMode(Mode.TODAY));
            save(frame.getRootPane(), out.resolve(theme + "-longtext-today-" + size[0] + ".png"));
        }
        resize(frame, 900, 640);
        run(() -> frame.showMode(Mode.WEEK));
        save(frame.getRootPane(), out.resolve(theme + "-longtext-week-900.png"));
        resize(frame, 1100, 720);
        run(() -> frame.showMode(Mode.WEEK));
        save(frame.getRootPane(), out.resolve(theme + "-longtext-week-1100.png"));
        run(() -> {
            frame.context().state().setSelectedDay(frame.context().schedules().today().plusDays(2));
            frame.showMode(Mode.MONTH);
        });
        save(frame.getRootPane(), out.resolve(theme + "-longtext-month-1100.png"));
        run(() -> frame.showMode(Mode.TODAY));
    }

    // ---- 그리기 도우미 ----

    /** 시트를 메인 창 위에 (기다리지 않고) 띄워 창 전체를 찍고 닫는다. 애니메이션은 꺼져 있어 바로 제자리에 보인다 */
    private static void shoot(MainFrame frame, Supplier<BaseDialog> factory, Path out, String name)
            throws InterruptedException, InvocationTargetException, IOException {
        BaseDialog[] d = new BaseDialog[1];
        run(() -> {
            d[0] = factory.get();
            d[0].open();
        });
        try {
            save(frame.getRootPane(), out.resolve(name + ".png"));
        } finally {
            run(d[0]::cancel);
        }
    }

    /** 아래 시트 위에 확인 시트를 하나 더 쌓아 찍는다 (시트에서 연 확인 창의 모습) */
    private static void shootStacked(MainFrame frame, Supplier<BaseDialog> below,
                                     java.util.function.Function<java.awt.Component, BaseDialog> above, Path out,
                                     String name) throws InterruptedException, InvocationTargetException, IOException {
        BaseDialog[] d = new BaseDialog[2];
        run(() -> {
            d[0] = below.get();
            d[0].open();
            d[1] = above.apply(d[0]);
            d[1].open();
        });
        try {
            save(frame.getRootPane(), out.resolve(name + ".png"));
        } finally {
            run(() -> {
                d[1].cancel();
                d[0].cancel();
            });
        }
    }

    private static void clickButton(Container root, String text) {
        for (Component c : new ArrayList<>(List.of(root.getComponents()))) {
            if (c instanceof AbstractButton b && text.equals(b.getText())) {
                b.doClick();
                return;
            }
            if (c instanceof Container inner) {
                clickButton(inner, text);
            }
        }
    }

    private static void run(Runnable r) throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(r);
        SwingUtilities.invokeAndWait(() -> { });
    }

    private static void save(JComponent c, Path file) throws IOException, InterruptedException,
            InvocationTargetException {
        BufferedImage[] img = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> img[0] = render(c));
        ImageIO.write(img[0], "png", file.toFile());
    }

    private static BufferedImage render(JComponent c) {
        Dimension d = c.getSize();
        c.doLayout();
        BufferedImage img = new BufferedImage(d.width * SCALE, d.height * SCALE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.scale(SCALE, SCALE);
        c.printAll(g);
        g.dispose();
        return img;
    }
}
