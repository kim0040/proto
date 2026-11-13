package proto.repository;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CsvCodecTest {

    @Test
    void plainFieldsAreNotQuoted() {
        assertEquals("a,b,한글\n", CsvCodec.encode(List.of(List.of("a", "b", "한글"))));
    }

    @Test
    void fieldsWithCommaQuoteOrNewlineAreQuotedAndInnerQuotesDoubled() {
        String csv = CsvCodec.encode(List.of(List.of("a,b", "say \"hi\"", "줄\n바꿈")));
        assertEquals("\"a,b\",\"say \"\"hi\"\"\",\"줄\n바꿈\"\n", csv);
    }

    @Test
    void parseReadsQuotedNewlinesAcrossLines() {
        List<List<String>> rows = CsvCodec.parse("id,memo\n1,\"첫 줄\n둘째 줄, 쉼표\"\n2,끝\n");
        assertEquals(List.of(List.of("id", "memo"), List.of("1", "첫 줄\n둘째 줄, 쉼표"), List.of("2", "끝")), rows);
    }

    @Test
    void roundTripKeepsTrickyValues() {
        List<List<String>> rows = List.of(
                List.of("제목, 쉼표", "따옴표 \"안녕\"", "줄1\n줄2", "윈도우\r\n줄바꿈", "", "한글 ✓"),
                List.of("", "", ""),
                List.of("\"", ",", "\n"));
        assertEquals(rows, CsvCodec.parse(CsvCodec.encode(rows)));
    }

    @Test
    void emptyFieldsAndTrailingEmptyFieldAreKept() {
        assertEquals(List.of(List.of("a", "", "c"), List.of("d", "")), CsvCodec.parse("a,,c\nd,\n"));
    }

    @Test
    void blankLinesAreSkippedAndAllLineEndingsAccepted() {
        assertEquals(List.of(List.of("a", "b"), List.of("c", "d"), List.of("e")),
                CsvCodec.parse("a,b\r\n\r\nc,d\r\n\ne\n"));
        assertEquals(List.of(List.of("a"), List.of("b")), CsvCodec.parse("a\rb"));
    }

    @Test
    void lastRowWithoutNewlineAndBomAreHandled() {
        assertEquals(List.of(List.of("a", "b")), CsvCodec.parse("﻿a,b"));
    }

    @Test
    void emptyTextHasNoRows() {
        assertEquals(List.of(), CsvCodec.parse(""));
    }

    @Test
    void unterminatedQuoteReportsTheLineWhereItOpened() {
        CsvSyntaxException ex = assertThrows(CsvSyntaxException.class, () -> CsvCodec.parse("a,b\n\"열림\n"));
        assertEquals(2, ex.lineNumber());
    }

    @Test
    void quoteInsideAnUnquotedFieldIsRejected() {
        CsvSyntaxException ex = assertThrows(CsvSyntaxException.class, () -> CsvCodec.parse("a,b\nc,d\"e\n"));
        assertEquals(2, ex.lineNumber());
    }

    @Test
    void textAfterAClosingQuoteIsRejected() {
        CsvSyntaxException ex = assertThrows(CsvSyntaxException.class, () -> CsvCodec.parse("ok\n\"ab\"x\n"));
        assertEquals(2, ex.lineNumber());
    }
}
