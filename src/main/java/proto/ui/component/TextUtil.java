package proto.ui.component;

import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.List;

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

    /** 글자를 width에 맞춰 최대 maxLines줄로 나눈다 (한글은 글자 단위, 넘치면 마지막 줄 끝을 …로) */
    public static List<String> wrap(String text, FontMetrics fm, int width, int maxLines) {
        List<String> lines = new ArrayList<>();
        String rest = text == null ? "" : text.strip();
        while (!rest.isEmpty() && lines.size() < maxLines) {
            if (lines.size() == maxLines - 1) {
                lines.add(ellipsize(rest, fm, width));
                break;
            }
            int cut = fitLength(rest, fm, width);
            lines.add(rest.substring(0, cut).stripTrailing());
            rest = rest.substring(cut).stripLeading();
        }
        return lines;
    }

    private static int fitLength(String s, FontMetrics fm, int width) {
        int n = 1;
        while (n < s.length() && fm.stringWidth(s.substring(0, n + 1)) <= width) {
            n++;
        }
        if (n < s.length()) {
            int space = s.lastIndexOf(' ', n);
            if (space > n / 2) {
                return space;
            }
        }
        return n;
    }
}
