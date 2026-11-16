package proto.repository;

import java.util.ArrayList;
import java.util.List;

/**
 * [저장소 repository] CSV 문자열과 행/칸 목록을 서로 바꾼다 (RFC 4180 방식).
 * ScheduleRepository와 CategoryRepository가 저장할 때 encode, 불러올 때 parse를 부른다 (F11).
 * 엑셀이나 메모장으로 열어도 깨지지 않고 메모의 쉼표·줄바꿈이 한 칸으로 읽히도록 표준 CSV 규칙을 따른다.
 * 쉼표·따옴표·줄바꿈이 든 칸은 따옴표로 감싸고 안쪽 따옴표는 두 번 쓰며, 따옴표 안의 줄바꿈은 여러 줄에 걸쳐도 읽는다.
 */
public final class CsvCodec {

    private CsvCodec() {
    }

    /** 행마다 한 줄(\n 끝)로 이어 붙인 CSV 문자열 */
    public static String encode(List<List<String>> rows) {
        StringBuilder out = new StringBuilder();
        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                out.append(encodeField(row.get(i)));
            }
            out.append('\n');
        }
        return out.toString();
    }

    static String encodeField(String field) {
        if (field.indexOf(',') < 0 && field.indexOf('"') < 0
                && field.indexOf('\n') < 0 && field.indexOf('\r') < 0) {
            return field;
        }
        return '"' + field.replace("\"", "\"\"") + '"';
    }

    /**
     * CSV 문자열을 행 목록으로 읽는다. 빈 줄은 건너뛰고, 줄바꿈은 \n, \r\n, \r 모두 받는다.
     *
     * @throws CsvSyntaxException 따옴표가 닫히지 않았거나, 따옴표 없는 칸 중간에 따옴표가 있거나,
     *                            닫는 따옴표 뒤에 쉼표·줄바꿈이 아닌 글자가 올 때
     */
    public static List<List<String>> parse(String text) {
        Parser parser = new Parser(text.startsWith("\uFEFF") ? text.substring(1) : text);
        return parser.parse();
    }

    /** 문자 하나씩 읽으며 칸과 행을 쌓는 상태 기계. 줄 번호는 오류 위치를 알려 주려고 센다 */
    private static final class Parser {
        private final String text;
        private final List<List<String>> rows = new ArrayList<>();
        private List<String> row = new ArrayList<>();
        private final StringBuilder field = new StringBuilder();
        private boolean quoted;
        private boolean fieldWasQuoted;
        private int line = 1;
        private int quoteStartLine;

        Parser(String text) {
            this.text = text;
        }

        List<List<String>> parse() {
            for (int i = 0; i < text.length(); i++) {
                i = quoted ? readQuoted(i) : readPlain(i);
            }
            if (quoted) {
                throw new CsvSyntaxException(quoteStartLine, "따옴표가 닫히지 않았어요");
            }
            if (fieldWasQuoted || field.length() > 0 || !row.isEmpty()) {
                endRow();
            }
            return rows;
        }

        /**
         * 따옴표 안의 문자 하나를 처리하고, 처리를 끝낸 마지막 위치를 돌려준다.
         * 쉼표·줄바꿈도 그냥 글자로 읽는다. 따옴표 두 개("")는 따옴표 글자 하나이고, 따옴표 하나는 칸의 끝이다.
         */
        private int readQuoted(int i) {
            char c = text.charAt(i);
            if (c != '"') {
                field.append(c);
                countLine(i);
            } else if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                field.append('"');
                return i + 1;
            } else {
                quoted = false;
            }
            return i;
        }

        /** 따옴표 밖의 글자를 처리한다. 쉼표는 칸의 끝, 줄바꿈은 행의 끝이다. \r\n은 한 번의 줄바꿈으로 센다 */
        private int readPlain(int i) {
            char c = text.charAt(i);
            if (c == ',') {
                endField();
            } else if (c == '\n' || c == '\r') {
                boolean crlf = c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n';
                endRow();
                line++;
                return crlf ? i + 1 : i;
            } else if (fieldWasQuoted) {
                throw new CsvSyntaxException(line, "닫는 따옴표 뒤에 다른 글자가 있어요");
            } else if (c == '"') {
                openQuote();
            } else {
                field.append(c);
            }
            return i;
        }

        /** 따옴표는 칸의 맨 앞에서만 열 수 있다 */
        private void openQuote() {
            if (field.length() > 0) {
                throw new CsvSyntaxException(line, "따옴표 없는 칸 중간에 따옴표가 있어요");
            }
            quoted = true;
            fieldWasQuoted = true;
            quoteStartLine = line;
        }

        /** 따옴표 안의 줄바꿈도 줄 번호에 센다 (\r\n은 한 번) */
        private void countLine(int i) {
            char c = text.charAt(i);
            boolean crBeforeLf = c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n';
            if ((c == '\n' || c == '\r') && !crBeforeLf) {
                line++;
            }
        }

        private void endField() {
            row.add(field.toString());
            field.setLength(0);
            fieldWasQuoted = false;
        }

        /** 행을 마무리한다. 칸이 하나뿐이고 비어 있는 줄(빈 줄)은 버린다 */
        private void endRow() {
            boolean blankLine = row.isEmpty() && field.length() == 0 && !fieldWasQuoted;
            endField();
            if (!blankLine) {
                rows.add(row);
            }
            row = new ArrayList<>();
        }
    }
}
