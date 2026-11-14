package proto.ui.theme;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * [경계 boundary] 글자 폭 재기와 그리기에 쓰는 글꼴 대체(fallback) 도우미.
 * TextUtil(말줄임·줄바꿈), TokenLabel, StrikeLabel, PillList 등 글자 폭을 재거나 그리는 곳이 모두 쓴다. 폭을 잴 때와 그릴 때 같은 글꼴을 써야 말줄임 위치가 어긋나지
 * 않는다.
 * 번들 글꼴(Pretendard)에 없는 글자(이모지 등)는 자바 기본 논리 글꼴(Dialog)로 그려서 네모(□)로 깨지지 않게 한다.
 */
public final class Glyphs {

    private static final Map<String, Font> FALLBACKS = new HashMap<>();

    private Glyphs() {
    }

    /** 글꼴로 그릴 수 없는 글자가 하나라도 있는지 */
    public static boolean needsFallback(Font font, String text) {
        return text != null && !text.isEmpty() && font.canDisplayUpTo(text) >= 0;
    }

    /** 같은 크기·굵기의 대체 글꼴 (시스템 논리 글꼴이라 이모지·기호가 들어 있다) */
    public static Font fallbackFor(Font primary) {
        String key = primary.getStyle() + ":" + primary.getSize2D();
        return FALLBACKS.computeIfAbsent(key,
                k -> new Font(Font.DIALOG, primary.getStyle(), 1).deriveFont(primary.getSize2D()));
    }

    /** text를 그릴 때의 가로 길이. 대체 글꼴이 필요한 글자도 실제 그려질 폭으로 잰다 */
    public static int width(FontMetrics fm, String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        Font primary = fm.getFont();
        if (!needsFallback(primary, text)) {
            return fm.stringWidth(text);
        }
        FontRenderContext frc = fm.getFontRenderContext();
        double sum = 0;
        for (Run r : runs(primary, text)) {
            sum += r.fallback ? fallbackFor(primary).getStringBounds(r.text, frc).getWidth() : fm.stringWidth(r.text);
        }
        return (int) Math.ceil(sum);
    }

    /** g2의 현재 글꼴로 text를 (x, baseline)에 그린다. 없는 글자는 대체 글꼴로 */
    public static void draw(Graphics2D g2, String text, int x, int y) {
        Font primary = g2.getFont();
        if (!needsFallback(primary, text)) {
            g2.drawString(text, x, y);
            return;
        }
        int cx = x;
        for (Run r : runs(primary, text)) {
            g2.setFont(r.fallback ? fallbackFor(primary) : primary);
            g2.drawString(r.text, cx, y);
            cx += g2.getFontMetrics().stringWidth(r.text);
        }
        g2.setFont(primary);
    }

    /** 한 줄 안의 같은 글꼴로 그릴 덩어리 */
    private record Run(String text, boolean fallback) {
    }

    private static List<Run> runs(Font primary, String text) {
        List<Run> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean curFallback = false;
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            boolean joiner = cp == 0x200D || (cp >= 0xFE00 && cp <= 0xFE0F) || (cp >= 0x1F3FB && cp <= 0x1F3FF);
            boolean fb = joiner ? curFallback && cur.length() > 0 : !primary.canDisplay(cp);
            if (cur.length() > 0 && fb != curFallback) {
                out.add(new Run(cur.toString(), curFallback));
                cur.setLength(0);
            }
            curFallback = fb;
            cur.appendCodePoint(cp);
        }
        if (cur.length() > 0) {
            out.add(new Run(cur.toString(), curFallback));
        }
        return out;
    }
}
