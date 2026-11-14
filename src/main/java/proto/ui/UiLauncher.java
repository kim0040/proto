package proto.ui;

import proto.control.CategoryController;
import proto.control.ReminderTimer;
import proto.control.ScheduleController;
import proto.ui.theme.ThemeManager;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.nio.file.Path;

/**
 * [경계 boundary] 화면 시작점. Main이 controller들을 만든 뒤 Swing 이벤트 스레드(EDT)에서 부른다.
 * 글꼴·테마(FlatLaf)를 켜고 메인 창을 만들어 controller 리스너에 연결한 뒤 보여 준다.
 */
public final class UiLauncher {

    private UiLauncher() {
    }

    /**
     * 화면 시스템(AWT)을 쓰기 전에 가장 먼저 부른다. macOS에서 메뉴 막대·Dock에 보일 앱 이름과 시스템 밝기 연동을 정한다.
     * AWT가 한 번 시작된 뒤에는 이 속성들이 먹히지 않는다.
     */
    public static void prepare() {
        System.setProperty("apple.awt.application.name", "일정 관리");
        System.setProperty("apple.awt.application.appearance", "system");
    }

    /** 이미 다른 창이 실행 중일 때 알리는 안내창 (데이터는 건드리지 않고 프로그램은 곧 끝난다). 어느 스레드에서 불러도 된다 */
    public static void showAlreadyRunning() {
        String message = "일정 관리가 이미 실행 중이에요.\n열려 있는 창을 확인해 주세요.";
        Runnable show = () -> JOptionPane.showMessageDialog(null, message, "일정 관리",
                JOptionPane.INFORMATION_MESSAGE);
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                show.run();
            } else {
                SwingUtilities.invokeAndWait(show);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.lang.reflect.InvocationTargetException | RuntimeException e) {
            System.err.println(message);
        }
    }

    public static void launch(ScheduleController schedules, CategoryController categories,
                              ReminderTimer reminders, Path dataDir) {
        MainFrame frame = create(schedules, categories, reminders, dataDir);
        frame.attachListeners();
        frame.setVisible(true);
    }

    /** 창만 만들고 보여 주지는 않는다 (스크린샷 도구도 쓴다) */
    public static MainFrame create(ScheduleController schedules, CategoryController categories,
                                   ReminderTimer reminders) {
        return create(schedules, categories, reminders, Path.of("data"));
    }

    /** dataDir은 설정 화면이 보여 주고 여는 데이터 폴더 (위치는 Main이 정해 넘긴다) */
    public static MainFrame create(ScheduleController schedules, CategoryController categories,
                                   ReminderTimer reminders, Path dataDir) {
        ThemeManager.install();
        if (!proto.ui.theme.Ui.isMac()) {
            JFrame.setDefaultLookAndFeelDecorated(true);
            JDialog.setDefaultLookAndFeelDecorated(true);
        }
        UiContext ctx = new UiContext(schedules, categories, reminders, dataDir);
        return new MainFrame(ctx);
    }
}
