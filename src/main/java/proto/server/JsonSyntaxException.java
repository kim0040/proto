package proto.server;

/**
 * [서버 대비 — 현재 사용 안 함] JSON 글자 구조가 깨졌을 때 던진다.
 * [저장소 repository] 닫히지 않은 따옴표, 잘못된 이스케이프, 배열 끝의 쉼표처럼 형식이 아닌 입력을 거절한다.
 * JsonCodec이 던지고, 일정·카테고리 JSON 변환(ScheduleJsonMapper, CategoryJsonMapper)을 거쳐 Syncing*Store가 받는다. IllegalArgumentException을 상속하므로
 * 저장소는 다른 형식 오류(RuntimeException)와 같은 길로 "서버 데이터를 쓰지 않고 이 컴퓨터 것을 유지"로 처리한다.
 */
public class JsonSyntaxException extends IllegalArgumentException {

    /** @param reason 깨진 이유 (한국어 문장. 몇 번째 글자인지 포함될 수 있다) */
    public JsonSyntaxException(String reason) {
        super("JSON 형식이 아니에요: " + reason);
    }
}
