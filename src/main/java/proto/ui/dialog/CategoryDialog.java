package proto.ui.dialog;

import proto.control.CategoryListener;
import proto.control.OperationResult;
import proto.entity.Category;
import proto.ui.UiContext;
import proto.ui.component.Buttons;
import proto.ui.component.CategoryDot;
import proto.ui.component.Ime;
import proto.ui.component.ScrollPanel;
import proto.ui.component.TextUtil;
import proto.ui.component.TokenLabel;
import proto.ui.theme.Fonts;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

/**
 * [경계 boundary] 카테고리 관리 창 (F7): 이름 바로 고치기, 삭제(개인은 불가), 추가.
 * UiActions#openCategories가 연다 (F7). 카테고리 변경은 CategoryController에 요청하고, 결과는 CategoryListener(categoriesChanged)로 돌아와
 * 목록을 다시 만든다.
 * "개인" 카테고리는 삭제 버튼이 꺼져 있다 (CategoryController#canDelete). 이름 검사 규칙은 화면이 아니라 controller가 가진다.
 */
public class CategoryDialog extends BaseDialog implements CategoryListener {

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
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                ctx.categories().removeListener(CategoryDialog.this);
            }
        });
    }

    private JPanel buildBody() {
        JPanel root = new JPanel(new BorderLayout(0, Ui.px(14)));
        root.setOpaque(false);
        root.add(new TokenLabel("카테고리", Fonts.semibold(Fonts.TITLE), Tokens.TEXT), BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(rows);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setPreferredSize(new Dimension(Ui.px(420), Ui.px(240)));
        this.scroll = scroll;
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
        getRootPane().setDefaultButton(null);
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

    /** 다른 곳(예: 같은 창의 추가·삭제)에서 카테고리가 바뀌면 불린다. controller가 어느 스레드에서든 알릴 수 있으므로 이벤트 스레드로 옮겨서 목록을 다시 만든다. */
    @Override
    public void categoriesChanged() {
        SwingUtilities.invokeLater(this::rebuildRows);
    }

    private void rebuildRows() {
        rows.removeAll();
        for (Category c : ctx.categories().getCategories()) {
            rows.add(row(c));
        }
        int height = Math.max(Ui.px(144), Math.min(Ui.px(320), ctx.categories().getCategories().size() * Ui.px(48)));
        scroll.setPreferredSize(new Dimension(Ui.px(420), height));
        rows.revalidate();
        rows.repaint();
        refit();
    }

    private JPanel row(Category c) {
        JPanel p = new JPanel(new BorderLayout(Ui.px(12), 0));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(Ui.px(3), Ui.px(4), Ui.px(3), Ui.px(4)));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, Ui.px(48)));
        p.setAlignmentX(0f);
        p.add(new CategoryDot(c.getId(), 10), BorderLayout.WEST);
        p.add(nameField(c), BorderLayout.CENTER);
        JButton del = Buttons.icon("trash", c.isPersonal() ? "‘개인’은 지울 수 없어요" : "삭제");
        del.setEnabled(ctx.categories().canDelete(c.getId()));
        del.addActionListener(e -> onDelete(c));
        p.add(del, BorderLayout.EAST);
        return p;
    }

    private JTextField nameField(Category c) {
        JTextField f = new JTextField(c.getName());
        f.setFont(Fonts.regular(Fonts.BODY));
        f.putClientProperty(FlatClientProperties.STYLE, "arc:8; margin:6,8,6,8; borderColor:$proto.surface;"
                + " background:$proto.surface; focusedBorderColor:$proto.accent");
        Ime.track(f);
        f.getAccessibleContext().setAccessibleName(c.getName() + " 이름");
        f.addActionListener(e -> commitRename(c, f));
        f.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commitRename(c, f);
            }
        });
        return f;
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
