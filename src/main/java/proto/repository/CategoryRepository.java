package proto.repository;

import proto.entity.Category;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * [저장소 repository] 카테고리 목록을 메모리에 들고 있고, CSV 파일(data/categories.csv: id,name)로 저장·불러온다 (F7, F11).
 * 파일이 없으면 기본 4개(수업, 과제, 팀플, 개인)로 시작한다.
 * 불러오면서 문제가 생기면 원본을 같은 폴더에 백업한 뒤에야 덮어쓴다 (백업 실패 시 저장하지 않음).
 */
public class CategoryRepository {

    /** 카테고리 이름 최대 글자 수 (앞뒤 공백 제외) */
    public static final int MAX_NAME_LENGTH = 20;

    private final Path file;
    private final Clock clock;
    private final List<Category> categories = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private boolean saveBlocked;

    public CategoryRepository(Path file) {
        this(file, Clock.systemDefaultZone());
    }

    /** @param clock 백업 파일 이름의 시각에 쓴다 */
    public CategoryRepository(Path file, Clock clock) {
        this.file = file;
        this.clock = clock;
    }

    /**
     * 파일이 있으면 읽고, 없으면 기본 카테고리로 시작해 바로 저장한다.
     * 파일 전체를 읽지 못했거나 CSV 구조가 깨졌으면 기본 카테고리로 시작한다. 문제가 있으면 원본을 백업한다.
     */
    public void loadOrSeed() {
        warnings.clear();
        saveBlocked = false;
        if (Files.exists(file)) {
            LoadProblems problems = new LoadProblems();
            List<Category> loaded = readFile(problems);
            categories.clear();
            categories.addAll(loaded);
            saveBlocked = !problems.preserveOriginal(file, LocalDateTime.now(clock), warnings);
            return;
        }
        categories.clear();
        categories.addAll(defaults());
        try {
            save();
        } catch (IOException e) {
            warnings.add("기본 카테고리를 저장하지 못했어요: " + e.getMessage());
        }
    }

    /** 전체를 파일에 쓴다. 불러올 때 원본을 백업하지 못했으면 덮어쓰지 않고 IOException을 던진다 */
    public void save() throws IOException {
        if (saveBlocked) {
            throw LoadProblems.saveBlocked(file);
        }
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

    /** 파일 전체를 임시 목록으로 읽는다. 읽기 실패·구조 오류면 기본 카테고리. "개인"이 없으면 덧붙인다 */
    private List<Category> readFile(LoadProblems problems) {
        List<Category> loaded = new ArrayList<>();
        try {
            List<List<String>> rows = CsvCodec.parse(CsvFileStore.read(file));
            for (int i = 0; i < rows.size(); i++) {
                loadRow(rows.get(i), i + 1, loaded, problems);
            }
        } catch (CsvSyntaxException e) {
            problems.add("카테고리 파일 " + e.lineNumber() + "번째 줄의 따옴표가 맞지 않아 기본 카테고리로 시작했어요");
            return new ArrayList<>(defaults());
        } catch (IOException e) {
            problems.add("카테고리 파일을 읽지 못해 기본 카테고리로 시작했어요: " + e.getMessage());
            return new ArrayList<>(defaults());
        }
        if (loaded.stream().noneMatch(Category::isPersonal)) {
            loaded.add(new Category(Category.PERSONAL_ID, "개인"));
        }
        return loaded;
    }

    private static void loadRow(List<String> row, int lineNo, List<Category> loaded, LoadProblems problems) {
        if (lineNo == 1 && !row.isEmpty() && row.get(0).equals("id")) {
            return;
        }
        String reason = rowProblem(row, loaded);
        if (reason != null) {
            problems.add("카테고리 파일 " + lineNo + "번째 줄을 읽지 못해 건너뛰었어요 (" + reason + ")");
            return;
        }
        loaded.add(new Category(row.get(0), row.get(1).trim()));
    }

    /** 행이 잘못된 이유. 괜찮으면 null. 이름은 앞뒤 공백 제외 1~20자, 대소문자 무시 중복 금지 */
    private static String rowProblem(List<String> row, List<Category> loaded) {
        if (row.size() != 2 || row.get(0).isBlank() || row.get(1).isBlank()) {
            return "id와 이름 두 칸이 필요해요";
        }
        String name = row.get(1).trim().toLowerCase(Locale.ROOT);
        if (name.length() > MAX_NAME_LENGTH) {
            return "이름이 " + MAX_NAME_LENGTH + "자를 넘어요";
        }
        if (loaded.stream().anyMatch(c -> c.getId().equals(row.get(0)))) {
            return "id가 겹쳐요";
        }
        boolean sameName = loaded.stream().anyMatch(c -> c.getName().toLowerCase(Locale.ROOT).equals(name));
        return sameName ? "이미 있는 이름이에요" : null;
    }
}
