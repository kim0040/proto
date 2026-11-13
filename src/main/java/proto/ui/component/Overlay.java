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

/**
 * [경계 boundary] 메인 창 위에 떠 있는 투명 층. 토스트(아래 가운데)와 알림 카드(아래 오른쪽)를 쌓아 보여 준다.
 * 카드가 없는 곳의 마우스는 아래 화면으로 그대로 통과한다.
 */
public class Overlay extends JComponent {

    private static final int GAP = 4;
    private static final int EDGE = 16;
    private static final int TOAST_MS = 2500;

    private final List<ToastCard> toasts = new ArrayList<>();
    private final List<ReminderCard> reminders = new ArrayList<>();

    public Overlay() {
        setLayout(null);
        setOpaque(false);
    }

    @Override
    public boolean contains(int x, int y) {
        for (Component c : getComponents()) {
            if (c instanceof FloatingCard && c.getBounds().contains(x, y) && c.contains(x - c.getX(), y - c.getY())) {
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
        toasts.add(holder[0]);
        add(holder[0]);
        revalidate();
        holder[0].playIn();
        if (!error) {
            Timer t = new Timer(TOAST_MS, e -> dismissToast(holder[0]));
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

    /** 알림 카드를 맨 아래에 쌓는다. 여러 개면 위로 쌓인다 */
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

    /** 지금 떠 있는 알림 카드 수 */
    public int reminderCount() {
        return reminders.size();
    }

    @Override
    public void doLayout() {
        int m = Ui.px(FloatingCard.MARGIN);
        int y = getHeight() - Ui.px(EDGE) + m;
        Rectangle reminderArea = null;
        for (ReminderCard c : reminders) {
            Dimension d = c.getPreferredSize();
            y -= d.height;
            c.setBounds(getWidth() - d.width - Ui.px(EDGE) + m, y, d.width, d.height);
            y -= Ui.px(GAP);
            reminderArea = reminderArea == null ? c.getBounds() : reminderArea.union(c.getBounds());
        }
        layoutToasts(reminderArea);
    }

    private void layoutToasts(Rectangle avoid) {
        int m = Ui.px(FloatingCard.MARGIN);
        int y = getHeight() - Ui.px(EDGE + 8) + m;
        for (ToastCard t : toasts) {
            Dimension d = t.getPreferredSize();
            int w = Math.min(d.width, Math.max(Ui.px(240), getWidth() - Ui.px(EDGE) * 2));
            int x = (getWidth() - w) / 2;
            y -= d.height;
            Rectangle r = new Rectangle(x, y, w, d.height);
            if (avoid != null && r.intersects(avoid)) {
                y = avoid.y - d.height;
                r.y = y;
            }
            t.setBounds(r);
            y -= Ui.px(GAP);
        }
    }
}
