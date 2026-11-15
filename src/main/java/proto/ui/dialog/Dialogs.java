package proto.ui.dialog;

import proto.control.EditScope;
import proto.entity.Occurrence;
import proto.ui.Fmt;
import proto.ui.component.CategoryDot;
import proto.ui.component.TextUtil;
import proto.ui.component.TokenLabel;
import proto.ui.dialog.ConfirmDialog.Choice;
import proto.ui.dialog.ConfirmDialog.Kind;
import proto.ui.theme.Fonts;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.BoxLayout;
import javax.swing.JPanel;
import java.awt.FlowLayout;
import java.awt.Component;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * [경계 boundary] 자주 쓰는 질문 시트 모음: 반복 범위 선택(F3, F4), 겹침 경고(F13), 변경 버리기, 카테고리 삭제 확인, 안내.
 * UiActions가 반복 일정 삭제(F4)·수정(F3) 전에, ScheduleDialog가 저장(F13) 전과 Esc로 닫을 때 부른다. 반복 없는 일정 삭제는 되돌릴 수 있으므로 묻지 않는다.
 * 반환 모양은 "참/거짓"이나 Optional로 단순하게 두어서 호출한 쪽이 화면 구조를 몰라도 된다.
 */
public final class Dialogs {

    private Dialogs() {
    }

    /** 고른 범위를 EditScope로 돌려준다 (이 일정만: THIS_ONLY, 앞으로 전부: THIS_AND_FOLLOWING). 취소하거나 Esc·바깥 클릭이면 Optional.empty()이다. */
    public static Optional<EditScope> chooseScope(Component owner, String title, String verb, boolean danger) {
        String message = "반복 일정을 " + verb + "할까요?";
        String detail = "‘이 일정만’은 이 날짜 하나만, ‘앞으로 전부’는 이 날짜부터 뒤의 모든 반복에 적용돼요.";
        Kind main = danger ? Kind.DANGER : Kind.PRIMARY;
        ConfirmDialog d = new ConfirmDialog(owner, title, message, detail, null, List.of(
                new Choice("취소", Kind.GHOST), new Choice(EditScope.THIS_ONLY.label(), Kind.SECONDARY),
                new Choice(EditScope.THIS_AND_FOLLOWING.label(), main)));
        return switch (d.ask()) {
            case 1 -> Optional.of(EditScope.THIS_ONLY);
            case 2 -> Optional.of(EditScope.THIS_AND_FOLLOWING);
            default -> Optional.empty();
        };
    }

    /** 시간이 겹치는 일정을 보여 주고 그래도 저장할지 묻는다 (F13) */
    public static boolean confirmConflicts(Component owner, List<Occurrence> conflicts,
                                           Function<String, String> categoryName) {
        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        int shown = Math.min(conflicts.size(), 4);
        for (int i = 0; i < shown; i++) {
            list.add(conflictRow(conflicts.get(i), categoryName));
        }
        if (conflicts.size() > shown) {
            list.add(new TokenLabel("외 " + (conflicts.size() - shown) + "개", Fonts.regular(Fonts.SMALL), Tokens.MUTED));
        }
        ConfirmDialog d = new ConfirmDialog(owner, "시간이 겹쳐요", "이 시간에 다른 일정이 있어요",
                "아래 일정과 시간이 겹쳐요. 그래도 저장할까요?", list,
                List.of(new Choice("돌아가기", Kind.SECONDARY), new Choice("그래도 저장", Kind.PRIMARY)));
        return d.ask() == 1;
    }

    private static JPanel conflictRow(Occurrence o, Function<String, String> categoryName) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, Ui.px(8), Ui.px(3)));
        row.setOpaque(false);
        row.setAlignmentX(0f);
        row.add(CategoryDot.of(o, 8));
        row.add(new TokenLabel(Fmt.dateShort(o.date()) + "  " + Fmt.range(o), Fonts.medium(Fonts.SMALL), Tokens.MUTED));
        row.add(new TokenLabel(TextUtil.plain(shorten(o.title())), Fonts.medium(Fonts.BODY), Tokens.TEXT));
        return row;
    }

    /** 확인 버튼 하나뿐인 안내 */
    public static void inform(Component owner, String title, String message) {
        new ConfirmDialog(owner, title, message, null, null, List.of(new Choice("확인", Kind.PRIMARY))).ask();
    }

    /** 입력하던 내용을 버리고 닫을지 묻는다. "계속 편집"이 기본(Enter)이고, Esc·바깥 클릭도 계속 편집이다 */
    public static boolean confirmDiscard(Component owner) {
        return discardPrompt(owner).ask() == 0;
    }

    /** 변경 버리기 질문 시트 (0: 버리기, 1: 계속 편집). 스크린샷 도구도 이 모양을 찍는다 */
    public static ConfirmDialog discardPrompt(Component owner) {
        return new ConfirmDialog(owner, "변경 버리기", "변경한 내용을 버릴까요?",
                "버리면 이 창에서 바꾼 내용이 저장되지 않아요.", null,
                List.of(new Choice("버리기", Kind.DANGER_OUTLINE), new Choice("계속 편집", Kind.PRIMARY)));
    }

    /** 카테고리 삭제 확인 (카테고리 삭제는 되돌릴 수 없어서 묻는다) */
    public static boolean confirmDeleteCategory(Component owner, String name, int scheduleCount) {
        String detail = scheduleCount == 0 ? "이 카테고리에는 일정이 없어요."
                : "일정 " + scheduleCount + "개가 ‘개인’으로 옮겨져요.";
        ConfirmDialog d = new ConfirmDialog(owner, "카테고리 삭제", "‘" + shorten(name) + "’ 카테고리를 지울까요?",
                detail, null, List.of(new Choice("취소", Kind.SECONDARY), new Choice("삭제", Kind.DANGER)));
        return d.ask() == 1;
    }

    /** 긴 이름을 24글자까지만 보여 주고 나머지는 "…"로 줄인다. 이모지를 한 글자로 세려고 코드 포인트로 센다. */
    public static String shorten(String raw) {
        String s = TextUtil.clean(raw);
        if (s.codePointCount(0, s.length()) <= 24) {
            return s;
        }
        return s.substring(0, s.offsetByCodePoints(0, 23)) + "…";
    }
}
