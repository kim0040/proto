package proto.server;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * [서버 대비 — 현재 사용 안 함] HTTP로 JSON을 가져오고 올린다.
 * [경계 boundary] java.net.http.HttpClient로 GET/PUT 하고, 제한 시간과 Authorization: Bearer 토큰을 붙인다.
 * 제한 시간은 응답 본문을 다 받을 때까지 한 번에 적용하고, 본문은 5MB까지만 받으며, UTF-8이 깨졌으면 거절한다.
 * 2xx가 아니거나 연결에 실패하면 한국어 메시지의 {@link RemoteApiException}을 던진다 (메시지·원인에 토큰은 넣지 않는다).
 */
public final class RemoteApiClient {

    /** 받을 수 있는 응답 본문의 최대 바이트 수 */
    static final int MAX_BODY_BYTES = 5 * 1024 * 1024;

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
        HttpRequest request = request(path, json);
        CompletableFuture<HttpResponse<byte[]>> future = http.sendAsync(request, RemoteApiClient::bodyHandler);
        try {
            return requireSuccess(future.get(settings.timeoutSeconds(), TimeUnit.SECONDS));
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new RemoteApiException("서버가 제한 시간 안에 응답을 끝내지 않았어요");
        } catch (ExecutionException e) {
            throw failure(e.getCause());
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new RemoteApiException("서버 요청이 중단됐어요", e);
        }
    }

    private static RemoteApiException failure(Throwable cause) {
        if (cause instanceof LimitedBodySubscriber.TooLargeException) {
            return new RemoteApiException("서버 응답이 너무 커서 받지 않았어요");
        }
        return new RemoteApiException("서버에 연결하지 못했어요", cause);
    }

    /** 토큰이 든 예외 원인을 남기지 않으려고, 요청을 만들다 난 오류는 원인 없이 새 예외로 바꾼다 */
    private HttpRequest request(String path, String json) throws RemoteApiException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(settings.baseUrl() + path))
                    .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + settings.token());
            if (json == null) {
                return builder.GET().build();
            }
            builder.header("Content-Type", "application/json");
            return builder.PUT(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
        } catch (IllegalArgumentException e) {
            throw new RemoteApiException("서버 요청을 만들지 못했어요. server.properties를 확인해 주세요");
        }
    }

    /** 2xx만 본문을 (최대 크기까지) 모은다. 그 밖은 본문을 받지 않는다 */
    private static HttpResponse.BodySubscriber<byte[]> bodyHandler(HttpResponse.ResponseInfo info) {
        if (info.statusCode() < 200 || info.statusCode() >= 300) {
            return HttpResponse.BodySubscribers.replacing(new byte[0]);
        }
        return new LimitedBodySubscriber(MAX_BODY_BYTES);
    }

    private static String requireSuccess(HttpResponse<byte[]> response) throws RemoteApiException {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new RemoteApiException("서버가 오류로 답했어요 (HTTP " + status + ")");
        }
        return decodeStrictly(response.body());
    }

    /** 깨진 UTF-8을 U+FFFD로 바꿔 받지 않고 거절한다 */
    private static String decodeStrictly(byte[] body) throws RemoteApiException {
        if (body == null || body.length == 0) {
            return "";
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(body)).toString();
        } catch (CharacterCodingException e) {
            throw new RemoteApiException("서버 응답이 올바른 UTF-8이 아니에요");
        }
    }
}
