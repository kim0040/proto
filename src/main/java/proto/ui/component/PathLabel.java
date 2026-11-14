package proto.ui.component;

import proto.ui.theme.Fonts;
import proto.ui.theme.Glyphs;
import proto.ui.theme.Tokens;
import proto.ui.theme.Ui;

import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Dimension;
import java.awt.event.MouseEvent;

/**
 * [경계 boundary] 파일 경로를 한 줄로 보여 주는 라벨. 길면 가운데를 "…"로 줄여 폴더 이름(끝)이 남고,
 * 마우스를 올리면 전체 경로가 나온다. 역슬래시와 한글 폴더 이름도 그대로 그린다.
 * SettingsView가 데이터 폴더 경로를 보여 줄 때 쓴다. 폴더 이름이 길어도 끝부분이 남도록 가운데를 줄인다.
 */
public class PathLabel extends TokenLabel {

    private final String path;

    public PathLabel(String path) {
        super(path, Fonts.regular(Fonts.SMALL), Tokens.MUTED);
        this.path = path;
        getAccessibleContext().setAccessibleName("데이터 폴더 " + path);
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        return TextUtil.tip(path);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(Ui.px(80), super.getPreferredSize().height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Ui.smooth(g2);
        g2.setFont(getFont());
        g2.setColor(getForeground());
        FontMetrics fm = g2.getFontMetrics();
        String shown = TextUtil.ellipsizeMiddle(path, fm, getWidth());
        Glyphs.draw(g2, shown, 0, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }
}
