package proto.ui.dialog;

import proto.ui.component.FadePanel;
import proto.ui.component.ScrollPanel;
import proto.ui.layout.ScreenFit;
import proto.ui.theme.Ui;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * [경계 boundary] 모든 대화상자의 공통 틀: 모달, Esc로 취소, 열릴 때 내용이 부드럽게 나타난다.
 * ScheduleDialog, CategoryDialog, FreeSlotDialog, ConfirmDialog가 모두 이 클래스를 상속한다. 열기는 showDialog, 닫기는 close(cancelled
 * 여부)로 통일해서
 * 호출한 쪽(UiActions)이 wasCancelled로 결과를 알 수 있다.
 * 내용은 스크롤되는 영역에 담기고(테두리 없음), 화면보다 커지지 않으며, 항상 부모 가운데에 화면 안쪽으로 놓인다.
 * 아래 버튼 줄(footer)은 스크롤에서 빼서 늘 보이게 둘 수 있다.
 */
public class BaseDialog extends JDialog {

    /** 화면 가장자리에서 띄울 여유 (논리 px) */
    private static final int SCREEN_MARGIN = 16;

    protected final FadePanel panel = new FadePanel(new BorderLayout());
    private JScrollPane scroll;
    private boolean cancelled = true;

    public BaseDialog(Window owner, String title) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        setContentPane(panel);
        setResizable(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getRootPane().registerKeyboardAction(this::cancelAction, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /** 내용을 스크롤 영역에 채우고 바깥 여백을 준다 */
    protected void setBody(JComponent body, int padding) {
        setBody(body, null, padding);
    }

    /** 내용(스크롤됨)과 항상 보이는 아래 버튼 줄을 채운다 */
    protected void setBody(JComponent body, JComponent footer, int padding) {
        panel.setBorder(BorderFactory.createEmptyBorder(Ui.px(padding), Ui.px(padding), Ui.px(padding),
                Ui.px(padding)));
        ScrollPanel holder = new ScrollPanel(new BorderLayout());
        holder.add(body, BorderLayout.CENTER);
        scroll = new JScrollPane(holder, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        panel.add(scroll, BorderLayout.CENTER);
        if (footer != null) {
            footer.setBorder(BorderFactory.createEmptyBorder(Ui.px(16), 0, 0, 0));
            panel.add(footer, BorderLayout.SOUTH);
        }
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
        limitToScreen();
        ScreenFit.fitAndCenter(this, getOwner(), Ui.px(SCREEN_MARGIN));
        panel.playIntro();
        setVisible(true);
    }

    /**
     * 내용이 바뀌어 크기를 다시 맞출 때 쓴다 (검증 오류가 나타나는 등). 화면 쓸 수 있는 영역보다 커지지 않게 줄이고
     * (넘치는 만큼은 스크롤), 이미 놓인 위치는 화면 안으로만 밀어 넣는다.
     */
    protected void refit() {
        if (!isDisplayable()) {
            return;
        }
        pack();
        limitToScreen();
        ScreenFit.keepOnScreen(this, Ui.px(SCREEN_MARGIN));
        repaint();
    }

    /** 창이 화면 쓸 수 있는 영역보다 커지면 줄인다 (넘치는 만큼은 본문 스크롤). 세로 스크롤바가 새로 생기면 그 너비만큼 창을 넓혀서 가로 내용이 잘리지 않게 한다. */
    private void limitToScreen() {
        java.awt.Rectangle area = ScreenFit.usableOf(getOwner() != null && getOwner().isShowing() ? getOwner() : this);
        int margin = Ui.px(SCREEN_MARGIN) * 2;
        int maxW = Math.max(Ui.px(320), area.width - margin);
        int maxH = Math.max(Ui.px(240), area.height - margin);
        Dimension d = getSize();
        boolean tooTall = d.height > maxH;
        int w = d.width;
        if (tooTall && scroll != null) {
            w += scroll.getVerticalScrollBar().getPreferredSize().width;
        }
        setSize(Math.min(w, maxW), Math.min(d.height, maxH));
    }
}
