package proto.ui.component;

/**
 * [경계 boundary] 시간표의 칸 크기 계산: 날짜 칸의 가로 위치, 분 ↔ 세로 위치 (화면 요소 없이 숫자만 다룬다).
 * TimelineGrid가 그리기·배치에, TimelineDrag가 마우스 위치를 날짜와 시각으로 바꾸는 데, TimelineHeader가 요일 머리글을 같은 칸에 맞추는 데 함께 쓴다 (F2).
 * 왼쪽 시각 글자 영역(gutter) 오른쪽을 7등분한 날짜 칸이 있고, 세로로는 위아래 여백(pad) 사이에 24시간이 놓인다. 값은 모두 실제 픽셀이다 (Ui.px로 환산해서 넘긴다).
 */
public final class TimelineMetrics {

    public static final int DAYS = 7;

    private final int gutter;
    private final int width;
    private final int hourHeight;
    private final int pad;

    public TimelineMetrics(int gutter, int width, int hourHeight, int pad) {
        this.gutter = gutter;
        this.width = Math.max(width, gutter + DAYS);
        this.hourHeight = Math.max(1, hourHeight);
        this.pad = pad;
    }

    public int gutter() {
        return gutter;
    }

    public int hourHeight() {
        return hourHeight;
    }

    public int totalHeight() {
        return pad * 2 + 24 * hourHeight;
    }

    /** day번째(0~6) 칸의 왼쪽 x. day가 7이면 마지막 칸의 오른쪽 끝(전체 너비)이다 */
    public int columnX(int day) {
        return gutter + (int) ((long) (width - gutter) * day / DAYS);
    }

    public int columnWidth(int day) {
        return columnX(day + 1) - columnX(day);
    }

    /** x 위치가 속한 날짜 칸 (0~6). 왼쪽·오른쪽 바깥은 가장자리 칸으로 본다 */
    public int dayAt(int x) {
        int day = (int) ((long) (x - gutter) * DAYS / Math.max(1, width - gutter));
        return Math.max(0, Math.min(DAYS - 1, day));
    }

    /** 분(0~1440)의 세로 위치 */
    public int yOf(int minute) {
        return pad + (int) Math.round(minute * hourHeight / 60.0);
    }

    /** 세로 위치가 가리키는 분 (0~1440으로 가둠, 1분 단위로 반올림) */
    public int minuteAt(int y) {
        return Math.max(0, Math.min(TimeSnap.DAY, (int) Math.round((y - pad) * 60.0 / hourHeight)));
    }

    /** 세로로 dy 픽셀 움직인 것을 분으로 바꾼다 */
    public int minutesOf(int dy) {
        return (int) Math.round(dy * 60.0 / hourHeight);
    }
}
