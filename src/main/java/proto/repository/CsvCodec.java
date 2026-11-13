package proto.repository;

import java.util.ArrayList;
import java.util.List;

/**
 * [저장소 repository] CSV 문자열과 행/칸 목록을 서로 바꾼다 (RFC 4180 방식).
 * 쉼표·따옴표·줄바꿈이 든 칸은 따옴표로 감싸고, 안쪽 따옴표는 두 번 쓴다. 따옴표 안의 줄바꿈은 여러 줄에 걸쳐도 읽는다.
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

    /** CSV 문자열을 행 목록으로 읽는다. 빈 줄은 건너뛰고, 줄바꿈은 \n, \r\n, \r 모두 받는다 */
    public static List<List<String>> parse(String text) {
        Parser parser = new Parser(text.startsWith("﻿") ? text.substring(1) : text);
        return parser.parse();
    }

    /** 문자 하나씩 읽으며 칸과 행을 쌓는 상태 기계 */
    private static final class Parser {
        private final String text;
        private final List<List<String>> rows = new ArrayList<>();
        private List<String> row = new ArrayList<>();
        private final StringBuilder field = new StringBuilder();
        private boolean quoted;
        private boolean fieldWasQuoted;

        Parser(String text) {
            this.text = text;
        }

        List<List<String>> parse() {
            for (int i = 0; i < text.length(); i++) {
                i = quoted ? readQuoted(i) : readPlain(i);
            }
            if (quoted || fieldWasQuoted || field.length() > 0 || !row.isEmpty()) {
                endRow();
            }
            return rows;
        }

        /** 따옴표 안의 문자 처리. 처리한 마지막 위치를 돌려준다 */
        private int readQuoted(int i) {
            char c = text.charAt(i);
            if (c != '"') {
                field.append(c);
            } else if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                field.append('"');
                return i + 1;
            } else {
                quoted = false;
            }
            return i;
        }

        private int readPlain(int i) {
            char c = text.charAt(i);
            if (c == '"') {
                quoted = true;
                fieldWasQuoted = true;
            } else if (c == ',') {
                endField();
            } else if (c == '\n' || c == '\r') {
                boolean crlf = c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n';
                endRow();
                return crlf ? i + 1 : i;
            } else {
                field.append(c);
            }
            return i;
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
