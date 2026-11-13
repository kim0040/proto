package proto.ui.component;

import com.formdev.flatlaf.FlatClientProperties;
import proto.ui.Fmt;
import proto.ui.theme.Fonts;
import proto.ui.theme.Icons;
import proto.ui.theme.Tokens;

import javax.swing.JButton;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * [경계 boundary] 날짜를 보여 주는 칸. 누르면 작은 월 달력이 팝업으로 열려 날짜를 고른다.
 */
public class DatePickerField extends JButton {

    private LocalDate date;
    private final LocalDate today;
    private final List<Consumer<LocalDate>> listeners = new ArrayList<>();

    public DatePickerField(LocalDate initial, LocalDate today) {
        this.date = initial;
        this.today = today;
        setFont(Fonts.regular(Fonts.BODY));
        setHorizontalAlignment(SwingConstants.LEFT);
        setIcon(Icons.of("calendar-days", 16, Tokens.MUTED));
        setIconTextGap(8);
        putClientProperty(FlatClientProperties.STYLE, "background:$proto.surface; foreground:$proto.text;"
                + " borderColor:$proto.borderStrong; hoverBackground:$proto.subtle; pressedBackground:$proto.hover;"
                + " focusedBackground:$proto.surface; arc:10; margin:6,12,6,12; focusWidth:1; minimumWidth:0");
        addActionListener(e -> openPopup());
        refreshText();
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate value) {
        this.date = value;
        refreshText();
    }

    public void addChangeListener(Consumer<LocalDate> l) {
        listeners.add(l);
    }

    private void refreshText() {
        setText(Fmt.dateFull(date));
    }

    private void openPopup() {
        JPopupMenu menu = new JPopupMenu();
        menu.setBorder(javax.swing.BorderFactory.createLineBorder(Tokens.get(Tokens.BORDER)));
        menu.add(new MiniCalendar(date, today, picked -> {
            setDate(picked);
            menu.setVisible(false);
            listeners.forEach(l -> l.accept(picked));
        }));
        menu.show(this, 0, getHeight() + 4);
    }
}
