package proto.ui.dialog;

import com.formdev.flatlaf.FlatClientProperties;
import proto.control.CategoryListener;
import proto.control.OperationResult;
import proto.entity.Category;
import proto.ui.UiContext;
import proto.ui.component.Buttons;
import proto.ui.component.ColorChip;
import proto.ui.component.ColorSwatchPopover;
import proto.ui.component.Ime;
import proto.ui.component.ScrollPanel;
import proto.ui.component.TextUtil;
import proto.ui.component.TokenLabel;
import proto.ui.theme.Fonts;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.KeyboardFocusManager;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * [경계 boundary] 카테고리 관리 창 (F7): 이름 바로 고치기, 색 고르기, 삭제(개인은 불가), 추가.
 * UiActions#openCategories가 연다. 변경은 CategoryController에 요청하고, 결과는 CategoryListener(categoriesChanged)로 돌아와 목록을 다시 만든다.
 * 줄 앞의 색 칩을 누르면 12색 팝오버(ColorSwatchPopover)가 열리고, 고르는 즉시 CategoryController#setColor로 저장되어 모든 화면에 반영된다.
 * "개인" 카테고리는 삭제 버튼이 꺼져 있다 (CategoryController#canDelete). 이름 검사 규칙은 화면이 아니라 controller가 가진다.
 * 새 이름 칸이 비어 있고 고치는 중인 이름이 없으면 바깥 클릭·Esc로 닫힌다. 카테고리 삭제는 되돌릴 수 없어서 따로 확인을 받는다.
 */
public class CategoryDialog extends BaseDialog implements CategoryListener {

    /** 이름 칸에 원래 이름을 붙여 두는 키 (고치는 중인지 알아보려고) */
    private static final String ORIGINAL = "proto.originalName";
    private static final int LIST_WIDTH = 420;
    private static final int ROW_HEIGHT = 48;
    private static final int LIST_MIN_HEIGHT = 144;
    private static final int LIST_MAX_HEIGHT = 320;

    private final UiContext ctx;
    private final ScrollPanel rows = new ScrollPanel(null);
    private final TokenLabel error = new TokenLabel("", Fonts.regular(Fonts.SMALL), Tokens.DANGER);
    private final JTextField addField = new JTextField();
    private JScrollPane scroll;

    public CategoryDialog(UiContext ctx) {
        super(ctx.window(), "카테고리 관리");
        this.ctx = ctx;
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        error.setVisible(false);
        setBody(buildBody(), 28);
        rebuildRows();
        ctx.categories().addListener(this);
        setInitialFocus(addField);
    }

    @Override
    protected void onClosed() {
        ctx.categories().removeListener(this);
    }

    /** 바깥 클릭: 새 이름을 쓰는 중이거나 이름을 고치는 중이면 닫지 않고 흔들어 알린다 */
    @Override
    protected void onScrimClick() {
        Ime.commitFocused();
        if (addField.getText().isBlank() && renaming() == null) {
            onCancel();
        } else {
            shake();
        }
    }

    /** Esc: 고치던 이름은 원래대로, 쓰던 새 이름은 지운다. 둘 다 없을 때 닫는다 */
    @Override
    protected void onEscape() {
        JTextField field = renaming();
        if (field != null) {
            field.setText((String) field.getClientProperty(ORIGINAL));
        } else if (!addField.getText().isEmpty()) {
            addField.setText("");
        } else {
            onCancel();
        }
    }

    /** 다른 곳(예: 같은 창의 추가·삭제)에서 카테고리가 바뀌면 불린다. controller가 어느 스레드에서든 알릴 수 있으므로 이벤트 스레드로 옮겨서 목록을 다시 만든다. */
    @Override
    public void categoriesChanged() {
        SwingUtilities.invokeLater(this::rebuildRows);
    }

    /** 이름을 고치는 중인 칸 (포커스가 있고 원래 이름과 다른 칸). 없으면 null */
    private JTextField renaming() {
        Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (owner instanceof JTextField t && t.getClientProperty(ORIGINAL) instanceof String original
                && !t.getText().strip().equals(original)) {
            return t;
        }
        return null;
    }

    private JPanel buildBody() {
        JPanel root = new JPanel(new BorderLayout(0, Ui.px(14)));
        root.setOpaque(false);
        root.add(new TokenLabel("카테고리", Fonts.semibold(Fonts.TITLE), Tokens.TEXT), BorderLayout.NORTH);
        scroll = new JScrollPane(rows);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setPreferredSize(new Dimension(Ui.px(LIST_WIDTH), Ui.px(240)));
        JPanel south = new JPanel(new BorderLayout(0, Ui.px(10)));
        south.setOpaque(false);
        south.add(error, BorderLayout.NORTH);
        south.add(addRow(), BorderLayout.CENTER);
        south.add(closeRow(), BorderLayout.SOUTH);
        JPanel center = new JPanel(new BorderLayout(0, Ui.px(8)));
        center.setOpaque(false);
        center.add(scroll, BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        return root;
    }

    private JPanel addRow() {
        FormKit.styleField(addField);
        addField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "새 카테고리 이름");
        JButton add = Buttons.secondary("추가");
        add.addActionListener(e -> onAdd());
        addField.addActionListener(e -> onAdd());
        Ime.track(addField);
        JPanel p = new JPanel(new BorderLayout(Ui.px(8), 0));
        p.setOpaque(false);
        p.add(addField, BorderLayout.CENTER);
        p.add(add, BorderLayout.EAST);
        return p;
    }

    private JPanel closeRow() {
        JButton close = Buttons.primary("닫기");
        close.addActionListener(e -> close(false));
        setDefaultButton(null);
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(Ui.px(6), 0, 0, 0));
        p.add(close);
        return p;
    }

    private void onAdd() {
        Ime.commit(addField);
        String name = addField.getText();
        if (name.isBlank()) {
            return;
        }
        OperationResult r = ctx.categories().add(name);
        if (r.success()) {
            addField.setText("");
            showError(null);
            ctx.actions().toast("카테고리를 추가했어요");
        } else {
            showError(r.message());
        }
    }

    private void showError(String message) {
        error.setText(message == null ? "" : TextUtil.plain(message));
        error.setVisible(message != null);
        refit();
    }

    private void rebuildRows() {
        String focusedChip = focusedChipCategory();
        rows.removeAll();
        for (Category c : ctx.categories().getCategories()) {
            rows.add(row(c));
        }
        int height = Math.max(Ui.px(LIST_MIN_HEIGHT),
                Math.min(Ui.px(LIST_MAX_HEIGHT), ctx.categories().getCategories().size() * Ui.px(ROW_HEIGHT)));
        scroll.setPreferredSize(new Dimension(Ui.px(LIST_WIDTH), height));
        rows.revalidate();
        rows.repaint();
        refit();
        restoreChipFocus(focusedChip);
    }

    /** 색 칩에 포커스가 있었다면 그 카테고리 id (목록을 다시 만들어도 키보드로 이어 쓰게 하려고). 없으면 null */
    private String focusedChipCategory() {
        Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        return owner instanceof ColorChip chip ? chip.categoryId() : null;
    }

    private void restoreChipFocus(String categoryId) {
        if (categoryId == null) {
            return;
        }
        for (Component row : rows.getComponents()) {
            if (row instanceof JPanel p && p.getComponent(0) instanceof ColorChip chip
                    && chip.categoryId().equals(categoryId)) {
                chip.requestFocusInWindow();
            }
        }
    }

    private JPanel row(Category c) {
        JPanel p = new JPanel(new BorderLayout(Ui.px(12), 0));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(Ui.px(3), Ui.px(4), Ui.px(3), Ui.px(4)));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, Ui.px(ROW_HEIGHT)));
        p.setAlignmentX(0f);
        p.add(new ColorChip(c.getId(), c.getName(), chip -> pickColor(chip, c)), BorderLayout.WEST);
        p.add(nameField(c), BorderLayout.CENTER);
        JButton del = Buttons.icon("trash", c.isPersonal() ? "‘개인’은 지울 수 없어요" : "삭제");
        del.setEnabled(ctx.categories().canDelete(c.getId()));
        del.addActionListener(e -> onDelete(c));
        p.add(del, BorderLayout.EAST);
        return p;
    }

    /** 색 칩 아래에 12색 팝오버를 열고, 고른 색을 바로 저장한다 (같은 색이면 controller가 아무것도 하지 않는다) */
    private void pickColor(ColorChip chip, Category c) {
        ColorSwatchPopover.show(chip, ctx.categories().palette(), ctx.categories().colorOf(c.getId()),
                key -> applyColor(c, key));
    }

    private void applyColor(Category c, String key) {
        OperationResult r = ctx.categories().setColor(c.getId(), key);
        showError(r.success() ? null : r.message());
    }

    private JTextField nameField(Category c) {
        JTextField field = new JTextField(c.getName());
        field.putClientProperty(ORIGINAL, c.getName());
        field.setFont(Fonts.regular(Fonts.BODY));
        field.putClientProperty(FlatClientProperties.STYLE, "arc:8; margin:6,8,6,8; borderColor:$proto.surface;"
                + " background:$proto.surface; focusedBorderColor:$proto.accent");
        Ime.track(field);
        field.getAccessibleContext().setAccessibleName(c.getName() + " 이름");
        field.addActionListener(e -> commitRename(c, field));
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commitRename(c, field);
            }
        });
        return field;
    }

    /** 이름 입력칸에서 Enter를 누르거나 포커스가 빠질 때 이름을 바꾼다. 바뀐 게 없으면 아무것도 하지 않고, 규칙을 어겨 거절되면 칸을 원래 이름으로 되돌리고 오류를 보여 준다. */
    private void commitRename(Category c, JTextField field) {
        Ime.commit(field);
        String text = field.getText().strip();
        if (text.equals(c.getName())) {
            return;
        }
        OperationResult r = ctx.categories().rename(c.getId(), text);
        if (r.success()) {
            showError(null);
        } else {
            showError(r.message());
            field.setText(c.getName());
        }
    }

    private void onDelete(Category c) {
        int count = ctx.categories().countSchedules(c.getId());
        if (!Dialogs.confirmDeleteCategory(this, c.getName(), count)) {
            return;
        }
        OperationResult r = ctx.categories().delete(c.getId());
        if (r.success()) {
            showError(null);
            ctx.actions().toast("카테고리를 삭제했어요");
        } else {
            showError(r.message());
        }
    }
}
