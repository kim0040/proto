package proto.app;

import proto.repository.CategoryRepository;
import proto.repository.CategoryStore;
import proto.repository.ScheduleRepository;
import proto.repository.ScheduleStore;
import proto.server.RemoteApiClient;
import proto.server.ServerSettings;
import proto.server.SettingsWarningScheduleStore;
import proto.server.SyncingCategoryStore;
import proto.server.SyncingScheduleStore;

import java.nio.file.Path;

/**
 * [애플리케이션 시작점] 일정·카테고리 저장소를 만든다.
 * 서버 설정이 꺼져 있으면(파일이 없거나 enabled가 true가 아니면) CSV 저장소를 그대로 돌려준다.
 * 서버를 켜는 갈림길은 {@code enableServerIfConfigured} 한 곳뿐이라, 이 메소드와 proto.server 패키지를 지우면
 * 서버 코드는 앱에서 사라진다.
 */
public final class StoreFactory {

    private StoreFactory() {
    }

    /**
     * dataDir 아래 schedules.csv, categories.csv를 연다.
     * server.properties는 있을 때만 읽고, 없거나 꺼져 있으면 파일을 만들지 않는다.
     */
    public static Stores create(Path dataDir) {
        ScheduleRepository schedules = new ScheduleRepository(dataDir.resolve("schedules.csv"));
        CategoryRepository categories = new CategoryRepository(dataDir.resolve("categories.csv"));
        return enableServerIfConfigured(dataDir, schedules, categories);
    }

    /** 서버를 빼려면 이 메소드를 지우고, create가 CSV 저장소를 바로 돌려주게 하면 된다 */
    private static Stores enableServerIfConfigured(Path dataDir, ScheduleRepository schedules,
                                                   CategoryRepository categories) {
        ServerSettings settings = ServerSettings.load(dataDir.resolve("server.properties"));
        if (!settings.isEnabled()) {
            return disabledStores(settings, schedules, categories);
        }
        RemoteApiClient client = new RemoteApiClient(settings);
        return new Stores(new SyncingScheduleStore(schedules, client), new SyncingCategoryStore(categories, client));
    }

    /**
     * 꺼진 이유가 설정 오류(경고가 있음)면 일정 저장소의 경고에 실어 시작 때 사용자에게 알린다.
     * 일부러 껐거나 파일이 없으면 경고가 없으므로 CSV 저장소 인스턴스를 그대로 돌려준다.
     */
    private static Stores disabledStores(ServerSettings settings, ScheduleRepository schedules,
                                         CategoryRepository categories) {
        if (settings.warning().isEmpty()) {
            return new Stores(schedules, categories);
        }
        return new Stores(new SettingsWarningScheduleStore(schedules, settings.warning()), categories);
    }

    /** 이번 실행이 쓸 일정 저장소와 카테고리 저장소 */
    public record Stores(ScheduleStore schedules, CategoryStore categories) {
    }
}
