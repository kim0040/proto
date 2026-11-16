package proto.ui.component;

import com.formdev.flatlaf.FlatClientProperties;
import proto.ui.Fmt;
import proto.ui.theme.Fonts;
import proto.ui.theme.Icons;
import proto.ui.theme.Tokens;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * [경계 boundary] 날짜를 보여 주는 칸. 누르면 작은 월 달력이 팝업으로 열려 날짜를 고른다. 값은 절대 null이 되지 않는다.
 * 일정 대화상자(ScheduleDialog)와 빈 시간 찾기 대화상자(FreeSlotDialog)에서 날짜를 고를 때 쓴다 (F1, F3, F12).
 * 키보드로는 달력을 열어 고르거나, 칸에 포커스가 있을 때 화살표(±1일, ±7일)와 PageUp/PageDown(±1달)으로 바로 바꾼다.
 */
public class DatePickerField extends JButton {

    private final LocalDate today;
    private final List<Consumer<LocalDate>> listeners = new ArrayList<>();
    private LocalDate date;

    public DatePickerField(LocalDate initial, LocalDate today) {
        this.today = today;
        this.date = initial == null ? today : initial;
        setFont(Fonts.regular(Fonts.BODY));
        setHorizontalAlignment(SwingConstants.LEFT);
        setIcon(Icons.of("calendar-days", 16, Tokens.MUTED));
        setIconTextGap(8);
        putClientProperty(FlatClientProperties.STYLE, "background:$proto.surface; foreground:$proto.text;"
                + " borderColor:$proto.borderStrong; hoverBackground:$proto.subtle; pressedBackground:$proto.hover;"
                + " focusedBackground:$proto.surface; arc:10; margin:6,12,6,12; focusWidth:1; minimumWidth:0");
        addActionListener(e -> openPopup());
        getAccessibleContext().setAccessibleName("날짜");
        bindStep(KeyEvent.VK_LEFT, d -> d.minusDays(1));
        bindStep(KeyEvent.VK_RIGHT, d -> d.plusDays(1));
        bindStep(KeyEvent.VK_UP, d -> d.minusDays(7));
        bindStep(KeyEvent.VK_DOWN, d -> d.plusDays(7));
        bindStep(KeyEvent.VK_PAGE_UP, d -> d.minusMonths(1));
        bindStep(KeyEvent.VK_PAGE_DOWN, d -> d.plusMonths(1));
        refreshText();
    }

    public LocalDate getDate() {
        return date;
    }

    /** 날짜를 바꾼다. null은 무시한다 (날짜는 늘 있어야 한다) */
    public void setDate(LocalDate value) {
        if (value == null) {
            return;
        }
        this.date = value;
        refreshText();
    }

    public void onPick(Consumer<LocalDate> l) {
        listeners.add(l);
    }

    /** 칸에 포커스가 있을 때 키 하나로 날짜를 옮긴다 (팝업을 열지 않고도 키보드만으로 고를 수 있게) */
    private void bindStep(int key, UnaryOperator<LocalDate> step) {
        String name = "proto-step-" + key;
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key, 0), name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                LocalDate next = step.apply(date);
                setDate(next);
                listeners.forEach(l -> l.accept(next));
            }
        });
    }

    private void refreshText() {
        setText(Fmt.dateFull(date));
    }

    private void openPopup() {
        JPopupMenu menu = new JPopupMenu();
        menu.setBorder(BorderFactory.createLineBorder(Tokens.get(Tokens.BORDER)));
        menu.setFocusable(true);
        MiniCalendar cal = new MiniCalendar(date, today, picked -> {
            setDate(picked);
            menu.setVisible(false);
            requestFocusInWindow();
            listeners.forEach(l -> l.accept(picked));
        });
        cal.setOnEscape(() -> {
            menu.setVisible(false);
            requestFocusInWindow();
        });
        menu.add(cal);
        menu.show(this, 0, getHeight() + 4);
        SwingUtilities.invokeLater(cal::focusGrid);
    }
}
