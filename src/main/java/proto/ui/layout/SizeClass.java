package proto.ui.layout;

/**
 * [경계 boundary] 창 본문 크기를 논리 px 기준으로 나눈 크기 등급. 너비 3단계와 세로 모양(세로로 긴 화면, 낮은 화면)을 함께 담는다.
 *
 * @param width        너비 등급
 * @param tall         세로로 긴 화면 (높이 &gt; 너비 × 0.9)
 * @param shortHeight  낮은 화면 (높이 &lt; 620)
 */
public record SizeClass(Width width, boolean tall, boolean shortHeight) {

    /** 너비 등급: 1280 이상 WIDE, 1000~1279 MEDIUM, 1000 미만 COMPACT */
    public enum Width { WIDE, MEDIUM, COMPACT }

    /** 사이드바가 아이콘 띠로 줄어드는 좁은 화면인지 */
    public boolean compact() {
        return width == Width.COMPACT;
    }

    /** 세로로 길거나 낮아서 세로 공간을 아껴야 하는 화면인지 */
    public boolean dense() {
        return tall || shortHeight;
    }

    /** 달력의 "고른 날" 패널을 격자 아래에 둘지 */
    public boolean dayPanelBelow() {
        return compact() || dense();
    }

    /** 달력 칸에 글자 대신 점과 개수를 보여 줄지 */
    public boolean monthDots() {
        return dense();
    }

    /** 사이드바 너비 (논리 px). 좁은 화면은 아이콘 띠 너비 */
    public int sidebarWidth() {
        return switch (width) {
            case WIDE -> 232;
            case MEDIUM -> 208;
            case COMPACT -> Breakpoints.RAIL_WIDTH;
        };
    }

    /** 달력 옆 패널 너비 (논리 px) */
    public int dayPanelWidth() {
        return width == Width.WIDE ? 304 : 280;
    }

    /** 가로 바깥 여백 (논리 px) */
    public int gutter() {
        return compact() ? 20 : 32;
    }

    /** 세로 여백을 한 단계 줄이는 만큼 (논리 px) */
    public int verticalTrim() {
        return dense() ? 6 : 0;
    }
}
