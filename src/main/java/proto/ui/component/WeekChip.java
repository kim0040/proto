package proto.ui.component;

import proto.entity.Occurrence;
import proto.entity.ScheduleState;
import proto.ui.Fmt;
import proto.ui.motion.Tween;
import proto.ui.theme.Fonts;
import proto.ui.theme.Glyphs;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * [경계 boundary] 이번 주 보기의 작은 일정 칩(칸이 좁을 때의 아젠다): 카테고리 색 둥근 네모 + 시간 또는 "하루 종일"(상태는 글자색과 짧은 말), 아래에 제목 최대 2줄.
 * WeekView가 하루 칸마다 일정 수만큼 만든다 (F2, F6). Alt+방향키로 옮기고 Ctrl/⌘+C로 복사한다. 상태는 RowHandler#stateOf로 받아 글자색과 짧은 말("지연", "진행", "완료")로만 나타낸다.
 */
public class WeekChip extends JComponent implements OccurrenceHolder {

    private final RowHandler handler;
    private final Occurrence occurrence;
    private final ScheduleState state;
    private final Tween hover = new Tween(0f, this::repaint);
    private int wrapWidth = 100;

    public WeekChip(RowHandler handler, Occurrence occurrence) {
        this.handler = handler;
        this.occurrence = occurrence;
        this.state = handler.stateOf(occurrence);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText(TextUtil.tip(occurrence.title(), AllDayTag.when(occurrence) + " · "
                + handler.categoryName(occurrence.categoryId()) + " · " + state.label()));
        setFocusable(true);
        Listeners.onKey(this, code -> {
            if (code == KeyEvent.VK_ENTER) {
                handler.open(occurrence);
            } else if (code == KeyEvent.VK_DELETE || code == KeyEvent.VK_BACK_SPACE) {
                handler.delete(occurrence);
            }
        });
        getAccessibleContext().setAccessibleName(TextUtil.clean(occurrence.title()) + ", " + AllDayTag.when(occurrence) + ", " + state.label());
        NudgeKeys.install(this, handler, this::occurrence);
        addMouseListener(new ChipMouse());
    }

    /** 이 칩이 보여 주는 하루치 일정 */
    @Override
    public Occurrence occurrence() {
        return occurrence;
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

    /** 칩이 놓일 칸의 너비. 제목을 이 너비로 줄바꿈한다 */
    public void setWrapWidth(int width) {
        if (width != wrapWidth) {
            wrapWidth = width;
            revalidate();
        }
    }

    @Override
    public Dimension getPreferredSize() {
        int lineH = getFontMetrics(Fonts.medium(Fonts.SMALL)).getHeight();
        return new Dimension(wrapWidth, Ui.px(10) * 2 + Ui.px(16) + Ui.px(2) + lineH * titleLines().size());
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.smooth(g2);
        int arc = Ui.px(8);
        g2.setColor(Tokens.blend(Tokens.get(Tokens.SUBTLE), Tokens.get(Tokens.HOVER), hover.value()));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
        int x = Ui.px(10);
        int y = Ui.px(10);
        boolean done = state == ScheduleState.DONE;
        int d = Ui.px(9);
        g2.setColor(EventPaint.base(occurrence));
        g2.fillRoundRect(x, y + (Ui.px(16) - d) / 2, d, d, Ui.px(3), Ui.px(3));
        g2.setFont(Fonts.medium(Fonts.CAPTION));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(timeColor());
        String head = (occurrence.allDay() ? AllDayTag.LABEL : Fmt.time(occurrence.startTime())) + suffix();
        int room = getWidth() - x * 2 - d - Ui.px(6);
        if (fm.stringWidth(head) > room) {
            head = occurrence.allDay() ? AllDayTag.LABEL : Fmt.time(occurrence.startTime());
        }
        g2.drawString(head, x + d + Ui.px(6),
                y + (Ui.px(16) - fm.getHeight()) / 2 + fm.getAscent());
        paintTitle(g2, x, y + Ui.px(18), done);
        g2.dispose();
    }

    /** 제목을 최대 2줄까지 그린다. 완료한 일정은 흐린 색에 취소선을 긋는다. */
    private void paintTitle(Graphics2D g2, int x, int y, boolean done) {
        g2.setFont(Fonts.medium(Fonts.SMALL));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(Tokens.get(done ? Tokens.MUTED : Tokens.TEXT));
        for (String line : titleLines()) {
            int base = y + fm.getAscent();
            Glyphs.draw(g2, line, x, base);
            if (done) {
                g2.fillRect(x, base - fm.getAscent() / 3, Glyphs.width(fm, line), Math.max(1, Ui.px(1)));
            }
            y += fm.getHeight();
        }
    }

    private Color timeColor() {
        return Tokens.get(switch (state) {
            case DELAYED -> Tokens.DANGER;
            case IN_PROGRESS -> Tokens.ACCENT;
            default -> Tokens.MUTED;
        });
    }

    private String suffix() {
        return switch (state) {
            case DELAYED -> " 지연";
            case IN_PROGRESS -> " 진행";
            case DONE -> " 완료";
            default -> "";
        };
    }

    private void menu(MouseEvent e) {
        if (e.isPopupTrigger()) {
            handler.showMenu(this, e.getPoint(), occurrence);
        }
    }

    private List<String> titleLines() {
        FontMetrics fm = getFontMetrics(Fonts.medium(Fonts.SMALL));
        return TextUtil.wrap(occurrence.title(), fm, wrapWidth - Ui.px(20), 2);
    }

    /** 칩 위의 마우스 동작 */
    private final class ChipMouse extends MouseAdapter {
        @Override
        public void mouseEntered(MouseEvent e) {
            hover.to(1f, 50);
        }

        @Override
        public void mouseExited(MouseEvent e) {
            hover.to(0f, 110);
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (SwingUtilities.isLeftMouseButton(e)) {
                handler.open(occurrence);
            }
        }

        @Override
        public void mousePressed(MouseEvent e) {
            menu(e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            menu(e);
        }
    }
}
