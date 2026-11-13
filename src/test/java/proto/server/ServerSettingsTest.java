package proto.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerSettingsTest {

    @TempDir
    Path dir;

    @Test
    void missingFileStaysDisabledAndCreatesNothing() {
        Path file = dir.resolve("server.properties");
        ServerSettings settings = ServerSettings.load(file);
        assertFalse(settings.isEnabled());
        assertEquals("", settings.warning());
        assertFalse(Files.exists(file));
    }

    @Test
    void enabledTrueWithBaseUrlTurnsServerOn() throws IOException {
        Path file = dir.resolve("server.properties");
        Files.writeString(file, """
                enabled=true
                baseUrl=http://127.0.0.1:9/
                token=abc
                timeoutSeconds=3
                """, StandardCharsets.UTF_8);
        ServerSettings settings = ServerSettings.load(file);
        assertTrue(settings.isEnabled());
        assertEquals("http://127.0.0.1:9", settings.baseUrl());
        assertEquals("abc", settings.token());
        assertEquals(3, settings.timeoutSeconds());
        assertEquals("", settings.warning());
    }

    @Test
    void garbageValuesStayDisabledWithWarning() throws IOException {
        Path file = dir.resolve("server.properties");
        Files.writeString(file, """
                enabled=true
                baseUrl=ftp://files.example
                timeoutSeconds=abc
                """, StandardCharsets.UTF_8);
        ServerSettings settings = ServerSettings.load(file);
        assertFalse(settings.isEnabled());
        assertEquals("server.properties의 baseUrl이 http 주소가 아니어서 서버를 쓰지 않아요", settings.warning());
    }

    @Test
    void unreadableEscapeStaysDisabledWithWarning() throws IOException {
        Path file = dir.resolve("server.properties");
        // Properties가 거절하는 잘못된 유니코드 이스케이프. 자바 소스가 먼저 해석하지 않게 이어 붙인다.
        Files.writeString(file, "enabled=true\ntoken=" + "\\" + "uZZZZ\n", StandardCharsets.UTF_8);
        ServerSettings settings = ServerSettings.load(file);
        assertFalse(settings.isEnabled());
        assertEquals("server.properties를 읽지 못해 서버를 쓰지 않아요", settings.warning());
    }
}
