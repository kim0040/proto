package proto.ui.dev;

import proto.control.CategoryController;
import proto.control.ReminderTimer;
import proto.control.ScheduleController;
import proto.entity.Occurrence;
import proto.ui.AppState.Mode;
import proto.ui.MainFrame;
import proto.ui.UiContext;
import proto.ui.UiLauncher;
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
import java.util.List;

/**
 * [경계 boundary] 개발용 화면 캡처 도구. 화면을 2배 크기 PNG로 그려 저장한다 (OS 화면 캡처 권한 필요 없음).
 * 메인 보기 4개, 일정 창, 빈 시간 창, 카테고리 창, 확인 창, 알림 카드를 밝은/어두운 테마로 찍는다.
 */
public final class ScreenshotTool {

    private static final int SCALE = 2;

    private ScreenshotTool() {
    }

    /** 이미 만들어진 controller로 모든 화면을 outDir에 PNG로 저장한다. EDT가 아닌 스레드에서 부른다 */
    public static void capture(ScheduleController schedules, CategoryController categories,
                               ReminderTimer reminders, Path outDir) throws IOException, InterruptedException,
            InvocationTargetException {
        Files.createDirectories(outDir);
        Motion.setEnabledTransient(false);
        MainFrame[] holder = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            holder[0] = UiLauncher.create(schedules, categories, reminders);
            holder[0].attachListeners();
            holder[0].setVisible(true);
        });
        MainFrame frame = holder[0];
        for (boolean dark : new boolean[] {false, true}) {
            String theme = dark ? "dark" : "light";
            run(() -> ThemeManager.apply(dark, false, false));
            captureMain(frame, outDir, theme, 1200, 780, "");
            captureMain(frame, outDir, theme, 960, 640, "-960");
            captureDialogs(frame, outDir, theme);
            captureOverlays(frame, outDir, theme);
        }
        run(frame::dispose);
    }

    private static void captureMain(MainFrame frame, Path out, String theme, int w, int h, String suffix)
            throws InterruptedException, InvocationTargetException, IOException {
        run(() -> frame.setSize(w, h));
        for (Mode mode : Mode.values()) {
            run(() -> frame.showMode(mode));
            run(() -> { });
            save(frame.getRootPane(), out.resolve(theme + "-" + mode.name().toLowerCase() + suffix + ".png"));
        }
        run(() -> frame.showMode(Mode.TODAY));
    }

    private static void captureDialogs(MainFrame frame, Path out, String theme)
            throws InterruptedException, InvocationTargetException, IOException {
        UiContext ctx = frame.context();
        LocalDate today = ctx.schedules().today();
        List<Occurrence> todays = ctx.schedules().getOccurrences(today, today, null, null);
        Occurrence repeating = todays.stream().filter(Occurrence::isRepeating).findFirst().orElse(todays.get(0));
        shoot(new ScheduleDialog(ctx, null, ctx.schedules().newDraft(today)), out, theme + "-dialog-new");
        shoot(new ScheduleDialog(ctx, repeating, ctx.schedules().draftOf(repeating)), out, theme + "-dialog-edit");
        ScheduleDialog invalid = new ScheduleDialog(ctx, null, ctx.schedules().newDraft(today));
        run(() -> clickButton(invalid, "저장"));
        shoot(invalid, out, theme + "-dialog-errors");
        shoot(new FreeSlotDialog(ctx), out, theme + "-dialog-freeslots");
        shoot(new CategoryDialog(ctx), out, theme + "-dialog-categories");
        shoot(new ConfirmDialog(frame, "반복 일정 삭제", "반복 일정을 삭제할까요?",
                "‘이 일정만’은 이 날짜 하나만, ‘앞으로 전부’는 이 날짜부터 뒤의 모든 반복에 적용돼요.", null,
                List.of(new ConfirmDialog.Choice("취소", ConfirmDialog.Kind.GHOST),
                        new ConfirmDialog.Choice("이 일정만", ConfirmDialog.Kind.SECONDARY),
                        new ConfirmDialog.Choice("앞으로 전부", ConfirmDialog.Kind.DANGER))),
                out, theme + "-dialog-scope");
    }

    private static void captureOverlays(MainFrame frame, Path out, String theme)
            throws InterruptedException, InvocationTargetException, IOException {
        UiContext ctx = frame.context();
        LocalDate today = ctx.schedules().today();
        Occurrence o = ctx.schedules().getOccurrences(today, today, null, null).get(0);
        run(() -> {
            frame.showMode(Mode.TODAY);
            frame.overlay().addReminder(o, false, ctx.categories().nameOf(o.categoryId()), noopActions());
            frame.overlay().addReminder(o, true, ctx.categories().nameOf(o.categoryId()), noopActions());
            ctx.actions().toast("일정을 저장했어요");
        });
        run(() -> { });
        save(frame.getRootPane(), out.resolve(theme + "-reminder-toast.png"));
        run(() -> frame.overlay().clearAll());
    }

    private static proto.ui.component.ReminderCard.Actions noopActions() {
        return new proto.ui.component.ReminderCard.Actions() {
            @Override
            public void snooze(Occurrence occurrence) {
            }

            @Override
            public void complete(Occurrence occurrence) {
            }
        };
    }

    private static void shoot(JDialog dialog, Path out, String name)
            throws InterruptedException, InvocationTargetException, IOException {
        run(dialog::pack);
        save(dialog.getRootPane(), out.resolve(name + ".png"));
        run(dialog::dispose);
    }

    private static void clickButton(Container root, String text) {
        for (Component c : root.getComponents()) {
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
