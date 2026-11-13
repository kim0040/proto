package proto.control;

import proto.entity.Category;
import proto.entity.Schedule;
import proto.repository.CategoryRepository;
import proto.repository.CategoryStore;
import proto.repository.ScheduleStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * [제어 control] 카테고리 추가·이름 변경·삭제를 처리한다 (F7).
 * 삭제하면 그 카테고리의 일정은 "개인"으로 옮기고, 카테고리와 일정 리스너 양쪽에 알린다.
 *
 * <p>모든 public 메소드는 Swing 이벤트 스레드(EDT)에서 불러야 한다 (화면이 이미 EDT에서 부른다).</p>
 */
public class CategoryController {

    private static final int MAX_NAME_LENGTH = CategoryRepository.MAX_NAME_LENGTH;
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

    public void addListener(CategoryListener listener) {
        listeners.add(listener);
    }

    public void removeListener(CategoryListener listener) {
        listeners.remove(listener);
    }

    /** 카테고리 목록. 기본 4개(수업, 과제, 팀플, 개인)가 앞, 추가한 것은 추가한 순서대로 뒤 */
    public List<Category> getCategories() {
        return List.copyOf(categories.getAll());
    }

    /** id의 이름. 없는 id면 "개인" 이름 */
    public String nameOf(String categoryId) {
        return categories.findById(categoryId)
                .or(() -> categories.findById(Category.PERSONAL_ID))
                .map(Category::getName)
                .orElse(PERSONAL_NAME);
    }

    /** 삭제할 수 있는지 ("개인"은 불가) */
    public boolean canDelete(String categoryId) {
        return !Category.PERSONAL_ID.equals(categoryId) && categories.findById(categoryId).isPresent();
    }

    /** 이 카테고리에 속한 일정(Schedule) 수. 삭제 확인 문구에 쓴다 */
    public int countSchedules(String categoryId) {
        return (int) schedules.getAll().stream().filter(s -> s.getCategoryId().equals(categoryId)).count();
    }

    /** 추가. 이름은 앞뒤 공백 제거 후 1~20자, 중복 금지(대소문자 무시) */
    public OperationResult add(String name) {
        String error = checkName(name, null);
        if (error != null) {
            return OperationResult.fail(error);
        }
        categories.add(new Category(UUID.randomUUID().toString(), name.trim()));
        saveCategoriesAndNotify();
        return OperationResult.ok();
    }

    /** 이름 변경. 규칙은 add와 같음 (자기 이름 그대로는 허용) */
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
     * 삭제. 남은 일정은 "개인"으로 옮기고 저장(F11), 두 리스너(카테고리, 일정)에 모두 알린다.
     * 일정 파일을 먼저 저장하고, 그 저장이 실패하면 옮긴 것을 되돌리고 카테고리도 지우지 않는다.
     * 일정 저장 뒤 카테고리 파일 저장만 실패하면 삭제는 유지하고 저장 실패만 알린다
     * (다음 실행 때 없는 카테고리의 일정은 "개인"으로 옮겨지므로 어긋나지 않는다).
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
        if (!trySave(schedules::save)) {
            moved.forEach(s -> s.setCategoryId(categoryId));
            return OperationResult.fail("저장하지 못해서 카테고리를 지우지 않았어요");
        }
        categories.remove(category.get());
        trySave(categories::save);
        scheduleController.notifySchedulesChanged();
        notifyCategoriesChanged();
        return OperationResult.ok();
    }

    /** 규칙을 어기면 이유, 괜찮으면 null. selfId는 이름을 바꾸는 자기 자신(중복 검사에서 뺀다) */
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

    private void saveCategoriesAndNotify() {
        trySave(categories::save);
        notifyCategoriesChanged();
    }

    /** 이 카테고리의 일정들을 to로 옮기고, 옮긴 일정들을 돌려준다 (되돌릴 때 쓴다) */
    private List<Schedule> moveSchedules(String fromId, String toId) {
        List<Schedule> moved = new ArrayList<>();
        for (Schedule s : schedules.getAll()) {
            if (s.getCategoryId().equals(fromId)) {
                s.setCategoryId(toId);
                moved.add(s);
            }
        }
        return moved;
    }

    /** 파일 저장. 실패하면 storageFailed로 알리고 false */
    private boolean trySave(FileSave save) {
        try {
            save.run();
            return true;
        } catch (IOException e) {
            scheduleController.notifyStorageFailed(ScheduleController.saveFailedMessage(e));
            return false;
        }
    }

    /** IOException을 던질 수 있는 저장 동작 */
    private interface FileSave {
        void run() throws IOException;
    }

    private void notifyCategoriesChanged() {
        EventThread.run(() -> listeners.forEach(CategoryListener::categoriesChanged));
    }
}
