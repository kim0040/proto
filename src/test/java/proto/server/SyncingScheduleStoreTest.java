package proto.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.entity.Schedule;
import proto.repository.ScheduleRepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncingScheduleStoreTest {

    @TempDir
    Path dir;

    @Test
    void loadReplacesLocalCsvWithServerSnapshot() throws Exception {
        Schedule localOnly = schedule("local-1", "로컬만", RepeatRule.NONE, null);
        Schedule remote = schedule("remote-1", "서버 일정", RepeatRule.WEEKLY, LocalDate.of(2026, 12, 31));
        remote.setCompletedOn(LocalDate.of(2026, 11, 9), true);
        remote.excludeDate(LocalDate.of(2026, 11, 23));
        ScheduleRepository repo = saved(localOnly);

        try (Api api = Api.start(ScheduleJsonMapper.writeSnapshot(List.of(remote)))) {
            SyncingScheduleStore store = open(repo, api.port);
            store.load();
            assertEquals("Bearer secret", api.authorization);
            assertTrue(store.getWarnings().isEmpty());
            assertEquals(1, store.getAll().size());
            assertSame(remote, store.findById("remote-1").orElseThrow());
            ScheduleRepository again = new ScheduleRepository(repoFile());
            again.load();
            assertSame(remote, again.findById("remote-1").orElseThrow());
            assertTrue(again.findById("local-1").isEmpty());
        }
    }

    @Test
    void serverDownKeepsLocalCsvAndWarns() throws Exception {
        Schedule localOnly = schedule("local-1", "로컬만", RepeatRule.NONE, null);
        ScheduleRepository repo = saved(localOnly);
        int closedPort = closedPort();

        SyncingScheduleStore store = open(repo, closedPort);
        store.load();

        assertEquals(List.of(SyncingScheduleStore.OFFLINE_WARNING), store.getWarnings());
        assertSame(localOnly, store.findById("local-1").orElseThrow());
        assertTrue(Files.readString(repoFile(), StandardCharsets.UTF_8).contains("local-1"));
    }

    @Test
    void savePushesSnapshotAfterWritingCsv() throws Exception {
        Schedule created = schedule("id-1", "회의, \"따옴표\"", RepeatRule.DAILY, LocalDate.of(2026, 11, 30));
        created.setMemo("줄\n바꿈");
        ScheduleRepository repo = new ScheduleRepository(repoFile());
        try (Api api = Api.start("{\"schedules\":[]}")) {
            SyncingScheduleStore store = open(repo, api.port);
            store.add(created);
            store.save();
            assertEquals("Bearer secret", api.authorization);
            assertEquals(1, api.puts.get());
            assertTrue(store.getWarnings().isEmpty());
            List<Schedule> pushed = ScheduleJsonMapper.readSnapshot(api.putBody.get());
            assertEquals(1, pushed.size());
            assertSame(created, pushed.get(0));
            ScheduleRepository again = new ScheduleRepository(repoFile());
            again.load();
            assertSame(created, again.findById("id-1").orElseThrow());
        }
    }

    @Test
    void pushFailureKeepsCsvAndWarns() throws Exception {
        Schedule created = schedule("id-1", "회의", RepeatRule.NONE, null);
        ScheduleRepository repo = new ScheduleRepository(repoFile());
        try (Api api = Api.start("{\"schedules\":[]}")) {
            api.putStatus = 500;
            SyncingScheduleStore store = open(repo, api.port);
            store.add(created);
            store.save();
            assertEquals(List.of(SyncingScheduleStore.PUSH_WARNING), store.getWarnings());
            ScheduleRepository again = new ScheduleRepository(repoFile());
            again.load();
            assertSame(created, again.findById("id-1").orElseThrow());
        }
    }

    private ScheduleRepository saved(Schedule schedule) throws IOException {
        ScheduleRepository repo = new ScheduleRepository(repoFile());
        repo.add(schedule);
        repo.save();
        return repo;
    }

    private Path repoFile() {
        return dir.resolve("schedules.csv");
    }

    private SyncingScheduleStore open(ScheduleRepository repo, int port) throws IOException {
        Path props = dir.resolve("server.properties");
        Files.writeString(props, "enabled=true\nbaseUrl=http://127.0.0.1:" + port
                + "\ntoken=secret\ntimeoutSeconds=2\n", StandardCharsets.UTF_8);
        return new SyncingScheduleStore(repo, new RemoteApiClient(ServerSettings.load(props)));
    }

    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }

    private static Schedule schedule(String id, String title, RepeatRule rule, LocalDate repeatEnd) {
        return new Schedule(id, title, LocalDate.of(2026, 11, 9), LocalTime.of(9, 30), LocalTime.of(10, 45),
                "team", "", rule, repeatEnd, ReminderOption.TEN_MINUTES);
    }

    private static void assertSame(Schedule expected, Schedule back) {
        assertEquals(expected.getId(), back.getId());
        assertEquals(expected.getTitle(), back.getTitle());
        assertEquals(expected.getMemo(), back.getMemo());
        assertEquals(expected.getDate(), back.getDate());
        assertEquals(expected.getStartTime(), back.getStartTime());
        assertEquals(expected.getEndTime(), back.getEndTime());
        assertEquals(expected.getCategoryId(), back.getCategoryId());
        assertEquals(expected.getRepeatRule(), back.getRepeatRule());
        assertEquals(expected.getRepeatEndDate(), back.getRepeatEndDate());
        assertEquals(expected.getReminder(), back.getReminder());
        assertEquals(expected.getCompletedDates(), back.getCompletedDates());
        assertEquals(expected.getExcludedDates(), back.getExcludedDates());
    }

    /** 테스트용 HTTP 서버. 포트 0이라 빈 포트를 고른다 */
    private static final class Api implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService pool;
        final int port;
        final AtomicReference<String> putBody = new AtomicReference<>();
        final AtomicInteger puts = new AtomicInteger();
        volatile int putStatus = 204;
        volatile String authorization;
        private final String getBody;

        private Api(HttpServer server, ExecutorService pool, String getBody) {
            this.server = server;
            this.pool = pool;
            this.port = server.getAddress().getPort();
            this.getBody = getBody;
        }

        static Api start(String getBody) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "schedule-api-test");
                thread.setDaemon(true);
                return thread;
            });
            Api api = new Api(server, pool, getBody);
            server.createContext("/api/v1/schedules", api::handle);
            server.setExecutor(pool);
            server.start();
            return api;
        }

        private void handle(HttpExchange exchange) throws IOException {
            try {
                authorization = exchange.getRequestHeaders().getFirst("Authorization");
                String body = read(exchange);
                if ("PUT".equals(exchange.getRequestMethod())) {
                    putBody.set(body);
                    puts.incrementAndGet();
                    respond(exchange, putStatus, "");
                    return;
                }
                respond(exchange, "GET".equals(exchange.getRequestMethod()) ? 200 : 405,
                        "GET".equals(exchange.getRequestMethod()) ? getBody : "");
            } finally {
                exchange.close();
            }
        }

        @Override
        public void close() {
            server.stop(0);
            pool.shutdownNow();
        }
    }

    private static String read(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body == null || body.isEmpty() ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 0) {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        }
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            if (bytes.length > 0) {
                out.write(bytes);
            }
        }
    }
}
