package proto.ui.command;

import com.formdev.flatlaf.util.Animator;
import proto.ui.motion.Motion;
import proto.ui.theme.ThemeManager;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.KeyStroke;
import javax.swing.LayoutFocusTraversalPolicy;
import javax.swing.SwingUtilities;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * [경계 boundary] 창 위쪽 가운데에 뜨는 명령 메뉴 층: 옅은 스크림 + 카드(폭 560px) + 열고 닫는 움직임 + 바깥을 누르면 닫기.
 * MainFrame이 레이어드 팬에 한 번 달아 두고(install), ⌘K나 헤더의 ⌘K 버튼이 open/close를 부른다. 시트(SheetHost)와 달리 창 안의 카드 하나뿐이고 키보드는 입력칸이 받는다.
 * 열릴 때 140ms(위에서 8px 내려오며 선명해짐), 닫힐 때 90ms(흐려짐). 애니메이션을 끄면 바로 바뀐다. 닫히면 포커스를 열기 전 자리로 돌려주고,
 * 고른 명령은 포커스를 돌려준 다음에 실행한다(명령이 시트를 열어도 그 시트가 돌아올 포커스 자리를 올바르게 알도록).
 * 닫히는 중에는 클릭이 아래 화면으로 지나간다.
 */
public final class PaletteLayer extends JComponent {

    private static final String KEY = "proto.paletteLayer";
    private static final int OPEN_MS = 140;
    private static final int CLOSE_MS = 90;
    private static final int WIDTH = 560;
    private static final int RISE = 8;
    private static final int CARD_ARC = 14;
    private static final float SCRIM_LIGHT = 0.14f;
    private static final float SCRIM_DARK = 0.34f;

    private CommandPalette palette;
    private Component previousFocus;
    private float progress;
    private boolean closing;
    private Motion.Handle running;

    private PaletteLayer() {
        setLayout(null);
        setOpaque(false);
        setFocusable(false);
        // 명령 메뉴 안에서만 포커스가 돌도록 한다 (SheetLayer와 같다). 입력칸 하나뿐이므로 뒤 화면으로 새지 않는다
        setFocusCycleRoot(true);
        setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
        setVisible(false);
        MouseAdapter blocker = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (palette != null && !closing && !palette.getBounds().contains(e.getPoint())) {
                    close();
                }
            }
        };
        addMouseListener(blocker);
        addMouseMotionListener(blocker);
        addMouseWheelListener(blocker);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "palette-close");
        getActionMap().put("palette-close", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                close();
            }
        });
    }

    /** 레이어드 팬에 명령 메뉴 층을 단다 (이미 있으면 그것을 돌려준다). 창 크기를 따라간다 */
    public static PaletteLayer install(JLayeredPane layers) {
        if (layers.getClientProperty(KEY) instanceof PaletteLayer existing) {
            return existing;
        }
        PaletteLayer layer = new PaletteLayer();
        layers.putClientProperty(KEY, layer);
        layers.add(layer, JLayeredPane.MODAL_LAYER);
        layer.setBounds(0, 0, layers.getWidth(), layers.getHeight());
        layers.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                layer.setBounds(0, 0, layers.getWidth(), layers.getHeight());
                layer.revalidate();
            }
        });
        return layer;
    }

    /** 열려 있는지 (닫히는 중이면 닫힌 것으로 본다) */
    public boolean isOpen() {
        return palette != null && !closing;
    }

    /** 열려 있으면 닫고, 닫혀 있으면 연다 */
    public void toggle(CommandCatalog catalog) {
        if (isOpen()) {
            close();
        } else {
            open(catalog);
        }
    }

    /** 명령 메뉴를 연다. 닫히는 중이면 모션을 끝내고 새로 연다 */
    public void open(CommandCatalog catalog) {
        if (isOpen()) {
            return;
        }
        if (palette != null) {
            running.finish();
        }
        previousFocus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
        palette = new CommandPalette(catalog::results, this::run, this::close, this::relayoutCard);
        add(palette);
        closing = false;
        setVisible(true);
        doLayout();
        palette.validate();
        animate(OPEN_MS, Motion.ENTER, 0f, 1f, () -> { });
        palette.focusField();
    }

    /** 닫는다. 포커스를 열기 전 자리로 돌려준다 */
    public void close() {
        if (!isOpen()) {
            return;
        }
        closing = true;
        Component back = previousFocus;
        animate(CLOSE_MS, Motion.EXIT, progress, 0f, this::removePalette);
        if (back != null && back.isShowing() && back.isFocusable()) {
            back.requestFocusInWindow();
        }
    }

    /** 화면 캡처·테스트가 쓰는 카드 안쪽 */
    CommandPalette palette() {
        return palette;
    }

    /** 카드를 위쪽 가운데에 둔다: 폭 560(창이 좁으면 좌우 16px 여백), 높이는 내용만큼 */
    @Override
    public void doLayout() {
        if (palette == null) {
            return;
        }
        int w = Math.min(Ui.px(WIDTH), getWidth() - Ui.px(32));
        int top = Math.max(Ui.px(56), Math.min(Ui.px(140), getHeight() * 14 / 100));
        int h = Math.min(palette.getPreferredSize().height, Math.max(Ui.px(120), getHeight() - top - Ui.px(16)));
        palette.setBounds((getWidth() - w) / 2, top, w, h);
        palette.validate();
    }

    /** 닫히는 중에는 이 층이 없는 것처럼 클릭이 아래로 지나간다 */
    @Override
    public boolean contains(int x, int y) {
        return palette != null && !closing && super.contains(x, y);
    }

    @Override
    public boolean isPaintingOrigin() {
        return true;
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (progress <= 0f) {
            return;
        }
        g.setColor(Tokens.alpha(Color.BLACK, (ThemeManager.isDark() ? SCRIM_DARK : SCRIM_LIGHT) * progress));
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    /** 카드 바탕(그림자·면·테두리)과 내용을 움직임에 맞춰 그린다. 움직임이 끝나면 변환 없이 그려 글자가 흐려지지 않는다 */
    @Override
    protected void paintChildren(Graphics g) {
        if (palette == null || progress <= 0.003f) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        Rectangle r = palette.getBounds();
        if (progress < 0.999f) {
            g2.translate(0, -(1f - progress) * Ui.px(RISE));
            double cx = r.getCenterX();
            g2.translate(cx, r.y);
            float scale = 0.985f + 0.015f * progress;
            g2.scale(scale, scale);
            g2.translate(-cx, -r.y);
            g2.setComposite(AlphaComposite.SrcOver.derive(Math.min(1f, progress)));
        }
        paintCard(g2, r);
        int arc = Ui.px(CARD_ARC);
        g2.clip(new RoundRectangle2D.Float(r.x, r.y, r.width, r.height, arc, arc));
        super.paintChildren(g2);
        g2.dispose();
    }

    /** 명령을 고르면: 먼저 닫아서 포커스를 돌려준 다음 실행한다 */
    private void run(Command command) {
        close();
        SwingUtilities.invokeLater(command.action());
    }

    private void removePalette() {
        if (palette != null) {
            remove(palette);
            palette = null;
        }
        closing = false;
        setVisible(false);
        repaint();
    }

    private void animate(int ms, Animator.Interpolator easing, float from, float to,
                         Runnable done) {
        if (running != null) {
            running.cancel();
        }
        running = Motion.run(ms, easing, f -> {
            progress = from + (to - from) * f;
            repaint();
        }, done);
    }

    private void relayoutCard() {
        revalidate();
        repaint();
    }

    /** 앱의 카드 모양: 옅은 그림자 몇 겹 + 둥근 면 + 얇은 테두리 (시트 카드와 같은 모양) */
    private static void paintCard(Graphics2D g, Rectangle r) {
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.smooth(g2);
        int arc = Ui.px(CARD_ARC);
        for (int i = 4; i >= 1; i--) {
            int spread = Ui.px(i * 3);
            g2.setColor(Tokens.alpha(Color.BLACK, 0.04f));
            g2.fillRoundRect(r.x - spread, r.y - spread + Ui.px(8), r.width + spread * 2, r.height + spread * 2,
                    arc + spread * 2, arc + spread * 2);
        }
        g2.setColor(Tokens.get(Tokens.SURFACE));
        g2.fillRoundRect(r.x, r.y, r.width, r.height, arc, arc);
        g2.setColor(Tokens.get(Tokens.BORDER_STRONG));
        g2.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, arc, arc);
        g2.dispose();
    }
}
