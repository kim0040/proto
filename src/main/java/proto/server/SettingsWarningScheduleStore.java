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
 * [저장소 repository] 서버 설정이 잘못돼 서버를 끈 이유를 일정 저장소의 경고 목록에 실어 나른다. 서버 대비 코드(현재 사용 안 함).
 * StoreFactory가 설정이 "잘못돼서" 꺼졌을 때만 CSV 저장소를 이 클래스로 감싼다.
 * ScheduleStore의 모든 메서드를 그대로 local에 넘기고(위임), getWarnings() 끝에 설정 경고 한 문장만 더한다.
 * 그러면 시작할 때 reportLoadWarnings가 다른 불러오기 경고와 같은 길(storageFailed)로 사용자에게 알린다.
 */
public final class SettingsWarningScheduleStore implements ScheduleStore {

    private final ScheduleStore local;
    private final String settingsWarning;

    public SettingsWarningScheduleStore(ScheduleStore local, String settingsWarning) {
        this.local = local;
        this.settingsWarning = settingsWarning;
    }

    @Override
    public void load() {
        local.load();
    }

    @Override
    public void loadOrSeed(LocalDate today) {
        local.loadOrSeed(today);
    }

    @Override
    public void loadOrSeed(LocalDateTime now) {
        local.loadOrSeed(now);
    }

    @Override
    public void save() throws IOException {
        local.save();
    }

    @Override
    public List<String> getWarnings() {
        List<String> all = new ArrayList<>(local.getWarnings());
        all.add(settingsWarning);
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
