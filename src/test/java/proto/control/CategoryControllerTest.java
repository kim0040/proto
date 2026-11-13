package proto.control;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import proto.entity.Category;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.repository.CategoryRepository;
import proto.repository.ScheduleRepository;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static proto.TestData.d;
import static proto.TestData.t;

class CategoryControllerTest {

    @TempDir
    Path dir;
    ControllerFixture f;
    CategoryController c;

    @BeforeEach
    void setUp() {
        f = new ControllerFixture(dir, "2026-11-09T10:00");
        c = f.categories;
    }

    private List<String> names() {
        return c.getCategories().stream().map(Category::getName).toList();
    }

    @Test
    void startsWithTheFourDefaultsAndAppendsNewOnesLast() {
        assertEquals(List.of("수업", "과제", "팀플", "개인"), names());
        assertTrue(c.add("  동아리  ").success());
        assertEquals(List.of("수업", "과제", "팀플", "개인", "동아리"), names());
    }

    @Test
    void emptyTooLongAndDuplicateNamesAreRejected() {
        assertEquals("카테고리 이름을 입력해 주세요", c.add("").message());
        assertEquals("카테고리 이름을 입력해 주세요", c.add("   ").message());
        assertEquals("카테고리 이름을 입력해 주세요", c.add(null).message());
        assertEquals("카테고리 이름은 20자까지 쓸 수 있어요", c.add("가".repeat(21)).message());
        assertTrue(c.add("나".repeat(20)).success());
        assertEquals("이미 있는 이름이에요", c.add("수업").message());
    }

    @Test
    void duplicateCheckIgnoresCaseAndSurroundingSpaces() {
        assertTrue(c.add("Math").success());
        assertEquals("이미 있는 이름이에요", c.add("math").message());
        assertEquals("이미 있는 이름이에요", c.add(" MATH ").message());
        assertEquals(5, c.getCategories().size());
    }

    @Test
    void renameFollowsTheSameRulesButAllowsItsOwnName() {
        assertEquals("이미 있는 이름이에요", c.rename("class", "과제").message());
        assertTrue(c.rename("class", "수업").success());
        assertTrue(c.rename("class", "강의").success());
        assertEquals("강의", c.nameOf("class"));
        assertFalse(c.rename("missing", "x").success());
        CategoryRepository onDisk = new CategoryRepository(f.categoryFile);
        onDisk.loadOrSeed();
        assertEquals("강의", onDisk.findById("class").orElseThrow().getName());
    }

    @Test
    void personalCategoryCannotBeDeleted() {
        assertFalse(c.canDelete(Category.PERSONAL_ID));
        OperationResult r = c.delete(Category.PERSONAL_ID);
        assertFalse(r.success());
        assertEquals("'개인' 카테고리는 지울 수 없어요", r.message());
        assertEquals(4, c.getCategories().size());
    }

    @Test
    void nameOfUnknownIdFallsBackToPersonal() {
        assertEquals("개인", c.nameOf("no-such-id"));
        assertFalse(c.canDelete("no-such-id"));
    }

    @Test
    void deleteMovesSchedulesToPersonalSavesBothFilesAndNotifiesBothListeners() {
        f.schedules.create(new ScheduleDraft("회의", d("2026-11-10"), t("09:00"), t("10:00"), "team", "",
                RepeatRule.NONE, null, ReminderOption.NONE));
        f.schedules.create(new ScheduleDraft("수업", d("2026-11-10"), t("11:00"), t("12:00"), "class", "",
                RepeatRule.NONE, null, ReminderOption.NONE));
        AtomicInteger categoryEvents = new AtomicInteger();
        c.addListener(categoryEvents::incrementAndGet);
        int scheduleEventsBefore = f.recorder.changed;
        assertEquals(1, c.countSchedules("team"));
        assertTrue(c.canDelete("team"));

        assertTrue(c.delete("team").success());

        assertEquals(Category.PERSONAL_ID, f.occurrenceOn("회의", "2026-11-10").categoryId());
        assertEquals("class", f.occurrenceOn("수업", "2026-11-10").categoryId());
        assertEquals(0, c.countSchedules("team"));
        assertEquals(List.of("수업", "과제", "개인"), names());
        assertEquals(1, categoryEvents.get());
        assertEquals(scheduleEventsBefore + 1, f.recorder.changed);

        ScheduleRepository schedulesOnDisk = new ScheduleRepository(f.scheduleFile);
        schedulesOnDisk.load();
        assertTrue(schedulesOnDisk.getAll().stream().noneMatch(s -> s.getCategoryId().equals("team")));
        CategoryRepository categoriesOnDisk = new CategoryRepository(f.categoryFile);
        categoriesOnDisk.loadOrSeed();
        assertEquals(3, categoriesOnDisk.getAll().size());
    }
}
