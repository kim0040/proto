package proto.ui;

import proto.ui.motion.Motion;
import proto.ui.motion.Tween;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * [경계 boundary] 좁은 화면에서 전체 사이드바를 본문 위에 펼치는 서랍. 왼쪽에서 미끄러지며 나타나고(200ms),
 * 바깥을 누르거나 Esc를 누르거나 항목을 고르면 닫힌다. 서랍 뒤 본문은 옅게 어두워진다.
 * 좁은 화면에서 MainFrame이 사이드바(Sidebar)를 이 서랍에 담아 연다. 레이어드 팬의 팔레트 층에 얹혀 본문 위에 그려진다.
 */
public final class SidebarDrawer extends JComponent {

    private static final int ANIMATION_MS = 200;
    private static final int WIDTH = 232;

    private final Tween open = new Tween(0f, this::layoutAndRepaint);
    private final Runnable onClosed;
    private Sidebar sidebar;
    private boolean opened;

    public SidebarDrawer(Runnable onClosed) {
        this.onClosed = onClosed;
        setLayout(null);
        setVisible(false);
        MouseAdapter outside = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getX() > Ui.px(WIDTH)) {
                    close();
                }
            }
        };
        addMouseListener(outside);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        getActionMap().put("close", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                close();
            }
        });
    }

    public boolean isOpened() {
        return opened;
    }

    /** 사이드바를 서랍에 담아 연다 */
    public void open(Sidebar content) {
        if (opened) {
            return;
        }
        opened = true;
        sidebar = content;
        sidebar.setLogicalWidth(WIDTH);
        sidebar.setVisible(true);
        add(sidebar);
        setVisible(true);
        open.set(0f);
        open.to(1f, ANIMATION_MS);
        sidebar.requestFocusInWindow();
    }

    /** 닫는다. 애니메이션이 있으면 미끄러져 사라진 뒤 정리한다 */
    public void close() {
        if (!opened) {
            return;
        }
        opened = false;
        float from = open.value();
        Motion.run(ANIMATION_MS, f -> open.set(from * (1f - f)), this::finishClose);
    }

    /** 애니메이션 없이 바로 닫는다 (창 크기가 넓어졌을 때) */
    public void closeNow() {
        if (sidebar == null) {
            return;
        }
        opened = false;
        open.set(0f);
        finishClose();
    }

    private void finishClose() {
        if (opened || sidebar == null) {
            return;
        }
        remove(sidebar);
        sidebar = null;
        setVisible(false);
        onClosed.run();
    }

    private void layoutAndRepaint() {
        doLayout();
        repaint();
    }

    /** 닫힌 동안 false를 돌려주어 마우스 클릭이 이 투명한 층을 통과해 아래 본문에 닿게 한다. */
    @Override
    public boolean contains(int x, int y) {
        return opened || open.value() > 0f;
    }

    @Override
    public void doLayout() {
        if (sidebar != null) {
            int w = Ui.px(WIDTH);
            sidebar.setBounds(Math.round(-w * (1f - open.value())), 0, w, getHeight());
            sidebar.validate();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(Tokens.alpha(java.awt.Color.BLACK, 0.28f * open.value()));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
    }

    @Override
    protected void paintChildren(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.SrcOver.derive(Math.max(0.02f, Math.min(1f, open.value() * 1.4f))));
        super.paintChildren(g2);
        g2.dispose();
    }
}
