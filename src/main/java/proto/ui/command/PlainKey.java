package proto.ui.command;

import java.awt.event.KeyEvent;
import java.util.Optional;

/**
 * [경계 boundary] ⌘·Ctrl 없이 한 번 누르는 단축키(C, T, W, M, D, ←, →, ?)와 그 키 판별. 키 이벤트의 숫자 값만 보는 순수 계산이라 따로 시험할 수 있다.
 * PlainKeys가 눌린 키를 이 목록에 대 보고 알맞은 동작을 부른다. Shift 외의 보조 키(Ctrl·Alt·⌘)가 함께 눌렸으면 어떤 키도 아니다.
 * 한글 입력 상태에서도 같은 자리의 키로 읽는다(ㅊ=C, ㅅ=T, ㅈ=W, ㅡ=M, ㅇ=D): 운영체제가 키 코드를 주지 않고 글자만 줄 때를 위해서다.
 */
public enum PlainKey {
    NEW, TODAY, WEEK, MONTH, DAY, PREVIOUS, NEXT, HELP;

    /**
     * 눌린 키가 어느 단축키인지 (아니면 빈 값).
     *
     * @param keyCode       KeyEvent의 키 코드
     * @param keyChar       KeyEvent의 글자 (없으면 CHAR_UNDEFINED)
     * @param shift         Shift가 눌렸는지
     * @param otherModifier Ctrl·Alt·Meta·AltGraph 중 하나라도 눌렸는지
     */
    public static Optional<PlainKey> of(int keyCode, char keyChar, boolean shift, boolean otherModifier) {
        if (otherModifier) {
            return Optional.empty();
        }
        if ((shift && keyCode == KeyEvent.VK_SLASH) || keyChar == '?') {
            return Optional.of(HELP);
        }
        if (shift) {
            return Optional.empty();
        }
        return switch (keyCode) {
            case KeyEvent.VK_C -> Optional.of(NEW);
            case KeyEvent.VK_T -> Optional.of(TODAY);
            case KeyEvent.VK_W -> Optional.of(WEEK);
            case KeyEvent.VK_M -> Optional.of(MONTH);
            case KeyEvent.VK_D -> Optional.of(DAY);
            case KeyEvent.VK_LEFT -> Optional.of(PREVIOUS);
            case KeyEvent.VK_RIGHT -> Optional.of(NEXT);
            default -> fromHangul(keyChar);
        };
    }

    /** 한글 두벌식에서 같은 자리의 자음·모음 글자 */
    private static Optional<PlainKey> fromHangul(char c) {
        return switch (c) {
            case 'ㅊ' -> Optional.of(NEW);
            case 'ㅅ' -> Optional.of(TODAY);
            case 'ㅈ' -> Optional.of(WEEK);
            case 'ㅡ' -> Optional.of(MONTH);
            case 'ㅇ' -> Optional.of(DAY);
            default -> Optional.empty();
        };
    }

    /** 방향키인지 (입력칸·달력 칸처럼 방향키를 쓰는 곳에서는 비켜 줘야 한다) */
    public boolean isArrow() {
        return this == PREVIOUS || this == NEXT;
    }

    /** 이 키의 키 코드 (방향키만) */
    int arrowCode() {
        return this == PREVIOUS ? KeyEvent.VK_LEFT : KeyEvent.VK_RIGHT;
    }
}
