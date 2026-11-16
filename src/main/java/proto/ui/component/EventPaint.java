package proto.ui.component;

import proto.entity.Occurrence;
import proto.ui.theme.CategoryColors;
import proto.ui.theme.EventColors;
import proto.ui.theme.ThemeManager;
import proto.ui.theme.Tokens;

import java.awt.Color;

/**
 * [경계 boundary] 시간표·달력·목록이 일정을 칠할 때 쓰는 바탕색·글자색을 한곳에서 정한다 (노션 캘린더처럼 연한 바탕과 진한 바탕).
 * TimelineBlock, AllDayChip, MonthPill이 같은 규칙을 쓰도록 모았다 (F2, F7). 시간이 있는 일정은 일정 색(일정만의 색, 없으면 카테고리 색)의 연한 바탕(tint)에 같은 계열의 진한 글자,
 * 하루 종일 일정과 고른 일정은 진한 바탕(solid)에 흰색/검정 글자이다. 마우스를 올리면 바탕이 조금 더 진해진다.
 * 어떤 카테고리 색이든 본문 글자가 바탕 위에서 WCAG 대비 4.5:1 이상이 되도록, 모자라면 글자를(진한 바탕은 바탕을) 조금씩 어둡게/밝게 조정한다.
 * 완료한 일정은 대비를 일부러 낮춘 흐린 색이다 (취소선과 함께 쓴다).
 */
public final class EventPaint {

    /** 본문 글자에 요구하는 최소 대비 (WCAG AA) */
    public static final double MIN_CONTRAST = 4.5;
    private static final float HOVER_TINT = 0.12f;
    private static final float HOVER_SOLID = 0.10f;
    private static final float DONE_FADE = 0.45f;
    /** 대비가 모자랄 때 글자·바탕을 한 번에 옮기는 비율과 최대 횟수 */
    private static final float CONTRAST_STEP = 0.04f;
    private static final int CONTRAST_TRIES = 25;

    private EventPaint() {
    }

    // ---- 색 조회는 아래 세 메서드만 거친다 (일정만의 색이 있으면 그 색, 없으면 카테고리 색: EventColors가 정한다) ----

    /** 일정의 기본 색 (왼쪽 막대, 하루 종일 바탕, 목록의 색 네모, 점) */
    static Color base(Occurrence o) {
        return EventColors.of(o);
    }

    private static Color tint(Occurrence o) {
        return EventColors.tint(o);
    }

    private static Color tintText(Occurrence o) {
        return EventColors.tintText(o);
    }

    /** 시간이 있는 일정의 왼쪽 막대 색: 기본 색 그대로 (완료하면 배경 쪽으로 옅어진다) */
    public static Color barColor(Occurrence o) {
        Color bar = base(o);
        return o.completed() ? Tokens.blend(bar, Tokens.get(Tokens.BG), DONE_FADE) : bar;
    }

    /** 시간이 있는 일정의 연한 바탕. hover(0~1)가 커질수록 카테고리 색 쪽으로 조금 진해진다. 완료하면 배경 쪽으로 옅어진다 */
    public static Color tintFill(Occurrence o, float hover) {
        Color tint = tint(o);
        Color fill = Tokens.blend(tint, base(o), HOVER_TINT * clamp(hover));
        return o.completed() ? Tokens.blend(fill, Tokens.get(Tokens.BG), DONE_FADE * 0.6f) : fill;
    }

    /** 진한 바탕(하루 종일, 고른 일정). 흰 글자/검은 글자가 4.5:1이 되도록 모자라면 바탕을 조금 어둡게(밝게) 한다. hover가 크면 대비가 더 커지는 쪽으로 조금 움직인다 */
    public static Color solidFill(Occurrence o, float hover) {
        Color base = legible(base(o));
        // 마우스를 올리면 글자와 대비가 커지는 쪽으로 움직인다 (흰 글자면 더 어둡게, 어두운 글자면 더 밝게)
        Color toward = CategoryColors.onColor(base).equals(Color.WHITE) ? Color.BLACK : Color.WHITE;
        Color fill = Tokens.blend(base, toward, HOVER_SOLID * clamp(hover));
        return o.completed() ? Tokens.blend(fill, Tokens.get(Tokens.BG), DONE_FADE * 0.5f) : fill;
    }

    /** 연한 바탕 위의 제목 글자색 (대비 4.5:1 이상. 완료하면 일부러 흐리게) */
    public static Color tintTitle(Occurrence o, Color fill) {
        Color text = ensure(tintText(o), fill);
        return o.completed() ? Tokens.blend(text, fill, DONE_FADE) : text;
    }

    /** 연한 바탕 위의 시간 글자색: 제목보다 아주 조금 옅다 (그래도 4.5:1 이상. 완료하면 일부러 흐리게) */
    public static Color tintSub(Occurrence o, Color fill) {
        Color text = ensure(Tokens.blend(tintText(o), fill, 0.12f), fill);
        return o.completed() ? Tokens.blend(text, fill, DONE_FADE) : text;
    }

    /** 진한 바탕 위의 글자색 (흰색/거의 검정. 완료하면 일부러 흐리게) */
    public static Color solidText(Occurrence o, Color fill) {
        Color text = CategoryColors.onColor(fill);
        return o.completed() ? Tokens.blend(text, fill, DONE_FADE) : text;
    }

    /** 연한 바탕 위의 "지연" 같은 경고 글자색 (대비 4.5:1 이상) */
    public static Color warning(Color fill) {
        return ensure(Tokens.get(Tokens.DANGER), fill);
    }

    /** 흰색 또는 거의 검정 글자가 4.5:1 이상 읽히는 바탕이 될 때까지 base를 조금씩 어둡게(밝은 글자) 또는 밝게(어두운 글자) 한다 */
    static Color legible(Color base) {
        Color text = CategoryColors.onColor(base);
        Color toward = text.equals(Color.WHITE) ? Color.BLACK : Color.WHITE;
        Color fill = base;
        for (int i = 1; i <= CONTRAST_TRIES && contrast(text, fill) < MIN_CONTRAST; i++) {
            fill = Tokens.blend(base, toward, i * CONTRAST_STEP);
        }
        return fill;
    }

    /** fg가 bg 위에서 4.5:1 미만이면 글자색을 밝은 테마는 검정 쪽, 어두운 테마는 흰색 쪽으로 조금씩 옮긴다 */
    static Color ensure(Color fg, Color bg) {
        Color toward = ThemeManager.isDark() ? Color.WHITE : Color.BLACK;
        Color out = fg;
        for (int i = 1; i <= CONTRAST_TRIES && contrast(out, bg) < MIN_CONTRAST; i++) {
            out = Tokens.blend(fg, toward, i * CONTRAST_STEP);
        }
        return out;
    }

    /** WCAG 대비 비율 (1~21) */
    public static double contrast(Color a, Color b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(Color c) {
        return 0.2126 * channel(c.getRed()) + 0.7152 * channel(c.getGreen()) + 0.0722 * channel(c.getBlue());
    }

    private static double channel(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
