package proto.server;

/**
 * [서버 대비 — 현재 사용 안 함] JSON 글자 구조가 깨졌을 때 던진다.
 * [저장소 repository] 닫히지 않은 따옴표, 잘못된 이스케이프, 배열 끝의 쉼표처럼 형식이 아닌 입력을 거절한다.
 */
public class JsonSyntaxException extends IllegalArgumentException {

    public JsonSyntaxException(String reason) {
        super("JSON 형식이 아니에요: " + reason);
    }
}
