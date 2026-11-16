package proto.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * [경계 boundary] HTTP 응답 본문을 바이트로 모으되 정해진 크기를 넘으면 받기를 멈춘다. 서버 대비 코드(현재 사용 안 함).
 * RemoteApiClient만 쓴다 (같은 패키지 안에서만 보인다). HttpClient가 본문을 조각조각 넘겨 주면 크기를 세면서 모으고,
 * 최대 크기를 넘는 순간 받기를 취소하고 {@link TooLargeException}으로 끝낸다.
 * 서버가 잘못되거나 악의적으로 아주 큰 응답을 보내도 메모리가 차지 않게 하려는 것이다.
 */
final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {

    private final long maxBytes;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final CompletableFuture<byte[]> result = new CompletableFuture<>();
    private Flow.Subscription subscription;

    LimitedBodySubscriber(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    public CompletionStage<byte[]> getBody() {
        return result;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(Long.MAX_VALUE);
    }

    /** 도착한 조각을 모은다. 이 조각까지 합치면 최대 크기를 넘는 순간 구독을 취소하고 TooLargeException으로 끝낸다 */
    @Override
    public void onNext(List<ByteBuffer> items) {
        for (ByteBuffer item : items) {
            if (result.isDone()) {
                return;
            }
            if (buffer.size() + (long) item.remaining() > maxBytes) {
                subscription.cancel();
                result.completeExceptionally(new TooLargeException(maxBytes));
                return;
            }
            byte[] chunk = new byte[item.remaining()];
            item.get(chunk);
            buffer.write(chunk, 0, chunk.length);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        result.completeExceptionally(throwable);
    }

    @Override
    public void onComplete() {
        result.complete(buffer.toByteArray());
    }

    /** 본문이 최대 크기를 넘었을 때 */
    static final class TooLargeException extends IOException {
        TooLargeException(long maxBytes) {
            super("응답 본문이 " + maxBytes + "바이트를 넘어요");
        }
    }
}
