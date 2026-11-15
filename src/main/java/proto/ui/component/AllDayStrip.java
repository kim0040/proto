package proto.ui.component;

import proto.entity.Occurrence;
import proto.ui.theme.Fonts;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.util.List;

/**
 * [경계 boundary] 오늘 보기 맨 위의 하루 종일 구역: "하루 종일" 작은 제목 아래에 하루 종일 일정 칩(AllDayChip)을 한 줄씩 쌓는다 (일정이 없으면 보이지 않는다).
 * TodayView가 요약 줄 아래, 시간 목록 위에 놓는다 (F2, F7). 칩은 주 시간표의 하루 종일 줄과 같은 꽉 찬 색 막대이고, 열기·메뉴(완료 포함)·삭제·Alt+←/→ 이동은 RowHandler에 맡긴다.
 * 다시 채울 때 키보드 포커스가 있던 칩은 같은 일정에 다시 준다.
 */
public class AllDayStrip extends JPanel {

    private final RowHandler handler;
    private final List<AllDayChip> chips = new java.util.ArrayList<>();

    public AllDayStrip(RowHandler handler) {
        this.handler = handler;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setVisible(false);
    }

    /** 오늘의 하루 종일 일정으로 구역을 다시 채운다 (비어 있으면 구역을 감춘다) */
    public void setItems(List<Occurrence> items) {
        String focused = chips.stream().filter(Component::isFocusOwner).map(c -> c.occurrence().key())
                .findFirst().orElse(null);
        removeAll();
        chips.clear();
        if (!items.isEmpty()) {
            add(title(items.size()));
            for (Occurrence o : items) {
                AllDayChip chip = new AllDayChip(handler, o, null);
                chip.setAlignmentX(LEFT_ALIGNMENT);
                chips.add(chip);
                add(chip);
                add(Box.createVerticalStrut(Ui.px(4)));
            }
        }
        setVisible(!items.isEmpty());
        revalidate();
        repaint();
        if (focused != null) {
            SwingUtilities.invokeLater(() -> chips.stream().filter(c -> c.occurrence().key().equals(focused))
                    .findFirst().ifPresent(AllDayChip::requestFocusInWindow));
        }
    }

    private TokenLabel title(int count) {
        TokenLabel label = new TokenLabel("하루 종일 " + count, Fonts.medium(Fonts.CAPTION), Tokens.MUTED);
        label.setAlignmentX(LEFT_ALIGNMENT);
        label.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, Ui.px(4), 0));
        return label;
    }

    /** 점검용: 지금 보이는 칩 개수 */
    public int chipCount() {
        return chips.size();
    }
}
