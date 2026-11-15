package proto.ui.component;

import proto.entity.Occurrence;
import proto.ui.motion.Motion;
import proto.ui.theme.Ui;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * [경계 boundary] 일정 행을 세로로 쌓은 목록. 같은 일정(id@날짜)의 행은 다시 만들지 않고 재사용해서
 * 완료·상태 변화 애니메이션이 끊기지 않고, 목록이 바뀔 때는 남은 행이 새 자리로 미끄러진다 (F2).
 * TodayView와 MonthView가 일정 목록으로 쓴다. 새 목록이 올 때마다 모두 지우고 다시 만들면 완료 체크·상태 색 애니메이션이 끊기므로,
 * 같은 키(일정id@날짜)의 줄은 그대로 두고 값만 바꾼다.
 * 목록 변화(FLIP): 남은 행은 예전 자리에서 새 자리로 160ms 미끄러지고(오버슈트 없음), 지워진 행은 제자리에서 100ms 흐려지고, 새 행은 6px 아래에서 올라오며
 * 나타난다 (여러 개여도 시차는 합쳐 60ms 이하). 검색어를 빠르게 칠 때는(이전 갱신 후 150ms 안) 움직이지 않고 바로 맞춘다.
 */
public class OccurrenceList extends ScrollPanel {

    /** 이보다 빨리 연달아 갱신되면(글자를 치는 중) 모션 없이 바로 맞춘다 */
    private static final long QUIET_NANOS = 150_000_000L;
    private static final int STAGGER_MS = 15;
    private static final int MAX_STAGGER_MS = 60;

    private final RowHandler handler;
    private final boolean compact;
    private final Map<String, OccurrenceRow> rows = new HashMap<>();
    private final List<RowGhost> ghosts = new ArrayList<>();
    private List<String> keys = List.of();
    private long lastSetNanos = System.nanoTime() - 10 * QUIET_NANOS;
    private boolean hideCategory;

    public OccurrenceList(RowHandler handler, boolean compact) {
        super(new FlipLayout());
        this.handler = handler;
        this.compact = compact;
    }

    /** 목록을 새 내용으로 맞춘다. animate가 true면 바뀐 행의 체크·색이 부드럽게 바뀌고, 행이 늘거나 줄거나 자리가 바뀌면 FLIP 모션이 따른다 */
    public void setItems(List<Occurrence> items, boolean animate) {
        List<String> newKeys = items.stream().map(Occurrence::key).toList();
        boolean flip = canFlip(newKeys);
        lastSetNanos = System.nanoTime();
        Map<String, Integer> oldY = flip ? displayedYs() : Map.of();
        List<RowGhost> fading = flip ? ghostsOfRemoved(newKeys) : List.of();
        clearGhosts();
        Set<OccurrenceRow> fresh = rebuild(items, animate);
        keys = newKeys;
        if (flip) {
            play(oldY, fresh, fading);
        } else {
            rows.values().forEach(r -> r.motion().settle());
        }
        revalidate();
        repaint();
    }

    /** 움직여도 되는 갱신인지: 모션이 켜져 있고, 화면에 보이고, 화면 전환 중이 아니고, 행의 구성이 달라졌고, 글자를 치는 중이 아니다 */
    private boolean canFlip(List<String> newKeys) {
        return Motion.enabled() && isShowing() && !inTransition() && !keys.equals(newKeys)
                && System.nanoTime() - lastSetNanos >= QUIET_NANOS;
    }

    /** 화면 전환(TransitionHost) 효과가 이미 돌고 있으면 목록은 따로 움직이지 않는다 */
    private boolean inTransition() {
        java.awt.Container host = SwingUtilities.getAncestorOfClass(TransitionHost.class, this);
        return host instanceof TransitionHost t && t.isTransitioning();
    }

    /** 지금 화면에 그려진 각 행의 세로 위치 (미끄러지는 중이면 그 위치) */
    private Map<String, Integer> displayedYs() {
        Map<String, Integer> ys = new HashMap<>();
        rows.forEach((key, row) -> ys.put(key, row.getY()));
        return ys;
    }

    /** 새 목록에 없는 행을 지우기 전에 그림자로 찍어 둔다 */
    private List<RowGhost> ghostsOfRemoved(List<String> newKeys) {
        List<RowGhost> out = new ArrayList<>();
        rows.forEach((key, row) -> {
            if (!newKeys.contains(key) && row.getWidth() > 0 && row.getHeight() > 0) {
                out.add(RowGhost.of(row));
            }
        });
        return out;
    }

    private void clearGhosts() {
        for (RowGhost g : ghosts) {
            g.cancel();
            remove(g);
        }
        ghosts.clear();
    }

    /** 행들을 새 순서로 다시 쌓는다. 새로 만든 행을 돌려준다 */
    private Set<OccurrenceRow> rebuild(List<Occurrence> items, boolean animate) {
        Set<OccurrenceRow> fresh = new HashSet<>();
        Map<String, OccurrenceRow> next = new HashMap<>();
        removeAll();
        for (Occurrence o : items) {
            OccurrenceRow row = rows.get(o.key());
            boolean reused = row != null;
            if (!reused) {
                row = new OccurrenceRow(handler, compact);
                fresh.add(row);
            }
            row.setHideCategoryName(hideCategory);
            row.update(o, handler.stateOf(o), animate && reused);
            next.put(o.key(), row);
            add(row);
        }
        rows.clear();
        rows.putAll(next);
        return fresh;
    }

    /** 새 자리 기준으로 남은 행은 미끄러지게, 새 행은 나타나게, 지워진 행은 흐려지게 시킨다 */
    private void play(Map<String, Integer> oldY, Set<OccurrenceRow> fresh, List<RowGhost> fading) {
        int y = 0;
        int appearing = 0;
        for (Component c : getComponents()) {
            if (!(c instanceof OccurrenceRow row)) {
                continue;
            }
            if (fresh.contains(row)) {
                row.motion().appear(Ui.px(6), Math.min(appearing++ * STAGGER_MS, MAX_STAGGER_MS));
            } else {
                glideIfMoved(row, oldY.get(row.occurrence().key()), y);
            }
            y += row.getPreferredSize().height;
        }
        fading.forEach(this::showGhost);
    }

    private static void glideIfMoved(OccurrenceRow row, Integer before, int after) {
        if (before == null || before == after) {
            row.motion().settle();
        } else {
            row.motion().glideFrom(before - after);
        }
    }

    private void showGhost(RowGhost ghost) {
        ghosts.add(ghost);
        add(ghost);
        ghost.fadeOut(() -> {
            ghosts.remove(ghost);
            remove(ghost);
            revalidate();
            repaint();
        });
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
        for (Component c : getComponents()) {
            if (c instanceof OccurrenceRow) {
                return c;
            }
        }
        return null;
    }
}
