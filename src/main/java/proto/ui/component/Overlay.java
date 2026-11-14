package proto.ui.component;

import proto.entity.Occurrence;
import proto.ui.theme.Ui;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * [경계 boundary] 메인 창 위에 떠 있는 투명 층. 토스트(아래 가운데)와 알림 카드(아래 오른쪽)를 쌓아 보여 준다.
 * MainFrame이 만들어 창의 맨 위 층(POPUP_LAYER)에 얹는다. UiActions가 토스트를, MainFrame#reminderDue가 알림 카드를 이 층에 올린다 (F10, F15).
 * 알림 카드는 가장 최근 3장만 보이고 나머지는 "+N개 더" 한 장으로 줄어 있다가 눌러서 펼친다.
 * 카드가 없는 곳의 마우스는 아래 화면으로 그대로 통과한다.
 */
public class Overlay extends JComponent {

    /** 접힌 상태에서 한꺼번에 보이는 알림 카드 수 */
    public static final int MAX_VISIBLE_REMINDERS = 3;

    private static final int GAP = 4;
    private static final int EDGE = 16;
    private static final int TOAST_MS = 2500;
    private static final int TOAST_MAX_WIDTH = 560;

    private final List<ToastCard> toasts = new ArrayList<>();
    private final List<ReminderCard> reminders = new ArrayList<>();
    private final MoreCard more = new MoreCard(this::toggleExpanded);
    private boolean expanded;

    public Overlay() {
        setLayout(null);
        setOpaque(false);
        more.setVisible(false);
        add(more);
    }

    /** 카드가 있는 곳에서만 true이다. 이 투명 층이 창 전체를 덮고 있어도 카드 밖의 클릭은 아래 화면으로 통과한다. */
    @Override
    public boolean contains(int x, int y) {
        for (Component c : getComponents()) {
            if (c.isVisible() && c instanceof FloatingCard && c.getBounds().contains(x, y)
                    && c.contains(x - c.getX(), y - c.getY())) {
                return true;
            }
        }
        return false;
    }

    /** 확인 토스트를 띄운다. 일반 토스트는 2.5초 뒤 사라지고, 오류 토스트는 닫을 때까지 남는다 */
    public void showToast(String message, boolean error) {
        if (!error) {
            new ArrayList<>(toasts).stream().filter(t -> !t.isError()).forEach(this::removeNow);
        }
        ToastCard[] holder = new ToastCard[1];
        holder[0] = new ToastCard(message, error, () -> dismissToast(holder[0]));
        addToast(holder[0], !error);
    }

    /** 닫을 때까지 남는 오류 토스트에 버튼 하나("자세히")를 붙여 띄운다 */
    public void showDetailToast(String message, String actionLabel, Runnable onAction) {
        ToastCard[] holder = new ToastCard[1];
        holder[0] = new ToastCard(message, true, () -> dismissToast(holder[0]), actionLabel, onAction);
        addToast(holder[0], false);
    }

    private void addToast(ToastCard card, boolean autoHide) {
        toasts.add(card);
        add(card);
        revalidate();
        card.playIn();
        if (autoHide) {
            Timer t = new Timer(TOAST_MS, e -> dismissToast(card));
            t.setRepeats(false);
            t.start();
        }
    }

    private void dismissToast(ToastCard card) {
        card.playOut(() -> removeNow(card));
    }

    private void removeNow(ToastCard card) {
        toasts.remove(card);
        remove(card);
        revalidate();
        repaint();
    }

    /** 알림 카드를 맨 아래에 쌓는다. 새 카드가 가장 아래에 오고 오래된 카드는 위로 밀려난다 */
    public void addReminder(Occurrence o, boolean snoozed, String categoryName, ReminderCard.Actions actions) {
        ReminderCard[] holder = new ReminderCard[1];
        holder[0] = new ReminderCard(o, snoozed, categoryName, actions, () -> dismissReminder(holder[0]));
        reminders.add(holder[0]);
        add(holder[0]);
        revalidate();
        holder[0].playIn();
    }

    private void dismissReminder(ReminderCard card) {
        card.playOut(() -> {
            reminders.remove(card);
            remove(card);
            revalidate();
            repaint();
        });
    }

    /** keep가 false인 알림 카드(지워지거나 완료된 일정)를 바로 치운다 */
    public void pruneReminders(Predicate<Occurrence> keep) {
        boolean changed = false;
        for (ReminderCard c : new ArrayList<>(reminders)) {
            if (!keep.test(c.occurrence())) {
                reminders.remove(c);
                remove(c);
                changed = true;
            }
        }
        if (changed) {
            revalidate();
            repaint();
        }
    }

    private void toggleExpanded() {
        expanded = !expanded;
        revalidate();
        repaint();
    }

    /** 떠 있는 토스트와 알림 카드를 모두 바로 치운다 */
    public void clearAll() {
        toasts.clear();
        reminders.clear();
        expanded = false;
        removeAll();
        more.setVisible(false);
        add(more);
        repaint();
    }

    /** 지금 떠 있는 알림 카드 수 (숨은 카드 포함) */
    public int reminderCount() {
        return reminders.size();
    }

    /** 지금 눈에 보이는 알림 카드 수 */
    public int visibleReminderCount() {
        return (int) reminders.stream().filter(Component::isVisible).count();
    }

    @Override
    public void doLayout() {
        int m = Ui.px(FloatingCard.MARGIN);
        List<Rectangle> taken = new ArrayList<>();
        layoutReminders(m, taken);
        layoutToasts(taken, m);
    }

    /**
     * 가장 최근 카드부터 아래에서 위로 쌓는다. 접힌 상태면 3장, 펼치면 화면에 들어가는 만큼 보인다. 왜 아래에서 위로: 가장 최근 카드가 맨 아래(눈에 가장 잘 띄는 곳)에 오게 하려는 것이다. 한
     * 장도 화면에 안 들어가는 높이여도 맨 아래 한 장은 보여 준다 (shown == 0).
     */
    private void layoutReminders(int m, List<Rectangle> taken) {
        int y = getHeight() - Ui.px(EDGE) + m;
        int top = Ui.px(EDGE) - m;
        int n = reminders.size();
        boolean hasMore = n > MAX_VISIBLE_REMINDERS;
        Dimension moreSize = more.getPreferredSize();
        int reserve = hasMore ? moreSize.height + Ui.px(GAP) : 0;
        int shown = 0;
        for (int i = n - 1; i >= 0; i--) {
            ReminderCard c = reminders.get(i);
            Dimension d = c.getPreferredSize();
            boolean limit = shown >= (expanded ? n : MAX_VISIBLE_REMINDERS);
            boolean fits = y - d.height - reserve >= top || shown == 0;
            c.setVisible(!limit && fits);
            if (!c.isVisible()) {
                continue;
            }
            int w = Math.min(d.width, Math.max(Ui.px(240), getWidth() - Ui.px(EDGE) * 2 + m * 2));
            y -= d.height;
            c.setBounds(getWidth() - w - Ui.px(EDGE) + m, y, w, d.height);
            taken.add(inner(c.getBounds(), m));
            y -= Ui.px(GAP);
            shown++;
        }
        placeMore(hasMore, n - shown, y, moreSize);
    }

    private void placeMore(boolean hasMore, int hidden, int y, Dimension size) {
        boolean wasVisible = more.isVisible();
        more.setVisible(hasMore);
        if (!hasMore) {
            return;
        }
        if (!wasVisible) {
            more.playIn();
        }
        more.setLabel(expanded ? "접기" : "+" + hidden + "개 더");
        Dimension d = more.getPreferredSize();
        more.setBounds(getWidth() - d.width - Ui.px(EDGE) + Ui.px(FloatingCard.MARGIN), y - d.height,
                d.width, d.height);
    }

    private static Rectangle inner(Rectangle r, int m) {
        return new Rectangle(r.x + m, r.y + m, r.width - m * 2, r.height - m * 2);
    }

    /** 토스트는 아래 가운데에 쌓는다. 알림 카드와 겹치면 카드 왼쪽으로 비켜 놓는다. */
    private void layoutToasts(List<Rectangle> taken, int m) {
        int y = getHeight() - Ui.px(EDGE + 8) + m;
        for (ToastCard t : toasts) {
            Dimension d = t.getPreferredSize();
            int limit = Math.min(Ui.px(TOAST_MAX_WIDTH) + m * 2, getWidth() - Ui.px(EDGE) * 2 + m * 2);
            int w = Math.min(d.width, Math.max(Ui.px(240), limit));
            y -= d.height;
            Rectangle r = new Rectangle((getWidth() - w) / 2, y, w, d.height);
            for (Rectangle other : taken) {
                if (inner(r, m).intersects(other)) {
                    r.x = Math.max(0, other.x - m - r.width + m * 2 - Ui.px(8));
                }
            }
            t.setBounds(r);
            y -= Ui.px(GAP);
        }
    }
}
