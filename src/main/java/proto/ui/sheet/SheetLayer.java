package proto.ui.sheet;

import proto.ui.theme.ThemeManager;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.LayoutFocusTraversalPolicy;
import javax.swing.MenuSelectionManager;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * [경계 boundary] 시트 한 장: 창 전체를 덮는 반투명 스크림과 그 가운데의 둥근 카드.
 * SheetHost가 시트를 열 때마다 하나 만들어 쌓는다. 스크림은 뒤 화면으로 가는 마우스(클릭·호버·휠)를 모두 막고, 카드 바깥을 누르면 시트에게 알린다.
 * Esc도 시트에게 넘기며, Tab은 이 카드 안에서만 돈다(포커스 순환의 뿌리). 닫히는 중에는 클릭을 받지 않아 아래 화면을 바로 누를 수 있다.
 */
final class SheetLayer extends JComponent {

    /** 스크림 진하기: 밝은 테마 24%, 어두운 테마 45% 검정 */
    private static final float SCRIM_LIGHT = 0.24f;
    private static final float SCRIM_DARK = 0.45f;
    private static final int ARC = 16;
    /** 창 가장자리 여백, 맥은 위쪽 제목 줄 버튼 자리를 더 비운다 (논리 px) */
    private static final int MARGIN = 16;
    private static final int MAC_TOP = 40;
    /** 그림자가 퍼지는 거리 (논리 px). 이 안쪽만 다시 그리면 카드만 바뀐 프레임을 그릴 수 있다 */
    private static final int SHADOW = 18;

    private final Sheet sheet;
    private final JComponent card;
    private final Component previousFocus;
    private final SheetMotion motion = new SheetMotion(this::motionChanged);
    private Component lastFocus;
    private boolean closing;

    SheetLayer(Sheet sheet, Component previousFocus) {
        this.sheet = sheet;
        this.card = sheet.card();
        this.previousFocus = previousFocus;
        setLayout(null);
        setOpaque(false);
        setFocusable(false);
        setFocusCycleRoot(true);
        setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
        add(card);
        installMouse();
        installEscape();
    }

    Sheet sheet() {
        return sheet;
    }

    Component previousFocus() {
        return previousFocus;
    }

    boolean closing() {
        return closing;
    }

    void playIn() {
        motion.enter();
    }

    /** 닫기 시작한다. 이때부터 클릭을 받지 않는다. 다 사라지면 done */
    void playOut(Runnable done) {
        closing = true;
        motion.exit(done);
    }

    void shake() {
        motion.shake();
    }

    /** 카드 안에 포커스가 들어간 것을 기억한다 (밖으로 빠졌다가 돌아올 자리) */
    void rememberFocus(Component c) {
        lastFocus = c;
    }

    /** 카드 안으로 포커스를 옮긴다: 마지막으로 있던 곳 → 시트가 정한 첫 칸 → 카드 안 첫 칸 순서 */
    void focusInside() {
        Component target = usable(lastFocus) ? lastFocus : sheet.initialFocus();
        if (!usable(target)) {
            target = getFocusTraversalPolicy().getDefaultComponent(this);
        }
        if (target != null) {
            target.requestFocusInWindow();
        }
    }

    /** 닫히는 중에는 이 층이 없는 것처럼 클릭이 아래로 지나간다 */
    @Override
    public boolean contains(int x, int y) {
        return !closing && super.contains(x, y);
    }

    /** 카드 안 입력칸이 스스로 다시 그릴 때도 이 층을 거쳐 그려야 애니메이션 중 변환(크기·투명도)이 어긋나지 않는다 */
    @Override
    public boolean isPaintingOrigin() {
        return true;
    }

    @Override
    public void doLayout() {
        boolean mac = Ui.isMac();
        int margin = Ui.px(MARGIN);
        int top = mac ? Ui.px(MAC_TOP) : margin;
        Dimension max = SheetBounds.max(getWidth(), getHeight(), Ui.px(SheetBounds.NARROW), Ui.px(SheetBounds.SHORT),
                margin, top);
        card.setBounds(SheetBounds.center(getWidth(), getHeight(), sheet.fit(max), top));
        card.validate();
    }

    @Override
    protected void paintComponent(Graphics g) {
        float scrim = motion.scrim();
        if (scrim <= 0f) {
            return;
        }
        g.setColor(Tokens.alpha(Color.BLACK, (ThemeManager.isDark() ? SCRIM_DARK : SCRIM_LIGHT) * scrim));
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    /** 카드 바탕(그림자·면·테두리)과 내용을 움직임 값에 맞춰 그린다. 움직임이 없을 때는 변환 없이 그려 글자가 흐려지지 않는다 */
    @Override
    protected void paintChildren(Graphics g) {
        if (motion.opacity() <= 0.003f) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        if (!motion.atRest()) {
            Rectangle r = card.getBounds();
            double cx = r.getCenterX();
            double cy = r.getCenterY();
            g2.translate(cx + motion.shakeOffset() * Ui.px(SheetMotion.SHAKE_PX),
                    cy + motion.rise() * Ui.px(SheetMotion.RISE_PX));
            g2.scale(motion.scale(), motion.scale());
            g2.translate(-cx, -cy);
            g2.setComposite(AlphaComposite.SrcOver.derive(motion.opacity()));
        }
        paintCard(g2, card.getBounds());
        super.paintChildren(g2);
        g2.dispose();
    }

    private boolean usable(Component c) {
        return c != null && c.isShowing() && c.isEnabled() && c.isFocusable() && isAncestorOf(c);
    }

    private void installMouse() {
        MouseAdapter blocker = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!closing && !card.getBounds().contains(e.getPoint())) {
                    sheet.scrimClicked();
                }
            }
        };
        addMouseListener(blocker);
        addMouseMotionListener(blocker);
        addMouseWheelListener(blocker);
    }

    /**
     * Esc를 시트에게 넘긴다. 왜 메뉴 확인이 먼저인가: 날짜 고르기 같은 팝업 메뉴가 열려 있으면 Esc는 그 팝업만 닫아야 하는데,
     * 팝업의 Esc 처리는 창 단위라서 이 바인딩보다 늦게 돌기 때문이다.
     */
    private void installEscape() {
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "sheet-escape");
        getActionMap().put("sheet-escape", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                MenuSelectionManager menus = MenuSelectionManager.defaultManager();
                if (menus.getSelectedPath().length > 0) {
                    menus.clearSelectedPath();
                } else if (!closing) {
                    sheet.escapePressed();
                }
            }
        });
    }

    private void motionChanged(boolean scrimChanged) {
        if (scrimChanged) {
            repaint();
            return;
        }
        int pad = Ui.px(SHADOW + SheetMotion.RISE_PX + SheetMotion.SHAKE_PX);
        Rectangle r = card.getBounds();
        r.grow(pad, pad);
        repaint(r);
    }

    /** 앱의 카드 모양: 옅은 그림자 몇 겹 + 둥근 면 + 얇은 테두리 */
    private static void paintCard(Graphics2D g, Rectangle r) {
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.smooth(g2);
        int arc = Ui.px(ARC);
        for (int i = 4; i >= 1; i--) {
            int spread = Ui.px(i * 3);
            g2.setColor(Tokens.alpha(Color.BLACK, 0.035f));
            g2.fillRoundRect(r.x - spread, r.y - spread + Ui.px(6), r.width + spread * 2, r.height + spread * 2,
                    arc + spread * 2, arc + spread * 2);
        }
        g2.setColor(Tokens.get(Tokens.SURFACE));
        g2.fillRoundRect(r.x, r.y, r.width, r.height, arc, arc);
        g2.setColor(Tokens.get(Tokens.BORDER_STRONG));
        g2.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, arc, arc);
        g2.dispose();
    }
}
