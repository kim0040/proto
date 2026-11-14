package proto.app;

import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Function;

/**
 * [애플리케이션 시작점] 데이터 폴더의 위치를 정한다.
 * 순서: 시스템 속성 proto.dataDir → (설치된 앱이면) 사용자별 폴더 → 실행 위치의 data/.
 * 설치된 앱은 실행 위치를 알 수 없으므로(Finder·탐색기에서 열면 보통 /) 운영체제가 정한 사용자 폴더를 쓴다.
 * 속성·환경 변수·운영체제 이름을 밖에서 받으므로 테스트에서 실제 홈 폴더를 건드리지 않는다.
 */
public final class AppPaths {

    /** 데이터 폴더를 직접 지정하는 시스템 속성 */
    static final String DATA_DIR_PROPERTY = "proto.dataDir";
    /** jpackage가 만든 실행 파일이 켜질 때 자바가 넣어 주는 속성 */
    static final String PACKAGED_PROPERTY = "jpackage.app-path";
    private static final String APP_FOLDER = "ScheduleApp";

    private AppPaths() {
    }

    /** 이 컴퓨터의 실제 속성·환경 변수로 데이터 폴더를 정한다 */
    public static Path dataDir() {
        return resolve(System::getProperty, System::getenv);
    }

    /** 주어진 속성·환경 변수 조회 함수로 데이터 폴더를 정한다 (테스트용으로 열어 둠) */
    static Path resolve(Function<String, String> property, Function<String, String> env) {
        String explicit = property.apply(DATA_DIR_PROPERTY);
        if (explicit != null && !explicit.isBlank()) {
            return Path.of(explicit.strip());
        }
        if (isPackaged(property)) {
            return userDataDir(property, env);
        }
        return Path.of("data");
    }

    /** jpackage 앱으로 켜졌는지 */
    static boolean isPackaged(Function<String, String> property) {
        String appPath = property.apply(PACKAGED_PROPERTY);
        return appPath != null && !appPath.isBlank();
    }

    /** 운영체제별 사용자 폴더. macOS는 Application Support, Windows는 APPDATA, 그 밖에는 홈의 .scheduleapp */
    static Path userDataDir(Function<String, String> property, Function<String, String> env) {
        String osName = property.apply("os.name");
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        String homeText = property.apply("user.home");
        Path home = Path.of(homeText == null ? "." : homeText);
        if (os.contains("mac")) {
            return home.resolve("Library").resolve("Application Support").resolve(APP_FOLDER);
        }
        String appData = env.apply("APPDATA");
        if (os.contains("win") && appData != null && !appData.isBlank()) {
            return Path.of(appData).resolve(APP_FOLDER);
        }
        return home.resolve(".scheduleapp");
    }
}
