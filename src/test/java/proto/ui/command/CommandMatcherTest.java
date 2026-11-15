package proto.ui.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 명령 메뉴의 글 맞추기: 대소문자·띄어쓰기 무시, 부분 일치, 낱말, 초성 */
class CommandMatcherTest {

    private static Command c(String title, String keywords) {
        return Command.of("g", title, null, "", keywords, () -> { });
    }

    private final List<Command> all = List.of(c("설정 열기", "settings preferences"), c("새 일정", "new add 추가"),
            c("테마: 어둡게", "dark theme"), c("오늘 보기", "today"), c("오늘로 이동", "go today"));

    private List<String> titles(String q) {
        return CommandMatcher.filter(all, q).stream().map(Command::title).toList();
    }

    @Test
    void 입력이_비면_모두_원래_순서() {
        assertEquals(all.stream().map(Command::title).toList(), titles(""));
        assertEquals(all.size(), titles("   ").size());
    }

    @Test
    void 이름의_일부와_띄어쓰기_무시() {
        assertEquals(List.of("설정 열기"), titles("설정"));
        assertEquals(List.of("설정 열기"), titles("설정열기"));
        assertEquals(List.of("설정 열기"), titles("정 열"));
    }

    @Test
    void 영어_낱말과_대소문자_무시() {
        assertEquals(List.of("설정 열기"), titles("SETTINGS"));
        assertEquals(List.of("테마: 어둡게"), titles("Dark"));
        assertEquals(List.of("새 일정"), titles("추가"));
    }

    @Test
    void 이름_앞부분이_같으면_안에_든_것보다_먼저() {
        // "오늘 보기" "오늘로 이동"은 앞부분 일치, "today"는 낱말에만 있다
        List<String> t = titles("오늘");
        assertEquals(List.of("오늘 보기", "오늘로 이동"), t);
        // 이름 안에 든 것(어둡게의 "어둡")이 낱말 일치보다 위
        List<Command> mix = List.of(c("달력 보기", "dark 어둡"), c("테마: 어둡게", ""));
        assertEquals("테마: 어둡게", CommandMatcher.filter(mix, "어둡").get(0).title());
    }

    @Test
    void 초성만_쳐도_찾는다() {
        assertEquals(List.of("설정 열기"), titles("ㅅㅈ"));
        assertEquals(List.of("새 일정"), titles("ㅅㅇㅈ"));
        assertEquals(List.of("오늘 보기", "오늘로 이동"), titles("ㅇㄴ"));
        assertTrue(titles("ㅋㅋ").isEmpty());
    }

    @Test
    void 맞는_게_없으면_빈_목록() {
        assertTrue(titles("zzzz").isEmpty());
    }

    @Test
    void 초성_뽑기() {
        assertEquals("ㅅㅈㅇㄱ", HangulInitials.of("설정 열기"));
        assertEquals("abㅎ", HangulInitials.of("AB 한"));
        assertTrue(HangulInitials.isInitialsOnly("ㅅㅈ"));
        assertTrue(!HangulInitials.isInitialsOnly("설ㅈ"));
        assertTrue(!HangulInitials.isInitialsOnly(""));
    }
}
