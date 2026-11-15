package proto.ui.command;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * [경계 boundary] 명령 메뉴에서 입력한 글에 맞는 항목을 고르고 잘 맞는 순서로 줄 세운다.
 * CommandCatalog가 쓴다. 규칙: 대소문자와 띄어쓰기를 무시하고, 이름 앞부분이 같으면 가장 위, 이름 안에 들어 있으면 그다음, 낱말(keywords)에만 있으면 그다음이다.
 * 입력이 자음만(ㅅㅈ)이면 이름의 초성과 맞춰 본다. 입력이 비어 있으면 모든 항목을 원래 순서대로 돌려준다. 같은 점수는 원래 순서를 지킨다.
 */
public final class CommandMatcher {

    static final int NO_MATCH = -1;
    private static final int PREFIX = 100;
    private static final int CONTAINS = 90;
    private static final int KEYWORD = 50;
    private static final int INITIALS_PREFIX = 70;
    private static final int INITIALS = 65;
    private static final int INITIALS_KEYWORD = 40;

    private CommandMatcher() {
    }

    /** 입력에 맞는 항목만 점수가 높은 것부터 돌려준다 */
    public static List<Command> filter(List<Command> all, String query) {
        String q = normalize(query);
        if (q.isEmpty()) {
            return new ArrayList<>(all);
        }
        List<Command> found = new ArrayList<>();
        List<Integer> scores = new ArrayList<>();
        for (Command c : all) {
            int s = score(q, c);
            if (s != NO_MATCH) {
                found.add(c);
                scores.add(s);
            }
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < found.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingInt((Integer i) -> -scores.get(i)));
        List<Command> sorted = new ArrayList<>();
        order.forEach(i -> sorted.add(found.get(i)));
        return sorted;
    }

    /** 이미 정리한 입력 q가 c와 얼마나 맞는지 (맞지 않으면 NO_MATCH) */
    static int score(String q, Command c) {
        String title = normalize(c.title());
        String keywords = normalize(c.keywords());
        if (title.startsWith(q)) {
            return PREFIX;
        }
        int at = title.indexOf(q);
        if (at >= 0) {
            return CONTAINS - Math.min(at, 20);
        }
        if (keywords.contains(q)) {
            return KEYWORD;
        }
        return HangulInitials.isInitialsOnly(q) ? initialsScore(q, title, keywords) : NO_MATCH;
    }

    private static int initialsScore(String q, String title, String keywords) {
        String initials = HangulInitials.of(title);
        if (initials.startsWith(q)) {
            return INITIALS_PREFIX;
        }
        if (initials.contains(q)) {
            return INITIALS;
        }
        return HangulInitials.of(keywords).contains(q) ? INITIALS_KEYWORD : NO_MATCH;
    }

    /** 소문자로, 공백 없이, 한글은 완성형으로 */
    static String normalize(String text) {
        String s = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFC);
        return s.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
