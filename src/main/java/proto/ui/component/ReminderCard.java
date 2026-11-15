package proto.ui.component;

import proto.entity.Occurrence;
import proto.ui.Fmt;
import proto.ui.theme.Fonts;
import proto.ui.theme.Icons;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;

/**
 * [경계 boundary] 알림 시각이 된 일정을 알리는 카드 (F10, F15): [5분 뒤 다시] [완료 처리] [닫기].
 * MainFrame#reminderDue가 만들어 Overlay에 올린다 (F10, F15). 버튼은 카드가 직접 처리하지 않고 Actions(MainFrame이 UiActions로 연결)에 맡기며,
 * Actions가 true를 돌려줄 때(처리 성공, 또는 이미 지워진 일정)만 카드를 닫는다.
 */
public class ReminderCard extends FloatingCard {

    /** 카드 버튼이 부르는 동작 */
    public interface Actions {
        /** [5분 뒤 다시]. 카드를 닫아도 되면 true */
        boolean snooze(Occurrence occurrence);

        /** [완료 처리]. 카드를 닫아도 되면 true */
        boolean complete(Occurrence occurrence);
    }

    private final Occurrence occurrence;

    public ReminderCard(Occurrence o, boolean snoozed, String categoryName, Actions actions, Runnable close) {
        super(new BorderLayout(0, Ui.px(10)));
        this.occurrence = o;
        add(header(snoozed), BorderLayout.NORTH);
        add(body(o, categoryName), BorderLayout.CENTER);
        add(buttons(o, actions, close), BorderLayout.SOUTH);
    }

    /** 이 카드가 알리는 하루치 일정 */
    public Occurrence occurrence() {
        return occurrence;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(Ui.px(360) + Ui.px(MARGIN) * 2, d.height);
    }

    private static JPanel header(boolean snoozed) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, Ui.px(6), 0));
        p.setOpaque(false);
        p.add(new JLabel(Icons.of("bell", 16, Tokens.ACCENT)));
        p.add(new TokenLabel(snoozed ? "다시 알려 드려요" : "곧 시작해요", Fonts.semibold(Fonts.SMALL), Tokens.ACCENT));
        return p;
    }

    private static JPanel body(Occurrence o, String categoryName) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        TokenLabel title = new TokenLabel(TextUtil.plain(o.title()), Fonts.semibold(Fonts.LEAD), Tokens.TEXT);
        title.setAlignmentX(LEFT_ALIGNMENT);
        JPanel category = new JPanel(new BorderLayout(Ui.px(6), 0));
        category.setOpaque(false);
        category.add(CategoryDot.of(o, 8), BorderLayout.WEST);
        category.add(new TokenLabel(TextUtil.plain(categoryName), Fonts.regular(Fonts.SMALL), Tokens.MUTED),
                BorderLayout.CENTER);
        JPanel meta = new JPanel(new BorderLayout(Ui.px(8), 0));
        meta.setOpaque(false);
        meta.setAlignmentX(LEFT_ALIGNMENT);
        meta.setBorder(BorderFactory.createEmptyBorder(Ui.px(2), 0, 0, 0));
        meta.add(new TokenLabel(Fmt.range(o), Fonts.regular(Fonts.SMALL), Tokens.MUTED), BorderLayout.WEST);
        meta.add(category, BorderLayout.CENTER);
        p.add(title);
        p.add(meta);
        return p;
    }

    private JPanel buttons(Occurrence o, Actions actions, Runnable close) {
        JButton snooze = Buttons.compact(Buttons.secondary("5분 뒤 다시"));
        JButton done = Buttons.compact(Buttons.primary("완료 처리"));
        JButton dismiss = Buttons.compact(Buttons.ghost("닫기"));
        snooze.addActionListener(e -> {
            if (actions.snooze(o)) {
                close.run();
            }
        });
        done.addActionListener(e -> {
            if (actions.complete(o)) {
                close.run();
            }
        });
        dismiss.addActionListener(e -> close.run());
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, Ui.px(8), 0));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(Ui.px(4), 0, 0, 0));
        p.add(snooze);
        p.add(done);
        p.add(Box.createHorizontalStrut(Ui.px(2)));
        p.add(dismiss);
        return p;
    }
}
