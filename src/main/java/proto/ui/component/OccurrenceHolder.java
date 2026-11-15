package proto.ui.component;

import proto.entity.Occurrence;

import java.awt.Component;

/**
 * [경계 boundary] 하루치 일정 하나를 들고 있는 화면 조각(일정 행, 주 칩, 시간표 블록)이라는 표시.
 * 복사(Ctrl/⌘+C)가 "지금 포커스가 놓인 일정"을 찾으려고 쓴다. OccurrenceRow, WeekChip, TimelineBlock이 구현한다.
 * 포커스가 일정 조각 안쪽 컴포넌트(체크 버튼 등)에 있어도 위쪽 부모를 따라 올라가며 찾는다.
 */
public interface OccurrenceHolder {

    /** 이 조각이 보여 주는 하루치 일정. 아직 채워지지 않았으면 null */
    Occurrence occurrence();

    /** from에서 시작해 부모 쪽으로 올라가며 처음 만나는 일정 조각의 일정. 없으면 null */
    static Occurrence find(Component from) {
        for (Component c = from; c != null; c = c.getParent()) {
            if (c instanceof OccurrenceHolder h) {
                return h.occurrence();
            }
        }
        return null;
    }
}
