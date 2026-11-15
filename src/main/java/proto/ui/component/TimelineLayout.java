package proto.ui.component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * [경계 boundary] 하루 칸 안에서 시간이 겹치는 일정을 가로로 나란히 나누는 계산 (화면 요소 없이 숫자만 다룬다).
 * TimelineGrid가 하루마다 불러 각 일정이 몇 번째 세로줄에 놓이고 줄이 모두 몇 개인지 받는다 (F2).
 * 겹치는 일정끼리 묶음(cluster)을 만들고, 묶음 안에서 가장 앞쪽의 빈 줄에 차례로 넣는다. 끝 시간과 시작 시간이 같으면(10:00 끝, 10:00 시작) 겹치지 않는다.
 * 너무 짧은 일정은 화면에서 최소 높이로 그려지므로, 그 높이만큼 길다고 보고 겹침을 판단한다 (minLength).
 */
public final class TimelineLayout {

    /** 일정 하나의 시작·끝(분) */
    public record Span(int start, int end) {
    }

    /** 놓일 위치: column번째 줄(0부터), 묶음 전체의 줄 수 columns */
    public record Placement(int column, int columns) {
    }

    private TimelineLayout() {
    }

    /**
     * 입력과 같은 순서로 위치를 돌려준다.
     *
     * @param minLength 겹침을 따질 때 일정이 최소한 차지하는 길이(분)
     */
    public static List<Placement> pack(List<Span> spans, int minLength) {
        int[] column = new int[spans.size()];
        int[] columns = new int[spans.size()];
        List<Integer> cluster = new ArrayList<>();
        List<Integer> lineEnds = new ArrayList<>();
        int clusterEnd = Integer.MIN_VALUE;
        for (int i : order(spans, minLength)) {
            Span s = spans.get(i);
            if (!cluster.isEmpty() && s.start() >= clusterEnd) {
                close(cluster, lineEnds.size(), columns);
                lineEnds.clear();
                clusterEnd = Integer.MIN_VALUE;
            }
            int end = effectiveEnd(s, minLength);
            column[i] = place(lineEnds, s.start(), end);
            cluster.add(i);
            clusterEnd = Math.max(clusterEnd, end);
        }
        close(cluster, lineEnds.size(), columns);
        List<Placement> result = new ArrayList<>();
        for (int i = 0; i < spans.size(); i++) {
            result.add(new Placement(column[i], columns[i]));
        }
        return result;
    }

    private static List<Integer> order(List<Span> spans, int minLength) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < spans.size(); i++) {
            idx.add(i);
        }
        idx.sort(Comparator.<Integer>comparingInt(i -> spans.get(i).start())
                .thenComparing(i -> -effectiveEnd(spans.get(i), minLength))
                .thenComparingInt(i -> i));
        return idx;
    }

    private static int effectiveEnd(Span s, int minLength) {
        return Math.max(s.end(), s.start() + minLength);
    }

    /** 끝난 줄(끝 <= start) 중 가장 앞 줄에 넣고, 없으면 새 줄을 만든다. 놓은 줄 번호를 돌려준다 */
    private static int place(List<Integer> lineEnds, int start, int end) {
        for (int c = 0; c < lineEnds.size(); c++) {
            if (lineEnds.get(c) <= start) {
                lineEnds.set(c, end);
                return c;
            }
        }
        lineEnds.add(end);
        return lineEnds.size() - 1;
    }

    private static void close(List<Integer> cluster, int lines, int[] columns) {
        for (int i : cluster) {
            columns[i] = lines;
        }
        cluster.clear();
    }
}
