package proto.ui.component;

import proto.ui.Fmt;

import javax.swing.JComboBox;
import javax.swing.JTextField;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.time.LocalTime;

/**
 * [경계 boundary] 30분 단위 목록에서 고르거나 "14:05"처럼 직접 입력하는 시간 콤보.
 */
public class TimeCombo extends JComboBox<String> {

    public TimeCombo(LocalTime initial) {
        super(slots());
        setEditable(true);
        setMaximumRowCount(8);
        setSelectedItem(Fmt.time(initial));
        getAccessibleContext().setAccessibleName("시간");
        ((JTextField) getEditor().getEditorComponent()).addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                normalize();
            }
        });
    }

    private static String[] slots() {
        String[] items = new String[48];
        for (int i = 0; i < items.length; i++) {
            items[i] = Fmt.time(LocalTime.of(i / 2, (i % 2) * 30));
        }
        return items;
    }

    /** 입력을 시간으로 읽는다. "9:00", "0900", "930"도 받는다. 읽을 수 없으면 null */
    public LocalTime time() {
        Object item = getEditor().getItem();
        return parse(item == null ? "" : item.toString());
    }

    public void setTime(LocalTime t) {
        setSelectedItem(Fmt.time(t));
    }

    /** 읽을 수 있는 입력이면 "HH:mm" 모양으로 정리한다 */
    private void normalize() {
        LocalTime t = time();
        if (t != null) {
            getEditor().setItem(Fmt.time(t));
        }
    }

    static LocalTime parse(String raw) {
        String s = raw.strip().replace("：", ":");
        try {
            if (s.contains(":")) {
                String[] p = s.split(":");
                return p.length == 2 ? LocalTime.of(Integer.parseInt(p[0].strip()), Integer.parseInt(p[1].strip())) : null;
            }
            if (s.matches("\\d{3,4}")) {
                int v = Integer.parseInt(s);
                return LocalTime.of(v / 100, v % 100);
            }
            return s.matches("\\d{1,2}") ? LocalTime.of(Integer.parseInt(s), 0) : null;
        } catch (NumberFormatException | java.time.DateTimeException e) {
            return null;
        }
    }
}
