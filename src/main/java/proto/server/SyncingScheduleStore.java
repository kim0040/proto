package proto.server;

import proto.entity.Schedule;
import proto.repository.ScheduleStore;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * [서버 대비 — 현재 사용 안 함] CSV 일정 저장소 앞에 두는 로컬 우선 장식자다.
 * [저장소 repository] 불러올 때는 서버 스냅샷(GET /api/v1/schedules)을 먼저 시도하고,
 * 성공하면 메모리와 CSV를 그 내용으로 바꾼다. 실패하면 이 컴퓨터의 CSV를 그대로 두고 경고만 남긴다.
 * StoreFactory가 서버가 켜져 있을 때 ScheduleRepository를 이 클래스로 감싼다 (ScheduleStore를 구현하므로 ScheduleController는 모른다).
 * 데이터의 기준은 언제나 이 컴퓨터의 CSV이다. 서버는 "불러올 때 한 번 받아 오고, 저장할 때마다 통째로 올리는" 보조 저장 장소일 뿐이다.
 * 저장은 항상 CSV를 먼저 쓰고, 그다음 서버에 통째로 PUT 한다. PUT이 실패해도 CSV는 지우지 않는다.
 */
public final class SyncingScheduleStore implements ScheduleStore {

    /** 서버에 닿지 못했을 때 getWarnings()에 넣는 문장 */
    static final String OFFLINE_WARNING = "서버에 연결하지 못해 이 컴퓨터에 저장된 일정으로 열었어요";

    /** 서버로 올리지 못했을 때 getWarnings()에 넣는 문장. CSV 저장은 이미 끝난 뒤다 */
    static final String PUSH_WARNING = "서버에 일정을 올리지 못했어요. 이 컴퓨터에는 저장되어 있어요";

    private static final String SAVE_WARNING = "서버에서 받은 일정을 이 컴퓨터에 저장하지 못해 원래 일정을 유지했어요";
    private static final String PATH = "/api/v1/schedules";

    private final ScheduleStore local;
    private final RemoteApiClient client;
    private final List<String> syncWarnings = new ArrayList<>();

    public SyncingScheduleStore(ScheduleStore local, RemoteApiClient client) {
        this.local = local;
        this.client = client;
    }

    @Override
    public void load() {
        reload(local::load);
    }

    @Override
    public void loadOrSeed(LocalDate today) {
        reload(() -> local.loadOrSeed(today));
    }

    @Override
    public void loadOrSeed(LocalDateTime now) {
        reload(() -> local.loadOrSeed(now));
    }

    private void reload(Runnable loadLocal) {
        syncWarnings.clear();
        loadLocal.run();
        pull();
    }

    /** 서버 스냅샷을 받아 로컬을 바꾼다. 받은 값이 이상하거나(RuntimeException) 서버에 닿지 못하면 로컬을 그대로 두고 경고만 남긴다. */
    private void pull() {
        try {
            replaceAndSave(ScheduleJsonMapper.readSnapshot(client.get(PATH)));
        } catch (RemoteApiException | RuntimeException e) {
            warn(OFFLINE_WARNING);
        }
    }

    /** 서버 내용으로 갈아끼운 뒤 CSV에 쓴다. 그 저장이 실패하면 메모리를 원래 목록으로 되돌린다 */
    private void replaceAndSave(List<Schedule> remote) {
        List<Schedule> previous = new ArrayList<>(local.getAll());
        replaceWith(remote);
        try {
            local.save();
        } catch (IOException e) {
            replaceWith(previous);
            warn(SAVE_WARNING);
        }
    }

    private void replaceWith(List<Schedule> next) {
        new ArrayList<>(local.getAll()).forEach(local::remove);
        next.forEach(local::add);
    }

    /**
     * CSV에 먼저 저장하고, 그다음 서버로 올린다. CSV 저장이 실패하면 IOException을 던지고 서버에는 올리지 않는다.
     * 서버에 올리지 못한 것은 예외가 아니라 경고({@link #PUSH_WARNING})로 남긴다. 이 컴퓨터에는 이미 저장됐기 때문이다.
     *
     * @throws IOException CSV 저장에 실패했을 때
     */
    @Override
    public void save() throws IOException {
        local.save();
        push();
    }

    private void push() {
        try {
            client.put(PATH, ScheduleJsonMapper.writeSnapshot(local.getAll()));
            syncWarnings.remove(PUSH_WARNING);
        } catch (RemoteApiException | RuntimeException e) {
            warn(PUSH_WARNING);
        }
    }

    /** 같은 경고를 중복해서 쌓지 않는다. 저장할 때마다 서버가 실패해도 경고가 하나만 남게 하려는 것이다. */
    private void warn(String message) {
        if (!syncWarnings.contains(message)) {
            syncWarnings.add(message);
        }
    }

    /** 로컬 저장소의 경고에 서버와 관련된 경고를 더한 목록 (복사본). */
    @Override
    public List<String> getWarnings() {
        List<String> all = new ArrayList<>(local.getWarnings());
        all.addAll(syncWarnings);
        return List.copyOf(all);
    }

    @Override
    public List<Schedule> getAll() {
        return local.getAll();
    }

    @Override
    public Optional<Schedule> findById(String id) {
        return local.findById(id);
    }

    @Override
    public void add(Schedule schedule) {
        local.add(schedule);
    }

    @Override
    public void remove(Schedule schedule) {
        local.remove(schedule);
    }
}
