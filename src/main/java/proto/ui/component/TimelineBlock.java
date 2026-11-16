package proto.ui.component;

import proto.entity.Occurrence;
import proto.entity.ScheduleState;
import proto.ui.Fmt;
import proto.ui.motion.Tween;
import proto.ui.theme.Fonts;
import proto.ui.theme.Glyphs;
import proto.ui.theme.Ui;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * [경계 boundary] 시간표 위의 일정 블록: 시작~끝 시간만큼의 높이를 카테고리 색의 연한 바탕 + 왼쪽 색 막대(LeftBar)로 채우고 제목 + 시간(상태 말)을 같은 계열의 진한 글자로 쓴다.
 * TimelineGrid가 일정마다 하나씩 만들어 놓는다 (F2, F3, F6, F7). 고른(포커스) 블록과 끌고 있는 카드는 진한 바탕에 흰/검은 글자로 또렷해진다(EventPaint).
 * 완료한 일정은 흐린 글자 + 취소선, 지연은 시간 옆에 빨간 "지연"을 붙인다. 높이가 낮으면 한 줄("제목 09:00")로 줄인다.
 * 마우스 누르기·끌기는 TimelineDrag로 넘기고(아래쪽 가장자리를 잡으면 크기 바꾸기), 클릭은 열기, 오른쪽 클릭은 메뉴, Enter는 열기, Delete는 삭제, Alt+방향키는 옮기기를 요청한다.
 */
public class TimelineBlock extends JComponent implements OccurrenceHolder {

    private static final int RESIZE_ZONE = 6;
    private static final int ARC = 5;
    private static final int PAD_X = LeftBar.WIDTH + 6;
    private static final int PAD_Y = 4;
    private static final int PAD_RIGHT = 5;
    private static final int MAX_LINES = 3;
    private static final float TIME_SIZE = 11f;

    private final TimelineHandler handler;
    private final TimelineGrid grid;
    private final Occurrence occurrence;
    private final ScheduleState state;
    private final Tween hover = new Tween(0f, this::repaint);
    private boolean dimmed;
    private boolean lifted;

    public TimelineBlock(TimelineHandler handler, TimelineGrid grid, Occurrence occurrence) {
        this.handler = handler;
        this.grid = grid;
        this.occurrence = occurrence;
        this.state = handler.stateOf(occurrence);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText(TextUtil.tip(occurrence.title(), Fmt.range(occurrence) + " · "
                + handler.categoryName(occurrence.categoryId()) + " · " + state.label()));
        setFocusable(true);
        Listeners.repaintOnFocus(this);
        addKeyListener(new BlockKeys());
        BlockMouse mouse = new BlockMouse();
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        getAccessibleContext().setAccessibleName(TextUtil.clean(occurrence.title()) + ", "
                + Fmt.range(occurrence) + ", " + state.label());
    }

    @Override
    public Occurrence occurrence() {
        return occurrence;
    }

    /** 끌려 나가 흐리게 그려지는 중인지 (점검용) */
    boolean isDimmed() {
        return dimmed;
    }

    /** 끌고 있는 동안 원래 자리의 블록을 흐리게 한다 */
    public void setDimmed(boolean on) {
        if (dimmed != on) {
            dimmed = on;
            repaint();
        }
    }

    /** 끌 때 마우스를 따라다닐 같은 모양의 복사본 (화면에 붙이지 않고 그리기에만 쓴다. 크기는 그릴 때마다 정한다) */
    TimelineBlock floating() {
        TimelineBlock copy = new TimelineBlock(handler, grid, occurrence);
        copy.lifted = true;
        return copy;
    }

    /** 복사본을 지금 크기(setSize)대로 g2에 그린다 (흐림·포커스 표시 없이 평소 모양 그대로) */
    void paintCard(Graphics2D g2) {
        paintComponent(g2);
    }

    /** 끌기 동안 마우스 모양 (null이면 평소 모양으로 돌린다) */
    void setDragCursor(Cursor c) {
        setCursor(c != null ? c : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    /** y(블록 안쪽 좌표)가 아래쪽 가장자리인지 */
    public Zone zoneAt(int y) {
        return y >= getHeight() - Ui.px(RESIZE_ZONE) ? Zone.RESIZE : Zone.BODY;
    }

    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {
                @Override
                public AccessibleRole getAccessibleRole() {
                    return AccessibleRole.PUSH_BUTTON;
                }
            };
        }
        return accessibleContext;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.smooth(g2);
        if (dimmed) {
            g2.setComposite(AlphaComposite.SrcOver.derive(0.45f));
        }
        int arc = Ui.px(ARC);
        Look look = look();
        g2.setColor(look.fill());
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
        if (!solid()) {
            LeftBar.paint(g2, EventPaint.barColor(occurrence), 0, 0, getWidth(), getHeight(), arc);
        }
        int lines = titleLines();
        if (lines > 0) {
            paintTall(g2, look, lines);
        } else {
            paintShort(g2, look);
        }
        if (isFocusOwner()) {
            FocusRing.paint(g2, getWidth(), getHeight(), arc);
        }
        g2.dispose();
    }

    /** 고른(포커스) 블록과 끌고 있는 카드는 진한 바탕이다 */
    private boolean solid() {
        return lifted || isFocusOwner();
    }

    private Look look() {
        if (solid()) {
            Color fill = EventPaint.solidFill(occurrence, hover.value());
            Color text = EventPaint.solidText(occurrence, fill);
            return new Look(fill, text, text, text);
        }
        Color fill = EventPaint.tintFill(occurrence, hover.value());
        Color sub = EventPaint.tintSub(occurrence, fill);
        Color word = state == ScheduleState.DELAYED && !occurrence.completed() ? EventPaint.warning(fill) : sub;
        return new Look(fill, EventPaint.tintTitle(occurrence, fill), sub, word);
    }

    /** 제목을 둘 수 있는 줄 수 (0이면 한 줄짜리 낮은 모양) */
    private int titleLines() {
        int lineH = getFontMetrics(Fonts.medium(Fonts.CAPTION)).getHeight();
        int timeH = getFontMetrics(Fonts.regular(TIME_SIZE)).getHeight();
        int room = getHeight() - Ui.px(PAD_Y) * 2 - timeH - Ui.px(1);
        return Math.max(0, Math.min(MAX_LINES, room / lineH));
    }

    /** 제목 몇 줄 + 그 아래 시간(상태 말). 완료한 일정은 흐린 색에 취소선 */
    private void paintTall(Graphics2D g2, Look look, int lines) {
        int x = Ui.px(PAD_X);
        int y = Ui.px(PAD_Y);
        g2.setFont(Fonts.medium(Fonts.CAPTION));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(look.title());
        for (String line : TextUtil.wrap(occurrence.title(), fm, getWidth() - x - Ui.px(PAD_RIGHT), lines)) {
            drawLine(g2, fm, line, x, y + fm.getAscent());
            y += fm.getHeight();
        }
        paintTime(g2, look, x, y + Ui.px(1), getWidth() - x - Ui.px(PAD_RIGHT));
    }

    /** 시간 줄: "09:00 – 10:30 지연". 좁으면 시작 시각만, 상태 말은 자리가 있을 때만 */
    private void paintTime(Graphics2D g2, Look look, int x, int top, int room) {
        g2.setFont(Fonts.regular(TIME_SIZE));
        FontMetrics fm = g2.getFontMetrics();
        String text = Fmt.range(occurrence);
        if (fm.stringWidth(text) > room) {
            text = Fmt.time(occurrence.startTime());
        }
        int base = top + fm.getAscent();
        g2.setColor(look.sub());
        g2.drawString(text, x, base);
        String word = suffix();
        int wx = x + fm.stringWidth(text) + Ui.px(4);
        if (!word.isEmpty() && wx + fm.stringWidth(word) <= x + room) {
            g2.setColor(look.word());
            g2.drawString(word, wx, base);
        }
    }

    /** 낮은 블록: "제목  09:00" 한 줄. 자리가 모자라면 제목만 */
    private void paintShort(Graphics2D g2, Look look) {
        int x = Ui.px(PAD_X);
        int room = getWidth() - x - Ui.px(PAD_RIGHT);
        g2.setFont(Fonts.medium(Fonts.CAPTION));
        FontMetrics fm = g2.getFontMetrics();
        int base = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
        String time = Fmt.time(occurrence.startTime());
        g2.setFont(Fonts.regular(TIME_SIZE));
        int timeW = g2.getFontMetrics().stringWidth(time) + Ui.px(6);
        boolean both = room >= timeW + Ui.px(48);
        g2.setFont(Fonts.medium(Fonts.CAPTION));
        g2.setColor(look.title());
        String title = TextUtil.ellipsize(TextUtil.clean(occurrence.title()), fm, both ? room - timeW : room);
        drawLine(g2, fm, title, x, base);
        if (both) {
            g2.setFont(Fonts.regular(TIME_SIZE));
            g2.setColor(look.sub());
            g2.drawString(time, x + Glyphs.width(fm, title) + Ui.px(6), base);
        }
    }

    private void drawLine(Graphics2D g2, FontMetrics fm, String text, int x, int baseline) {
        Glyphs.draw(g2, text, x, baseline);
        if (state == ScheduleState.DONE) {
            g2.fillRect(x, baseline - fm.getAscent() / 3, Glyphs.width(fm, text), Math.max(1, Ui.px(1)));
        }
    }

    private String suffix() {
        return switch (state) {
            case DELAYED -> "지연";
            case IN_PROGRESS -> "진행";
            case DONE -> "완료";
            default -> "";
        };
    }

    /** 블록 위에서 마우스가 놓인 자리 */
    public enum Zone { BODY, RESIZE }

    /** 키보드: Enter 열기, Delete 삭제, Alt+←/→ 하루, Alt+↑/↓ 15분 */
    private final class BlockKeys extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            int code = e.getKeyCode();
            if (e.isAltDown()) {
                nudgeKey(code, e);
            } else if (code == KeyEvent.VK_ENTER) {
                handler.open(occurrence);
            } else if (code == KeyEvent.VK_DELETE || code == KeyEvent.VK_BACK_SPACE) {
                handler.delete(occurrence);
            }
        }

        private void nudgeKey(int code, KeyEvent e) {
            int days = code == KeyEvent.VK_LEFT ? -1 : code == KeyEvent.VK_RIGHT ? 1 : 0;
            int minutes = code == KeyEvent.VK_UP ? -TimeSnap.STEP : code == KeyEvent.VK_DOWN ? TimeSnap.STEP : 0;
            if (days != 0 || minutes != 0) {
                e.consume();
                grid.nudge(occurrence, days, minutes);
            }
        }
    }

    /** 블록 위의 마우스 동작: 위에 올림(강조), 메뉴, 누르기·끌기·놓기는 TimelineDrag로 */
    private final class BlockMouse extends MouseAdapter {
        @Override
        public void mouseEntered(MouseEvent e) {
            hover.to(1f, 50);
        }

        @Override
        public void mouseExited(MouseEvent e) {
            hover.to(0f, 120);
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            setCursor(Cursor.getPredefinedCursor(zoneAt(e.getY()) == Zone.RESIZE
                    ? Cursor.S_RESIZE_CURSOR : Cursor.HAND_CURSOR));
        }

        @Override
        public void mousePressed(MouseEvent e) {
            if (!menu(e) && SwingUtilities.isLeftMouseButton(e)) {
                requestFocusInWindow();
                grid.drag().pressBlock(TimelineBlock.this, inGrid(e), zoneAt(e.getY()));
            }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            grid.drag().dragged(inGrid(e));
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (!menu(e) && SwingUtilities.isLeftMouseButton(e)) {
                grid.drag().released(inGrid(e), e.getClickCount());
            }
        }

        private boolean menu(MouseEvent e) {
            if (e.isPopupTrigger()) {
                handler.showMenu(TimelineBlock.this, e.getPoint(), occurrence);
            }
            return e.isPopupTrigger();
        }

        private Point inGrid(MouseEvent e) {
            return SwingUtilities.convertPoint(TimelineBlock.this, e.getPoint(), grid);
        }
    }

    /** 지금 모양의 바탕·글자색 묶음 */
    private record Look(Color fill, Color title, Color sub, Color word) {
    }
}
