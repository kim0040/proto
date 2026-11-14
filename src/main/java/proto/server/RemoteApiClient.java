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
 * 서버와 주고받는 저장소(SyncingScheduleStore, SyncingCategoryStore)만 이 클래스를 쓴다. 지금은 server.properties가 없으면 만들어지지 않는다.
 * 네트워크가 느리거나 서버가 이상해도 앱이 멈추거나 메모리가 넘치지 않게, 시간·크기·글자 인코딩의 한계를 이 클래스에서 모두 지킨다.
 * 제한 시간은 응답 본문을 다 받을 때까지 한 번에 적용하고, 본문은 5MB까지만 받으며, UTF-8이 깨졌으면 거절한다.
 * 2xx가 아니거나 연결에 실패하면 한국어 메시지의 {@link RemoteApiException}을 던진다 (메시지·원인에 토큰은 넣지 않는다).
 */
public final class RemoteApiClient {

    /** 받을 수 있는 응답 본문의 최대 바이트 수 */
    static final int MAX_BODY_BYTES = 5 * 1024 * 1024;

    private final ServerSettings settings;
    private final HttpClient http;

    /**
     * 설정의 제한 시간으로 HTTP 클라이언트를 만든다. 리다이렉트는 따라가지 않는다 (토큰이 든 요청이 다른 주소로 자동으로 다시 보내지지 않게),
     * HTTP/1.1로 고정한다.
     *
     * @param settings 주소·토큰·제한 시간이 든 서버 설정 (켜져 있어야 의미가 있다)
     */
    public RemoteApiClient(ServerSettings settings) {
        this.settings = settings;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(settings.timeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * 서버에서 본문을 읽어 온다. path는 /api/v1/schedules처럼 슬래시로 시작한다.
     *
     * @return 응답 본문 문자열 (본문이 없으면 빈 문자열)
     * @throws RemoteApiException 연결 실패, 제한 시간 초과, 2xx가 아닌 응답, 5MB 초과, UTF-8이 아닌 본문일 때
     */
    public String get(String path) throws RemoteApiException {
        return decodeStrictly(send(path, null).body());
    }

    /**
     * 서버에 JSON을 통째로 올린다 (PUT). 본문은 UTF-8 JSON이고, 2xx면 응답 본문을 버리고 성공으로 본다.
     * 응답 본문은 쓰지 않으므로 읽지도, UTF-8 검사도 하지 않는다 (서버가 이미 저장했는데 실패로 보는 일을 막는다).
     *
     * @throws RemoteApiException 연결 실패, 제한 시간 초과, 2xx가 아닌 응답일 때
     */
    public void put(String path, String json) throws RemoteApiException {
        send(path, json);
    }

    /**
     * 요청을 비동기로 보내고 정해진 시간까지만 기다린다. 왜 sendAsync + get(시간)인가: 요청 자체의 timeout은 응답 헤더까지만 보장하기 때문에,
     * 본문을 다 받을 때까지의 전체 시간을 따로 제한하려는 것이다.
     */
    private HttpResponse<byte[]> send(String path, String json) throws RemoteApiException {
        HttpRequest request = request(path, json);
        boolean wantBody = json == null;
        CompletableFuture<HttpResponse<byte[]>> future = http.sendAsync(request, info -> bodyHandler(info, wantBody));
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
            throw new RemoteApiException("서버 요청이 중단됐어요");
        }
    }

    /**
     * 원래 예외는 원인(cause)으로 붙이지 않는다. 이상한 서버가 토큰을 응답에 되돌려 보내면
     * 그 내용이 원래 예외의 메시지에 들어가 스택 추적으로 새어 나갈 수 있기 때문이다. 예외 종류 이름만 남긴다.
     */
    private static RemoteApiException failure(Throwable cause) {
        if (cause instanceof LimitedBodySubscriber.TooLargeException) {
            return new RemoteApiException("서버 응답이 너무 커서 받지 않았어요");
        }
        String kind = cause == null ? "알 수 없음" : cause.getClass().getSimpleName();
        return new RemoteApiException("서버에 연결하지 못했어요 (" + kind + ")");
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

    /** 오류 응답(2xx가 아님)과 PUT 응답은 본문을 읽지 않고 버린다. 필요 없는 큰 본문을 받느라 시간을 쓰지 않기 위해서이다. */
    private static HttpResponse.BodySubscriber<byte[]> bodyHandler(HttpResponse.ResponseInfo info, boolean wantBody) {
        if (!wantBody || info.statusCode() < 200 || info.statusCode() >= 300) {
            return HttpResponse.BodySubscribers.replacing(new byte[0]);
        }
        return new LimitedBodySubscriber(MAX_BODY_BYTES);
    }

    private static HttpResponse<byte[]> requireSuccess(HttpResponse<byte[]> response) throws RemoteApiException {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new RemoteApiException("서버가 오류로 답했어요 (HTTP " + status + ")");
        }
        return response;
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
