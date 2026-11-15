package proto.control;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import proto.entity.Category;
import proto.entity.CategoryPalette;
import proto.repository.CategoryRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static proto.control.ControllerFixture.draft;

class CategoryColorControllerTest {

    @TempDir
    Path dir;
    ControllerFixture f;
    CategoryController c;
    final AtomicInteger categoryCalls = new AtomicInteger();

    @BeforeEach
    void setUp() {
        f = new ControllerFixture(dir, "2026-11-09T10:00");
        c = f.categories;
        c.addListener(categoryCalls::incrementAndGet);
    }

    private String idOf(String name) {
        return c.getCategories().stream().filter(x -> x.getName().equals(name)).findFirst().orElseThrow().getId();
    }

    @Test
    void defaultsKeepTheirOldColoursAndPaletteHasTwelveKeys() {
        assertEquals("blue", c.colorOf("class"));
        assertEquals("orange", c.colorOf("assignment"));
        assertEquals("teal", c.colorOf("team"));
        assertEquals("green", c.colorOf(Category.PERSONAL_ID));
        assertEquals(CategoryPalette.KEYS, c.palette());
        assertEquals(12, c.palette().size());
    }

    @Test
    void unknownCategoryUsesThePersonalColourLikeNameOf() {
        assertEquals("green", c.colorOf("gone"));
        assertTrue(c.setColor(Category.PERSONAL_ID, "purple").success());
        assertEquals("purple", c.colorOf("gone"));
    }

    @Test
    void setColorSavesAndNotifiesCategoryAndScheduleListeners() throws IOException {
        int schedulesBefore = f.recorder.changed;
        assertTrue(c.setColor("class", "red").success());
        assertEquals("red", c.colorOf("class"));
        assertEquals(1, categoryCalls.get());
        assertEquals(schedulesBefore + 1, f.recorder.changed);
        assertTrue(Files.readString(f.categoryFile).contains("class,수업,red\n"));

        CategoryRepository reloaded = new CategoryRepository(f.categoryFile);
        reloaded.loadOrSeed();
        assertEquals("red", reloaded.findById("class").orElseThrow().getColorKey());
    }

    @Test
    void setColorRejectsUnknownCategoryOrKeyWithoutChangingAnything() {
        assertEquals("없는 카테고리예요", c.setColor("gone", "red").message());
        assertEquals("고를 수 없는 색이에요", c.setColor("class", "pink").message());
        assertEquals("고를 수 없는 색이에요", c.setColor("class", null).message());
        assertEquals("고를 수 없는 색이에요", c.setColor("class", "Red").message());
        assertEquals("blue", c.colorOf("class"));
        assertEquals(0, categoryCalls.get());
    }

    @Test
    void settingTheSameColourIsOkWithoutNotifying() {
        int schedulesBefore = f.recorder.changed;
        assertTrue(c.setColor("class", "blue").success());
        assertEquals(0, categoryCalls.get());
        assertEquals(schedulesBefore, f.recorder.changed);
    }

    @Test
    void addPicksTheFirstColourNoCategoryUses() {
        // 기본 4개가 blue, orange, teal, green을 쓰므로 rose → purple → sky → brown 순서
        assertTrue(c.add("동아리").success());
        assertTrue(c.add("알바").success());
        assertEquals("rose", c.colorOf(idOf("동아리")));
        assertEquals("purple", c.colorOf(idOf("알바")));
        assertTrue(c.setColor(idOf("동아리"), "gray").success());
        assertTrue(c.add("운동").success());
        assertEquals("rose", c.colorOf(idOf("운동")));
    }

    @Test
    void whenEveryColourIsUsedAddFallsBackToTheIdDefault() {
        for (int i = 0; i < 8; i++) {
            assertTrue(c.add("추가" + i).success());
        }
        List<String> used = c.getCategories().stream().map(Category::getColorKey).sorted().toList();
        assertEquals(CategoryPalette.KEYS.stream().sorted().toList(), used);
        assertTrue(c.add("하나 더").success());
        String id = idOf("하나 더");
        assertEquals(CategoryPalette.legacyDefault(id), c.colorOf(id));
    }

    @Test
    void colourChangeDoesNotTouchScheduleUndoHistory() {
        assertTrue(f.schedules.create(draft("수업", "2026-11-09", "13:00", "14:00")).success());
        assertTrue(c.setColor("class", "red").success());
        assertTrue(f.schedules.canUndo());
        assertEquals("일정 추가", f.schedules.undoLabel().orElseThrow());
        assertTrue(f.schedules.undo().success());
        assertFalse(f.schedules.canUndo());
        assertEquals("red", c.colorOf("class"));
    }
}
