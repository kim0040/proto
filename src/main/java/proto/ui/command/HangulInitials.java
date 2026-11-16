package proto.ui.command;

import java.text.Normalizer;
import java.util.Locale;

/**
 * [경계 boundary] 한글 글자에서 첫 자음(초성)만 뽑는 작은 도우미: "설정 열기" → "ㅅㅈㅇㄱ".
 * CommandMatcher가 "ㅅㅈ"처럼 초성만 친 글자도 명령을 찾게 하려고 쓴다. 한글이 아닌 글자는 소문자로, 공백은 뺀다.
 */
public final class HangulInitials {

    private static final String CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
    private static final int FIRST = 0xAC00;
    private static final int LAST = 0xD7A3;
    private static final int PER_INITIAL = 588;
    /** 호환 자모 영역의 첫 자음 ㄱ과 마지막 ㅎ */
    private static final int JAMO_FIRST = 0x3131;
    private static final int JAMO_LAST = 0x314E;

    private HangulInitials() {
    }

    /** text의 각 글자를 초성으로 바꾼 글 (공백은 없앤다) */
    public static String of(String text) {
        String s = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFC);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= FIRST && c <= LAST) {
                out.append(CHO.charAt((c - FIRST) / PER_INITIAL));
            } else if (!Character.isWhitespace(c)) {
                out.append(String.valueOf(c).toLowerCase(Locale.ROOT));
            }
        }
        return out.toString();
    }

    /** text가 자음 글자(ㄱ~ㅎ)로만 되어 있는지 (비어 있으면 false) */
    public static boolean isInitialsOnly(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < JAMO_FIRST || c > JAMO_LAST) {
                return false;
            }
        }
        return true;
    }
}
