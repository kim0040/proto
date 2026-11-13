package proto.ui.dialog;

import proto.control.ScheduleDraft;
import proto.entity.TimeSlot;
import proto.ui.Fmt;
import proto.ui.UiContext;
import proto.ui.component.Buttons;
import proto.ui.component.DatePickerField;
import proto.ui.component.ScrollPanel;
import proto.ui.component.Segmented;
import proto.ui.component.TimeCombo;
import proto.ui.component.TokenLabel;
import proto.ui.theme.Fonts;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * [경계 boundary] 빈 시간 찾기 창 (F12): 날짜·길이·시간대를 고르면 비어 있는 구간을 보여 주고, 고른 구간으로 새 일정을 연다.
 */
public class FreeSlotDialog extends BaseDialog {

    /** 길이 선택지 */
    private enum Length {
        M30("30분", 30), H1("1시간", 60), H1_30("1시간 30분", 90), H2("2시간", 120), CUSTOM("직접", 0);

        private final String label;
        private final int minutes;

        Length(String label, int minutes) {
            this.label = label;
            this.minutes = minutes;
        }
    }

    private final UiContext ctx;
    private final DatePickerField date;
    private final Segmented<Length> length = new Segmented<>(List.of(Length.values()), l -> l.label);
    private final JSpinner custom = new JSpinner(new SpinnerNumberModel(45, 15, 480, 15));
    private final JPanel customRow = new JPanel(new FlowLayout(FlowLayout.LEFT, Ui.px(8), 0));
    private final TimeCombo from = new TimeCombo(proto.ui.UiSettings.freeFrom());
    private final TimeCombo to = new TimeCombo(proto.ui.UiSettings.freeTo());
    private final ScrollPanel results = new ScrollPanel(null);
    private TimeSlot chosen;

    public FreeSlotDialog(UiContext ctx) {
        super(ctx.window(), "빈 시간 찾기");
        this.ctx = ctx;
        date = new DatePickerField(ctx.schedules().today(), ctx.schedules().today());
        results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS));
        length.setValue(Length.H1, false);
        setBody(buildBody(), 28);
        search();
    }

    /** 사용자가 고른 빈 시간 (없으면 null) */
    public TimeSlot chosenSlot() {
        return chosen;
    }

    private JPanel buildBody() {
        JPanel root = new proto.ui.component.WidthPanel(new BorderLayout(0, Ui.px(16)), 520);
        root.add(new TokenLabel("빈 시간 찾기", Fonts.semibold(Fonts.TITLE), Tokens.TEXT), BorderLayout.NORTH);
        root.add(content(), BorderLayout.CENTER);
        JButton close = Buttons.secondary("닫기");
        close.addActionListener(e -> onCancel());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        south.setOpaque(false);
        south.add(close);
        root.add(south, BorderLayout.SOUTH);
        return root;
    }

    private JPanel content() {
        date.onPick(d -> search());
        length.addChangeListener(l -> {
            customRow.setVisible(l == Length.CUSTOM);
            search();
        });
        custom.addChangeListener(e -> search());
        from.addActionListener(e -> search());
        to.addActionListener(e -> search());
        customRow.setOpaque(false);
        customRow.add(custom);
        customRow.add(new TokenLabel("분 (15~480)", Fonts.regular(Fonts.SMALL), Tokens.MUTED));
        customRow.setVisible(false);
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(FormKit.block("날짜", date, new JLabel()));
        p.add(Box.createVerticalStrut(Ui.px(14)));
        p.add(FormKit.block("필요한 시간", lengthBox(), new JLabel()));
        p.add(Box.createVerticalStrut(Ui.px(14)));
        p.add(FormKit.block("찾을 시간대", window(), new JLabel()));
        p.add(Box.createVerticalStrut(Ui.px(18)));
        p.add(resultScroll());
        return p;
    }

    private JPanel lengthBox() {
        JPanel p = new JPanel(new BorderLayout(0, Ui.px(8)));
        p.setOpaque(false);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(length);
        p.add(left, BorderLayout.NORTH);
        p.add(customRow, BorderLayout.CENTER);
        return p;
    }

    private JPanel window() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        from.setPreferredSize(new Dimension(Ui.px(110), from.getPreferredSize().height));
        to.setPreferredSize(new Dimension(Ui.px(110), to.getPreferredSize().height));
        p.add(from);
        p.add(javax.swing.Box.createHorizontalStrut(Ui.px(12)));
        p.add(new TokenLabel("–", Fonts.regular(Fonts.BODY), Tokens.MUTED));
        p.add(javax.swing.Box.createHorizontalStrut(Ui.px(12)));
        p.add(to);
        return p;
    }

    private JScrollPane resultScroll() {
        JScrollPane s = new JScrollPane(results);
        s.setBorder(BorderFactory.createEmptyBorder());
        s.setOpaque(false);
        s.getViewport().setOpaque(false);
        s.setPreferredSize(new Dimension(Ui.px(464), Ui.px(210)));
        s.setAlignmentX(LEFT_ALIGNMENT);
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, Ui.px(210)));
        return s;
    }

    private int minutes() {
        Length l = length.value();
        return l == Length.CUSTOM ? (Integer) custom.getValue() : l.minutes;
    }

    private void search() {
        LocalTime f = from.time();
        LocalTime t = to.time();
        results.removeAll();
        if (f == null || t == null || !f.isBefore(t)) {
            results.add(message("시간대를 14:00처럼 시작이 끝보다 빠르게 입력해 주세요"));
        } else {
            List<TimeSlot> slots = ctx.schedules().findFreeSlots(date.getDate(), minutes(), f, t);
            if (slots.isEmpty()) {
                results.add(message("이 날은 빈 시간이 없어요"));
            }
            slots.forEach(s -> results.add(slotRow(s)));
        }
        results.revalidate();
        results.repaint();
    }

    private JPanel message(String text) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, Ui.px(36)));
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, Ui.px(120)));
        p.setAlignmentX(0f);
        p.add(new TokenLabel(text, Fonts.medium(Fonts.BODY), Tokens.MUTED));
        return p;
    }

    private JPanel slotRow(TimeSlot s) {
        JPanel p = new JPanel(new BorderLayout(Ui.px(12), 0));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(Ui.px(8), Ui.px(4), Ui.px(8), Ui.px(4)));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, Ui.px(60)));
        p.setAlignmentX(0f);
        JPanel text = new JPanel(new FlowLayout(FlowLayout.LEFT, Ui.px(8), 0));
        text.setOpaque(false);
        text.add(new TokenLabel(Fmt.range(s.start(), s.end()), Fonts.semibold(Fonts.BODY), Tokens.TEXT));
        text.add(new TokenLabel("· " + Fmt.duration(s.minutes()) + " 비어 있어요", Fonts.regular(Fonts.SMALL),
                Tokens.MUTED));
        JButton add = Buttons.compact(Buttons.secondary("이 시간에 추가"));
        add.addActionListener(e -> {
            chosen = s;
            close(false);
        });
        p.add(text, BorderLayout.CENTER);
        p.add(add, BorderLayout.EAST);
        return p;
    }

    /** 고른 구간으로 채운 새 일정 입력값 (고르지 않았으면 null) */
    public ScheduleDraft draft() {
        return chosen == null ? null : ctx.schedules().draftForSlot(chosen);
    }
}
