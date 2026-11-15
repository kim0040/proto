package proto.ui.command;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 단축키 안내 목록: 비어 있는 줄이 없고, 이 앱의 모든 단축키가 들어 있다 */
class ShortcutCatalogTest {

    @Test
    void 모든_줄에_하는_일과_키가_있다() {
        for (ShortcutCatalog.Group g : ShortcutCatalog.groups()) {
            assertFalse(g.title().isBlank());
            assertFalse(g.entries().isEmpty());
            for (ShortcutCatalog.Entry e : g.entries()) {
                assertFalse(e.what().isBlank());
                assertFalse(e.keys().isEmpty());
                e.keys().forEach(k -> assertFalse(k.isBlank()));
            }
        }
    }

    @Test
    void 필수_단축키가_들어_있다() {
        Set<String> keys = new HashSet<>();
        ShortcutCatalog.groups().forEach(g -> g.entries().forEach(e -> keys.addAll(e.keys())));
        for (String k : new String[]{"C", "T", "W", "M", "D", "?", "←", "→"}) {
            assertTrue(keys.contains(k), k);
        }
        assertTrue(keys.stream().anyMatch(k -> k.endsWith("K")));
        assertTrue(keys.stream().anyMatch(k -> k.endsWith("Z")));
        assertTrue(keys.stream().anyMatch(k -> k.endsWith("C")));
        assertTrue(keys.stream().anyMatch(k -> k.endsWith("V")));
        assertTrue(keys.stream().anyMatch(k -> k.endsWith("↑")));
    }
}
