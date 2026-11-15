package proto.ui.component;

import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseWheelEvent;

/**
 * [경계 boundary] 하루 종일 줄(AllDayRow)을 담아, 펼친 줄이 한도보다 길 때 그 안에서만 세로로 스크롤되게 하는 작은 보이는 영역.
 * WeekView가 요일 머리글 아래(시간표 스크롤 영역의 열 머리글)에 놓는다. 높이는 AllDayRow#shownHeight를 따르고, 폭은 시간 본문과 같다(칸이 어긋나지 않게
 * 스크롤 막대 대신 왼쪽 시각 글자 영역 끝에 얇은 위치 표시만 그린다).
 * 휠은 줄이 넘칠 때만 여기서 쓰고, 넘치지 않으면 바깥 시간표 스크롤 영역으로 넘긴다. 키보드로 가려진 칩에 포커스가 가면 AllDayRow가 그 칩이 보이게 스크롤한다.
 */
public final class AllDayPane extends JViewport {

    private final AllDayRow row;
    private double wheelRest;

    public AllDayPane(AllDayRow row) {
        this.row = row;
        setOpaque(false);
        setScrollMode(SIMPLE_SCROLL_MODE);
        setView(row);
        addChangeListener(e -> {
            row.viewMoved();
            repaint();
        });
        addMouseWheelListener(this::wheel);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(row.getPreferredSize().width, row.shownHeight());
    }

    /** 줄이 넘치는지 (보이는 높이보다 내용이 길다) */
    public boolean overflows() {
        return row.getPreferredSize().height > getHeight() && getHeight() > 0;
    }

    /** 휠: 넘치면 이 안에서 스크롤하고, 아니면 바깥 스크롤 영역으로 넘긴다 */
    private void wheel(MouseWheelEvent e) {
        if (!overflows()) {
            forward(e);
            return;
        }
        wheelRest += e.getPreciseWheelRotation() * Ui.px(AllDayChip.HEIGHT);
        int step = (int) wheelRest;
        wheelRest -= step;
        scrollBy(step);
        e.consume();
    }

    /** 세로로 dy만큼 스크롤한다 (내용 끝을 넘지 않는다) */
    public void scrollBy(int dy) {
        int max = Math.max(0, row.getHeight() - getHeight());
        int y = Math.max(0, Math.min(max, getViewPosition().y + dy));
        if (y != getViewPosition().y) {
            setViewPosition(new Point(0, y));
        }
    }

    private void forward(MouseWheelEvent e) {
        JScrollPane outer = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
        if (outer != null) {
            outer.dispatchEvent(SwingUtilities.convertMouseEvent(this, e, outer));
        }
    }

    /** 줄 위에, 넘칠 때만 왼쪽 칸 끝의 얇은 위치 표시와 아래 경계선을 그린다 */
    @Override
    public void paint(Graphics g) {
        super.paint(g);
        if (!overflows()) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.smooth(g2);
        int content = row.getHeight();
        int h = getHeight();
        int thumb = Math.max(Ui.px(16), h * h / content);
        int top = (h - thumb) * getViewPosition().y / Math.max(1, content - h);
        int w = Ui.px(3);
        g2.setColor(Tokens.alpha(Tokens.get(Tokens.FAINT), 0.6f));
        g2.fillRoundRect(row.gutter() - Ui.px(5), top + Ui.px(2), w, thumb - Ui.px(4), w, w);
        g2.setColor(Tokens.get(Tokens.BORDER));
        g2.fillRect(0, h - 1, getWidth(), 1);
        g2.dispose();
    }
}
