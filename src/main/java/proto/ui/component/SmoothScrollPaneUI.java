package proto.ui.component;

import com.formdev.flatlaf.ui.FlatScrollPaneUI;

import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;
import java.awt.event.MouseWheelListener;

/**
 * [경계 boundary] 모든 스크롤 영역(JScrollPane)이 쓰는 화면 모양 정의. 마우스 휠 한 칸을 부드럽게 이어 주는 NotchWheel만 FlatLaf 기본에 더했다.
 * ThemeManager가 ScrollPaneUI로 등록하므로 오늘 목록·주간 시간표·달력 옆 목록·통계·설정·대화상자 본문 모두 한 곳에서 같이 적용된다.
 * FlatLaf의 부드러운 스크롤은 소수 단위(트랙패드) 입력만 다루고 휠 한 칸은 그대로 건너뛰기 때문에 이 클래스가 그 부분을 채운다.
 */
public class SmoothScrollPaneUI extends FlatScrollPaneUI {

    public static ComponentUI createUI(JComponent c) {
        return new SmoothScrollPaneUI();
    }

    @Override
    protected MouseWheelListener createMouseWheelListener() {
        return new NotchWheel(scrollpane, super.createMouseWheelListener());
    }
}
