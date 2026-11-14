package proto.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteApiClientTest {

    private static final int LIMIT = 5 * 1024 * 1024;

    @TempDir
    Path dir;

    @Test
    void malformedUtf8BodyIsRejectedNotReplaced() throws Exception {
        byte[] body = "{\"title\":\"REPLACE\"}".getBytes(StandardCharsets.UTF_8);
        body[10] = (byte) 0xFF;
        try (FakeApiServer api = FakeApiServer.start().get("/x", body)) {
            RemoteApiClient client = api.client(dir, "t", 2);
            RemoteApiException e = assertThrows(RemoteApiException.class, () -> client.get("/x"));
            assertTrue(e.getMessage().contains("UTF-8"));
        }
    }

    @Test
    void validKoreanBodyStillDecodes() throws Exception {
        try (FakeApiServer api = FakeApiServer.start().get("/x", "{\"title\":\"한글 😀\"}")) {
            assertEquals("{\"title\":\"한글 😀\"}", api.client(dir, "t", 2).get("/x"));
        }
    }

    @Test
    void bodyOverLimitIsRejected() throws Exception {
        byte[] big = new byte[LIMIT + 1];
        Arrays.fill(big, (byte) ' ');
        try (FakeApiServer api = FakeApiServer.start().get("/big", big)) {
            RemoteApiClient client = api.client(dir, "t", 10);
            assertThrows(RemoteApiException.class, () -> client.get("/big"));
        }
    }

    @Test
    void bodyExactlyAtLimitIsAccepted() throws Exception {
        byte[] exact = new byte[LIMIT];
        Arrays.fill(exact, (byte) ' ');
        try (FakeApiServer api = FakeApiServer.start().get("/exact", exact)) {
            assertEquals(LIMIT, api.client(dir, "t", 10).get("/exact").length());
        }
    }

    @Test
    void slowBodyHitsTheWholeExchangeTimeout() throws Exception {
        try (FakeApiServer api = FakeApiServer.start().get("/slow", "{\"a\":1,\"b\":2}").stallBodyFor(3500)) {
            RemoteApiClient client = api.client(dir, "t", 1);
            long start = System.nanoTime();
            assertThrows(RemoteApiException.class, () -> client.get("/slow"));
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
            assertTrue(elapsedMillis < 2800, "본문 지연에도 제한 시간 안에 끝나야 해요: " + elapsedMillis + "ms");
        }
    }

    @Test
    void headerBuildFailureNeverCarriesTheToken() throws Exception {
        RemoteApiClient client = new RemoteApiClient(settingsWithToken("probe-SECRET-\u0001-tail"));
        RemoteApiException e = assertThrows(RemoteApiException.class, () -> client.get("/x"));
        assertNull(e.getCause());
        for (Throwable t = e; t != null; t = t.getCause()) {
            assertTrue(t.getMessage() == null || !t.getMessage().contains("SECRET"));
        }
    }

    @Test
    void putIgnoresUnusedResponseBodyEvenIfNotUtf8() throws Exception {
        try (FakeApiServer api = FakeApiServer.start().putBody("/p", new byte[] {(byte) 0xFF})) {
            api.client(dir, "t", 2).put("/p", "{}");
            assertEquals(1, api.puts.get());
        }
    }

    @Test
    void transportFailureNeverKeepsTheReflectedToken() throws Exception {
        try (ServerSocket raw = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread echo = new Thread(() -> reflectTokenAsStatusLine(raw));
            echo.start();
            Path props = dir.resolve("server.properties");
            Files.writeString(props, "enabled=true\nbaseUrl=http://127.0.0.1:" + raw.getLocalPort()
                    + "\ntoken=probe-SECRET-42\ntimeoutSeconds=2\n", StandardCharsets.UTF_8);
            RemoteApiClient client = new RemoteApiClient(ServerSettings.load(props));
            RemoteApiException e = assertThrows(RemoteApiException.class, () -> client.get("/x"));
            echo.join(3000);
            assertNull(e.getCause());
            assertEquals(0, e.getSuppressed().length);
            assertTrue(!e.toString().contains("SECRET"));
        }
    }

    /** 잘못된 서버 흉내: 받은 토큰을 HTTP 상태 줄 자리에 그대로 되돌려 보낸다 */
    private static void reflectTokenAsStatusLine(ServerSocket raw) {
        try (Socket s = raw.accept()) {
            s.getInputStream().read(new byte[4096]);
            s.getOutputStream().write("probe-SECRET-42\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            s.getOutputStream().flush();
        } catch (IOException ignored) {
            // 테스트 서버의 연결 오류는 클라이언트 쪽 단언으로 확인한다
        }
    }

    /** ServerSettings.load는 이런 토큰을 이미 거절하므로, 방어선만 보려고 생성자를 직접 쓴다 */
    private static ServerSettings settingsWithToken(String token) throws Exception {
        Constructor<ServerSettings> ctor = ServerSettings.class.getDeclaredConstructor(
                boolean.class, String.class, String.class, int.class, String.class);
        ctor.setAccessible(true);
        return ctor.newInstance(true, "http://127.0.0.1:9", token, 2, "");
    }
}
