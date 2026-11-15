package proto.ui.component;

import proto.ui.theme.CategoryColors;
import proto.ui.theme.Ui;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * [경계 boundary] 카테고리 색을 고르는 작은 팝오버: 12색 견본 격자(현재 색에 체크)를 단추 아래에 띄운다 (F7).
 * CategoryDialog의 색 칩을 누르면 {@link #show}로 열고, 색을 고르면 닫으며 고른 색 키를 돌려준다. Esc나 바깥 클릭으로 닫으면 아무것도 바꾸지 않는다.
 * 팝오버가 닫히면 포커스는 칩으로 돌아가 키보드로 이어서 쓸 수 있다.
 */
public final class ColorSwatchPopover {

    private static final int COLUMNS = 6;
    private static final String CLOSE = "proto.closeSwatch";

    private ColorSwatchPopover() {
    }

    /** anchor 아래에 색 팝오버를 연다. keys는 팔레트 색 키, current는 지금 색, onPick은 고른 색 키를 받는다 */
    public static void show(JComponent anchor, List<String> keys, String current, Consumer<String> onPick) {
        SwatchGrid grid = new SwatchGrid(swatches(keys), COLUMNS, false, current);
        JPopupMenu popup = new JPopupMenu();
        popup.setFocusable(true);
        popup.setBorder(BorderFactory.createCompoundBorder(popup.getBorder(),
                BorderFactory.createEmptyBorder(Ui.px(4), Ui.px(4), Ui.px(4), Ui.px(4))));
        popup.add(grid);
        grid.addChangeListener(key -> {
            close(popup, anchor);
            onPick.accept(key);
        });
        bindEscape(grid, popup, anchor);
        popup.show(anchor, 0, anchor.getHeight() + Ui.px(4));
        SwingUtilities.invokeLater(grid::requestFocusInWindow);
    }

    private static List<SwatchGrid.Swatch> swatches(List<String> keys) {
        return keys.stream()
                .map(k -> new SwatchGrid.Swatch(k, ColorLabels.of(k), () -> CategoryColors.ofKey(k)))
                .toList();
    }

    private static void bindEscape(JComponent grid, JPopupMenu popup, Component anchor) {
        grid.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), CLOSE);
        grid.getActionMap().put(CLOSE, new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                close(popup, anchor);
            }
        });
    }

    private static void close(JPopupMenu popup, Component anchor) {
        popup.setVisible(false);
        anchor.requestFocusInWindow();
    }
}
