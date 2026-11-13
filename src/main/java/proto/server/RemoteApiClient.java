package proto.server;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * [서버 대비 — 현재 사용 안 함] HTTP로 JSON을 가져오고 올린다.
 * [경계 boundary] java.net.http.HttpClient로 GET/PUT 하고, 제한 시간과 Authorization: Bearer 토큰을 붙인다.
 * 2xx가 아니거나 연결에 실패하면 한국어 메시지의 {@link RemoteApiException}을 던진다.
 */
public final class RemoteApiClient {

    private final ServerSettings settings;
    private final HttpClient http;

    public RemoteApiClient(ServerSettings settings) {
        this.settings = settings;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(settings.timeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /** path는 /api/v1/schedules처럼 슬래시로 시작한다. 본문 문자열을 돌려준다 */
    public String get(String path) throws RemoteApiException {
        return send(path, null);
    }

    /** 본문은 UTF-8 JSON이다. 2xx면 응답 본문을 버리고 성공으로 본다 */
    public void put(String path, String json) throws RemoteApiException {
        send(path, json);
    }

    private String send(String path, String json) throws RemoteApiException {
        try {
            HttpResponse<String> response = http.send(request(path, json), bodyHandler());
            return requireSuccess(response);
        } catch (RemoteApiException e) {
            throw e;
        } catch (IOException | IllegalArgumentException e) {
            throw new RemoteApiException("서버에 연결하지 못했어요", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RemoteApiException("서버 요청이 중단됐어요", e);
        }
    }

    private HttpRequest request(String path, String json) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(java.net.URI.create(settings.baseUrl() + path))
                .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + settings.token());
        if (json == null) {
            return builder.GET().build();
        }
        builder.header("Content-Type", "application/json");
        return builder.PUT(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
    }

    private static HttpResponse.BodyHandler<String> bodyHandler() {
        return HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8);
    }

    private static String requireSuccess(HttpResponse<String> response) throws RemoteApiException {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new RemoteApiException("서버가 오류로 답했어요 (HTTP " + status + ")");
        }
        return response.body() == null ? "" : response.body();
    }
}
