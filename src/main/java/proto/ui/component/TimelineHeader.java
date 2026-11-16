package proto.ui.component;

import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.function.Consumer;

/**
 * [경계 boundary] 시간표 위쪽에 붙어 스크롤해도 남는 요일 머리글 줄: 날짜 칸마다 DayHeader 하나, 고른 날은 옅은 바탕, 아래에 가는 선.
 * WeekView가 스크롤 영역의 열 머리글로 달아 둔다 (F2). 칸 위치는 TimelineGrid와 같은 TimelineMetrics로 계산해 본문 칸과 어긋나지 않게 한다.
 * 머리글을 누르면 그 날을 고른 날로 알린다 (붙여넣기 대상 등에 쓴다).
 */
public class TimelineHeader extends JPanel {

    private static final int HEIGHT = 40;

    private final LocalDate[] days = new LocalDate[TimelineMetrics.DAYS];
    private final DayHeader[] heads = new DayHeader[TimelineMetrics.DAYS];
    private final Consumer<LocalDate> onSelect;
    private final int gutter;
    private final MouseAdapter click = new HeaderClick();
    private LocalDate selected;

    public TimelineHeader(int gutter, Consumer<LocalDate> onSelect) {
        super(null);
        this.gutter = gutter;
        this.onSelect = onSelect;
        setOpaque(false);
        addMouseListener(click);
    }

    /** 이번에 보여 줄 한 주와 오늘, 고른 날을 정한다 */
    public void setWeek(LocalDate start, LocalDate today, LocalDate selected) {
        removeAll();
        this.selected = selected;
        for (int i = 0; i < days.length; i++) {
            days[i] = start.plusDays(i);
            heads[i] = new DayHeader(days[i], days[i].equals(today));
            add(heads[i]);
            listen(heads[i], click);
        }
        revalidate();
        repaint();
    }

    /** 고른 날만 바꿔 다시 그린다 */
    public void setSelected(LocalDate day) {
        selected = day;
        repaint();
    }

    @Override
    public void doLayout() {
        TimelineMetrics m = metrics();
        for (int i = 0; i < heads.length; i++) {
            if (heads[i] != null) {
                Dimension p = heads[i].getPreferredSize();
                heads[i].setBounds(m.columnX(i) + Ui.px(2), (getHeight() - Ui.px(1) - p.height) / 2,
                        Math.min(p.width, m.columnWidth(i) - Ui.px(4)), p.height);
            }
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(gutter + Ui.px(100) * TimelineMetrics.DAYS, Ui.px(HEIGHT));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.smooth(g2);
        TimelineMetrics m = metrics();
        for (int i = 0; i < days.length; i++) {
            if (days[i] != null && days[i].equals(selected)) {
                g2.setColor(Tokens.alpha(Tokens.get(Tokens.SUBTLE), 0.9f));
                int arc = Ui.px(8);
                g2.fillRoundRect(m.columnX(i) + 1, Ui.px(3), m.columnWidth(i) - 2, getHeight() - Ui.px(7), arc, arc);
            }
        }
        g2.setColor(Tokens.get(Tokens.BORDER));
        g2.fillRect(0, getHeight() - 1, getWidth(), 1);
        g2.dispose();
    }

    /** 머리글 위의 어느 글자를 눌러도 눌린 것으로 받도록 모든 자식에 같은 리스너를 단다 (라벨이 마우스를 가로채서) */
    private static void listen(Component c, MouseAdapter l) {
        c.addMouseListener(l);
        if (c instanceof Container box) {
            for (Component child : box.getComponents()) {
                listen(child, l);
            }
        }
    }

    private LocalDate dayAt(int x) {
        return days[0] == null ? null : days[metrics().dayAt(x)];
    }

    private TimelineMetrics metrics() {
        return new TimelineMetrics(gutter, getWidth(), Ui.px(48), 0);
    }

    private final class HeaderClick extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e)) {
                return;
            }
            int x = SwingUtilities.convertMouseEvent(e.getComponent(), e, TimelineHeader.this).getX();
            LocalDate day = dayAt(x);
            if (day != null) {
                selected = day;
                repaint();
                onSelect.accept(day);
            }
        }
    }
}
