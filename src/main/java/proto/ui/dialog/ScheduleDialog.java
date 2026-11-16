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
import proto.ui.component.Listeners;
import proto.ui.component.Segmented;
import proto.ui.component.TimeCombo;
import proto.ui.component.ToggleSwitch;
import proto.ui.component.TokenLabel;
import proto.ui.component.WidthPanel;
import proto.ui.dialog.FieldErrors.Field;
import proto.ui.theme.CategoryColors;
import proto.ui.theme.Fonts;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * [경계 boundary] 일정 등록·수정 창 (F1, F3, F13). 저장하면 (반복이면) 범위 선택 → 검증 → 겹침 확인 → 저장 순으로 진행한다.
 * UiActions가 새 일정(newSchedule, openNew)과 수정(open)에서 연다. 이 창은 입력값을 ScheduleDraft로 모아 ScheduleController에 넘기기만 하고,
 * 검증 규칙(F1)과 겹침 판단(F13)은 모두 controller가 한다.
 * 아무것도 바꾸지 않았으면 바깥 클릭·Esc로 바로 닫히고, 바꾼 것이 있으면 바깥 클릭은 흔들림으로 막고 Esc는 버릴지 묻는다.
 * "하루 종일" 스위치를 켜면 시작·끝 시간과 알림 줄이 접히고(160ms) 저장할 때 하루 종일 일정이 된다(시간은 00:00~23:59, 알림 없음으로 controller가 맞춘다).
 * 끄면 접기 전의 시간(수정 중이던 하루 종일 일정은 09:00~10:00)이 그대로 돌아온다.
 */
public class ScheduleDialog extends BaseDialog {

    private static final int MEMO_MAX = 200;
    private static final int BODY_WIDTH = 520;
    /** 입력칸 줄 사이의 세로 간격 (논리 px) */
    private static final int ROW_GAP = 14;
    private static final LocalTime DEFAULT_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_END = LocalTime.of(10, 0);
    private static final LocalTime ALL_DAY_END = LocalTime.of(23, 59);

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
    private final ToggleSwitch allDay;
    private final ColorSwatchRow colors;
    private final JTextArea memo = new JTextArea(3, 20);
    private final TokenLabel counter = new TokenLabel("0/" + MEMO_MAX, Fonts.regular(Fonts.CAPTION), Tokens.MUTED);
    private CollapseRow timeRow;
    private CollapseRow reminderRow;
    private JButton saveButton;
    private JButton deleteButton;
    private boolean saved;
    private boolean busy;
    /** 열 때의 입력값. 지금 값과 다르면 "바꾼 것이 있다"(dirty) */
    private List<Object> initial;

    /** editing이 null이면 새 일정, 아니면 그 일정을 수정한다. draft는 처음 채울 값 */
    public ScheduleDialog(UiContext ctx, Occurrence editing, ScheduleDraft draft) {
        super(ctx.window(), editing == null ? "새 일정" : "일정 수정");
        this.ctx = ctx;
        this.editing = editing;
        LocalDate today = ctx.schedules().today();
        date = new DatePickerField(draft.date(), today);
        // 하루 종일 일정의 시간(00:00~23:59)은 보여 주지 않는다. 스위치를 끄면 돌아올 시간으로 09:00~10:00을 둔다
        boolean timed = !draft.allDay();
        start = new TimeCombo(timed && draft.startTime() != null ? draft.startTime() : DEFAULT_START).named("시작 시간");
        end = new TimeCombo(timed && draft.endTime() != null ? draft.endTime() : DEFAULT_END).named("끝 시간");
        allDay = new ToggleSwitch(draft.allDay(), "하루 종일");
        category = FormKit.categoryCombo(ctx.categories().getCategories());
        colors = new ColorSwatchRow(ctx.categories().palette(), this::categoryColor, draft.colorKey());
        category.addActionListener(e -> colors.categoryChanged());
        repeatEnd = new DatePickerField(draft.repeatEndDate() == null ? draft.date().plusMonths(1) : draft.repeatEndDate(), today);
        fill(draft);
        JPanel footer = buttons();
        setBody(buildBody(), footer, 28);
        setDefaultButton(saveButton);
        setInitialFocus(title);
        Ime.track(title);
        Ime.track(memo);
        start.onBlur(this::checkTimes);
        end.onBlur(this::checkTimes);
        initial = snapshot();
    }

    public boolean wasSaved() {
        return saved;
    }

    /** 열고 나서 무엇이든 바꿨는지 */
    boolean isDirty() {
        return initial != null && !initial.equals(snapshot());
    }

    /** 바깥 클릭: 바꾼 게 없으면 닫고, 있으면 닫지 않고 흔들어 알린다 (입력한 내용을 실수로 잃지 않게) */
    @Override
    protected void onScrimClick() {
        if (busy) {
            return;
        }
        Ime.commitFocused();
        if (isDirty()) {
            shake();
        } else {
            onCancel();
        }
    }

    /** Esc: 바꾼 게 없으면 닫고, 있으면 버릴지 묻는다 ("계속 편집"이 기본) */
    @Override
    protected void onEscape() {
        if (busy) {
            return;
        }
        if (!isDirty() || Dialogs.confirmDiscard(this)) {
            onCancel();
        }
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
        form.add(allDayRow());
        timeRow = collapsible(row(timeBlock()));
        form.add(timeRow);
        form.add(row(FormKit.block("카테고리", category, errors.label(Field.CATEGORY))));
        form.add(row(FormKit.block("색", colors, new TokenLabel("", Fonts.regular(Fonts.SMALL), Tokens.DANGER))));
        form.add(row(repeatBlock()));
        reminderRow = collapsible(row(FormKit.block("알림", reminder,
                new TokenLabel("", Fonts.regular(Fonts.SMALL), Tokens.DANGER))));
        form.add(reminderRow);
        applyAllDay(false);
        form.add(row(memoBlock()));
        form.add(errors.label(Field.GENERAL));
        JPanel root = new WidthPanel(new BorderLayout(), BODY_WIDTH);
        TokenLabel heading = new TokenLabel(getTitle(), Fonts.semibold(Fonts.TITLE), Tokens.TEXT);
        heading.setBorder(BorderFactory.createEmptyBorder(0, 0, Ui.px(18), 0));
        root.add(heading, BorderLayout.NORTH);
        root.add(form, BorderLayout.CENTER);
        return root;
    }

    private CollapseRow collapsible(JPanel content) {
        CollapseRow row = new CollapseRow(content, this::relayout);
        row.setAlignmentX(LEFT_ALIGNMENT);
        return row;
    }

    /** "하루 종일" 한 줄: 글자(누르면 스위치도 바뀐다)와 오른쪽 스위치. Enter는 저장이 아니라 스위치를 바꾸는 데만 쓴다 */
    private JPanel allDayRow() {
        TokenLabel label = new TokenLabel("하루 종일", Fonts.medium(Fonts.BODY), Tokens.TEXT);
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        proto.ui.component.Listeners.onClick(label, e -> {
            allDay.setOn(!allDay.isOn());
            applyAllDay(true);
        });
        allDay.addChangeListener(on -> applyAllDay(true));
        allDay.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    e.consume();
                }
            }
        });
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(0, 0, Ui.px(ROW_GAP), 0));
        p.add(label, BorderLayout.WEST);
        p.add(allDay, BorderLayout.EAST);
        p.setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }

    /** 스위치 상태에 맞춰 시간·알림 줄을 접거나 편다. 접을 때는 시간 칸의 오류 표시도 지운다 */
    private void applyAllDay(boolean animate) {
        boolean on = allDay.isOn();
        timeRow.setCollapsed(on, animate);
        reminderRow.setCollapsed(on, animate);
        if (on) {
            start.markInvalid(false);
            end.markInvalid(false);
            errors.put(Field.TIME, null);
        }
    }

    private static JPanel row(JPanel block) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(0, 0, Ui.px(ROW_GAP), 0));
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
        Listeners.onTextChange(memo, this::updateCounter);
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

    /** 지금 입력값을 비교할 수 있게 모은다. 시간은 읽을 수 없는 글자여도 입력한 글자 그대로 비교한다 */
    private List<Object> snapshot() {
        return Arrays.asList(title.getText(), date.getDate(), start.getEditor().getItem(),
                end.getEditor().getItem(), category.getSelectedIndex(), repeat.value(), hasRepeatEnd.isSelected(),
                repeatEnd.getDate(), reminder.getSelectedItem(), memo.getText(), allDay.isOn(), colors.value());
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
    private String timeProblem(LocalTime startTime, LocalTime endTime) {
        start.markInvalid(startTime == null);
        end.markInvalid(endTime == null);
        if (startTime != null && endTime != null) {
            return null;
        }
        String which = startTime == null && endTime == null ? "시작과 끝 시간을"
                : startTime == null ? "시작 시간을" : "끝 시간을";
        return which + " 14:00처럼 입력해 주세요";
    }

    /** 시간 칸에서 포커스가 빠질 때: 읽을 수 없으면 칸 아래에 바로 안내하고, 읽을 수 있으면 안내를 지운다 */
    private void checkTimes() {
        errors.put(Field.TIME, timeProblem(start.time(), end.time()));
    }

    /** 입력값을 읽는다. 시간을 읽을 수 없으면 오류를 보여 주고 null */
    private ScheduleDraft readDraft() {
        if (allDay.isOn()) {
            return draftOf(LocalTime.MIDNIGHT, ALL_DAY_END, ReminderOption.NONE);
        }
        LocalTime startTime = start.time();
        LocalTime endTime = end.time();
        String problem = timeProblem(startTime, endTime);
        if (problem != null) {
            errors.show(Field.TIME, problem);
            return null;
        }
        return draftOf(startTime, endTime, (ReminderOption) reminder.getSelectedItem());
    }

    /**
     * 칸에 적힌 값으로 입력값을 만든다. 일정만의 색(없으면 카테고리 색)도 담는다.
     * 하루 종일이면 시간은 00:00~23:59, 알림은 없음으로 보내고(어차피 controller가 그렇게 맞춘다) allDay를 켠다.
     */
    private ScheduleDraft draftOf(LocalTime startTime, LocalTime endTime, ReminderOption reminderOption) {
        Category c = (Category) category.getSelectedItem();
        LocalDate until = repeat.value() != RepeatRule.NONE && hasRepeatEnd.isSelected() ? repeatEnd.getDate() : null;
        return new ScheduleDraft(title.getText(), date.getDate(), startTime, endTime, c == null ? null : c.getId(), memo.getText(),
                repeat.value(), until, reminderOption, allDay.isOn(), colors.value());
    }

    /** 지금 고른 카테고리의 색 ("색" 줄의 첫 점이 이 색으로 그려진다) */
    private Color categoryColor() {
        Category c = (Category) category.getSelectedItem();
        return CategoryColors.of(c == null ? null : c.getId());
    }

    /**
     * 저장. 이미 진행 중이면 무시한다.
     * 한글을 입력하는 중에 누른 Enter는 마지막 글자를 확정하는 용도이므로(Ime.swallowIfComposing) 저장으로 이어지면 안 된다. 마우스 클릭은 그대로 저장한다.
     */
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

    /** 범위를 검증보다 먼저 묻는 이유: "이 일정만"으로 바꾸면 반복이 사라지므로, 검증을 그 범위에 맞는 규칙(반복 없음)으로 해야 하기 때문이다. */
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
            ctx.actions().toastUndoable(editing == null ? "일정을 추가했어요" : "일정을 저장했어요");
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
