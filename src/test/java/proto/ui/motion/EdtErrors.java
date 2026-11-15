package proto.ui.motion;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 시험용: 이벤트 처리 스레드(EDT)에서 잡히지 않고 터진 예외를 모아 둔다.
 * 타이머·애니메이션 프레임에서 난 예외는 시험 메서드로 올라오지 않으므로, 끝 값만 보지 않고 이것으로 "예외가 없었는지"를 함께 본다.
 * try-with-resources로 쓰고, 닫으면 원래 처리기로 되돌린다.
 */
public final class EdtErrors implements AutoCloseable {

    private final List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
    private final Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();

    public EdtErrors() {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> errors.add(e));
        try {
            SwingUtilities.invokeAndWait(() -> Thread.currentThread()
                    .setUncaughtExceptionHandler((t, e) -> errors.add(e)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 지금까지 모인 예외 (없으면 빈 목록) */
    public List<Throwable> errors() {
        synchronized (errors) {
            return List.copyOf(errors);
        }
    }

    /** EDT에 쌓인 일을 모두 처리할 때까지 기다린다 */
    public static void flush() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    @Override
    public void close() {
        Thread.setDefaultUncaughtExceptionHandler(before);
        try {
            SwingUtilities.invokeAndWait(() -> Thread.currentThread().setUncaughtExceptionHandler(null));
        } catch (Exception ignored) {
            // 시험 정리 단계라 무시한다
        }
    }
}
