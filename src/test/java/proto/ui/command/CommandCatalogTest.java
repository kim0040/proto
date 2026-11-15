package proto.ui.command;

import org.junit.jupiter.api.Test;
import proto.entity.Category;
import proto.ui.AppState.Mode;
import proto.ui.theme.ThemeManager.Choice;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 명령 메뉴 항목 목록: 날짜로 이동, 새 일정 만들기, 보기·카테고리·테마 항목이 알맞은 일을 부른다 */
class CommandCatalogTest {

    /** 부른 일을 적어 두는 가짜 */
    private static final class FakeHost implements CommandHost {
        final List<String> calls = new ArrayList<>();
        final List<String> asked = new ArrayList<>();
        proto.entity.Occurrence target;
        boolean undo;
        boolean redo;
        String category;
        java.util.Optional<LocalDate> resolved = java.util.Optional.empty();

        @Override public LocalDate today() { return LocalDate.of(2026, 11, 16); }
        @Override public java.util.Optional<LocalDate> resolveDate(String text) {
            asked.add(text);
            return resolved.isPresent() ? resolved : proto.logic.DateQueryParser.parse(text, today());
        }
        @Override public void showMode(Mode mode) { calls.add("mode:" + mode); }
        @Override public void goToday() { calls.add("today"); }
        @Override public void goToDate(LocalDate date) { calls.add("date:" + date); }
        @Override public void newSchedule(String title) { calls.add("new:" + title); }
        @Override public void filterCategory(String id) { calls.add("cat:" + id); }
        @Override public List<Category> categories() { return List.of(new Category("class", "수업"), new Category("work", "업무")); }
        @Override public String currentCategory() { return category; }
        @Override public void openFreeSlots() { calls.add("free"); }
        @Override public void setTheme(Choice c) { calls.add("theme:" + c); }
        @Override public Choice theme() { return Choice.SYSTEM; }
        @Override public void openSettings() { calls.add("settings"); }
        @Override public void showShortcuts() { calls.add("help"); }
        @Override public boolean canUndo() { return undo; }
        @Override public boolean canRedo() { return redo; }
        @Override public String undoLabel() { return "일정 삭제"; }
        @Override public String redoLabel() { return "일정 추가"; }
        @Override public void undo() { calls.add("undo"); }
        @Override public void redo() { calls.add("redo"); }
        @Override public java.util.Optional<proto.entity.Occurrence> targetSchedule() { return java.util.Optional.ofNullable(target); }
        @Override public void changeColor(proto.entity.Occurrence o, String key) { calls.add("color:" + key); }
    }

    private final FakeHost host = new FakeHost();
    private final CommandCatalog catalog = new CommandCatalog(host);

    private Command first(String query) {
        return catalog.results(query).get(0);
    }

    private List<String> titles(String query) {
        return catalog.results(query).stream().map(Command::title).toList();
    }

    private static proto.entity.Occurrence occurrence(String colorKey) {
        return new proto.entity.Occurrence("s1", LocalDate.of(2026, 11, 16), "회의", java.time.LocalTime.of(9, 0),
                java.time.LocalTime.of(10, 0), "class", "", proto.entity.RepeatRule.NONE, proto.entity.ReminderOption.NONE,
                false, false, colorKey);
    }

    @Test
    void 선택한_일정이_없으면_색_바꾸기_명령이_없다() {
        assertFalse(titles("").stream().anyMatch(t -> t.startsWith("선택한 일정 색 바꾸기")));
    }

    @Test
    void 선택한_일정이_있으면_카테고리_색과_팔레트_색_명령이_나오고_고르면_바꾼다() {
        host.target = occurrence("rose");
        List<Command> found = catalog.results("색 바꾸기").stream()
                .filter(c -> c.title().startsWith("선택한 일정 색 바꾸기")).toList();
        assertEquals(13, found.size());
        found.stream().filter(c -> c.title().endsWith("분홍")).findFirst().orElseThrow().action().run();
        found.stream().filter(c -> c.title().endsWith("카테고리 색")).findFirst().orElseThrow().action().run();
        assertEquals(List.of("color:rose", "color:null"), host.calls);
        assertEquals("지금", found.stream().filter(c -> c.title().endsWith("분홍")).findFirst().orElseThrow().detail());
    }

    @Test
    void 보기_이동_네_가지() {
        first("달력").action().run();
        first("통계").action().run();
        first("이번 주").action().run();
        first("오늘 보기").action().run();
        assertEquals(List.of("mode:MONTH", "mode:STATS", "mode:WEEK", "mode:TODAY"), host.calls);
    }

    @Test
    void 오늘로_이동과_설정_단축키_보기와_빈_시간() {
        first("오늘로").action().run();
        first("설정").action().run();
        first("단축키").action().run();
        first("빈 시간").action().run();
        assertEquals(List.of("today", "settings", "help", "free"), host.calls);
    }

    @Test
    void 날짜를_치면_그_날짜로_이동이_맨_위() {
        Command c = first("11월 20일");
        assertEquals("2026년 11월 20일 (금)로 이동", c.title());
        c.action().run();
        first("내일").action().run();
        first("3일 뒤").action().run();
        assertEquals(List.of("date:2026-11-20", "date:2026-11-17", "date:2026-11-19"), host.calls);
    }

    @Test
    void 오늘을_치면_날짜_이동_항목은_없고_오늘로_이동이_있다() {
        List<String> t = titles("오늘");
        assertFalse(t.stream().anyMatch(s -> s.endsWith("로 이동") && s.contains("년")));
        assertTrue(t.contains("오늘로 이동"));
    }

    @Test
    void 글이_있으면_그_제목으로_새_일정_만들기가_맨_아래() {
        List<Command> r = catalog.results("팀 회의");
        assertEquals(1, r.size());
        assertEquals("‘팀 회의’ 일정 만들기", r.get(0).title());
        r.get(0).action().run();
        assertEquals(List.of("new:팀 회의"), host.calls);
        assertEquals("새 일정", first("새 일정").title());
    }

    @Test
    void 빈_입력은_전체_목록_글이_없으면_일정_만들기_항목_없음() {
        List<Command> all = catalog.results("");
        assertTrue(all.stream().noneMatch(c -> c.title().endsWith("일정 만들기")));
        assertTrue(all.size() >= 14);
    }

    @Test
    void 카테고리_보기_전환() {
        first("모든 카테고리").action().run();
        first("‘업무’ 카테고리만 보기").action().run();
        assertEquals(List.of("cat:null", "cat:work"), host.calls);
        assertEquals("work", catalog.results("업무").get(0).dotCategory());
    }

    @Test
    void 지금_보는_카테고리와_테마에는_지금_표시() {
        host.category = "class";
        assertEquals("지금", first("‘수업’").detail());
        assertEquals("", first("‘업무’").detail());
        assertEquals("지금", first("시스템 설정").detail());
        assertEquals("", first("어둡게").detail());
    }

    @Test
    void 테마_세_가지() {
        first("밝게").action().run();
        first("어둡게").action().run();
        first("시스템 설정 따르기").action().run();
        assertEquals(List.of("theme:LIGHT", "theme:DARK", "theme:SYSTEM"), host.calls);
    }

    @Test
    void 되돌리기와_다시_실행은_할_수_있을_때만() {
        assertTrue(titles("되돌리기").stream().noneMatch(s -> s.equals("되돌리기")));
        host.undo = true;
        host.redo = true;
        Command undo = first("되돌리기");
        assertEquals("되돌리기", undo.title());
        assertEquals("일정 삭제", undo.detail());
        undo.action().run();
        first("다시 실행").action().run();
        assertEquals(List.of("undo", "redo"), host.calls);
    }

    @Test
    void 초성으로_명령_찾기() {
        assertEquals("설정 열기", first("ㅅㅈ").title());
    }

    @Test
    void 모든_항목에_이름_묶음_할_일이_있다() {
        host.undo = true;
        host.redo = true;
        for (Command c : catalog.results("")) {
            assertFalse(c.title().isBlank());
            assertFalse(c.group().isBlank());
            assertTrue(c.action() != null);
        }
    }

    @Test
    void 날짜_해석은_logic을_직접_부르지_않고_호스트에_요청한다() {
        host.resolved = java.util.Optional.of(LocalDate.of(2026, 12, 25));
        assertTrue(titles("아무 글").stream().anyMatch(t -> t.endsWith("로 이동")));
        assertTrue(host.asked.contains("아무 글"));
    }
}
