package proto.server;

import proto.entity.Category;
import proto.repository.CategoryStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * [저장소 repository] CSV 카테고리 저장소 앞에 두는 로컬 우선 장식자(decorator). 서버 대비 코드(현재 사용 안 함).
 * StoreFactory가 서버가 켜져 있을 때 CategoryRepository를 이 클래스로 감싼다 (CategoryStore를 구현하므로 CategoryController는 모른다).
 * 불러올 때는 GET /api/v1/categories 스냅샷을 시도해 검사를 통과하면 CSV를 그 내용으로 바꾸고, 실패하면 CSV를 그대로 두고 경고만 남긴다.
 * 스냅샷에 "개인"(id personal)이 없으면 이 컴퓨터 목록에만 덧붙이고, 덧붙인 뒤 이름이 겹치면 스냅샷을 받지 않는다.
 * 저장은 CSV를 먼저 쓰고 서버에 PUT 하며, PUT이 실패해도 그 파일은 남긴다. 서버가 안 돼도 이 컴퓨터의 데이터가 우선이기 때문이다.
 */
public final class SyncingCategoryStore implements CategoryStore {

    static final String OFFLINE_WARNING = "서버에 연결하지 못해 이 컴퓨터에 저장된 카테고리로 열었어요";
    static final String PUSH_WARNING = "서버에 카테고리를 올리지 못했어요. 이 컴퓨터에는 저장되어 있어요";

    static final String NAME_CONFLICT_WARNING = "서버의 카테고리 이름이 겹쳐서 이 컴퓨터에 저장된 카테고리로 열었어요";

    private static final String SAVE_WARNING = "서버에서 받은 카테고리를 이 컴퓨터에 저장하지 못해 원래 카테고리를 유지했어요";
    private static final String PATH = "/api/v1/categories";

    private final CategoryStore local;
    private final RemoteApiClient client;
    private final List<String> syncWarnings = new ArrayList<>();

    public SyncingCategoryStore(CategoryStore local, RemoteApiClient client) {
        this.local = local;
        this.client = client;
    }

    @Override
    public void loadOrSeed() {
        syncWarnings.clear();
        local.loadOrSeed();
        pull();
    }

    /**
     * CSV에 먼저 저장하고, 그다음 서버로 올린다. CSV 저장이 실패하면 IOException을 던지고 서버에는 올리지 않는다.
     * 서버에 올리지 못한 것은 이 컴퓨터에는 이미 저장됐으므로 예외가 아니라 경고({@link #PUSH_WARNING})로 남긴다.
     *
     * @throws IOException CSV 저장에 실패했을 때
     */
    @Override
    public void save() throws IOException {
        local.save();
        push();
    }

    /** 로컬 저장소의 경고에 서버와 관련된 경고를 더한 목록 (복사본) */
    @Override
    public List<String> getWarnings() {
        List<String> all = new ArrayList<>(local.getWarnings());
        all.addAll(syncWarnings);
        return List.copyOf(all);
    }

    @Override
    public List<Category> getAll() {
        return local.getAll();
    }

    @Override
    public Optional<Category> findById(String id) {
        return local.findById(id);
    }

    @Override
    public void add(Category category) {
        local.add(category);
    }

    @Override
    public void remove(Category category) {
        local.remove(category);
    }

    /** 서버 스냅샷을 받아 로컬을 바꾼다. 받은 값이 이상하거나(RuntimeException) 서버에 닿지 못하면 로컬을 그대로 두고 경고만 남긴다 */
    private void pull() {
        try {
            List<Category> remote = withPersonal(CategoryJsonMapper.readSnapshot(client.get(PATH)));
            if (hasDuplicateName(remote)) {
                warn(NAME_CONFLICT_WARNING);
                return;
            }
            replaceAndSave(remote);
        } catch (RemoteApiException | RuntimeException e) {
            warn(OFFLINE_WARNING);
        }
    }

    /** 서버 스냅샷에 삭제 불가인 "개인"이 없으면 로컬 목록에만 붙인다 */
    private static List<Category> withPersonal(List<Category> loaded) {
        if (loaded.stream().anyMatch(Category::isPersonal)) {
            return loaded;
        }
        List<Category> copy = new ArrayList<>(loaded);
        copy.add(new Category(Category.PERSONAL_ID, "개인"));
        return copy;
    }

    /**
     * 덧붙인 "개인"까지 포함해 대소문자를 무시한 이름이 겹치는지 본다.
     * CSV 저장소(CategoryRepository)처럼 "개인"의 이름을 먼저 지키므로, 같은 이름의 다른 분류가 있으면 겹침이다.
     */
    private static boolean hasDuplicateName(List<Category> categories) {
        List<String> seen = new ArrayList<>();
        for (Category category : categories) {
            String lower = category.getName().trim().toLowerCase(Locale.ROOT);
            if (seen.contains(lower)) {
                return true;
            }
            seen.add(lower);
        }
        return false;
    }

    /** 서버 내용으로 갈아끼운 뒤 CSV에 쓴다. 그 저장이 실패하면 메모리와 파일이 달라지지 않게 메모리를 원래 목록으로 되돌린다 */
    private void replaceAndSave(List<Category> remote) {
        List<Category> previous = new ArrayList<>(local.getAll());
        replaceWith(remote);
        try {
            local.save();
        } catch (IOException e) {
            replaceWith(previous);
            warn(SAVE_WARNING);
        }
    }

    private void replaceWith(List<Category> next) {
        new ArrayList<>(local.getAll()).forEach(local::remove);
        next.forEach(local::add);
    }

    private void push() {
        try {
            client.put(PATH, CategoryJsonMapper.writeSnapshot(local.getAll()));
            syncWarnings.remove(PUSH_WARNING);
        } catch (RemoteApiException | RuntimeException e) {
            warn(PUSH_WARNING);
        }
    }

    private void warn(String message) {
        if (!syncWarnings.contains(message)) {
            syncWarnings.add(message);
        }
    }
}
