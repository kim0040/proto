package proto.ui.dialog;

import proto.ui.component.FadePanel;
import proto.ui.theme.Ui;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * [경계 boundary] 모든 대화상자의 공통 틀: 모달, Esc로 취소, 열릴 때 내용이 부드럽게 나타난다.
 */
public class BaseDialog extends JDialog {

    protected final FadePanel panel = new FadePanel(new BorderLayout());
    private boolean cancelled = true;

    public BaseDialog(Window owner, String title) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        setContentPane(panel);
        setResizable(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getRootPane().registerKeyboardAction(this::cancelAction, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /** 내용을 채우고 바깥 여백을 준다 */
    protected void setBody(JComponent body, int padding) {
        panel.setBorder(BorderFactory.createEmptyBorder(Ui.px(padding), Ui.px(padding), Ui.px(padding),
                Ui.px(padding)));
        panel.add(body, BorderLayout.CENTER);
    }

    private void cancelAction(ActionEvent e) {
        onCancel();
    }

    /** Esc나 취소 버튼에서 불린다 */
    protected void onCancel() {
        close(true);
    }

    /** 닫는다. cancelled가 false면 "확인했다"는 뜻이다 */
    protected void close(boolean wasCancelled) {
        cancelled = wasCancelled;
        dispose();
    }

    public boolean wasCancelled() {
        return cancelled;
    }

    /** 창 크기를 맞추고 부모 가운데에 모달로 연다. 닫힐 때까지 돌아오지 않는다 */
    public void showDialog() {
        pack();
        setLocationRelativeTo(getOwner());
        panel.playIntro();
        setVisible(true);
    }
}
