package proto.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * [서버 대비 — 현재 사용 안 함] JSON 문자열과 자바 값(Map, List, String, Number, Boolean, null)을 서로 바꾼다.
 * [저장소 repository] 객체·배열·문자열·숫자·참/거짓·null만 다룬다. 외부 라이브러리는 쓰지 않는다.
 * 문자열의 따옴표, 역슬래시, 줄바꿈, 유니코드 이스케이프를 지키고, 형식이 깨지면 {@link JsonSyntaxException}을 던진다.
 */
public final class JsonCodec {

    /** 객체·배열이 이 깊이보다 더 겹치면 거절한다 (끝없는 중첩으로 스택이 넘치는 것을 막는다) */
    static final int MAX_DEPTH = 64;

    private JsonCodec() {
    }

    /** 객체의 키 순서는 넘긴 Map의 순서를 따른다 */
    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(value, out);
        return out.toString();
    }

    /** 앞뒤 공백은 허용한다. 값이 끝난 뒤에 글자가 더 있으면 예외 */
    public static Object parse(String json) {
        if (json == null) {
            throw new JsonSyntaxException("내용이 비어 있어요");
        }
        return new Parser(json).parseDocument();
    }

    private static void writeValue(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            writeString(text, out);
        } else if (value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Number number) {
            writeNumber(number, out);
        } else if (value instanceof Map<?, ?> map) {
            writeObject(map, out);
        } else if (value instanceof List<?> list) {
            writeArray(list, out);
        } else {
            throw new IllegalArgumentException("JSON으로 쓸 수 없는 값이에요");
        }
    }

    private static void writeNumber(Number number, StringBuilder out) {
        if (number instanceof Double || number instanceof Float) {
            double value = number.doubleValue();
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("JSON 숫자로 쓸 수 없어요");
            }
            out.append(value);
            return;
        }
        out.append(number);
    }

    private static void writeObject(Map<?, ?> map, StringBuilder out) {
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("JSON 객체의 키는 문자열이어야 해요");
            }
            if (!first) {
                out.append(',');
            }
            first = false;
            writeString(key, out);
            out.append(':');
            writeValue(entry.getValue(), out);
        }
        out.append('}');
    }

    private static void writeArray(List<?> list, StringBuilder out) {
        out.append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            writeValue(list.get(i), out);
        }
        out.append(']');
    }

    private static void writeString(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            writeChar(value.charAt(i), out);
        }
        out.append('"');
    }

    private static void writeChar(char c, StringBuilder out) {
        switch (c) {
            case '"' -> out.append("\\\"");
            case '\\' -> out.append("\\\\");
            case '\b' -> out.append("\\b");
            case '\f' -> out.append("\\f");
            case '\n' -> out.append("\\n");
            case '\r' -> out.append("\\r");
            case '\t' -> out.append("\\t");
            default -> writeRaw(c, out);
        }
    }

    /** 제어 문자는 유니코드 이스케이프로, 한글처럼 그 밖은 글자 그대로 쓴다 */
    private static void writeRaw(char c, StringBuilder out) {
        if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
            return;
        }
        out.append(c);
    }

    private static final class Parser {
        private final String text;
        private int index;
        private int depth;

        private Parser(String text) {
            this.text = text;
        }

        private Object parseDocument() {
            Object value = readValue();
            skipSpace();
            if (index != text.length()) {
                throw error("값이 끝난 뒤에 문자가 더 있어요");
            }
            return value;
        }

        private Object readValue() {
            skipSpace();
            if (index >= text.length()) {
                throw error("값이 비어 있어요");
            }
            return readToken(text.charAt(index));
        }

        private Object readToken(char c) {
            if (c == '{' || c == '[') {
                return readContainer(c);
            }
            if (c == '"') {
                return readString();
            }
            if (c == 't' || c == 'f') {
                return readBoolean();
            }
            if (c == 'n') {
                return readNull();
            }
            if (c == '-' || isDigit(c)) {
                return readNumber();
            }
            throw error("알 수 없는 값이에요");
        }

        /** 객체나 배열. 겹친 깊이를 세어 MAX_DEPTH를 넘으면 거절한다 (예외가 나면 파싱 전체가 끝난다) */
        private Object readContainer(char open) {
            if (++depth > MAX_DEPTH) {
                throw error("객체와 배열이 " + MAX_DEPTH + "겹보다 깊어요");
            }
            Object value = open == '{' ? readObject() : readArray();
            depth--;
            return value;
        }

        private Map<String, Object> readObject() {
            expect('{');
            Map<String, Object> object = new LinkedHashMap<>();
            if (skipClosing('}')) {
                return object;
            }
            while (true) {
                readEntry(object);
                if (skipClosing('}')) {
                    return object;
                }
                expect(',');
            }
        }

        private void readEntry(Map<String, Object> object) {
            skipSpace();
            if (!peek('"')) {
                throw error("객체의 키는 문자열이어야 해요");
            }
            String key = readString();
            skipSpace();
            expect(':');
            object.put(key, readValue());
        }

        private List<Object> readArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            if (skipClosing(']')) {
                return list;
            }
            while (true) {
                list.add(readValue());
                if (skipClosing(']')) {
                    return list;
                }
                expect(',');
            }
        }

        private String readString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (index < text.length()) {
                char c = text.charAt(index++);
                if (c == '"') {
                    return out.toString();
                }
                out.append(c == '\\' ? readEscape() : rawChar(c));
            }
            throw error("문자열이 닫히지 않았어요");
        }

        /** 문자열 안의 제어 문자는 이스케이프 없이 두면 거절한다 */
        private char rawChar(char c) {
            if (c < 0x20) {
                throw error("문자열에 제어 문자가 그대로 있어요");
            }
            return c;
        }

        private char readEscape() {
            if (index >= text.length()) {
                throw error("이스케이프가 끝나지 않았어요");
            }
            char c = text.charAt(index++);
            return switch (c) {
                case '"', '\\', '/' -> c;
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case 'u' -> readUnicode();
                default -> throw error("알 수 없는 이스케이프예요");
            };
        }

        private char readUnicode() {
            if (index + 4 > text.length()) {
                throw error("유니코드 이스케이프는 16진수 4자리가 필요해요");
            }
            int code = 0;
            for (int n = 0; n < 4; n++) {
                code = (code << 4) + hexDigit();
            }
            return (char) code;
        }

        private int hexDigit() {
            char c = text.charAt(index++);
            int digit = c >= '0' && c <= '9' ? c - '0'
                    : c >= 'a' && c <= 'f' ? c - 'a' + 10
                    : c >= 'A' && c <= 'F' ? c - 'A' + 10 : -1;
            if (digit < 0) {
                throw error("유니코드 이스케이프는 16진수 4자리가 필요해요");
            }
            return digit;
        }

        private Boolean readBoolean() {
            if (match("true")) {
                return Boolean.TRUE;
            }
            if (match("false")) {
                return Boolean.FALSE;
            }
            throw error("true나 false가 아니에요");
        }

        private Object readNull() {
            if (match("null")) {
                return null;
            }
            throw error("null이 아니에요");
        }

        private Number readNumber() {
            int start = index;
            if (peek('-')) {
                index++;
            }
            readIntDigits();
            boolean fractional = readFraction();
            return toNumber(text.substring(start, index), fractional);
        }

        /** 소수부나 지수가 있으면 true */
        private boolean readFraction() {
            boolean fractional = false;
            if (peek('.')) {
                fractional = true;
                index++;
                readFracDigits();
            }
            if (peek('e') || peek('E')) {
                fractional = true;
                index++;
                if (peek('+') || peek('-')) {
                    index++;
                }
                readFracDigits();
            }
            return fractional;
        }

        /** 정수부는 0으로 시작하면 그 한 자리만 허용한다 (01은 거절) */
        private void readIntDigits() {
            if (!hasDigit()) {
                throw error("숫자가 아니에요");
            }
            char first = text.charAt(index++);
            if (first == '0') {
                if (hasDigit()) {
                    throw error("숫자는 0으로 시작할 수 없어요");
                }
                return;
            }
            while (hasDigit()) {
                index++;
            }
        }

        private void readFracDigits() {
            if (!hasDigit()) {
                throw error("숫자가 아니에요");
            }
            while (hasDigit()) {
                index++;
            }
        }

        private Number toNumber(String token, boolean fractional) {
            try {
                if (!fractional) {
                    return Long.parseLong(token);
                }
                double value = Double.parseDouble(token);
                if (!Double.isFinite(value)) {
                    throw error("숫자를 읽지 못했어요");
                }
                return value;
            } catch (NumberFormatException e) {
                throw error("숫자를 읽지 못했어요");
            }
        }

        private boolean skipClosing(char closing) {
            skipSpace();
            if (!peek(closing)) {
                return false;
            }
            index++;
            return true;
        }

        private boolean match(String word) {
            if (!text.startsWith(word, index)) {
                return false;
            }
            index += word.length();
            return true;
        }

        private void expect(char c) {
            if (!peek(c)) {
                throw error(c + "가 필요해요");
            }
            index++;
        }

        private boolean peek(char c) {
            return index < text.length() && text.charAt(index) == c;
        }

        private boolean hasDigit() {
            return index < text.length() && isDigit(text.charAt(index));
        }

        private void skipSpace() {
            while (index < text.length() && isSpace(text.charAt(index))) {
                index++;
            }
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }

        private static boolean isSpace(char c) {
            return c == ' ' || c == '\n' || c == '\r' || c == '\t';
        }

        private JsonSyntaxException error(String reason) {
            return new JsonSyntaxException((index + 1) + "번째 글자: " + reason);
        }
    }
}
