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

class CategoryColorCsvTest {

    @TempDir
    Path dir;

    private static List<String> colors(CategoryRepository repo) {
        return repo.getAll().stream().map(Category::getColorKey).toList();
    }

    @Test
    void seededFileHasTheColourColumnWithLegacyDefaults() throws IOException {
        Path file = dir.resolve("categories.csv");
        CategoryRepository repo = new CategoryRepository(file);
        repo.loadOrSeed();
        assertEquals("id,name,color\nclass,수업,blue\nassignment,과제,orange\nteam,팀플,teal\npersonal,개인,green\n",
                Files.readString(file));
    }

    @Test
    void chosenColoursSurviveSaveAndLoad() throws IOException {
        Path file = dir.resolve("categories.csv");
        CategoryRepository repo = new CategoryRepository(file);
        repo.loadOrSeed();
        repo.add(new Category("club", "동아리", "indigo"));
        repo.findById("class").orElseThrow().setColorKey("red");
        repo.save();

        CategoryRepository loaded = new CategoryRepository(file);
        loaded.loadOrSeed();
        assertEquals(List.of("red", "orange", "teal", "green", "indigo"), colors(loaded));
        assertTrue(loaded.getWarnings().isEmpty());
    }

    @Test
    void oldFileWithoutColourLoadsWithDefaultsAndIsRewrittenWithTheColumn() throws IOException {
        Path file = dir.resolve("categories.csv");
        Files.writeString(file, "id,name\nclass,수업\npersonal,개인\nclub,동아리\nx,기타\n");
        CategoryRepository repo = new CategoryRepository(file);
        repo.loadOrSeed();
        assertEquals(List.of("blue", "green", "rose", "brown"), colors(repo));
        assertTrue(repo.getWarnings().isEmpty());

        repo.save();
        assertEquals("id,name,color\nclass,수업,blue\npersonal,개인,green\nclub,동아리,rose\nx,기타,brown\n",
                Files.readString(file));
    }

    @Test
    void blankOrUnknownColourFallsBackToTheDefaultWithoutWarning() throws IOException {
        Path file = dir.resolve("categories.csv");
        Files.writeString(file, "id,name,color\nclass,수업,\npersonal,개인,pink\nclub,동아리, gray \n");
        CategoryRepository repo = new CategoryRepository(file);
        repo.loadOrSeed();
        assertEquals(List.of("blue", "green", "gray"), colors(repo));
        assertTrue(repo.getWarnings().isEmpty());
    }

    @Test
    void rowWithTooManyCellsIsStillSkipped() throws IOException {
        Path file = dir.resolve("categories.csv");
        Files.writeString(file, "id,name,color\nclass,수업,blue,extra\npersonal,개인,green\n");
        CategoryRepository repo = new CategoryRepository(file);
        repo.loadOrSeed();
        assertEquals(List.of(Category.PERSONAL_ID), repo.getAll().stream().map(Category::getId).toList());
        assertEquals(1, repo.getWarnings().size());
        assertTrue(repo.getWarnings().get(0).contains("2번째 줄"));
    }
}
