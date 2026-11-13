package proto.server;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonCodecTest {

    @Test
    void roundTripsNestedValuesKoreanAndEscapes() {
        List<Object> inner = new ArrayList<>();
        inner.add(1L);
        inner.add(null);
        inner.add("한");
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("inner", inner);

        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("name", "한글");
        obj.put("tricky", "말 \"인용\" \\끝\n줄\t탭\u0001");
        obj.put("ok", true);
        obj.put("no", false);
        obj.put("nil", null);
        obj.put("n", 42L);
        obj.put("pi", 1.5d);
        obj.put("neg", -2L);
        obj.put("emptyArr", List.of());
        obj.put("emptyObj", new LinkedHashMap<>());
        obj.put("nested", nested);

        String json = JsonCodec.write(obj);
        assertTrue(json.contains("한글"));
        assertEquals(obj, JsonCodec.parse(json));
    }

    @Test
    void parsesEscapedUnicodeLiteralKoreanAndEmptyContainers() {
        // JSON 원문은 역슬래시-u 와 16진수 네 자리(한글)다. 자바 소스가 먼저 해석하지 않게 이어 붙인다.
        String escaped = "\"" + "\\" + "uD55C" + "\\" + "uAE00\"";
        assertEquals("한글", JsonCodec.parse(escaped));
        assertEquals("한/글", JsonCodec.parse("\"한" + "\\" + "/글\""));

        Object parsed = JsonCodec.parse("{ \"empty\" : [ ] , \"obj\" : { } , \"n\" : null , \"ok\" : false }");
        Map<?, ?> root = (Map<?, ?>) parsed;
        assertEquals(List.of(), root.get("empty"));
        assertEquals(Map.of(), root.get("obj"));
        assertNull(root.get("n"));
        assertEquals(Boolean.FALSE, root.get("ok"));
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(JsonSyntaxException.class, () -> JsonCodec.parse(""));
        assertThrows(JsonSyntaxException.class, () -> JsonCodec.parse("{"));
        assertThrows(JsonSyntaxException.class, () -> JsonCodec.parse("{\"a\":1,}"));
        assertThrows(JsonSyntaxException.class, () -> JsonCodec.parse("[1,]"));
        assertThrows(JsonSyntaxException.class, () -> JsonCodec.parse("\"unterminated"));
        assertThrows(JsonSyntaxException.class, () -> JsonCodec.parse("{'a':1}"));
        assertThrows(JsonSyntaxException.class, () -> JsonCodec.parse("01"));
        assertThrows(JsonSyntaxException.class, () -> JsonCodec.parse("\"" + "\\" + "u12\""));
        assertThrows(JsonSyntaxException.class, () -> JsonCodec.parse("\"" + "\\" + "q\""));
    }

    @Test
    void rejectsNestingDeeperThanLimitInsteadOfOverflowingTheStack() {
        String deepArray = "[".repeat(20_000) + "]".repeat(20_000);
        assertThrows(JsonSyntaxException.class, () -> JsonCodec.parse(deepArray));
        String deepIgnored = "{\"schedules\":[],\"ignored\":" + deepArray + "}";
        assertThrows(JsonSyntaxException.class, () -> JsonCodec.parse(deepIgnored));
    }

    @Test
    void acceptsNestingUpToTheLimitOnly() {
        int limit = JsonCodec.MAX_DEPTH;
        JsonCodec.parse("[".repeat(limit) + "]".repeat(limit));
        assertThrows(JsonSyntaxException.class,
                () -> JsonCodec.parse("[".repeat(limit + 1) + "]".repeat(limit + 1)));
    }

    @Test
    void unicodeEscapeAcceptsOnlyAsciiHexDigits() {
        // 전각 숫자 １２３４는 Character.digit이 받아 주던 값이다. 자바 소스가 먼저 해석하지 않게 이어 붙인다.
        assertThrows(JsonSyntaxException.class, () -> JsonCodec.parse("\"" + "\\" + "u\uFF11\uFF12\uFF13\uFF14\""));
        assertThrows(JsonSyntaxException.class, () -> JsonCodec.parse("\"" + "\\" + "u00g0\""));
        assertEquals("\u00e9\u00E9", JsonCodec.parse("\"" + "\\" + "u00e9" + "\\" + "u00E9\""));
    }
}
