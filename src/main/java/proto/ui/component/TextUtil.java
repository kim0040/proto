package proto.ui.component;

import proto.ui.theme.Glyphs;

import java.awt.FontMetrics;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;

/**
 * [경계 boundary] 글자를 주어진 너비에 맞춰 "…"로 줄이고, 화면에 안전하게 보이도록 다듬는 도우미.
 * 이모지·한글 조합 글자는 쪼개지 않고, 번들 글꼴에 없는 글자는 대체 글꼴 폭으로 잰다.
 */
public final class TextUtil {

    private static final String DOTS = "…";

    private TextUtil() {
    }

    /** 화면에 한 줄로 보여 줄 글자: 줄바꿈·탭 같은 제어 문자를 공백 하나로, 앞뒤 공백은 뗀다 */
    public static String clean(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("[\\p{Cntrl}\\s&&[^ ]]+", " ").replaceAll(" {2,}", " ").strip();
    }

    /** JLabel에 사용자가 쓴 글을 그대로 넣을 때: "&lt;html&gt;"로 시작해도 HTML로 해석되지 않게 한다 */
    public static String plain(String text) {
        String s = clean(text);
        return s.regionMatches(true, 0, "<html", 0, 5) ? "​" + s : s;
    }

    /** 툴팁에 넣을 전체 글. 길면 적당한 너비로 줄바꿈되고, HTML 문자는 무력화한다 */
    public static String tip(String... lines) {
        StringBuilder sb = new StringBuilder("<html><body style='width:280px'>");
        boolean first = true;
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            sb.append(first ? "" : "<br>").append(escape(line.strip()));
            first = false;
        }
        return first ? null : sb.append("</body></html>").toString();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }

    /** text가 width보다 길면 끝을 "…"로 줄인 문자열 */
    public static String ellipsize(String text, FontMetrics fm, int width) {
        String s = clean(text);
        if (Glyphs.width(fm, s) <= width) {
            return s;
        }
        int limit = width - Glyphs.width(fm, DOTS);
        if (limit <= 0) {
            return "";
        }
        int lo = 0;
        int hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (Glyphs.width(fm, s.substring(0, mid)) <= limit) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return s.substring(0, boundary(s, lo)).stripTrailing() + DOTS;
    }

    /** 가운데를 "…"로 줄인다 (경로처럼 끝이 중요한 글) */
    public static String ellipsizeMiddle(String text, FontMetrics fm, int width) {
        String s = clean(text);
        if (Glyphs.width(fm, s) <= width) {
            return s;
        }
        int keepTail = Math.max(8, s.length() / 3);
        String tail = s.substring(boundary(s, Math.max(0, s.length() - keepTail)));
        String head = ellipsize(s, fm, width - Glyphs.width(fm, tail));
        return head.isEmpty() ? DOTS + tail : head + tail;
    }

    /** index 이하에서 가장 가까운 글자(이모지·조합 글자 한 덩어리) 경계 */
    private static int boundary(String s, int index) {
        if (index <= 0 || index >= s.length()) {
            return Math.max(0, Math.min(index, s.length()));
        }
        BreakIterator it = BreakIterator.getCharacterInstance();
        it.setText(s);
        return it.isBoundary(index) ? index : Math.max(0, it.preceding(index));
    }

    /** 메모의 첫 줄만 (비어 있으면 빈 문자열) */
    public static String firstLine(String memo) {
        if (memo == null) {
            return "";
        }
        String trimmed = memo.strip();
        int nl = trimmed.indexOf('\n');
        return clean(nl < 0 ? trimmed : trimmed.substring(0, nl));
    }

    /** 글자를 width에 맞춰 최대 maxLines줄로 나눈다 (한글은 글자 단위, 넘치면 마지막 줄 끝을 …로) */
    public static List<String> wrap(String text, FontMetrics fm, int width, int maxLines) {
        List<String> lines = new ArrayList<>();
        String rest = clean(text);
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
        int lo = 1;
        int hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (Glyphs.width(fm, s.substring(0, mid)) <= width) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        int n = Math.max(1, boundary(s, lo));
        if (n < s.length()) {
            int space = s.lastIndexOf(' ', n);
            if (space > n / 2) {
                return space;
            }
        }
        return n;
    }
}
