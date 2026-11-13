package proto.ui;

import proto.control.CategoryController;
import proto.control.ReminderTimer;
import proto.control.ScheduleController;
import proto.ui.theme.ThemeManager;

import javax.swing.JDialog;
import javax.swing.JFrame;

/**
 * [경계 boundary] 화면 시작점. Main이 controller들을 만든 뒤 Swing 이벤트 스레드(EDT)에서 부른다.
 * 글꼴·테마(FlatLaf)를 켜고 메인 창을 만들어 controller 리스너에 연결한 뒤 보여 준다.
 */
public final class UiLauncher {

    private UiLauncher() {
    }

    public static void launch(ScheduleController schedules, CategoryController categories,
                              ReminderTimer reminders) {
        MainFrame frame = create(schedules, categories, reminders);
        frame.attachListeners();
        frame.setVisible(true);
    }

    /** 창만 만들고 보여 주지는 않는다 (스크린샷 도구도 쓴다) */
    public static MainFrame create(ScheduleController schedules, CategoryController categories,
                                   ReminderTimer reminders) {
        ThemeManager.install();
        if (!proto.ui.theme.Ui.isMac()) {
            JFrame.setDefaultLookAndFeelDecorated(true);
            JDialog.setDefaultLookAndFeelDecorated(true);
        }
        UiContext ctx = new UiContext(schedules, categories, reminders);
        return new MainFrame(ctx);
    }
}
