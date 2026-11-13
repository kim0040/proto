package proto.ui.dialog;

import proto.ui.component.TokenLabel;
import proto.ui.theme.Fonts;
import proto.ui.theme.Tokens;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * [경계 boundary] 검증 오류 문장을 알맞은 입력칸 아래에 나눠 보여 주는 도우미 (문장 속 낱말로 칸을 찾는다).
 */
final class FieldErrors {

    /** 오류가 붙을 수 있는 칸 */
    enum Field { TITLE, DATE, TIME, CATEGORY, REPEAT, MEMO, GENERAL }

    private final Map<Field, TokenLabel> labels = new EnumMap<>(Field.class);
    private final Runnable relayout;

    FieldErrors(Runnable relayout) {
        this.relayout = relayout;
        for (Field f : Field.values()) {
            TokenLabel l = new TokenLabel("", Fonts.regular(Fonts.SMALL), Tokens.DANGER);
            l.setVisible(false);
            labels.put(f, l);
        }
    }

    TokenLabel label(Field f) {
        return labels.get(f);
    }

    /** 모든 오류를 지운다 */
    void clear() {
        labels.values().forEach(l -> {
            l.setText("");
            l.setVisible(false);
        });
    }

    /** 오류 문장들을 칸별로 나눠 보여 준다 */
    void show(List<String> messages) {
        clear();
        Map<Field, StringBuilder> grouped = new EnumMap<>(Field.class);
        for (String m : messages) {
            StringBuilder sb = grouped.computeIfAbsent(classify(m), k -> new StringBuilder());
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(m);
        }
        grouped.forEach((f, text) -> {
            TokenLabel l = labels.get(f);
            l.setText(text.toString());
            l.setVisible(true);
        });
        relayout.run();
    }

    /** 오류 하나를 해당 칸에 바로 보여 준다 */
    void show(Field f, String message) {
        clear();
        labels.get(f).setText(message);
        labels.get(f).setVisible(true);
        relayout.run();
    }

    static Field classify(String message) {
        if (message.contains("제목")) {
            return Field.TITLE;
        }
        if (message.contains("반복 종료일")) {
            return Field.REPEAT;
        }
        if (message.contains("시간")) {
            return Field.TIME;
        }
        if (message.contains("날짜")) {
            return Field.DATE;
        }
        if (message.contains("메모")) {
            return Field.MEMO;
        }
        return message.contains("카테고리") ? Field.CATEGORY : Field.GENERAL;
    }
}
