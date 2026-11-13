package proto.app;

import proto.control.CategoryController;
import proto.control.ReminderTimer;
import proto.control.ScheduleController;
import proto.control.StateRefreshTimer;
import proto.ui.UiLauncher;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * [애플리케이션 시작점] 저장소·controller·타이머를 만들어 연결하고 화면(UiLauncher)을 띄운다.
 * 데이터는 실행 위치의 data/ 폴더(schedules.csv, categories.csv)에 저장된다.
 */
public final class Main {

    private static final Path DATA_DIR = Path.of("data");

    private Main() {
    }

    public static void main(String[] args) {
        Clock clock = Clock.systemDefaultZone();
        StoreFactory.Stores stores = StoreFactory.create(DATA_DIR);
        stores.categories().loadOrSeed();
        stores.schedules().loadOrSeed(LocalDateTime.now(clock));

        ScheduleController schedules = new ScheduleController(stores.schedules(), stores.categories(), clock);
        CategoryController categories = new CategoryController(stores.categories(), stores.schedules(), schedules);
        ReminderTimer reminders = new ReminderTimer(schedules, clock);
        new StateRefreshTimer(schedules).start();
        reminders.start();

        SwingUtilities.invokeLater(() -> {
            UiLauncher.launch(schedules, categories, reminders);
            // 화면이 리스너를 등록한 뒤, 불러오기 경고(건너뛴 줄 등)를 알린다
            SwingUtilities.invokeLater(schedules::reportLoadWarnings);
        });
    }
}
