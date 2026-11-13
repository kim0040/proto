package proto.ui.view;

import proto.entity.Occurrence;
import proto.ui.AppState;
import proto.ui.Fmt;
import proto.ui.UiContext;
import proto.ui.component.ScrollPanel;
import proto.ui.component.TokenLabel;
import proto.ui.component.WeekChip;
import proto.ui.theme.Fonts;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * [경계 boundary] 이번 주 보기 (F2): 월~일 7칸에 하루치 일정을 작은 칩으로 보여 준다. 오늘은 숫자 뒤에 강조 동그라미.
 */
public class WeekView implements ContentView {

    private static final int GAP = 10;
    private static final String[] ORDINALS = {"첫째", "둘째", "셋째", "넷째", "다섯째"};

    private final UiContext ctx;
    private final JPanel root = new JPanel(new BorderLayout());
    private final JPanel columns = new JPanel(new GridLayout(1, 7, Ui.px(GAP), 0));
    private final List<WeekChip> chips = new ArrayList<>();

    public WeekView(UiContext ctx) {
        this.ctx = ctx;
        root.setOpaque(false);
        columns.setOpaque(false);
        ScrollPanel holder = new ScrollPanel(new BorderLayout());
        holder.add(columns, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(holder);
        scroll.setBorder(BorderFactory.createEmptyBorder(Ui.px(4), Ui.px(24), Ui.px(24), Ui.px(24)));
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        root.add(scroll, BorderLayout.CENTER);
        columns.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                applyChipWidth();
            }
        });
    }

    @Override
    public JComponent component() {
        return root;
    }

    @Override
    public String title() {
        LocalDate start = ctx.state().weekStart();
        if (start.equals(thisWeekStart())) {
            return "이번 주";
        }
        LocalDate thursday = start.plusDays(3);
        return thursday.getMonthValue() + "월 " + ORDINALS[(thursday.getDayOfMonth() - 1) / 7] + " 주";
    }

    private LocalDate thisWeekStart() {
        LocalDate t = ctx.schedules().today();
        return t.minusDays(t.getDayOfWeek().getValue() - 1L);
    }

    @Override
    public String subtitle() {
        LocalDate start = ctx.state().weekStart();
        return Fmt.dateRange(start, start.plusDays(6));
    }

    @Override
    public boolean hasNavigator() {
        return true;
    }

    @Override
    public boolean usesFilters() {
        return true;
    }

    @Override
    public void refresh(boolean animate) {
        AppState s = ctx.state();
        LocalDate start = s.weekStart();
        List<Occurrence> all = ctx.schedules().getOccurrences(start, start.plusDays(6), s.categoryId(), s.keyword());
        columns.removeAll();
        chips.clear();
        for (int i = 0; i < 7; i++) {
            LocalDate day = start.plusDays(i);
            columns.add(column(day, all.stream().filter(o -> o.date().equals(day)).toList()));
        }
        applyChipWidth();
        columns.revalidate();
        columns.repaint();
    }

    private void applyChipWidth() {
        int w = Math.max(Ui.px(60), (columns.getWidth() - Ui.px(GAP) * 6) / 7);
        chips.forEach(c -> c.setWrapWidth(w));
    }

    private JPanel column(LocalDate day, List<Occurrence> items) {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.add(header(day));
        col.add(Box.createVerticalStrut(Ui.px(8)));
        for (Occurrence o : items) {
            WeekChip chip = new WeekChip(ctx.actions(), o);
            chip.setAlignmentX(0f);
            chips.add(chip);
            col.add(chip);
            col.add(Box.createVerticalStrut(Ui.px(6)));
        }
        col.add(Box.createVerticalGlue());
        return col;
    }

    private JPanel header(LocalDate day) {
        boolean today = day.equals(ctx.schedules().today());
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, Ui.px(6), 0));
        p.setOpaque(false);
        p.setAlignmentX(0f);
        p.add(new TokenLabel(Fmt.weekdayShort(day.getDayOfWeek()), Fonts.medium(Fonts.SMALL),
                today ? Tokens.ACCENT : Tokens.MUTED));
        p.add(new DayNumber(day.getDayOfMonth(), today));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    /** 날짜 숫자. 오늘이면 뒤에 강조색 동그라미를 깐다 */
    private static final class DayNumber extends JComponent {
        private final int number;
        private final boolean today;

        DayNumber(int number, boolean today) {
            this.number = number;
            this.today = today;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(Ui.px(28), Ui.px(28));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Ui.smooth(g2);
            if (today) {
                g2.setColor(Tokens.get(Tokens.ACCENT));
                g2.fillOval(0, 0, getWidth(), getHeight());
            }
            g2.setFont(Fonts.semibold(Fonts.BODY));
            java.awt.FontMetrics fm = g2.getFontMetrics();
            Color c = Tokens.get(today ? Tokens.ON_ACCENT : Tokens.TEXT);
            g2.setColor(c);
            String s = String.valueOf(number);
            g2.drawString(s, (getWidth() - fm.stringWidth(s)) / 2, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }
}
