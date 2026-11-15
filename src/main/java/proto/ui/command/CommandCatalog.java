package proto.ui.command;

import proto.entity.Category;
import proto.entity.CategoryPalette;
import proto.entity.Occurrence;
import proto.ui.dialog.ColorMenu;
import proto.ui.AppState.Mode;
import proto.ui.Fmt;
import proto.ui.theme.ThemeManager.Choice;
import proto.ui.theme.Ui;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * [경계 boundary] 명령 메뉴의 항목 목록을 만든다: 보기 이동, 날짜로 이동, 새 일정, 카테고리 보기, 테마, 설정, 되돌리기·다시 실행, 단축키 보기.
 * PaletteLayer가 메뉴를 열 때 하나 만들고, 글을 입력할 때마다 results를 부른다. 항목이 하는 일은 모두 CommandHost에 맡긴다 (F1, F2, F7, F12).
 * 입력한 글이 날짜로 읽히면(controller의 날짜 해석) "그 날짜로 이동" 항목이 맨 위에 나오고, 글이 있으면 맨 아래에 "이 제목으로 새 일정"이 늘 붙는다.
 * 되돌리기·다시 실행은 할 수 있을 때만 나온다.
 */
public final class CommandCatalog {

    static final String G_VIEW = "보기";
    static final String G_GO = "이동";
    static final String G_SCHEDULE = "일정";
    static final String G_CATEGORY = "카테고리";
    static final String G_COLOR = "선택한 일정";
    static final String G_APP = "표시와 설정";

    private final CommandHost host;

    public CommandCatalog(CommandHost host) {
        this.host = host;
    }

    /** 입력한 글(비어 있어도 된다)에 맞는 항목을 보여 줄 순서로 */
    public List<Command> results(String query) {
        String q = query == null ? "" : query.strip();
        List<Command> out = new ArrayList<>();
        dateJump(q).ifPresent(out::add);
        out.addAll(CommandMatcher.filter(all(), q));
        if (!q.isEmpty()) {
            out.add(createWithTitle(q));
        }
        return out;
    }

    /** 글을 입력하기 전에 보이는 전체 목록 */
    List<Command> all() {
        List<Command> list = new ArrayList<>();
        list.addAll(views());
        list.add(Command.of(G_GO, "오늘로 이동", "arrow-right", "T", "today go 오늘", host::goToday));
        list.addAll(scheduleCommands());
        list.addAll(colorCommands());
        list.addAll(categoryCommands());
        list.addAll(appCommands());
        return list;
    }

    private List<Command> views() {
        return List.of(
                Command.of(G_VIEW, "오늘 보기", "calendar-check", "D", "today day view 오늘", () -> host.showMode(Mode.TODAY)),
                Command.of(G_VIEW, "이번 주 보기", "calendar-range", "W", "week view 주간 시간표", () -> host.showMode(Mode.WEEK)),
                Command.of(G_VIEW, "달력 보기", "calendar-days", "M", "month view 월간", () -> host.showMode(Mode.MONTH)),
                Command.of(G_VIEW, "주간 통계 보기", "chart", Ui.shortcut("4"), "stats statistics 통계 그래프",
                        () -> host.showMode(Mode.STATS)));
    }

    private List<Command> scheduleCommands() {
        List<Command> list = new ArrayList<>();
        list.add(Command.of(G_SCHEDULE, "새 일정", "plus", "C", "new add create 일정 추가 만들기", () -> host.newSchedule(null)));
        list.add(Command.of(G_SCHEDULE, "빈 시간 찾기", "hourglass", "", "free time slot 비는 시간 여유", host::openFreeSlots));
        if (host.canUndo()) {
            list.add(new Command(G_SCHEDULE, "되돌리기", "undo", null, host.undoLabel(), Ui.shortcut("Z"), "undo 취소",
                    host::undo));
        }
        if (host.canRedo()) {
            list.add(new Command(G_SCHEDULE, "다시 실행", "redo", null, host.redoLabel(), shiftShortcut("Z"),
                    "redo 재실행", host::redo));
        }
        return list;
    }

    /**
     * 선택한(= 명령 메뉴를 열기 전에 포커스가 있던) 일정의 색 바꾸기: "카테고리 색" + 팔레트 색마다 하나. 일정이 없으면 나오지 않는다.
     * 지금 색에는 "지금"이라고 적는다. 반복 일정이면 시리즈 전체의 색이 바뀐다.
     */
    private List<Command> colorCommands() {
        Optional<Occurrence> target = host.targetSchedule();
        if (target.isEmpty()) {
            return List.of();
        }
        Occurrence o = target.get();
        List<Command> list = new ArrayList<>();
        list.add(colorCommand(o, null, null));
        for (String key : CategoryPalette.KEYS) {
            list.add(colorCommand(o, key, key));
        }
        return list;
    }

    private Command colorCommand(Occurrence o, String key, String dot) {
        String title = "선택한 일정 색 바꾸기: " + ColorMenu.nameOf(key);
        String now = Objects.equals(o.colorKey(), key) ? "지금" : "";
        return new Command(G_COLOR, title, dot == null ? "palette" : null, null, dot, now, "", "color 색 바꾸기 색상 " + ColorMenu.nameOf(key),
                () -> host.changeColor(o, key));
    }

    private static String shiftShortcut(String key) {
        return Ui.isMac() ? "⇧⌘" + key : "Ctrl+Shift+" + key;
    }

    /** 카테고리 보기 전환: 전체 + 카테고리마다 하나 (지금 보는 것은 "지금"이라고 적는다) */
    private List<Command> categoryCommands() {
        List<Command> list = new ArrayList<>();
        String current = host.currentCategory();
        list.add(new Command(G_CATEGORY, "모든 카테고리 보기", "tag", null, current == null ? "지금" : "", "",
                "all category 전체 카테고리", () -> host.filterCategory(null)));
        for (Category c : host.categories()) {
            String id = c.getId();
            list.add(new Command(G_CATEGORY, "‘" + c.getName() + "’ 카테고리만 보기", null, id,
                    id.equals(current) ? "지금" : "", "", "category filter 카테고리 " + c.getName(),
                    () -> host.filterCategory(id)));
        }
        return list;
    }

    private List<Command> appCommands() {
        Choice now = host.theme();
        return List.of(
                theme("테마: 밝게", "sun", Choice.LIGHT, now, "light 라이트 밝은"),
                theme("테마: 어둡게", "moon", Choice.DARK, now, "dark 다크 어두운"),
                theme("테마: 시스템 설정 따르기", "monitor", Choice.SYSTEM, now, "system auto 자동"),
                Command.of(G_APP, "설정 열기", "settings", Ui.shortcut(","), "settings preferences 환경설정 색 강조색",
                        host::openSettings),
                Command.of(G_APP, "단축키 보기", "keyboard", "?", "shortcuts keys help 도움말 키",
                        host::showShortcuts));
    }

    private Command theme(String title, String icon, Choice choice, Choice now, String keywords) {
        return new Command(G_APP, title, icon, null, choice == now ? "지금" : "", "", "theme 테마 " + keywords,
                () -> host.setTheme(choice));
    }

    /** 입력한 글이 날짜로 읽히면 "그 날짜로 이동" 항목. 오늘이면 "오늘로 이동"이 따로 있으니 만들지 않는다 */
    private Optional<Command> dateJump(String q) {
        Optional<LocalDate> date = host.resolveDate(q);
        if (date.isEmpty() || date.get().equals(host.today())) {
            return Optional.empty();
        }
        LocalDate d = date.get();
        String title = Fmt.dateFull(d) + "로 이동";
        return Optional.of(new Command(G_GO, title, "arrow-right", null, "", "", "", () -> host.goToDate(d)));
    }

    private Command createWithTitle(String title) {
        String shown = title.length() > 30 ? title.substring(0, 30) + "…" : title;
        return new Command(G_SCHEDULE, "‘" + shown + "’ 일정 만들기", "plus", null, "", "", "", () -> host.newSchedule(title));
    }
}
