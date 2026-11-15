package proto.ui.component;

import proto.entity.Occurrence;
import proto.ui.theme.CategoryColors;
import proto.ui.theme.Ui;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

/**
 * [경계 boundary] 카테고리 색을 보여 주는 작은 둥근 네모 (노션처럼 약 10px에 모서리 3px, 카테고리는 색 칩 + 이름으로 나타낸다).
 * OccurrenceRow, ReminderCard, 카테고리 대화상자, 사이드바 목록, 폼의 카테고리 콤보 등 카테고리를 보여 주는 곳 어디서나 쓴다 (F7).
 * 색은 CategoryColors가 카테고리 id로 정한다. 일정 하나를 보여 줄 때는 setOccurrence로 넘기면 일정만의 색(있으면)이 먼저다. 같은 모양을 다른 컴포넌트가 직접 그릴 수 있게 {@link #paint}를 함께 둔다.
 */
public class CategoryDot extends JComponent {

    private final int size;
    private String categoryId;
    /** 일정만의 색 키 (없으면 null, 카테고리 색) */
    private String colorKey;

    public CategoryDot(String categoryId, int size) {
        this.categoryId = categoryId;
        this.size = size;
        setOpaque(false);
    }

    public void setCategoryId(String id) {
        this.categoryId = id;
        this.colorKey = null;
        repaint();
    }

    /** 이 일정의 색(일정만의 색, 없으면 카테고리 색)으로 보여 준다 */
    public void setOccurrence(Occurrence o) {
        this.categoryId = o.categoryId();
        this.colorKey = o.colorKey();
        repaint();
    }

    /** 일정 하나의 색 네모 */
    public static CategoryDot of(Occurrence o, int size) {
        CategoryDot dot = new CategoryDot(o.categoryId(), size);
        dot.colorKey = o.colorKey();
        return dot;
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
        int d = Math.min(getWidth(), getHeight());
        Color c = colorKey != null ? CategoryColors.ofKey(colorKey) : CategoryColors.of(categoryId);
        paint(g2, (getWidth() - d) / 2, (getHeight() - d) / 2, d, c);
        g2.dispose();
    }

    /** 한 변이 d(이미 화면 픽셀)인 둥근 네모를 (x, y)에 칠한다. 모서리 반지름은 한 변의 30%이다 (10px이면 3px) */
    public static void paint(Graphics2D g2, int x, int y, int d, Color color) {
        int arc = Math.round(d * 0.6f);
        g2.setColor(color);
        g2.fillRoundRect(x, y, d, d, arc, arc);
    }
}
