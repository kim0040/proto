package proto.ui;

import proto.ui.component.TokenPanel;
import proto.ui.layout.SizeClass;
import proto.ui.motion.Motion;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import java.awt.Dimension;

/**
 * [경계 boundary] 본문 왼쪽 자리를 차지하는 틀. 넓은 화면에서는 전체 사이드바를, 좁은 화면에서는 아이콘 띠(SidebarRail)를 보인다.
 * MainFrame이 왼쪽에 붙이고, 크기 등급(SizeClass)이 바뀌면 apply로 너비를 200ms 안에 부드럽게 바꾼다 (애니메이션을 끄면 바로 바뀐다).
 * 사이드바와 아이콘 띠는 둘 다 이 틀 안에 있고, 너비가 둘의 중간보다 넓을 때만 전체 사이드바를 보인다.
 */
public final class SidebarHost extends TokenPanel {

    private static final int ANIMATION_MS = 200;
    /** 좁은 화면에서 서랍 대신 틀 안에 있을 때 쓰는 전체 사이드바 너비 (논리 px) */
    private static final int COMPACT_FULL_WIDTH = 208;
    private static final int RAIL_WIDTH = 64;

    private final Sidebar full;
    private final SidebarRail rail;
    private float width;
    /** 숨기지 않았을 때의 너비 (크기 등급에 따라 전체 사이드바 또는 아이콘 띠의 너비) */
    private float shownWidth;
    private int fullWidth;
    private boolean compact;
    /** 사용자가 사이드바를 숨겼는지. 숨기면 너비가 0까지 줄어들어 본문이 창 왼쪽 끝까지 넓어진다 */
    private boolean hidden;
    /** 지금 움직임이 숨기기/보이기인지 (크기 등급 전환과 전체·띠를 고르는 규칙이 다르다) */
    private boolean toggling;
    private Motion.Handle handle;

    public SidebarHost(Sidebar full, SidebarRail rail, SizeClass initial) {
        super(null, Tokens.SIDEBAR);
        this.full = full;
        this.rail = rail;
        this.compact = initial.compact();
        this.fullWidth = Ui.px(compact ? COMPACT_FULL_WIDTH : initial.sidebarWidth());
        this.shownWidth = Ui.px(initial.sidebarWidth());
        this.width = shownWidth;
        add(rail);
        if (!compact) {
            attachFull();
        }
        full.setLogicalWidth(compact ? COMPACT_FULL_WIDTH : initial.sidebarWidth());
    }

    /** 새 크기 등급에 맞춰 너비와 내용을 바꾼다. animate가 true이고 애니메이션이 켜져 있으면 미끄러진다 */
    public void apply(SizeClass sc, boolean animate) {
        toggling = false;
        compact = sc.compact();
        shownWidth = Ui.px(sc.sidebarWidth());
        if (!compact) {
            fullWidth = Ui.px(sc.sidebarWidth());
            full.setLogicalWidth(sc.sidebarWidth());
            attachFull();
        }
        slideTo(hidden ? 0 : shownWidth, animate);
    }

    /** 사이드바를 숨기거나 다시 보인다. 숨기면 지금 보이던 것(전체 사이드바 또는 아이콘 띠)이 왼쪽으로 접혀 들어간다 */
    public void setHidden(boolean value, boolean animate) {
        toggling = true;
        hidden = value;
        if (!compact) {
            attachFull();
        }
        slideTo(hidden ? 0 : shownWidth, animate);
    }

    public boolean isHidden() {
        return hidden;
    }

    private void slideTo(float to, boolean animate) {
        if (handle != null) {
            handle.cancel();
        }
        float from = width;
        if (!animate || !Motion.enabled() || from == to) {
            setWidth(to);
            finish();
            return;
        }
        handle = Motion.run(ANIMATION_MS, f -> setWidth(from + (to - from) * f), this::finish);
    }

    /** 전체 사이드바를 서랍으로 넘길 때 이 틀에서 뺀다 */
    public Sidebar releaseFull() {
        if (full.getParent() == this) {
            remove(full);
        }
        return full;
    }

    /** 좁은 화면에서 보이는 아이콘 띠 */
    public SidebarRail rail() {
        return rail;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Math.round(width), 10);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(0, 0);
    }

    @Override
    public void doLayout() {
        int h = getHeight();
        int railW = Ui.px(RAIL_WIDTH);
        // 숨기거나 다시 보일 때는 그 크기 등급의 모습(넓으면 전체 사이드바, 좁으면 띠)이 그대로 접히고 펼쳐진다.
        // 크기 등급이 바뀌는 중에는 너비가 띠와 전체의 중간을 넘을 때 둘을 바꾼다 (좁은 너비에 전체 사이드바가 잘려 보이지 않게)
        boolean attached = full.getParent() == this;
        boolean showFull = attached && (toggling ? !compact : width > (railW + fullWidth) / 2f);
        boolean gone = width < 1;
        // 서랍이 전체 사이드바를 가져가 있으면 그쪽 배치와 표시는 서랍이 맡는다
        if (attached) {
            full.setBounds(0, 0, fullWidth, h);
            full.setVisible(showFull && !gone);
            full.validate();
        }
        rail.setBounds(0, 0, railW, h);
        rail.setVisible(!showFull && !gone);
        rail.validate();
    }

    /** 애니메이션이 끝나면 좁은 화면에서는 전체 사이드바를 틀에서 떼어 낸다. 그래야 서랍(SidebarDrawer)이 이 사이드바를 가져다 쓸 수 있다. */
    private void finish() {
        if (compact && full.getParent() == this) {
            remove(full);
        }
        relayout();
    }

    private void attachFull() {
        if (full.getParent() != this) {
            add(full);
        }
    }

    private void setWidth(float value) {
        width = value;
        relayout();
    }

    private void relayout() {
        revalidate();
        repaint();
    }
}
