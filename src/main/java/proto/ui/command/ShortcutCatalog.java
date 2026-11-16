package proto.ui.command;

import proto.ui.theme.Ui;

import java.util.List;

/**
 * [경계 boundary] 이 앱의 모든 단축키를 묶음별로 적어 둔 목록. 단축키 안내 시트(ShortcutHelpDialog)가 그대로 보여 준다.
 * 새 단축키를 달면 여기에도 한 줄 더한다 (MainFrame의 ⌘ 단축키, EditShortcuts, PlainKeys, 일정 조각의 Alt+방향키, 끌기).
 * 글자는 운영체제에 맞게 쓴다 (macOS는 ⌘·⇧·⌥, Windows는 Ctrl·Shift·Alt).
 */
public final class ShortcutCatalog {

    /** 단축키 한 줄: keys는 눌러야 할 키들(칸마다 하나씩 보여 준다), what은 하는 일 */
    public record Entry(List<String> keys, String what) {
    }

    /** 같은 종류의 단축키 묶음 */
    public record Group(String title, List<Entry> entries) {
    }

    private ShortcutCatalog() {
    }

    /** 단축키 전체 */
    public static List<Group> groups() {
        return List.of(
                new Group("이동", List.of(
                        entry("오늘로 이동", "T"),
                        entry("이전 / 다음 (주 · 달)", "←", "→"),
                        entry("오늘 보기", "D"),
                        entry("이번 주 보기", "W"),
                        entry("달력 보기", "M"),
                        entry("보기 바꾸기 (오늘 · 이번 주 · 달력 · 통계)", mod("1"), mod("2"), mod("3"), mod("4")))),
                new Group("일정", List.of(
                        entry("새 일정", "C"),
                        entry("새 일정 (다른 방법)", mod("N")),
                        entry("선택한 일정 복사", mod("C")),
                        entry("복사한 일정 붙여넣기", mod("V")),
                        entry("되돌리기", mod("Z")),
                        entry("다시 실행", shift("Z")),
                        entry("선택한 일정 열기", "Enter"),
                        entry("선택한 일정 삭제", "Delete"))),
                new Group("일정 옮기기", List.of(
                        entry("하루 앞·뒤로", alt("←"), alt("→")),
                        entry("15분 앞·뒤로 (하루 종일 일정은 해당 없음)", alt("↑"), alt("↓")),
                        entry("끌어서 옮기기 · 아래쪽 끝을 끌어 길이 바꾸기", "마우스 끌기"),
                        entry("끌다가 취소", "Esc"))),
                new Group("찾기와 설정", List.of(
                        entry("명령 메뉴 (보기·날짜·테마 등 무엇이든)", mod("K")),
                        entry("검색창", mod("F")),
                        entry("설정", mod(",")),
                        entry("사이드바 숨기기·보이기", mod("\\")),
                        entry("이 안내 보기", "?"))));
    }

    private static Entry entry(String what, String... keys) {
        return new Entry(List.of(keys), what);
    }

    /** ⌘ 또는 Ctrl과 함께 누르는 키 */
    private static String mod(String key) {
        return Ui.shortcut(key);
    }

    private static String shift(String key) {
        return Ui.isMac() ? "⇧⌘" + key : "Ctrl+Shift+" + key;
    }

    private static String alt(String key) {
        return (Ui.isMac() ? "⌥" : "Alt+") + key;
    }
}
