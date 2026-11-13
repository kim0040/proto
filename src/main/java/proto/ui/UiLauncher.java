package proto.ui;

import proto.control.CategoryController;
import proto.control.ReminderTimer;
import proto.control.ScheduleController;

/**
 * [경계 boundary] 화면 시작점. Main이 controller들을 만든 뒤 Swing 이벤트 스레드(EDT)에서 부른다.
 * 테마(FlatLaf), 글꼴, 메인 창 생성은 모두 ui 패키지 안에서 한다.
 *
 * <p>뼈대(API 고정용). 본문은 화면 구현 단계에서 채운다.</p>
 */
public final class UiLauncher {

    private UiLauncher() {
    }

    public static void launch(ScheduleController schedules, CategoryController categories,
                              ReminderTimer reminders) {
        throw new UnsupportedOperationException("아직 구현되지 않음");
    }
}
