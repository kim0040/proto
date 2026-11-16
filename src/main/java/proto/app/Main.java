package proto.app;

import proto.control.CategoryController;
import proto.control.ReminderTimer;
import proto.control.ScheduleController;
import proto.control.StateRefreshTimer;
import proto.ui.UiLauncher;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * [애플리케이션 시작점] 저장소·controller·타이머를 만들어 연결하고 화면(UiLauncher)을 띄운다.
 * 데이터 폴더는 AppPaths가 정한다 (개발 실행은 ./data, 설치된 앱은 사용자별 폴더).
 * 같은 데이터를 두 프로세스가 동시에 쓰지 않도록 폴더 안의 app.lock을 프로세스가 끝날 때까지 잡고,
 * 이미 잡혀 있으면 안내만 하고 끝낸다.
 */
public final class Main {

    private static final Path DATA_DIR = AppPaths.dataDir();

    /** 프로세스가 끝날 때까지 잡고 있는 단일 실행 잠금 (참조를 지키지 않으면 채널이 닫혀 잠금이 풀린다) */
    private static FileChannel lockChannel;
    private static FileLock lock;

    private Main() {
    }

    /** 프로그램을 시작한다. 이미 실행 중이면 안내만 하고 끝낸다 */
    public static void main(String[] args) {
        UiLauncher.prepare();
        if (!acquireSingleInstanceLock()) {
            UiLauncher.showAlreadyRunning();
            System.exit(0);
            return;
        }
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
            UiLauncher.launch(schedules, categories, reminders, DATA_DIR);
            // 화면이 리스너를 등록한 뒤, 불러오기 경고(건너뛴 줄 등)를 알린다
            SwingUtilities.invokeLater(schedules::reportLoadWarnings);
        });
    }

    /** 데이터 폴더의 app.lock을 잡는다. 다른 프로세스가 이미 잡았으면 false. 폴더를 못 만들 때처럼 잠금 자체를 쓸 수 없으면 막지 않고 true */
    private static boolean acquireSingleInstanceLock() {
        try {
            Files.createDirectories(DATA_DIR);
            lockChannel = FileChannel.open(DATA_DIR.resolve("app.lock"), StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
            lock = lockChannel.tryLock();
            return lock != null;
        } catch (IOException | RuntimeException e) {
            // OverlappingFileLockException(같은 JVM 안에서 이미 잡음)도 "이미 실행 중"이다
            return !(e instanceof OverlappingFileLockException);
        }
    }
}
