package proto.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** 테스트용 HTTP 서버. GET은 경로별로 정해 둔 바이트를 돌려주고, PUT은 정해 둔 상태 코드로 답한다 */
public final class FakeApiServer implements AutoCloseable {

    private final HttpServer server;
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "fake-api-test");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, byte[]> bodies = new ConcurrentHashMap<>();
    private final Map<String, Integer> putStatuses = new ConcurrentHashMap<>();
    private final Map<String, byte[]> putBodies = new ConcurrentHashMap<>();
    private volatile long stallMillis;
    public final AtomicInteger puts = new AtomicInteger();

    private FakeApiServer(HttpServer server) {
        this.server = server;
    }

    public static FakeApiServer start() throws IOException {
        FakeApiServer api = new FakeApiServer(HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0));
        api.server.createContext("/", api::handle);
        api.server.setExecutor(api.pool);
        api.server.start();
        return api;
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public FakeApiServer get(String path, String body) {
        return get(path, body.getBytes(StandardCharsets.UTF_8));
    }

    public FakeApiServer get(String path, byte[] body) {
        bodies.put(path, body);
        return this;
    }

    public FakeApiServer putStatus(String path, int status) {
        putStatuses.put(path, status);
        return this;
    }

    /** PUT에 200과 이 본문으로 답한다 (클라이언트가 쓰지 않는 응답 본문 테스트) */
    public FakeApiServer putBody(String path, byte[] body) {
        putBodies.put(path, body);
        return this;
    }

    /** GET 본문의 첫 바이트만 보내고 이만큼 멈춘다 (본문 수신 제한 시간 테스트) */
    public FakeApiServer stallBodyFor(long millis) {
        stallMillis = millis;
        return this;
    }

    /** 이 서버를 가리키는 server.properties를 dir에 쓰고 그 설정으로 클라이언트를 만든다 */
    public RemoteApiClient client(Path dir, String token, int timeoutSeconds) throws IOException {
        Path props = dir.resolve("server.properties");
        Files.writeString(props, "enabled=true\nbaseUrl=http://127.0.0.1:" + port()
                + "\ntoken=" + token + "\ntimeoutSeconds=" + timeoutSeconds + "\n", StandardCharsets.UTF_8);
        return new RemoteApiClient(ServerSettings.load(props));
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            exchange.getRequestBody().readAllBytes();
            if ("PUT".equals(exchange.getRequestMethod())) {
                puts.incrementAndGet();
                if (putBodies.containsKey(path)) {
                    sendBody(exchange, putBodies.get(path));
                    return;
                }
                exchange.sendResponseHeaders(putStatuses.getOrDefault(path, 204), -1);
                return;
            }
            byte[] body = bodies.get(path);
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            sendBody(exchange, body);
        } finally {
            exchange.close();
        }
    }

    private void sendBody(HttpExchange exchange, byte[] body) throws IOException {
        exchange.sendResponseHeaders(200, body.length == 0 ? -1 : body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            if (stallMillis > 0 && body.length > 1) {
                out.write(body, 0, 1);
                out.flush();
                stall();
                out.write(body, 1, body.length - 1);
                return;
            }
            out.write(body);
        }
    }

    private void stall() {
        try {
            Thread.sleep(stallMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        server.stop(0);
        pool.shutdownNow();
    }
}
