package proto.server;

/**
 * [저장소 repository] JSON 글자 구조가 깨졌을 때(닫히지 않은 따옴표, 잘못된 이스케이프, 배열 끝의 쉼표 등) 던진다. 서버 대비 코드(현재 사용 안 함).
 * JsonCodec이 던지고, JSON 변환(ScheduleJsonMapper, CategoryJsonMapper)을 거쳐 Syncing*Store가 받는다.
 * IllegalArgumentException을 상속하므로 다른 형식 오류와 같은 길로 "서버 데이터를 쓰지 않고 이 컴퓨터 것을 유지"로 처리된다.
 */
public class JsonSyntaxException extends IllegalArgumentException {

    /** @param reason 깨진 이유 (한국어 문장. 몇 번째 글자인지 포함될 수 있다) */
    public JsonSyntaxException(String reason) {
        super("JSON 형식이 아니에요: " + reason);
    }
}
