package proto.server;

import proto.entity.Category;
import proto.repository.CategoryStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * [서버 대비 — 현재 사용 안 함] CSV 카테고리 저장소 앞에 두는 로컬 우선 장식자다.
 * [저장소 repository] 불러올 때는 GET /api/v1/categories 스냅샷을 시도하고, 성공하면 CSV를 그 내용으로 바꾼다.
 * 스냅샷에 "개인"(id personal)이 없으면 이 컴퓨터 목록에만 덧붙인다. 실패하면 CSV를 그대로 두고 경고만 남긴다.
 * 저장은 CSV를 먼저 쓰고, PUT이 실패해도 그 파일은 남긴다.
 */
public final class SyncingCategoryStore implements CategoryStore {

    static final String OFFLINE_WARNING = "서버에 연결하지 못해 이 컴퓨터에 저장된 카테고리로 열었어요";
    static final String PUSH_WARNING = "서버에 카테고리를 올리지 못했어요. 이 컴퓨터에는 저장되어 있어요";

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

    private void pull() {
        try {
            replaceAndSave(withPersonal(CategoryJsonMapper.readSnapshot(client.get(PATH))));
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

    @Override
    public void save() throws IOException {
        local.save();
        push();
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
}
