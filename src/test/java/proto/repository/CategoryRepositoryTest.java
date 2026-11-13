package proto.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import proto.entity.Category;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoryRepositoryTest {

    @TempDir
    Path dir;

    @Test
    void missingFileGivesTheFourDefaultsInOrderAndSavesThem() {
        Path file = dir.resolve("data/categories.csv");
        CategoryRepository repo = new CategoryRepository(file);
        repo.loadOrSeed();

        assertEquals(List.of("수업", "과제", "팀플", "개인"), repo.getAll().stream().map(Category::getName).toList());
        assertEquals(List.of("class", "assignment", "team", Category.PERSONAL_ID),
                repo.getAll().stream().map(Category::getId).toList());
        assertTrue(Files.exists(file));
    }

    @Test
    void saveAndLoadKeepsAddedAndRenamedCategories() throws IOException {
        Path file = dir.resolve("categories.csv");
        CategoryRepository repo = new CategoryRepository(file);
        repo.loadOrSeed();
        repo.add(new Category("c1", "동아리, \"모임\""));
        repo.findById("class").orElseThrow().setName("강의");
        repo.save();

        CategoryRepository loaded = new CategoryRepository(file);
        loaded.loadOrSeed();
        assertEquals(List.of("강의", "과제", "팀플", "개인", "동아리, \"모임\""),
                loaded.getAll().stream().map(Category::getName).toList());
    }

    @Test
    void personalIsAddedBackWhenMissingAndBadRowsAreSkipped() throws IOException {
        Path file = dir.resolve("categories.csv");
        Files.writeString(file, "id,name\nclass,수업\nonly-id\n,이름만\nclass,중복\n");
        CategoryRepository repo = new CategoryRepository(file);
        repo.loadOrSeed();

        assertEquals(List.of("class", Category.PERSONAL_ID), repo.getAll().stream().map(Category::getId).toList());
        assertEquals(3, repo.getWarnings().size());
    }
}
