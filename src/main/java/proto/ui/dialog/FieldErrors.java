package proto.ui.dialog;

import proto.ui.component.TokenLabel;
import proto.ui.theme.Fonts;
import proto.ui.theme.Tokens;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * [경계 boundary] 검증 오류 문장을 알맞은 입력칸 아래에 나눠 보여 주는 도우미 (문장 속 낱말로 칸을 찾는다).
 * ScheduleDialog가 controller의 검증 결과(문장 목록, ScheduleValidator)를 받으면 여기에 넘겨서 해당하는 입력칸 아래에 보여 준다.
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

    /** 다른 칸의 오류는 그대로 두고 이 칸의 오류만 바꾼다 */
    void put(Field f, String message) {
        TokenLabel l = labels.get(f);
        l.setText(message == null ? "" : message);
        l.setVisible(message != null);
        relayout.run();
    }

    /** 오류 하나를 해당 칸에 바로 보여 준다 */
    void show(Field f, String message) {
        clear();
        labels.get(f).setText(message);
        labels.get(f).setVisible(true);
        relayout.run();
    }

    /**
     * 오류 문장에 들어 있는 낱말(제목, 날짜, 시간, 메모, 카테고리, 반복 종료일)로 어느 칸의 오류인지 고른다. 어디에도 맞지 않으면 GENERAL(대화상자 아래쪽)에 보여 준다.
     * 검사 순서가 중요하다: "반복 종료일은 시작 날짜보다…"는 "날짜"도 들어 있으므로 "반복 종료일"을 먼저 본다.
     * 이 방식은 검증 문장에 그 낱말이 들어 있다는 약속에 기대므로, ScheduleValidator의 문장을 바꿀 때 함께 확인해야 한다.
     */
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
