package proto.ui.dialog;

import proto.control.EditScope;
import proto.control.OperationResult;
import proto.control.ScheduleDraft;
import proto.entity.Category;
import proto.entity.Occurrence;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.ui.UiContext;
import proto.ui.component.Buttons;
import proto.ui.component.DatePickerField;
import proto.ui.component.Ime;
import proto.ui.component.Segmented;
import proto.ui.component.TimeCombo;
import proto.ui.component.TokenLabel;
import proto.ui.dialog.FieldErrors.Field;
import proto.ui.theme.Fonts;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * [경계 boundary] 일정 등록·수정 창 (F1, F3, F13). 저장하면 검증 → 겹침 확인 → (반복이면) 범위 선택 → 저장 순으로 진행한다.
 */
public class ScheduleDialog extends BaseDialog {

    private static final int MEMO_MAX = 200;

    private final UiContext ctx;
    private final Occurrence editing;
    private final FieldErrors errors = new FieldErrors(this::relayout);

    private final JTextField title = new JTextField();
    private final DatePickerField date;
    private final TimeCombo start;
    private final TimeCombo end;
    private final JComboBox<Category> category;
    private final Segmented<RepeatRule> repeat = new Segmented<>(List.of(RepeatRule.values()), ScheduleDialog::repeatLabel);
    private final JCheckBox hasRepeatEnd = new JCheckBox("반복 종료일");
    private final DatePickerField repeatEnd;
    private final JPanel repeatEndRow = new JPanel(new FlowLayout(FlowLayout.LEFT, Ui.px(10), 0));
    private final JComboBox<ReminderOption> reminder = FormKit.labelCombo(ReminderOption.values(), ReminderOption::label);
    private final JTextArea memo = new JTextArea(3, 20);
    private final TokenLabel counter = new TokenLabel("0/" + MEMO_MAX, Fonts.regular(Fonts.CAPTION), Tokens.MUTED);
    private boolean saved;
    private boolean busy;
    private JButton deleteButton;

    /** editing이 null이면 새 일정, 아니면 그 일정을 수정한다. draft는 처음 채울 값 */
    public ScheduleDialog(UiContext ctx, Occurrence editing, ScheduleDraft draft) {
        super(ctx.window(), editing == null ? "새 일정" : "일정 수정");
        this.ctx = ctx;
        this.editing = editing;
        LocalDate today = ctx.schedules().today();
        date = new DatePickerField(draft.date(), today);
        start = new TimeCombo(draft.startTime() == null ? LocalTime.of(9, 0) : draft.startTime()).named("시작 시간");
        end = new TimeCombo(draft.endTime() == null ? LocalTime.of(10, 0) : draft.endTime()).named("끝 시간");
        category = FormKit.categoryCombo(ctx.categories().getCategories());
        repeatEnd = new DatePickerField(draft.repeatEndDate() == null ? draft.date().plusMonths(1) : draft.repeatEndDate(), today);
        fill(draft);
        JPanel footer = buttons();
        setBody(buildBody(), footer, 28);
        getRootPane().setDefaultButton(saveButton);
        Ime.track(title);
        Ime.track(memo);
        start.onBlur(this::checkTimes);
        end.onBlur(this::checkTimes);
        title.requestFocusInWindow();
    }

    private JButton saveButton;

    public boolean wasSaved() {
        return saved;
    }

    private static String repeatLabel(RepeatRule r) {
        return r == RepeatRule.NONE ? "안 함" : r.label();
    }

    private void fill(ScheduleDraft d) {
        title.setText(d.title() == null ? "" : d.title());
        memo.setText(d.memo() == null ? "" : d.memo());
        selectCategory(d.categoryId());
        repeat.setValue(d.repeatRule() == null ? RepeatRule.NONE : d.repeatRule(), false);
        reminder.setSelectedItem(d.reminder() == null ? ReminderOption.NONE : d.reminder());
        hasRepeatEnd.setSelected(d.repeatEndDate() != null);
        updateRepeatEndVisibility();
        updateCounter();
    }

    private void selectCategory(String id) {
        for (int i = 0; i < category.getItemCount(); i++) {
            if (category.getItemAt(i).getId().equals(id)) {
                category.setSelectedIndex(i);
                return;
            }
        }
    }

    private JPanel buildBody() {
        FormKit.styleField(title);
        title.setFont(Fonts.semibold(Fonts.TITLE - 2));
        title.putClientProperty("JTextField.placeholderText", "일정 제목");
        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(row(FormKit.block("제목", title, errors.label(Field.TITLE))));
        form.add(row(FormKit.block("날짜", date, errors.label(Field.DATE))));
        form.add(row(timeBlock()));
        form.add(row(FormKit.block("카테고리", category, errors.label(Field.CATEGORY))));
        form.add(row(repeatBlock()));
        form.add(row(FormKit.block("알림", reminder, new TokenLabel("", Fonts.regular(Fonts.SMALL), Tokens.DANGER))));
        form.add(row(memoBlock()));
        form.add(errors.label(Field.GENERAL));
        JPanel root = new proto.ui.component.WidthPanel(new BorderLayout(), 520);
        root.add(form, BorderLayout.CENTER);
        return root;
    }

    private static JPanel row(JPanel block) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(0, 0, Ui.px(14), 0));
        p.add(block, BorderLayout.CENTER);
        p.setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }

    private JPanel timeBlock() {
        JPanel times = new JPanel(new GridLayout(1, 2, Ui.px(12), 0));
        times.setOpaque(false);
        times.add(start);
        times.add(end);
        JPanel heads = new JPanel(new GridLayout(1, 2, Ui.px(12), 0));
        heads.setOpaque(false);
        heads.add(new TokenLabel("시작", Fonts.medium(Fonts.CAPTION), Tokens.MUTED));
        heads.add(new TokenLabel("끝", Fonts.medium(Fonts.CAPTION), Tokens.MUTED));
        JPanel p = new JPanel(new BorderLayout(0, Ui.px(6)));
        p.setOpaque(false);
        p.add(heads, BorderLayout.NORTH);
        p.add(times, BorderLayout.CENTER);
        p.add(errors.label(Field.TIME), BorderLayout.SOUTH);
        return p;
    }

    private JPanel repeatBlock() {
        hasRepeatEnd.setOpaque(false);
        hasRepeatEnd.setFont(Fonts.regular(Fonts.BODY));
        hasRepeatEnd.addActionListener(e -> updateRepeatEndVisibility());
        repeat.addChangeListener(r -> updateRepeatEndVisibility());
        repeatEndRow.setOpaque(false);
        repeatEndRow.setBorder(BorderFactory.createEmptyBorder(Ui.px(10), 0, 0, 0));
        repeatEndRow.add(hasRepeatEnd);
        repeatEndRow.add(repeatEnd);
        JPanel inner = new JPanel(new BorderLayout());
        inner.setOpaque(false);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(repeat);
        inner.add(left, BorderLayout.NORTH);
        inner.add(repeatEndRow, BorderLayout.CENTER);
        inner.add(errors.label(Field.REPEAT), BorderLayout.SOUTH);
        return FormKit.block("반복", inner, new TokenLabel("", Fonts.regular(Fonts.SMALL), Tokens.DANGER));
    }

    private void updateRepeatEndVisibility() {
        boolean repeating = repeat.value() != RepeatRule.NONE;
        repeatEndRow.setVisible(repeating);
        repeatEnd.setVisible(repeating && hasRepeatEnd.isSelected());
        relayout();
    }

    private JPanel memoBlock() {
        memo.setLineWrap(true);
        memo.setWrapStyleWord(true);
        memo.setFont(Fonts.regular(Fonts.BODY));
        proto.ui.component.Listeners.onTextChange(memo, this::updateCounter);
        JScrollPane scroll = new JScrollPane(memo);
        scroll.setPreferredSize(new Dimension(Ui.px(100), Ui.px(84)));
        scroll.putClientProperty("JComponent.roundRect", true);
        JPanel p = new JPanel(new BorderLayout(0, Ui.px(4)));
        p.setOpaque(false);
        counter.setHorizontalAlignment(TokenLabel.RIGHT);
        p.add(scroll, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(errors.label(Field.MEMO), BorderLayout.CENTER);
        south.add(counter, BorderLayout.EAST);
        p.add(south, BorderLayout.SOUTH);
        return FormKit.block("메모", p, new TokenLabel("", Fonts.regular(Fonts.SMALL), Tokens.DANGER));
    }

    private void updateCounter() {
        int n = memo.getText().length();
        counter.setText(n + "/" + MEMO_MAX);
        counter.setColorToken(n > MEMO_MAX ? Tokens.DANGER : Tokens.MUTED);
    }

    private JPanel buttons() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, Ui.px(8), 0));
        right.setOpaque(false);
        JButton cancel = Buttons.secondary("취소");
        cancel.addActionListener(e -> onCancel());
        saveButton = Buttons.primary("저장");
        saveButton.addActionListener(e -> onSave(e.getModifiers() == 0));
        right.add(cancel);
        right.add(saveButton);
        p.add(right, BorderLayout.EAST);
        if (editing != null) {
            deleteButton = Buttons.danger("삭제");
            deleteButton.addActionListener(e -> onDelete());
            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            left.setOpaque(false);
            left.add(deleteButton);
            p.add(left, BorderLayout.WEST);
        }
        return p;
    }

    private void relayout() {
        refit();
    }

    /** 저장·삭제가 진행되는 동안 버튼을 잠근다 (Enter를 연타해도 두 번 저장되지 않게) */
    private void setBusy(boolean value) {
        busy = value;
        saveButton.setEnabled(!value);
        if (deleteButton != null) {
            deleteButton.setEnabled(!value);
        }
    }

    private void onDelete() {
        if (busy) {
            return;
        }
        Ime.commitFocused();
        setBusy(true);
        try {
            if (ctx.actions().delete(editing)) {
                close(false);
            }
        } finally {
            setBusy(false);
        }
    }

    /** 시간 입력칸 둘을 검사해 읽을 수 없는 칸을 표시하고 안내 문장을 돌려준다 (문제 없으면 null) */
    private String timeProblem(LocalTime s, LocalTime e) {
        start.markInvalid(s == null);
        end.markInvalid(e == null);
        if (s != null && e != null) {
            return null;
        }
        String which = s == null && e == null ? "시작과 끝 시간을" : s == null ? "시작 시간을" : "끝 시간을";
        return which + " 14:00처럼 입력해 주세요";
    }

    /** 시간 칸에서 포커스가 빠질 때: 읽을 수 없으면 칸 아래에 바로 안내하고, 읽을 수 있으면 안내를 지운다 */
    private void checkTimes() {
        errors.put(Field.TIME, timeProblem(start.time(), end.time()));
    }

    /** 입력값을 읽는다. 시간을 읽을 수 없으면 오류를 보여 주고 null */
    private ScheduleDraft readDraft() {
        LocalTime s = start.time();
        LocalTime e = end.time();
        String problem = timeProblem(s, e);
        if (problem != null) {
            errors.show(Field.TIME, problem);
            return null;
        }
        Category c = (Category) category.getSelectedItem();
        LocalDate until = repeat.value() != RepeatRule.NONE && hasRepeatEnd.isSelected() ? repeatEnd.getDate() : null;
        return new ScheduleDraft(title.getText(), date.getDate(), s, e, c == null ? null : c.getId(), memo.getText(),
                repeat.value(), until, (ReminderOption) reminder.getSelectedItem());
    }

    /** 저장. 이미 진행 중이거나, 키보드(Enter)로 눌렀는데 한글 조합 중이면(그 Enter는 글자 확정용) 무시한다 */
    private void onSave(boolean fromKeyboard) {
        if (busy || (fromKeyboard && Ime.swallowIfComposing())) {
            return;
        }
        Ime.commitFocused();
        setBusy(true);
        try {
            save();
        } finally {
            setBusy(false);
        }
    }

    private void save() {
        errors.clear();
        ScheduleDraft draft = readDraft();
        if (draft == null) {
            return;
        }
        Occurrence target = editing;
        if (editing != null) {
            Optional<Occurrence> latest = ctx.actions().fresh(editing);
            if (latest.isEmpty()) {
                close(false);
                return;
            }
            target = latest.get();
        }
        // 반복 일정이면 범위를 먼저 고른다. "이 일정만"은 반복이 떨어져 나가므로 그 규칙으로 검사해야 한다
        Optional<EditScope> scope = chooseScope(target);
        if (scope.isEmpty()) {
            return;
        }
        List<String> problems = ctx.schedules().validate(draft, target, scope.get());
        if (!problems.isEmpty()) {
            errors.show(problems);
            return;
        }
        if (!confirmConflicts(draft, target, scope.get())) {
            return;
        }
        persist(draft, target, scope.get());
    }

    private void persist(ScheduleDraft draft, Occurrence target, EditScope scope) {
        OperationResult r = editing == null ? ctx.schedules().create(draft)
                : ctx.schedules().update(target, draft, scope);
        if (r.success()) {
            saved = true;
            ctx.actions().toast(editing == null ? "일정을 추가했어요" : "일정을 저장했어요");
            close(false);
        } else {
            errors.show(Field.GENERAL, r.message());
        }
    }

    /** 반복 일정 수정이면 범위를 묻고, 아니면 기본값을 돌려준다 */
    private Optional<EditScope> chooseScope(Occurrence target) {
        if (target != null && target.isRepeating()) {
            return Dialogs.chooseScope(this, "반복 일정 수정", "수정", false);
        }
        return Optional.of(EditScope.THIS_AND_FOLLOWING);
    }

    private boolean confirmConflicts(ScheduleDraft draft, Occurrence target, EditScope scope) {
        List<Occurrence> conflicts = ctx.schedules().findConflicts(draft, target, scope);
        return conflicts.isEmpty() || Dialogs.confirmConflicts(this, conflicts, ctx.categories()::nameOf);
    }
}
