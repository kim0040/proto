package proto.ui.component;

import proto.ui.Fmt;
import proto.ui.motion.Tween;
import proto.ui.theme.CategoryColors;
import proto.ui.theme.Fonts;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import javax.swing.JComponent;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/**
 * [경계 boundary] 카테고리별 계획 시간 가로 막대 그래프 (F14). 막대는 데이터라서 카테고리 색을 쓰고,
 * 열릴 때 0에서 자라난다.
 */
public class HorizontalBars extends JComponent {

    /** 막대 한 줄: 카테고리 id, 이름, 분 */
    public record Row(String categoryId, String name, long minutes) {
    }

    private static final int ROW_H = 44;

    private List<Row> rows = new ArrayList<>();
    private final Tween grow = new Tween(1f, this::repaint);

    public HorizontalBars() {
        setOpaque(false);
    }

    /** 데이터를 바꾸고 animate면 막대가 0에서 자란다 */
    public void setRows(List<Row> data, boolean animate) {
        rows = new ArrayList<>(data);
        revalidate();
        if (animate) {
            grow.set(0f);
            grow.to(1f, 420);
        } else {
            grow.set(1f);
        }
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Ui.px(400), Ui.px(ROW_H) * Math.max(1, rows.size()));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.smooth(g2);
        long max = rows.stream().mapToLong(Row::minutes).max().orElse(1);
        int labelW = Ui.px(120);
        int valueW = Ui.px(88);
        int barX = labelW + Ui.px(12);
        int barW = Math.max(Ui.px(40), getWidth() - barX - valueW - Ui.px(12));
        for (int i = 0; i < rows.size(); i++) {
            paintRow(g2, rows.get(i), i, max, barX, barW, labelW);
        }
        g2.dispose();
    }

    private void paintRow(Graphics2D g2, Row r, int i, long max, int barX, int barW, int labelW) {
        int rowH = Ui.px(ROW_H);
        int y = i * rowH;
        int cy = y + rowH / 2;
        g2.setFont(Fonts.medium(Fonts.BODY));
        FontMetrics fm = g2.getFontMetrics();
        int d = Ui.px(8);
        g2.setColor(CategoryColors.of(r.categoryId()));
        g2.fillOval(0, cy - d / 2, d, d);
        g2.setColor(Tokens.get(Tokens.TEXT));
        g2.drawString(TextUtil.ellipsize(r.name(), fm, labelW - d - Ui.px(10)), d + Ui.px(10),
                cy - fm.getHeight() / 2 + fm.getAscent());
        int barH = Ui.px(10);
        g2.setColor(Tokens.get(Tokens.SUBTLE));
        g2.fillRoundRect(barX, cy - barH / 2, barW, barH, barH, barH);
        float local = Math.max(0f, Math.min(1f, grow.value() * 1.3f - i * 0.1f));
        int w = Math.max(barH, Math.round(barW * (r.minutes() / (float) max) * local));
        if (local > 0f) {
            g2.setColor(CategoryColors.of(r.categoryId()));
            g2.fillRoundRect(barX, cy - barH / 2, w, barH, barH, barH);
        }
        g2.setFont(Fonts.regular(Fonts.SMALL));
        fm = g2.getFontMetrics();
        String v = Fmt.duration(r.minutes());
        g2.setColor(Tokens.get(Tokens.MUTED));
        g2.drawString(v, getWidth() - fm.stringWidth(v), cy - fm.getHeight() / 2 + fm.getAscent());
    }
}
