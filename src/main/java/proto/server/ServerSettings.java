package proto.server;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * [서버 대비 — 현재 사용 안 함] data/server.properties에서 서버를 쓸지 읽는다.
 * [엔티티 entity] 켜짐, 주소, 토큰, 제한 시간, 경고 문장을 담는다.
 * StoreFactory가 시작할 때 한 번 읽고, 켜져 있으면 서버 저장소로 갈아 끼운다. 값이 잘못돼도 앱이 못 켜지면 안 되므로 예외 대신 "꺼짐 + 경고 문장"을 돌려준다.
 * 객체는 만든 뒤 바뀌지 않는다. 경고는 SettingsWarningScheduleStore가 사용자에게 전달한다.
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
     * 설정 파일을 읽는다. 어떤 경우에도 예외를 던지지 않는다.
     *
     * @param file data/server.properties 경로
     * @return 켜진 설정, 또는 꺼진 설정(이유가 있으면 {@link #warning()}에 한국어 문장)
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

    /** 서버를 쓸지 여부. enabled=true이고 나머지 값이 모두 올바를 때만 true이다. */
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

    /** 서버를 기다리는 제한 시간(초). 적지 않았으면 5, 올바른 값은 1~120이다. */
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
        if (!isHeaderSafe(token)) {
            return off("server.properties의 token에 쓸 수 없는 문자가 있어 서버를 쓰지 않아요");
        }
        Integer timeout = timeoutSeconds(value(props, "timeoutSeconds"));
        if (timeout == null) {
            return off("server.properties의 timeoutSeconds 값을 읽지 못해 서버를 쓰지 않아요");
        }
        return new ServerSettings(true, baseUrl, token, timeout, "");
    }

    /** 왜 검사하나: 토큰은 HTTP 헤더(Authorization)에 그대로 들어가는데, 줄바꿈이나 한글이 있으면 요청이 깨지거나 헤더가 위조될 수 있다. */
    private static boolean isHeaderSafe(String token) {
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c < 0x21 || c > 0x7E) {
                return false;
            }
        }
        return true;
    }

    /** http(s) 주소만 받는다. 끝 슬래시는 떼고, URI로 읽어 호스트·포트가 올바르지 않으면 null */
    private static String normalizeUrl(String raw) {
        String trimmed = stripTrailingSlashes(raw);
        URI uri = parse(trimmed);
        if (uri == null || !isHttp(uri.getScheme())) {
            return null;
        }
        boolean hasHost = uri.getHost() != null && !uri.getHost().isBlank();
        boolean portOk = uri.getPort() == -1 || (uri.getPort() >= 1 && uri.getPort() <= 65535);
        boolean plain = uri.getRawQuery() == null && uri.getRawFragment() == null && uri.getRawUserInfo() == null;
        return hasHost && portOk && plain ? trimmed : null;
    }

    private static URI parse(String text) {
        try {
            return new URI(text);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static boolean isHttp(String scheme) {
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    private static String stripTrailingSlashes(String raw) {
        String trimmed = raw;
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
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
