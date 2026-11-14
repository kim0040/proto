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
 * F7(카테고리 관리)의 저장을 맡는다. CategoryController가 CategoryStore 인터페이스로만 이 클래스를 쓰고, StoreFactory가 만들어 준다.
 * 메모리 목록이 "진짜 데이터"이고, 파일은 save()를 부를 때마다 통째로 새로 쓴다. 파일 쓰기는 CsvFileStore, 글자 변환은 CsvCodec이 맡는다.
 * 파일이 없으면 기본 4개(수업, 과제, 팀플, 개인)로 시작한다.
 * 불러오면서 문제가 생기면 원본을 같은 폴더에 백업한 뒤에야 덮어쓴다 (백업 실패 시 저장하지 않음).
 * "개인"(id personal)을 먼저 확보하고, 그 이름과 같은 다른 행은 경고와 함께 건너뛴다.
 */
public class CategoryRepository implements CategoryStore {

    /** 파일에 "개인" 행이 없을 때 덧붙이는 이름. 이 이름과 같은 다른 행은 건너뛴다 */
    private static final String DEFAULT_PERSONAL_NAME = "개인";

    private final Path file;
    private final Clock clock;
    private final List<Category> categories = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private boolean saveBlocked;

    /**
     * 시스템 시계를 쓰는 보통 생성자.
     *
     * @param file 카테고리 CSV 파일 경로 (예: data/categories.csv)
     */
    public CategoryRepository(Path file) {
        this(file, Clock.systemDefaultZone());
    }

    /**
     * 시계를 바꿔 끼울 수 있는 생성자. 시험에서 가짜 시계를 넣으면 백업 파일 이름을 미리 알 수 있다.
     *
     * @param file  카테고리 CSV 파일 경로
     * @param clock 백업 파일 이름에 넣는 시각을 얻는 시계
     */
    public CategoryRepository(Path file, Clock clock) {
        this.file = file;
        this.clock = clock;
    }

    /**
     * 파일이 있으면 읽고, 없으면 기본 카테고리로 시작해 바로 저장한다.
     * 파일 전체를 읽지 못했거나 CSV 구조가 깨졌으면 기본 카테고리로 시작한다. 문제가 있으면 원본을 백업한다.
     * 이 메서드는 예외를 던지지 않는다. 문제는 모두 {@link #getWarnings()}에 한국어 문장으로 쌓고, 앱은 그대로 시작한다.
     */
    @Override
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

    /**
     * 메모리 목록 전체를 파일에 새로 쓴다 (첫 줄은 열 이름). 쓰기는 임시 파일 + 옮기기 방식이라 도중에 끊겨도 파일이 깨지지 않는다.
     * 불러올 때 원본을 백업하지 못했으면(saveBlocked) 덮어쓰지 않고 IOException을 던진다. 읽지 못한 옛 기록이 조용히 사라지지 않게 하기 위해서이다.
     *
     * @throws IOException 쓰기에 실패했거나 저장이 막혀 있을 때
     */
    @Override
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

    /** 불러올 때 생긴 경고들 (한국어 문장). 없으면 빈 목록. 복사본을 돌려주므로 호출한 쪽이 고쳐도 안전하다. */
    @Override
    public List<String> getWarnings() {
        return List.copyOf(warnings);
    }

    /** 메모리의 카테고리 목록. 고칠 수 없는 보기(view)라서, 목록을 바꾸려면 add/remove를 써야 한다. */
    @Override
    public List<Category> getAll() {
        return Collections.unmodifiableList(categories);
    }

    /** id가 같은 카테고리. 없으면 Optional.empty(). */
    @Override
    public Optional<Category> findById(String id) {
        return categories.stream().filter(c -> c.getId().equals(id)).findFirst();
    }

    /** 목록 맨 뒤에 더한다. 메모리만 바뀌고 파일은 save()를 불러야 바뀐다. 이름 중복 같은 검사는 CategoryController가 먼저 한다. */
    @Override
    public void add(Category category) {
        categories.add(category);
    }

    /** 목록에서 뺀다 (id가 같은 것). 없으면 아무 일도 없다. 메모리만 바뀌고 파일은 save()를 불러야 바뀐다. */
    @Override
    public void remove(Category category) {
        categories.remove(category);
    }

    private static List<Category> defaults() {
        return List.of(new Category("class", "수업"), new Category("assignment", "과제"),
                new Category("team", "팀플"), new Category(Category.PERSONAL_ID, DEFAULT_PERSONAL_NAME));
    }

    /** 파일 전체를 임시 목록으로 읽는다. 읽기 실패·구조 오류면 기본 카테고리. "개인"을 먼저 확보한다 */
    private List<Category> readFile(LoadProblems problems) {
        List<Category> loaded = new ArrayList<>();
        try {
            List<List<String>> rows = CsvCodec.parse(CsvFileStore.read(file));
            String personalName = reservedPersonalName(rows);
            for (int i = 0; i < rows.size(); i++) {
                loadRow(rows.get(i), i + 1, loaded, problems, personalName);
            }
        } catch (CsvSyntaxException e) {
            problems.add("카테고리 파일 " + e.lineNumber() + "번째 줄의 따옴표가 맞지 않아 기본 카테고리로 시작했어요");
            return new ArrayList<>(defaults());
        } catch (IOException e) {
            problems.add("카테고리 파일을 읽지 못해 기본 카테고리로 시작했어요: " + e.getMessage());
            return new ArrayList<>(defaults());
        }
        if (loaded.stream().noneMatch(Category::isPersonal)) {
            loaded.add(new Category(Category.PERSONAL_ID, DEFAULT_PERSONAL_NAME));
        }
        return loaded;
    }

    /**
     * "개인" 이름을 먼저 정해 두는 이유: 다른 행이 "개인"과 같은 이름을 쓰고 있으면, 그 행이 파일에서 먼저 나오더라도
     * (기본 카테고리인) 개인을 지키고 그 다른 행을 건너뛰기 위해서이다.
     */
    private static String reservedPersonalName(List<List<String>> rows) {
        for (List<String> row : rows) {
            if (isUsablePersonalRow(row)) {
                return row.get(1).trim();
            }
        }
        return DEFAULT_PERSONAL_NAME;
    }

    /** id가 personal이고 이름 규칙을 통과하는 행인지 */
    private static boolean isUsablePersonalRow(List<String> row) {
        if (row.size() != 2 || !Category.PERSONAL_ID.equals(row.get(0)) || row.get(1).isBlank()) {
            return false;
        }
        return row.get(1).trim().length() <= Category.MAX_NAME_LENGTH;
    }

    private static void loadRow(List<String> row, int lineNo, List<Category> loaded, LoadProblems problems,
                                String personalName) {
        if (lineNo == 1 && !row.isEmpty() && row.get(0).equals("id")) {
            return;
        }
        String reason = rowProblem(row, loaded, personalName);
        if (reason != null) {
            problems.add("카테고리 파일 " + lineNo + "번째 줄을 읽지 못해 건너뛰었어요 (" + reason + ")");
            return;
        }
        loaded.add(new Category(row.get(0), row.get(1).trim()));
    }

    /**
     * 행이 잘못된 이유. 괜찮으면 null. 이름은 앞뒤 공백 제외 1~20자, 대소문자 무시 중복 금지.
     * personal이 아직 목록에 없어도 그 이름과 같은 다른 행은 중복이다.
     */
    private static String rowProblem(List<String> row, List<Category> loaded, String personalName) {
        if (row.size() != 2 || row.get(0).isBlank() || row.get(1).isBlank()) {
            return "id와 이름 두 칸이 필요해요";
        }
        String name = row.get(1).trim().toLowerCase(Locale.ROOT);
        if (name.length() > Category.MAX_NAME_LENGTH) {
            return "이름이 " + Category.MAX_NAME_LENGTH + "자를 넘어요";
        }
        if (loaded.stream().anyMatch(c -> c.getId().equals(row.get(0)))) {
            return "id가 겹쳐요";
        }
        boolean reserved = !Category.PERSONAL_ID.equals(row.get(0))
                && name.equals(personalName.toLowerCase(Locale.ROOT));
        boolean taken = reserved || loaded.stream().anyMatch(c -> nameMatches(c, name));
        return taken ? "이미 있는 이름이에요" : null;
    }

    private static boolean nameMatches(Category category, String lowerName) {
        return category.getName().toLowerCase(Locale.ROOT).equals(lowerName);
    }
}
