package proto.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * [저장소 repository] JsonCodec이 돌려준 값을 맵·목록으로 좁힌다. 서버 대비 코드(현재 사용 안 함).
 * ScheduleJsonMapper와 CategoryJsonMapper가 JSON을 읽을 때 쓴다. JsonCodec.parse는 어떤 값이든 Object로 돌려주므로,
 * "이 자리는 객체/배열이어야 한다"를 확인하고 아니면 IllegalArgumentException을 던져 이상한 서버 데이터를 일찍 거절한다.
 */
final class JsonValues {

    private JsonValues() {
    }

    /** value가 JSON 객체(Map)이고 모든 키가 문자열이면 맵으로 돌려준다. 아니면 IllegalArgumentException이다 */
    static Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("JSON 객체가 필요해요");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("JSON 객체의 키가 문자열이 아니에요");
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    /** value가 JSON 배열(List)이면 목록으로 돌려준다. 아니면 IllegalArgumentException이다 */
    static List<Object> asList(Object value) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("JSON 배열이 필요해요");
        }
        return new ArrayList<>(list);
    }
}
