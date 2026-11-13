package proto.ui.component;

import java.awt.FontMetrics;

/**
 * [경계 boundary] 글자를 주어진 너비에 맞춰 "…"로 줄이는 도우미.
 */
public final class TextUtil {

    private TextUtil() {
    }

    /** text가 width보다 길면 끝을 "…"로 줄인 문자열 */
    public static String ellipsize(String text, FontMetrics fm, int width) {
        if (text == null || fm.stringWidth(text) <= width) {
            return text == null ? "" : text;
        }
        String dots = "…";
        int limit = width - fm.stringWidth(dots);
        if (limit <= 0) {
            return "";
        }
        int lo = 0;
        int hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (fm.stringWidth(text.substring(0, mid)) <= limit) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return text.substring(0, lo).stripTrailing() + dots;
    }

    /** 메모의 첫 줄만 (비어 있으면 빈 문자열) */
    public static String firstLine(String memo) {
        if (memo == null) {
            return "";
        }
        int nl = memo.indexOf('\n');
        return (nl < 0 ? memo : memo.substring(0, nl)).strip();
    }
}
