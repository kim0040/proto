package proto.ui.view;

import proto.entity.Occurrence;
import proto.entity.ScheduleState;
import proto.ui.AppState;
import proto.ui.Fmt;
import proto.ui.UiContext;
import proto.ui.component.OccurrenceList;
import proto.ui.component.TokenLabel;
import proto.ui.layout.SizeClass;
import proto.ui.theme.Fonts;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.time.LocalDate;
import java.util.List;

/**
 * [경계 boundary] 오늘 보기 (F2): 한 줄 요약과 시간순 일정 목록. 일정이 없으면 안내 문구를 보여 준다.
 */
public class TodayView implements ContentView {

    private static final String LIST = "list";
    private static final String EMPTY = "empty";

    private final UiContext ctx;
    private final JPanel root = new JPanel(new CardLayout());
    private final TokenLabel summary = new TokenLabel("", Fonts.medium(Fonts.BODY), Tokens.MUTED);
    private final OccurrenceList list;
    private final EmptyState empty;
    private final JScrollPane scroll;

    public TodayView(UiContext ctx) {
        this.ctx = ctx;
        this.list = new OccurrenceList(ctx.actions(), false);
        this.empty = new EmptyState("새 일정", () -> ctx.actions().newSchedule(ctx.schedules().today()));
        this.scroll = new JScrollPane(list);
        root.setOpaque(false);
        root.add(buildList(), LIST);
        root.add(empty, EMPTY);
    }

    private JPanel buildList() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        applyPadding(ctx.size());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        p.add(summary, BorderLayout.NORTH);
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    private void applyPadding(SizeClass sc) {
        int g = sc.gutter();
        int trim = sc.verticalTrim();
        summary.setBorder(BorderFactory.createEmptyBorder(Ui.px(4 - trim / 2), Ui.px(g), Ui.px(12 - trim), Ui.px(g)));
        scroll.setBorder(BorderFactory.createEmptyBorder(0, Ui.px(g - 4), Ui.px(24 - trim), Ui.px(g - 4)));
    }

    /** 좁은 화면에서는 카테고리 이름을 감추고 점만 남긴다 (상태 알약은 항상 보인다) */
    @Override
    public void applySize(SizeClass sc) {
        applyPadding(sc);
        list.setHideCategoryName(sc.compact());
    }

    @Override
    public JComponent component() {
        return root;
    }

    @Override
    public String title() {
        return "오늘";
    }

    @Override
    public String subtitle() {
        return Fmt.dateLong(ctx.schedules().today());
    }

    @Override
    public boolean hasNavigator() {
        return false;
    }

    @Override
    public boolean usesFilters() {
        return true;
    }

    @Override
    public void refresh(boolean animate) {
        AppState s = ctx.state();
        LocalDate today = ctx.schedules().today();
        List<Occurrence> items = ctx.schedules().getOccurrences(today, today, s.categoryId(), s.keyword());
        CardLayout cards = (CardLayout) root.getLayout();
        if (items.isEmpty()) {
            empty.set(s.isFiltered() ? "조건에 맞는 일정이 없어요" : "오늘은 일정이 없어요",
                    s.isFiltered() ? "검색어나 카테고리를 바꿔 보세요" : "새 일정을 추가해 하루를 채워 보세요",
                    !s.isFiltered());
            cards.show(root, EMPTY);
            list.setItems(items, false);
            return;
        }
        summary.setText(summaryText(items));
        list.setItems(items, animate);
        cards.show(root, LIST);
    }

    private String summaryText(List<Occurrence> items) {
        long done = items.stream().filter(Occurrence::completed).count();
        long delayed = items.stream().filter(o -> ctx.schedules().stateOf(o) == ScheduleState.DELAYED).count();
        return "일정 " + items.size() + "개 · 완료 " + done + " · 지연 " + delayed;
    }
}
