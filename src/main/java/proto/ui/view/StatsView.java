package proto.ui.view;

import proto.entity.WeeklyStatistics;
import proto.ui.Fmt;
import proto.ui.UiContext;
import proto.ui.component.HorizontalBars;
import proto.ui.component.ScrollPanel;
import proto.ui.component.TokenLabel;
import proto.ui.component.WeekdayColumns;
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
import java.awt.CardLayout;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * [경계 boundary] 주간 통계 보기 (F14): 큰 숫자 3개(전체·완료율·지연), 카테고리별 계획 시간 막대, 요일별 일정 수.
 */
public class StatsView implements ContentView {

    private final UiContext ctx;
    private final JPanel root = new JPanel(new CardLayout());
    private final TokenLabel total = bigNumber();
    private final TokenLabel rate = bigNumber();
    private final TokenLabel delayed = bigNumber();
    private final HorizontalBars bars = new HorizontalBars();
    private final WeekdayColumns columns = new WeekdayColumns();
    private final EmptyState empty;

    public StatsView(UiContext ctx) {
        this.ctx = ctx;
        this.empty = new EmptyState("새 일정", () -> ctx.actions().newSchedule(ctx.state().weekStart()));
        empty.set("이 주에는 일정이 없어요", "일정을 추가하면 계획 시간과 완료율을 볼 수 있어요", true);
        root.setOpaque(false);
        root.add(buildContent(), "content");
        root.add(empty, "empty");
    }

    private static TokenLabel bigNumber() {
        return new TokenLabel("0", Fonts.bold(Fonts.DISPLAY + 6), Tokens.TEXT);
    }

    private JComponent buildContent() {
        ScrollPanel body = new ScrollPanel(null);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(Ui.px(8), Ui.px(32), Ui.px(32), Ui.px(32)));
        body.add(numbers());
        body.add(Box.createVerticalStrut(Ui.px(36)));
        body.add(section("카테고리별 계획 시간", bars));
        body.add(Box.createVerticalStrut(Ui.px(32)));
        body.add(section("요일별 일정 수", columns));
        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        return scroll;
    }

    private JPanel numbers() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setAlignmentX(0f);
        p.add(stat(total, "전체 일정"));
        p.add(Box.createHorizontalStrut(Ui.px(64)));
        p.add(stat(rate, "완료율"));
        p.add(Box.createHorizontalStrut(Ui.px(64)));
        p.add(stat(delayed, "지연"));
        p.add(Box.createHorizontalGlue());
        return p;
    }

    private static JPanel stat(TokenLabel number, String label) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        number.setAlignmentX(0f);
        TokenLabel l = new TokenLabel(label, Fonts.medium(Fonts.SMALL), Tokens.MUTED);
        l.setAlignmentX(0f);
        p.add(number);
        p.add(l);
        return p;
    }

    private static JPanel section(String title, JComponent content) {
        JPanel p = new JPanel(new BorderLayout(0, Ui.px(14)));
        p.setOpaque(false);
        p.setAlignmentX(0f);
        p.add(new TokenLabel(title, Fonts.semibold(Fonts.LEAD), Tokens.TEXT), BorderLayout.NORTH);
        p.add(content, BorderLayout.CENTER);
        return p;
    }

    @Override
    public JComponent component() {
        return root;
    }

    @Override
    public String title() {
        return "주간 통계";
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
        return false;
    }

    @Override
    public void refresh(boolean animate) {
        WeeklyStatistics st = ctx.schedules().weeklyStatistics(ctx.state().anchor());
        CardLayout cards = (CardLayout) root.getLayout();
        if (st.total() == 0) {
            cards.show(root, "empty");
            return;
        }
        total.setText(String.valueOf(st.total()));
        rate.setText(Math.round(st.completionRate() * 100) + "%");
        delayed.setText(String.valueOf(st.delayed()));
        delayed.setColorToken(st.delayed() > 0 ? Tokens.DANGER : Tokens.TEXT);
        bars.setRows(rows(st.minutesByCategory()), false);
        LocalDate today = ctx.schedules().today();
        boolean thisWeek = !today.isBefore(st.weekStart()) && !today.isAfter(st.weekEnd());
        columns.setCounts(st.countByDay(), thisWeek ? today.getDayOfWeek() : null, false);
        cards.show(root, "content");
    }

    @Override
    public void shown() {
        bars.replay();
        columns.replay();
    }

    private List<HorizontalBars.Row> rows(Map<String, Long> minutes) {
        return minutes.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey(Comparator.naturalOrder())))
                .map(e -> new HorizontalBars.Row(e.getKey(), ctx.categories().nameOf(e.getKey()), e.getValue()))
                .toList();
    }
}
