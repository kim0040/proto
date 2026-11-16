package proto.control;

import proto.entity.Category;
import proto.entity.CategoryPalette;
import proto.entity.Schedule;
import proto.repository.CategoryStore;
import proto.repository.ScheduleStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * [제어 control] 카테고리 추가·이름 변경·색 변경·삭제를 처리한다 (F7).
 * CategoryDialog가 부르고, CategoryStore에 저장하며, 결과를 CategoryListener와 ScheduleController를 거쳐 화면에 알린다.
 * 카테고리를 지우면 일정이 "카테고리 없음" 상태가 되지 않도록 그 일정들을 "개인"으로 옮기고,
 * 일정 되돌리기 기록을 지운 뒤 카테고리와 일정 리스너 양쪽에 알린다.
 * 색은 팔레트 12색(CategoryPalette의 키) 중 하나이다. 새 카테고리는 아직 아무도 쓰지 않은 첫 색을 받는다.
 * 색을 바꾸면 일정 블록도 다시 그려야 하므로 양쪽 리스너에 알리며, 일정 되돌리기 기록과는 상관없다.
 *
 * <p>모든 public 메소드는 Swing 이벤트 스레드(EDT)에서 불러야 한다 (화면이 이미 EDT에서 부른다).</p>
 */
public class CategoryController {

    private static final int MAX_NAME_LENGTH = Category.MAX_NAME_LENGTH;
    private static final String PERSONAL_NAME = "개인";

    private final CategoryStore categories;
    private final ScheduleStore schedules;
    private final ScheduleController scheduleController;
    private final List<CategoryListener> listeners = new CopyOnWriteArrayList<>();

    /** @param scheduleController 일정 쪽 리스너(일정 목록 갱신, 저장 실패 알림)에 알리는 데 쓴다 */
    public CategoryController(CategoryStore categories, ScheduleStore schedules,
                              ScheduleController scheduleController) {
        this.categories = categories;
        this.schedules = schedules;
        this.scheduleController = scheduleController;
    }

    /** 카테고리 변화를 받으려고 자신을 등록한다 (CategoryDialog 등) */
    public void addListener(CategoryListener listener) {
        listeners.add(listener);
    }

    /** 등록했던 리스너를 뺀다. 등록하지 않은 것이면 아무 일도 없다 */
    public void removeListener(CategoryListener listener) {
        listeners.remove(listener);
    }

    /**
     * 카테고리 목록. 기본 4개(수업, 과제, 팀플, 개인)가 앞, 추가한 것은 추가한 순서대로 뒤.
     * 돌려주는 목록은 복사본이라 호출한 쪽이 고쳐도 저장소에 영향이 없다.
     */
    public List<Category> getCategories() {
        return List.copyOf(categories.getAll());
    }

    /**
     * id의 이름. 지워진 카테고리를 가리키는 일정이 있어도 화면이 깨지지 않도록, 없는 id면 "개인" 이름을 돌려준다.
     */
    public String nameOf(String categoryId) {
        return categories.findById(categoryId)
                .or(() -> categories.findById(Category.PERSONAL_ID))
                .map(Category::getName)
                .orElse(PERSONAL_NAME);
    }

    /**
     * id의 색 키 (CategoryPalette.KEYS 중 하나). 없는 id면 nameOf처럼 "개인"의 색, "개인"도 없으면 그 id의 기본 색.
     */
    public String colorOf(String categoryId) {
        return categories.findById(categoryId)
                .or(() -> categories.findById(Category.PERSONAL_ID))
                .map(Category::getColorKey)
                .orElse(CategoryPalette.legacyDefault(categoryId));
    }

    /** 고를 수 있는 색 키 12개 (팔레트 순서). 바꿀 수 없는 목록이다 */
    public List<String> palette() {
        return CategoryPalette.KEYS;
    }

    /**
     * 카테고리 색을 바꾼다. 저장한 뒤(F11) 카테고리 리스너와 일정 리스너 모두에 알린다 (일정 블록 색도 바뀌므로).
     * 이미 그 색이면 저장·알림 없이 ok()이다. 저장에 실패하면 storageFailed로 알리고 메모리의 새 색은 유지한다 (이름 변경과 같다).
     *
     * @return 성공이면 ok(), 카테고리가 없거나 팔레트에 없는 색 키면 이유가 든 실패 (아무것도 바꾸지 않음)
     */
    public OperationResult setColor(String categoryId, String colorKey) {
        Optional<Category> category = categories.findById(categoryId);
        if (category.isEmpty()) {
            return OperationResult.fail("없는 카테고리예요");
        }
        if (!CategoryPalette.isKnown(colorKey)) {
            return OperationResult.fail("고를 수 없는 색이에요");
        }
        if (colorKey.equals(category.get().getColorKey())) {
            return OperationResult.ok();
        }
        category.get().setColorKey(colorKey);
        saveCategoriesAndNotify();
        scheduleController.notifySchedulesChanged();
        return OperationResult.ok();
    }

    /** 삭제할 수 있는지 ("개인"은 불가) */
    public boolean canDelete(String categoryId) {
        return !Category.PERSONAL_ID.equals(categoryId) && categories.findById(categoryId).isPresent();
    }

    /** 이 카테고리에 속한 일정(Schedule) 수. 삭제 확인 문구에 쓴다 */
    public int countSchedules(String categoryId) {
        return (int) schedules.getAll().stream().filter(s -> s.getCategoryId().equals(categoryId)).count();
    }

    /**
     * 카테고리 추가. 이름은 앞뒤 공백을 지운 뒤 1~20자여야 하고, 대소문자를 구별하지 않고 이미 있는 이름이면 안 된다.
     * 색은 팔레트 순서대로 보아 다른 카테고리가 아직 쓰지 않은 첫 색이다. 12색을 모두 쓰고 있으면 새 id의 기본 색이다.
     *
     * @return 성공이면 ok(), 규칙을 어기면 이유가 든 실패 (저장하지 않음)
     */
    public OperationResult add(String name) {
        String error = checkName(name, null);
        if (error != null) {
            return OperationResult.fail(error);
        }
        String id = UUID.randomUUID().toString();
        categories.add(new Category(id, name.trim(), unusedColor(id)));
        saveCategoriesAndNotify();
        return OperationResult.ok();
    }

    /**
     * 카테고리 이름 변경. 규칙은 add와 같고, 자기 자신의 이름 그대로 저장하는 것은 허용한다.
     *
     * @return 성공이면 ok(), 카테고리가 없거나 규칙을 어기면 이유가 든 실패
     */
    public OperationResult rename(String categoryId, String newName) {
        Optional<Category> category = categories.findById(categoryId);
        if (category.isEmpty()) {
            return OperationResult.fail("없는 카테고리예요");
        }
        String error = checkName(newName, categoryId);
        if (error != null) {
            return OperationResult.fail(error);
        }
        category.get().setName(newName.trim());
        saveCategoriesAndNotify();
        return OperationResult.ok();
    }

    /**
     * 삭제. 남은 일정은 "개인"으로 옮기고 저장(F11)한 뒤 두 리스너(카테고리, 일정)에 모두 알린다.
     * 카테고리가 없거나 "개인"이면 바로 실패를 돌려준다.
     * 일정 파일을 먼저 저장하는 이유: 일정이 없는 카테고리를 가리키게 되는 일이 가장 위험해서이다.
     * 그 저장이 실패하면 옮긴 것을 되돌리고 카테고리도 지우지 않는다. 카테고리 파일 저장만 실패하면 삭제는 유지하고
     * 저장 실패만 알린다 (다음 실행 때 없는 카테고리의 일정은 "개인"으로 옮겨지므로 어긋나지 않는다).
     */
    public OperationResult delete(String categoryId) {
        if (Category.PERSONAL_ID.equals(categoryId)) {
            return OperationResult.fail("'개인' 카테고리는 지울 수 없어요");
        }
        Optional<Category> category = categories.findById(categoryId);
        if (category.isEmpty()) {
            return OperationResult.fail("없는 카테고리예요");
        }
        List<Schedule> moved = moveSchedules(categoryId, Category.PERSONAL_ID);
        if (!trySave(schedules::save, schedules::getWarnings)) {
            moved.forEach(s -> s.setCategoryId(categoryId));
            return OperationResult.fail("저장하지 못해서 카테고리를 지우지 않았어요");
        }
        categories.remove(category.get());
        trySave(categories::save, categories::getWarnings);
        scheduleController.clearHistory();
        scheduleController.notifySchedulesChanged();
        notifyCategoriesChanged();
        return OperationResult.ok();
    }

    /**
     * 이름을 검사한다. 비었거나 20자를 넘거나 이미 있으면 사용자에게 보여 줄 이유를, 괜찮으면 null을 돌려준다.
     *
     * @param selfId 이름을 바꾸는 자기 자신의 id (중복 검사에서 뺀다). 새로 추가할 때는 null
     */
    private String checkName(String name, String selfId) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            return "카테고리 이름을 입력해 주세요";
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            return "카테고리 이름은 " + MAX_NAME_LENGTH + "자까지 쓸 수 있어요";
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        boolean duplicate = categories.getAll().stream()
                .anyMatch(c -> !c.getId().equals(selfId) && c.getName().toLowerCase(Locale.ROOT).equals(lower));
        return duplicate ? "이미 있는 이름이에요" : null;
    }

    /** 팔레트 순서대로 아직 아무 카테고리도 쓰지 않은 첫 색. 모두 쓰고 있으면 newId의 기본 색 */
    private String unusedColor(String newId) {
        Set<String> used = new HashSet<>();
        categories.getAll().forEach(c -> used.add(c.getColorKey()));
        return CategoryPalette.KEYS.stream()
                .filter(key -> !used.contains(key))
                .findFirst()
                .orElse(CategoryPalette.legacyDefault(newId));
    }

    private void saveCategoriesAndNotify() {
        trySave(categories::save, categories::getWarnings);
        notifyCategoriesChanged();
    }

    /** fromId 카테고리의 일정들을 toId로 옮기고, 옮긴 일정들을 돌려준다 (저장이 실패하면 되돌리는 데 쓴다) */
    private List<Schedule> moveSchedules(String fromId, String toId) {
        List<Schedule> moved = new ArrayList<>();
        for (Schedule schedule : schedules.getAll()) {
            if (schedule.getCategoryId().equals(fromId)) {
                schedule.setCategoryId(toId);
                moved.add(schedule);
            }
        }
        return moved;
    }

    /**
     * 파일 저장. 실패하면 storageFailed로 알리고 false.
     * 저장이 끝난 뒤 저장소에 새로 생긴 경고(서버에 올리지 못함 등)도 한 번씩 알린다.
     */
    private boolean trySave(FileSave save, Supplier<List<String>> warnings) {
        List<String> before = List.copyOf(warnings.get());
        try {
            save.run();
        } catch (IOException e) {
            scheduleController.notifyStorageFailed(ScheduleController.saveFailedMessage(e));
            return false;
        }
        scheduleController.reportNewWarnings(before, warnings.get());
        return true;
    }

    private void notifyCategoriesChanged() {
        EventThread.run(() -> listeners.forEach(CategoryListener::categoriesChanged));
    }

    /** IOException을 던질 수 있는 저장 동작 */
    private interface FileSave {
        void run() throws IOException;
    }
}
