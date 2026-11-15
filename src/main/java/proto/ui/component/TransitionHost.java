package proto.ui.component;

import com.formdev.flatlaf.util.Animator;
import proto.ui.motion.Motion;
import proto.ui.theme.Tokens;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;

/**
 * [경계 boundary] 화면 내용을 바꿀 때 나가는 화면은 제자리에서 흐려지고 들어오는 화면은 살짝 밀려 들어오며 나타나게 하는 틀.
 * MainFrame이 본문 영역으로 쓴다 (보기 이동, 이전/다음 이동). 바꾸기 전 모습만 사진 한 장(SnapshotBuffer)으로 찍어 두고, 들어오는 화면은 진짜
 * 컴포넌트를 투명도와 밀림을 줘서 그대로 그린다. 그래서 전환 중에도 새 화면이 바로 마우스·키보드 입력을 받는다.
 * 전환 중에 또 전환이 오면 지금 보이는 모습(두 겹이 섞인 장면)을 새 "나가는 화면"으로 삼아 이어 가므로 줄 서거나 튀지 않는다.
 * 시간과 거리는 TransitionPlan이 정한다. 애니메이션이 꺼져 있으면 즉시 바꾼다.
 */
public class TransitionHost extends JPanel {

    /** 새 내용이 들어오는 방향. UP은 아래에서 올라오고 DOWN은 위에서 내려온다 (사이드바에서 아래쪽 항목으로 가면 UP) */
    public enum Dir {
        UP, DOWN, FROM_RIGHT, FROM_LEFT, NONE;

        /** 사이드바 순서(위에서 아래)의 from번째에서 to번째 보기로 갈 때의 방향 */
        public static Dir toward(int from, int to) {
            return to >= from ? UP : DOWN;
        }
    }

    private static final Animator.Interpolator LINEAR = f -> f;

    private SnapshotBuffer outgoing = new SnapshotBuffer();
    private SnapshotBuffer spare = new SnapshotBuffer();
    private TransitionPlan plan;
    private float elapsedMs;
    private boolean running;
    private boolean changing;
    private Motion.Handle handle;
    private Runnable pendingDone;

    public TransitionHost() {
        super(new BorderLayout());
        setOpaque(true);
    }

    /** 애니메이션 없이 내용을 정한다 */
    public void setContent(JComponent content) {
        removeAll();
        add(content, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    /** change로 내용을 바꾸면서 dir 방향의 전환 효과를 보여 준다 */
    public void transition(Dir direction, Runnable change) {
        transition(direction, change, () -> { });
    }

    /** 전환이 끝난 뒤(애니메이션이 없으면 바로) onDone을 한 번 부른다. 끝나기 전에 다른 전환이 오면 이어 가는 쪽만 부른다 */
    public void transition(Dir direction, Runnable change, Runnable onDone) {
        if (!canAnimate(direction)) {
            stopRunning();
            change.run();
            revalidate();
            repaint();
            onDone.run();
            return;
        }
        captureOutgoing();
        changing = true;
        try {
            change.run();
        } finally {
            changing = false;
        }
        begin(direction, onDone);
    }

    /** 전환 효과가 돌고 있는지 (내용을 바꾸는 중 포함). 목록 같은 안쪽 요소가 따로 움직이지 않을 때 묻는다 */
    public boolean isTransitioning() {
        return running || changing;
    }

    private boolean canAnimate(Dir direction) {
        return Motion.enabled() && isShowing() && getWidth() > 0 && getHeight() > 0 && direction != Dir.NONE;
    }

    /** 나가는 화면을 찍는다. 전환 중이면 지금 보이는 장면(나가는 사진 + 들어오는 진짜 화면)을 합쳐서 찍는다 */
    private void captureOutgoing() {
        if (!running) {
            outgoing.capture(this);
            return;
        }
        Graphics2D g = spare.begin(this);
        paintComponent(g);
        paintIncoming(g);
        g.dispose();
        SnapshotBuffer done = outgoing;
        outgoing = spare;
        spare = done;
        stopRunning();
    }

    private void begin(Dir direction, Runnable onDone) {
        plan = TransitionPlan.of(direction);
        pendingDone = onDone;
        elapsedMs = 0f;
        running = true;
        handle = Motion.run(plan.totalMs(), LINEAR, f -> {
            elapsedMs = f * plan.totalMs();
            repaint();
        }, this::end);
        repaint();
    }

    private void end() {
        running = false;
        handle = null;
        spare.release();
        Runnable done = pendingDone;
        pendingDone = null;
        repaint();
        if (done != null) {
            done.run();
        }
    }

    /** 진행 중인 전환을 마무리 동작 없이 멈춘다 */
    private void stopRunning() {
        if (handle != null) {
            handle.cancel();
            handle = null;
        }
        running = false;
        pendingDone = null;
    }

    /** 전환 중에는 자식의 repaint도 이 틀을 거쳐 그려져야 투명도와 밀림이 한결같다 */
    @Override
    protected boolean isPaintingOrigin() {
        return running;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(Tokens.get(Tokens.BG));
        g2.fillRect(0, 0, getWidth(), getHeight());
        if (running) {
            outgoing.draw(g2, getWidth(), getHeight(), plan.outgoingAlpha(elapsedMs));
        }
        g2.dispose();
    }

    @Override
    protected void paintChildren(Graphics g) {
        if (running) {
            paintIncoming((Graphics2D) g);
        } else {
            super.paintChildren(g);
        }
    }

    /** 들어오는 화면(진짜 컴포넌트)을 지금 시각의 투명도와 밀림으로 그린다 */
    private void paintIncoming(Graphics2D g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.clipRect(0, 0, getWidth(), getHeight());
        g2.translate(plan.offsetX(elapsedMs), plan.offsetY(elapsedMs));
        g2.setComposite(AlphaComposite.SrcOver.derive(Math.min(1f, plan.incomingAlpha(elapsedMs))));
        super.paintChildren(g2);
        g2.dispose();
    }
}
