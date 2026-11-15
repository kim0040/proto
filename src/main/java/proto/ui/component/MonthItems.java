package proto.ui.component;

/**
 * [경계 boundary] 달력 칸 안의 일정 줄 배치 계산 (그리지 않고 숫자만 계산한다).
 * MonthGrid가 일정 줄을 그릴 때와, MonthDrag가 "눌린 자리가 어느 줄인가"를 찾을 때 같은 계산을 쓰게 하려고 따로 뒀다 (F2, F3).
 * 모든 길이는 화면 배율을 곱한 픽셀이다.
 */
public final class MonthItems {

    /** 한 칸에 보이는 일정 줄의 최대 수 (마지막 줄은 "+N"이 차지할 수 있다) */
    public static final int MAX_ROWS = 3;

    private MonthItems() {
    }

    /** 일정 size개 중 실제로 이름을 그릴 줄 수. 줄이 모자라면 마지막 줄을 "+N"에 내준다 */
    public static int shown(int size, int availableHeight, int rowHeight) {
        int maxRows = Math.max(1, Math.min(MAX_ROWS, availableHeight / rowHeight));
        return size <= maxRows ? size : maxRows - 1;
    }

    /**
     * 칸 안쪽 좌표 (x, y)가 몇 번째 일정 줄인지. 줄 밖(여백, "+N" 줄, 칸 위쪽 날짜 숫자 줄)이면 -1이다.
     *
     * @param top    첫 줄이 시작하는 y
     * @param left   줄의 왼쪽 x
     * @param width  줄의 너비
     * @param avail  줄들이 쓸 수 있는 높이
     */
    public static int indexAt(int x, int y, int size, int top, int left, int width, int avail, int rowHeight) {
        if (x < left || x >= left + width || y < top) {
            return -1;
        }
        int i = (y - top) / rowHeight;
        return i < shown(size, avail, rowHeight) ? i : -1;
    }
}
