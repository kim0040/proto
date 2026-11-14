package proto.app;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AppPaths의 데이터 폴더 결정 순서 검사. 속성·환경 변수를 직접 넣어 실제 홈 폴더를 건드리지 않는다. */
class AppPathsTest {

    private static Function<String, String> of(String... keyValues) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map::get;
    }

    @Test
    void defaultsToDataFolderInWorkingDirectory() {
        assertEquals(Path.of("data"), AppPaths.resolve(of(), of()));
    }

    @Test
    void devRunUsesDataFolderOnAnyOs() {
        Function<String, String> mac = of("os.name", "Mac OS X", "user.home", "/Users/kim");
        assertEquals(Path.of("data"), AppPaths.resolve(mac, of()));
    }

    @Test
    void explicitPropertyWinsOverEverything() {
        Function<String, String> props = of("proto.dataDir", "/tmp/my-data", "jpackage.app-path", "/Apps/x",
                "os.name", "Mac OS X", "user.home", "/Users/kim");
        assertEquals(Path.of("/tmp/my-data"), AppPaths.resolve(props, of()));
    }

    @Test
    void blankExplicitPropertyIsIgnored() {
        assertEquals(Path.of("data"), AppPaths.resolve(of("proto.dataDir", "  "), of()));
    }

    @Test
    void packagedMacUsesApplicationSupport() {
        Function<String, String> props = of("jpackage.app-path", "/Applications/ScheduleApp.app/Contents/MacOS/ScheduleApp",
                "os.name", "Mac OS X", "user.home", "/Users/kim");
        assertEquals(Path.of("/Users/kim/Library/Application Support/ScheduleApp"), AppPaths.resolve(props, of()));
    }

    @Test
    void packagedWindowsUsesAppData() {
        Function<String, String> props = of("jpackage.app-path", "C:\\Apps\\ScheduleApp.exe",
                "os.name", "Windows 11", "user.home", "C:\\Users\\kim");
        Function<String, String> env = of("APPDATA", "/win/AppData/Roaming");
        assertEquals(Path.of("/win/AppData/Roaming").resolve("ScheduleApp"), AppPaths.resolve(props, env));
    }

    @Test
    void packagedWindowsWithoutAppDataFallsBackToHome() {
        Function<String, String> props = of("jpackage.app-path", "x", "os.name", "Windows 11", "user.home", "/h");
        assertEquals(Path.of("/h/.scheduleapp"), AppPaths.resolve(props, of()));
    }

    @Test
    void packagedLinuxUsesHiddenFolderInHome() {
        Function<String, String> props = of("jpackage.app-path", "x", "os.name", "Linux", "user.home", "/home/kim");
        assertEquals(Path.of("/home/kim/.scheduleapp"), AppPaths.resolve(props, of()));
    }

    @Test
    void packagedDetectionFollowsJpackageProperty() {
        assertTrue(AppPaths.isPackaged(of("jpackage.app-path", "/a")));
        assertFalse(AppPaths.isPackaged(of()));
        assertFalse(AppPaths.isPackaged(of("jpackage.app-path", " ")));
    }
}
