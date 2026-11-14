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
import proto.ui.component.ReminderCard;
import proto.ui.component.TimeCombo;
import proto.ui.dialog.CategoryDialog;
import proto.ui.dialog.ConfirmDialog;
import proto.ui.dialog.FreeSlotDialog;
import proto.ui.dialog.ScheduleDialog;
import proto.ui.motion.Motion;
import proto.ui.theme.ThemeManager;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
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
 * 여러 창 크기(1440×900, 1100×720, 900×640, 760×540, 세로 800×1000)로 메인 보기 5개를 밝은/어두운 테마로 찍고,
 * 서랍·주간 아젠다·긴 글/이모지·시간 입력 오류·알림 더미 같은 경계 장면과 대화상자도 찍는다.
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
            captureSettingsBottom(frame, out, theme);
            captureDialogs(frame, out, theme);
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

    /** 설정 보기는 길어서 맨 아래(데이터·정보)로 스크롤한 장면을 한 장 더 찍는다 */
    private static void captureSettingsBottom(MainFrame frame, Path out, String theme)
            throws InterruptedException, InvocationTargetException, IOException {
        resize(frame, 1200, 780);
        run(() -> frame.showMode(Mode.SETTINGS));
        run(() -> {
            javax.swing.JScrollBar bar = ((javax.swing.JScrollPane) frame.currentView().component())
                    .getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
        run(() -> { });
        save(frame.getRootPane(), out.resolve(theme + "-settings-bottom.png"));
        run(() -> frame.showMode(Mode.TODAY));
    }

    private static void captureDialogs(MainFrame frame, Path out, String theme)
            throws InterruptedException, InvocationTargetException, IOException {
        resize(frame, 1200, 780);
        UiContext ctx = frame.context();
        LocalDate today = ctx.schedules().today();
        Occurrence any = firstOccurrence(ctx, today);
        shoot(() -> new ScheduleDialog(ctx, null, ctx.schedules().newDraft(today)), out, theme + "-dialog-new");
        if (any != null) {
            shoot(() -> new ScheduleDialog(ctx, any, ctx.schedules().draftOf(any)), out, theme + "-dialog-edit");
        }
        shoot(() -> invalidDialog(ctx, today, null), out, theme + "-dialog-errors");
        shoot(() -> invalidDialog(ctx, today, "25:00"), out, theme + "-dialog-time-error");
        shoot(() -> new FreeSlotDialog(ctx), out, theme + "-dialog-freeslots");
        shoot(() -> new CategoryDialog(ctx), out, theme + "-dialog-categories");
        shoot(() -> scopeDialog(frame), out, theme + "-dialog-scope");
    }

    private static JDialog scopeDialog(MainFrame frame) {
        return new ConfirmDialog(frame, "반복 일정 삭제", "반복 일정을 삭제할까요?",
                "‘이 일정만’은 이 날짜 하나만, ‘앞으로 전부’는 이 날짜부터 뒤의 모든 반복에 적용돼요.", null,
                List.of(new ConfirmDialog.Choice("취소", ConfirmDialog.Kind.GHOST),
                        new ConfirmDialog.Choice("이 일정만", ConfirmDialog.Kind.SECONDARY),
                        new ConfirmDialog.Choice("앞으로 전부", ConfirmDialog.Kind.DANGER)));
    }

    /** 저장을 눌러 오류가 보이는 새 일정 창. startText가 있으면 시작 시간 칸에 그 글을 먼저 넣는다 */
    private static JDialog invalidDialog(UiContext ctx, LocalDate today, String startText) {
        ScheduleDialog d = new ScheduleDialog(ctx, null, ctx.schedules().newDraft(today));
        if (startText != null) {
            findCombo(d).setSelectedItem(startText);
        }
        clickButton(d, "저장");
        return d;
    }

    private static TimeCombo findCombo(Container root) {
        for (Component c : root.getComponents()) {
            if (c instanceof TimeCombo t) {
                return t;
            }
            if (c instanceof Container inner) {
                TimeCombo found = findCombo(inner);
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

    private static void shoot(Supplier<JDialog> factory, Path out, String name)
            throws InterruptedException, InvocationTargetException, IOException {
        JDialog[] d = new JDialog[1];
        run(() -> {
            d[0] = factory.get();
            d[0].pack();
        });
        try {
            save(d[0].getRootPane(), out.resolve(name + ".png"));
        } finally {
            run(d[0]::dispose);
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
