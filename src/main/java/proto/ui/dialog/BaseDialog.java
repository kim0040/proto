package proto.ui.dialog;

import proto.ui.component.Ime;
import proto.ui.component.ScrollPanel;
import proto.ui.sheet.Sheet;
import proto.ui.sheet.SheetHost;
import proto.ui.theme.Ui;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.RootPaneContainer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * [경계 boundary] 모든 대화상자의 공통 틀: 메인 창 안에 스크림과 함께 뜨는 시트. 열기는 showDialog, 닫기는 close(취소 여부)로 통일한다.
 * ScheduleDialog, CategoryDialog, FreeSlotDialog, ConfirmDialog, SettingsSheet가 이 클래스를 상속한다. 호출한 쪽(UiActions 등)은
 * showDialog가 닫힐 때까지 기다렸다가 wasCancelled로 결과를 읽는다.
 * 별도 창(JDialog)을 쓰지 않는 이유: 운영체제 창은 열고 닫을 때 깜박이고 메인 창의 포커스를 빼앗으며 바깥 클릭을 받을 수 없다. 그래서 시트는 메인 창의
 * 시트 층(SheetHost)에 올리고, 기다리기는 SecondaryLoop(이벤트를 계속 돌리면서 이 자리에서만 멈춤)로 한다. 시트에서 연 확인 창은 그 위에 쌓인다.
 * 바깥(스크림) 클릭과 Esc는 기본으로 취소이고, 입력이 있는 시트는 onScrimClick·onEscape를 바꿔 막거나 되묻는다.
 * 내용은 창보다 커지지 않는 스크롤 영역에 담기고, 아래 버튼 줄(footer)은 스크롤에서 빼서 늘 보이게 둘 수 있다.
 */
public class BaseDialog extends JPanel implements Sheet {

    /** 세로 스크롤바가 생겼을 때 내용과 스크롤바 사이 틈 (논리 px) */
    private static final int SCROLL_GAP = 14;

    private final Component owner;
    private final String title;
    private JScrollPane scroll;
    private JButton defaultButton;
    private Component initialFocus;
    private boolean cancelled = true;
    private boolean closed;
    private SheetHost host;
    private SecondaryLoop loop;

    /** owner는 시트를 띄울 창이나 그 안의 컴포넌트(다른 시트 포함). title은 화면 읽기 도구가 읽는 이름이다 */
    public BaseDialog(Component owner, String title) {
        super(new BorderLayout());
        setOpaque(false);
        this.owner = owner;
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    /** 내용을 스크롤 영역에 채우고 바깥 여백을 준다 */
    protected void setBody(JComponent body, int padding) {
        setBody(body, null, padding);
    }

    /** 내용(스크롤됨)과 항상 보이는 아래 버튼 줄을 채운다 */
    protected void setBody(JComponent body, JComponent footer, int padding) {
        setBorder(BorderFactory.createEmptyBorder(Ui.px(padding), Ui.px(padding), Ui.px(padding), Ui.px(padding)));
        ScrollPanel holder = new ScrollPanel(new BorderLayout());
        holder.add(body, BorderLayout.CENTER);
        scroll = new JScrollPane(holder, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        keepGapBesideScrollbar(holder);
        add(scroll, BorderLayout.CENTER);
        if (footer != null) {
            footer.setBorder(BorderFactory.createEmptyBorder(Ui.px(16), 0, 0, 0));
            add(footer, BorderLayout.SOUTH);
        }
    }

    /** 내용 스크롤 영역 (스크린샷 도구가 맨 아래로 내릴 때 쓴다) */
    public JScrollPane scrollPane() {
        return scroll;
    }

    /** Enter로 눌릴 버튼을 정한다 (null이면 없음) */
    protected void setDefaultButton(JButton button) {
        defaultButton = button;
        if (host != null && !closed) {
            host.defaultButtonChanged();
        }
    }

    /** 열릴 때 처음 포커스를 받을 칸을 정한다. 정하지 않으면 기본 버튼, 그것도 없으면 카드 안 첫 칸이다 */
    protected void setInitialFocus(Component c) {
        initialFocus = c;
    }

    /** 취소 버튼에서 부른다. Esc와 바깥 클릭도 기본으로는 이것을 부른다 */
    protected void onCancel() {
        close(true);
    }

    /** 바깥(스크림)을 눌렀을 때. 기본은 취소이다 */
    protected void onScrimClick() {
        onCancel();
    }

    /** Esc를 눌렀을 때. 기본은 취소이다 */
    protected void onEscape() {
        onCancel();
    }

    /** 닫힌 직후 한 번 불린다 (리스너 떼기 등 정리) */
    protected void onClosed() {
    }

    /** 지금은 닫을 수 없다는 신호로 시트를 좌우로 짧게 흔든다 */
    protected void shake() {
        if (host != null && !closed) {
            host.shake(this);
        }
    }

    /** 닫는다. cancelled가 false면 "확인했다"는 뜻이다. 기다리던 showDialog가 바로 돌아가고, 시트는 사라지는 모션 뒤에 떨어진다 */
    protected void close(boolean wasCancelled) {
        if (closed) {
            return;
        }
        closed = true;
        cancelled = wasCancelled;
        if (host != null) {
            host.pop(this);
        }
        if (loop != null) {
            loop.exit();
        }
        onClosed();
    }

    /** 바깥에서 취소로 닫는다 (스크린샷 도구·테스트가 쓴다). 사용자 동작은 onCancel·onEscape·onScrimClick을 거친다 */
    public void cancel() {
        close(true);
    }

    public boolean wasCancelled() {
        return cancelled;
    }

    /** 열려 있는지 (열렸고 아직 닫히지 않았는지) */
    public boolean isOpen() {
        return host != null && !closed;
    }

    /** 기다리지 않고 연다 (스크린샷 도구, 테스트). 띄울 창을 찾지 못하면 취소로 닫힌 상태가 된다 */
    public void open() {
        SheetHost found = owner == null ? null : SheetHost.of(owner);
        openOn(found != null ? found : SheetHost.of(fallbackWindow()));
    }

    /** 정해진 시트 층에 연다 (open과 테스트가 쓴다). 층이 없으면 취소로 닫힌 상태가 된다 */
    void openOn(SheetHost target) {
        if (host != null || closed) {
            return;
        }
        if (target == null) {
            closed = true;
            return;
        }
        host = target;
        host.push(this);
    }

    /** 시트를 열고 닫힐 때까지 돌아오지 않는다. 그동안에도 화면은 계속 그려지고 토스트·알림 카드도 움직인다 */
    public void showDialog() {
        open();
        if (closed) {
            return;
        }
        loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        loop.enter();
        loop = null;
    }

    /** 내용이 바뀌어 크기를 다시 맞출 때 쓴다 (검증 오류가 나타나는 등). 열리기 전에는 아무것도 하지 않는다 */
    protected void refit() {
        if (host != null && !closed) {
            host.relayout(this);
        }
    }

    // ---- Sheet ----

    @Override
    public JComponent card() {
        return this;
    }

    /** 원하는 크기를 최대 크기 안으로 줄인다. 세로로 넘쳐 스크롤바가 생기면 그 너비와 틈만큼 넓혀서 가로 내용이 잘리지 않게 한다 */
    @Override
    public Dimension fit(Dimension max) {
        Dimension d = getPreferredSize();
        int w = d.width;
        if (d.height > max.height && scroll != null) {
            w += scroll.getVerticalScrollBar().getPreferredSize().width + Ui.px(SCROLL_GAP);
        }
        return new Dimension(Math.min(w, max.width), Math.min(d.height, max.height));
    }

    @Override
    public Component initialFocus() {
        return initialFocus != null ? initialFocus : defaultButton;
    }

    @Override
    public JButton defaultButton() {
        return defaultButton;
    }

    @Override
    public void scrimClicked() {
        if (!closed) {
            onScrimClick();
        }
    }

    /** 한글을 조합하는 중에 누른 Esc는 글자 확정으로만 쓴다 (입력 중인 글자가 사라지거나 창이 닫히지 않게) */
    @Override
    public void escapePressed() {
        if (!closed && !Ime.swallowIfComposing()) {
            onEscape();
        }
    }

    /** 화면 읽기 도구에는 대화상자로, 제목을 이름으로 알린다 */
    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJPanel() {
                @Override
                public AccessibleRole getAccessibleRole() {
                    return AccessibleRole.DIALOG;
                }
            };
            accessibleContext.setAccessibleName(title);
        }
        return accessibleContext;
    }

    /** 스크롤바가 보일 때만 내용 오른쪽에 틈을 둔다. 스크롤바가 입력칸·스위치에 붙어 보이지 않게 한다 */
    private void keepGapBesideScrollbar(JComponent holder) {
        // 처음에는 숨겨 두어야, 처음부터 넘치는 내용에서도 "보이게 됨" 알림이 온다
        scroll.getVerticalScrollBar().setVisible(false);
        scroll.getVerticalScrollBar().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                holder.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, Ui.px(SCROLL_GAP)));
            }

            @Override
            public void componentHidden(ComponentEvent e) {
                holder.setBorder(null);
            }
        });
    }

    /** 부모를 모를 때: 지금 활성 창, 없으면 보이는 첫 창 */
    private static Component fallbackWindow() {
        Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        if (active instanceof RootPaneContainer) {
            return active;
        }
        for (Frame f : Frame.getFrames()) {
            if (f.isShowing() && f instanceof RootPaneContainer) {
                return f;
            }
        }
        return null;
    }
}
