package proto.ui.component;

import proto.ui.theme.CategoryColors;
import proto.ui.theme.Ui;

import javax.swing.JComponent;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

/**
 * [경계 boundary] 카테고리 색을 보여 주는 작은 동그라미 (카테고리는 점 + 이름으로 나타낸다).
 * OccurrenceRow, ReminderCard, 카테고리 대화상자 등 카테고리를 보여 주는 곳 어디서나 쓴다 (F7). 색은 CategoryColors가 카테고리 id로 정한다.
 */
public class CategoryDot extends JComponent {

    private final int size;
    private String categoryId;

    public CategoryDot(String categoryId, int size) {
        this.categoryId = categoryId;
        this.size = size;
        setOpaque(false);
    }

    public void setCategoryId(String id) {
        this.categoryId = id;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Ui.px(size), Ui.px(size));
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.smooth(g2);
        g2.setColor(CategoryColors.of(categoryId));
        int d = Math.min(getWidth(), getHeight());
        g2.fillOval((getWidth() - d) / 2, (getHeight() - d) / 2, d, d);
        g2.dispose();
    }
}
