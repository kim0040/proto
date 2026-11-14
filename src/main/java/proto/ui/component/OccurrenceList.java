package proto.ui.component;

import proto.entity.Occurrence;

import javax.swing.BoxLayout;
import java.awt.Component;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * [경계 boundary] 일정 행을 세로로 쌓은 목록. 같은 일정(id@날짜)의 행은 다시 만들지 않고 재사용해서
 * 완료·상태 변화 애니메이션이 끊기지 않는다.
 * TodayView와 MonthView가 일정 목록으로 쓴다 (F2). 새 목록이 올 때마다 모두 지우고 다시 만들면 완료 체크·상태 색 애니메이션이 끊기므로,
 * 같은 키(일정id@날짜)의 줄은 그대로 두고 값만 바꾼다.
 */
public class OccurrenceList extends ScrollPanel {

    private final RowHandler handler;
    private final boolean compact;
    private final Map<String, OccurrenceRow> rows = new HashMap<>();
    private boolean hideCategory;

    public OccurrenceList(RowHandler handler, boolean compact) {
        super(null);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        this.handler = handler;
        this.compact = compact;
    }

    /** 목록을 새 내용으로 맞춘다. animate가 true면 바뀐 행만 부드럽게 바뀐다 */
    public void setItems(List<Occurrence> items, boolean animate) {
        Map<String, OccurrenceRow> next = new HashMap<>();
        removeAll();
        for (Occurrence o : items) {
            OccurrenceRow row = rows.get(o.key());
            boolean reused = row != null;
            if (!reused) {
                row = new OccurrenceRow(handler, compact);
            }
            row.setHideCategoryName(hideCategory);
            row.update(o, handler.stateOf(o), animate && reused);
            next.put(o.key(), row);
            add(row);
        }
        rows.clear();
        rows.putAll(next);
        revalidate();
        repaint();
    }

    /** 좁은 화면에서 카테고리 이름을 감추고 점만 보여 줄지 (모든 줄에 적용) */
    public void setHideCategoryName(boolean hide) {
        hideCategory = hide;
        rows.values().forEach(r -> r.setHideCategoryName(hide));
    }

    public int count() {
        return rows.size();
    }

    /** 첫 번째 행 (키보드 포커스 이동용). 없으면 null */
    public Component firstRow() {
        return getComponentCount() > 0 ? getComponent(0) : null;
    }
}
