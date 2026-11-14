package proto.ui.component;

import proto.ui.Fmt;

import javax.swing.JComboBox;
import javax.swing.JTextField;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.time.LocalTime;

/**
 * [경계 boundary] 30분 단위 목록에서 고르거나 "14:05"처럼 직접 입력하는 시간 콤보.
 * ScheduleDialog와 FreeSlotDialog, SettingsView의 시간 입력칸이다 (F1, F12). 입력을 읽는 규칙(parse)은 이 클래스에만 있다.
 * 읽을 수 없는 입력은 예외 없이 null을 돌려주고, 사용자에게 보이는 오류 문장은 화면(FieldErrors)이 만든다.
 */
public class TimeCombo extends JComboBox<String> {

    private static final java.util.regex.Pattern COLON = java.util.regex.Pattern.compile("([0-9]{1,2}):([0-9]{1,2})");

    public TimeCombo(LocalTime initial) {
        super(slots());
        setEditable(true);
        setMaximumRowCount(8);
        setSelectedItem(Fmt.time(initial));
        getAccessibleContext().setAccessibleName("시간");
        Ime.track(this);
        ((JTextField) getEditor().getEditorComponent()).addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                normalize();
                markInvalid(time() == null);
            }
        });
    }

    /** 입력칸에서 포커스가 빠질 때(정리·검사가 끝난 뒤) 부를 동작을 단다 */
    public void onBlur(Runnable action) {
        getEditor().getEditorComponent().addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                action.run();
            }
        });
    }

    /** 읽을 수 없는 입력이면 칸 테두리를 오류색으로 표시한다 */
    public void markInvalid(boolean invalid) {
        putClientProperty(com.formdev.flatlaf.FlatClientProperties.OUTLINE,
                invalid ? com.formdev.flatlaf.FlatClientProperties.OUTLINE_ERROR : null);
    }

    /** 이름을 정한다 ("시작 시간", "끝 시간" 등 — 화면 읽기 프로그램이 읽는다) */
    public TimeCombo named(String name) {
        getAccessibleContext().setAccessibleName(name);
        return this;
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
        Ime.commit(getEditor().getEditorComponent());
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

    /**
     * 글을 시간으로 읽는다. "9", "09:00", "9:5", "930", "0930", "21:30"은 받고
     * "25:00", "ab", "", "9:00:", "14:05::", "9:000"은 null (예외 없이)
     */
    static LocalTime parse(String raw) {
        String s = raw == null ? "" : raw.strip().replace("：", ":");
        java.util.regex.Matcher colon = COLON.matcher(s);
        try {
            if (colon.matches()) {
                return LocalTime.of(Integer.parseInt(colon.group(1)), Integer.parseInt(colon.group(2)));
            }
            if (s.matches("[0-9]{3,4}")) {
                int v = Integer.parseInt(s);
                return LocalTime.of(v / 100, v % 100);
            }
            return s.matches("[0-9]{1,2}") ? LocalTime.of(Integer.parseInt(s), 0) : null;
        } catch (NumberFormatException | java.time.DateTimeException e) {
            return null;
        }
    }
}
