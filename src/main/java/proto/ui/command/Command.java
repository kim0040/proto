package proto.ui.command;

/**
 * [경계 boundary] 명령 메뉴(⌘K)의 항목 하나: 이름, 그림, 단축키 표시, 찾을 때 쓰는 낱말, 고르면 할 일.
 * CommandCatalog가 만들고, CommandMatcher가 입력한 글에 맞게 거르고, PaletteResults가 그린다. 항목은 화면 동작을 직접 하지 않고 action 하나만 가진다.
 *
 * @param group        묶음 이름 (글을 입력하기 전에 제목으로 보인다)
 * @param title        항목 이름 (찾는 글자는 이것과 keywords에서 찾는다)
 * @param icon         아이콘 이름 (없으면 null)
 * @param dotCategory  아이콘 대신 보일 카테고리 색 점의 카테고리 id (없으면 null)
 * @param dotColorKey  아이콘 대신 보일 팔레트 색 점의 색 키 (없으면 null. dotCategory보다 우선한다)
 * @param detail       이름 오른쪽의 옅은 보조 글 ("지금", 되돌릴 일 이름 등, 없으면 빈 글)
 * @param shortcut     단축키 표시 ("T", "⌘N" 등, 없으면 빈 글)
 * @param keywords     이름에 없어도 찾아지게 하는 낱말 (영어 이름 등)
 * @param action       고르면 할 일
 */
public record Command(String group, String title, String icon, String dotCategory, String dotColorKey, String detail,
                      String shortcut, String keywords, Runnable action) {

    public Command {
        detail = detail == null ? "" : detail;
        shortcut = shortcut == null ? "" : shortcut;
        keywords = keywords == null ? "" : keywords;
    }

    /** 색 점 없이 아이콘(또는 카테고리 색 점)만 있는 항목 */
    public Command(String group, String title, String icon, String dotCategory, String detail, String shortcut,
                   String keywords, Runnable action) {
        this(group, title, icon, dotCategory, null, detail, shortcut, keywords, action);
    }

    /** 아이콘만 있고 부가 정보는 없는 항목 */
    public static Command of(String group, String title, String icon, String shortcut, String keywords,
                             Runnable action) {
        return new Command(group, title, icon, null, "", shortcut, keywords, action);
    }
}
