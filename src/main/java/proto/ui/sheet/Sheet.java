package proto.ui.sheet;

import javax.swing.JButton;
import javax.swing.JComponent;
import java.awt.Component;
import java.awt.Dimension;

/**
 * [경계 boundary] 시트 층(SheetHost)에 올라가는 시트 하나가 지켜야 할 약속: 카드 내용, 크기 맞추기, 첫 포커스, 바깥 클릭·Esc 처리.
 * BaseDialog가 이 약속을 구현한다. 시트 층은 시트가 무엇을 담는지 모르고, 바깥을 누르거나 Esc를 누르면 시트에게 "어떻게 할지" 묻기만 한다.
 */
public interface Sheet {

    /** 카드 안에 들어갈 내용 */
    JComponent card();

    /** 최대 크기(max) 안에서 카드가 차지할 크기. 넘치는 만큼은 시트가 스스로 스크롤한다 */
    Dimension fit(Dimension max);

    /** 열릴 때 처음 포커스를 받을 곳 (없으면 null: 카드 안 첫 칸) */
    Component initialFocus();

    /** Enter로 눌릴 기본 버튼 (없으면 null) */
    JButton defaultButton();

    /** 스크림(카드 바깥)을 눌렀을 때 */
    void scrimClicked();

    /** Esc를 눌렀을 때 */
    void escapePressed();
}
