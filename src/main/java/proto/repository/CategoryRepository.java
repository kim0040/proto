package proto.repository;

import proto.entity.Category;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * [저장소 repository] 카테고리 목록을 메모리에 들고 있고, CSV 파일(data/categories.csv: id,name)로 저장·불러온다 (F7, F11).
 * 파일이 없으면 기본 4개(수업, 과제, 팀플, 개인)로 시작한다.
 */
public class CategoryRepository {

    private final Path file;
    private final List<Category> categories = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public CategoryRepository(Path file) {
        this.file = file;
    }

    /** 파일이 있으면 읽고, 없으면 기본 카테고리로 시작해 바로 저장한다 */
    public void loadOrSeed() {
        categories.clear();
        warnings.clear();
        if (Files.exists(file)) {
            readFile();
            return;
        }
        categories.addAll(defaults());
        try {
            save();
        } catch (IOException e) {
            warnings.add("기본 카테고리를 저장하지 못했어요: " + e.getMessage());
        }
    }

    public void save() throws IOException {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("id", "name"));
        for (Category c : categories) {
            rows.add(List.of(c.getId(), c.getName()));
        }
        CsvFileStore.writeAtomically(file, CsvCodec.encode(rows));
    }

    /** 불러올 때 생긴 경고들 (한국어 문장). 없으면 빈 목록 */
    public List<String> getWarnings() {
        return List.copyOf(warnings);
    }

    public List<Category> getAll() {
        return Collections.unmodifiableList(categories);
    }

    public Optional<Category> findById(String id) {
        return categories.stream().filter(c -> c.getId().equals(id)).findFirst();
    }

    public void add(Category category) {
        categories.add(category);
    }

    public void remove(Category category) {
        categories.remove(category);
    }

    private static List<Category> defaults() {
        return List.of(new Category("class", "수업"), new Category("assignment", "과제"),
                new Category("team", "팀플"), new Category(Category.PERSONAL_ID, "개인"));
    }

    private void readFile() {
        try {
            List<List<String>> rows = CsvCodec.parse(CsvFileStore.read(file));
            for (int i = 0; i < rows.size(); i++) {
                loadRow(rows.get(i), i + 1);
            }
        } catch (IOException e) {
            warnings.add("카테고리 파일을 읽지 못했어요: " + e.getMessage());
        }
        if (findById(Category.PERSONAL_ID).isEmpty()) {
            categories.add(new Category(Category.PERSONAL_ID, "개인"));
        }
    }

    private void loadRow(List<String> row, int lineNo) {
        if (lineNo == 1 && !row.isEmpty() && row.get(0).equals("id")) {
            return;
        }
        if (row.size() < 2 || row.get(0).isBlank() || row.get(1).isBlank() || findById(row.get(0)).isPresent()) {
            warnings.add("카테고리 파일의 " + lineNo + "번째 줄을 읽지 못해 건너뛰었어요");
            return;
        }
        categories.add(new Category(row.get(0), row.get(1)));
    }
}
