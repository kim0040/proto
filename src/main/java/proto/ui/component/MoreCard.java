package proto.ui.component;

import proto.ui.theme.Ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import java.awt.BorderLayout;
import java.awt.Dimension;

/**
 * [경계 boundary] 알림 카드가 많을 때 숨은 카드를 한 줄로 줄여 보여 주는 작은 카드 ("+2개 더" / "접기"). 누르면 펼치거나 접는다.
 */
public class MoreCard extends FloatingCard {

    private final JButton button;

    public MoreCard(Runnable onClick) {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(MARGIN + 4, MARGIN + 8, MARGIN + 4, MARGIN + 8));
        button = Buttons.ghost("");
        button.addActionListener(e -> onClick.run());
        add(button, BorderLayout.CENTER);
    }

    /** 카드 글을 바꾼다 (접근성 이름도 같이) */
    public void setLabel(String text) {
        button.setText(text);
        button.getAccessibleContext().setAccessibleName(text);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(Math.max(d.width, Ui.px(120) + Ui.px(MARGIN) * 2), d.height);
    }
}
