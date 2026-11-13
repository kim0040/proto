package proto.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * [서버 대비 — 현재 사용 안 함] JsonCodec가 돌려준 값을 맵·목록으로 좁힌다.
 * [저장소 repository] 키가 문자열이 아니거나 배열이 아니면 예외를 던진다.
 */
final class JsonValues {

    private JsonValues() {
    }

    static Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("JSON 객체가 필요해요");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("JSON 객체의 키가 문자열이 아니에요");
            }
            out.put(key, entry.getValue());
        }
        return out;
    }

    static List<Object> asList(Object value) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("JSON 배열이 필요해요");
        }
        return new ArrayList<>(list);
    }
}
