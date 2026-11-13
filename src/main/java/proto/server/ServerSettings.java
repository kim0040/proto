package proto.server;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * [서버 대비 — 현재 사용 안 함] data/server.properties에서 서버를 쓸지 읽는다.
 * [엔티티 entity] 켜짐, 주소, 토큰, 제한 시간, 경고 문장을 담는다.
 * 파일이 없거나 값이 잘못되면 예외를 던지지 않고 꺼진 상태로 돌아간다. 파일을 새로 만들지도 않는다.
 */
public final class ServerSettings {

    /** timeoutSeconds를 적지 않았을 때 쓰는 초 */
    public static final int DEFAULT_TIMEOUT_SECONDS = 5;

    private static final int MAX_TIMEOUT_SECONDS = 120;

    private final boolean enabled;
    private final String baseUrl;
    private final String token;
    private final int timeoutSeconds;
    private final String warning;

    private ServerSettings(boolean enabled, String baseUrl, String token, int timeoutSeconds, String warning) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.token = token;
        this.timeoutSeconds = timeoutSeconds;
        this.warning = warning;
    }

    /**
     * 파일이 없으면 꺼짐(경고 없음). enabled가 true가 아니어도 꺼짐.
     * 파일을 읽지 못하거나 주소·제한 시간이 이상하면 꺼짐과 한국어 경고를 돌려준다.
     */
    public static ServerSettings load(Path file) {
        if (!Files.exists(file)) {
            return off("");
        }
        Properties props = new Properties();
        try {
            props.load(new StringReader(readText(file)));
        } catch (IOException | IllegalArgumentException e) {
            return off("server.properties를 읽지 못해 서버를 쓰지 않아요");
        }
        return fromProperties(props);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 끝의 슬래시는 빠져 있다. 꺼져 있으면 빈 문자열 */
    public String baseUrl() {
        return baseUrl;
    }

    /** 꺼져 있거나 토큰을 적지 않았으면 빈 문자열 */
    public String token() {
        return token;
    }

    public int timeoutSeconds() {
        return timeoutSeconds;
    }

    /** 꺼진 이유. 일부러 끈 것이거나 파일이 없으면 빈 문자열 */
    public String warning() {
        return warning;
    }

    private static ServerSettings fromProperties(Properties props) {
        String enabledText = value(props, "enabled");
        if (!enabledText.equalsIgnoreCase("true")) {
            return off(enabledProblem(enabledText));
        }
        return enabledSettings(props);
    }

    /** true/false가 아닌 값은 깨진 설정이다. 비어 있거나 false면 일부러 끈 것이라 경고가 없다 */
    private static String enabledProblem(String enabledText) {
        if (enabledText.isEmpty() || enabledText.equalsIgnoreCase("false")) {
            return "";
        }
        return "server.properties의 enabled 값을 읽지 못해 서버를 쓰지 않아요";
    }

    private static ServerSettings enabledSettings(Properties props) {
        String baseUrl = normalizeUrl(value(props, "baseUrl"));
        if (baseUrl == null) {
            return off("server.properties의 baseUrl이 http 주소가 아니어서 서버를 쓰지 않아요");
        }
        String token = value(props, "token");
        if (token.indexOf('\n') >= 0 || token.indexOf('\r') >= 0) {
            return off("server.properties의 token을 읽지 못해 서버를 쓰지 않아요");
        }
        Integer timeout = timeoutSeconds(value(props, "timeoutSeconds"));
        if (timeout == null) {
            return off("server.properties의 timeoutSeconds 값을 읽지 못해 서버를 쓰지 않아요");
        }
        return new ServerSettings(true, baseUrl, token, timeout, "");
    }

    /** http(s)만 받는다. 끝 슬래시는 떼고, 호스트가 없으면 null */
    private static String normalizeUrl(String raw) {
        String trimmed = stripTrailingSlashes(raw);
        String host = hostOf(trimmed);
        if (host == null || host.isBlank() || host.indexOf(' ') >= 0) {
            return null;
        }
        return trimmed;
    }

    private static String stripTrailingSlashes(String raw) {
        String trimmed = raw;
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /** http(s) 주소가 아니면 null, 맞으면 스킴 뒤의 호스트 부분 */
    private static String hostOf(String url) {
        if (url.startsWith("https://")) {
            return url.substring("https://".length());
        }
        if (url.startsWith("http://")) {
            return url.substring("http://".length());
        }
        return null;
    }

    /** 비어 있으면 기본 5초. 1~120 밖이거나 숫자가 아니면 null (호출하는 쪽이 서버를 끈다) */
    private static Integer timeoutSeconds(String raw) {
        if (raw.isEmpty()) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
        try {
            int seconds = Integer.parseInt(raw);
            if (seconds < 1 || seconds > MAX_TIMEOUT_SECONDS) {
                return null;
            }
            return seconds;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String value(Properties props, String key) {
        String raw = props.getProperty(key);
        return raw == null ? "" : raw.trim();
    }

    /** 윈도우 메모장이 붙이는 UTF-8 BOM은 키 이름에 섞이지 않게 뗀다 */
    private static String readText(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) {
            return text.substring(1);
        }
        return text;
    }

    private static ServerSettings off(String warning) {
        return new ServerSettings(false, "", "", DEFAULT_TIMEOUT_SECONDS, warning);
    }
}
