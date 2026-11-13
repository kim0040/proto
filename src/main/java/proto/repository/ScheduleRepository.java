package proto.repository;

import proto.entity.Schedule;
import proto.logic.SampleScheduleFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * [저장소 repository] 일정(Schedule) 목록을 메모리에 들고 있고, CSV 파일(data/schedules.csv)로 저장·불러온다 (F11).
 * 읽을 수 없는 줄은 건너뛰고 경고로 남긴다. 파일 경로는 생성자로 받는다.
 */
public class ScheduleRepository {

    private final Path file;
    private final List<Schedule> schedules = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public ScheduleRepository(Path file) {
        this.file = file;
    }

    public boolean fileExists() {
        return Files.exists(file);
    }

    /** 파일에서 다시 읽는다. 파일이 없으면 빈 목록. 읽지 못한 줄은 건너뛰고 {@link #getWarnings()}에 남긴다 */
    public void load() {
        schedules.clear();
        warnings.clear();
        if (!fileExists()) {
            return;
        }
        try {
            List<List<String>> rows = CsvCodec.parse(CsvFileStore.read(file));
            for (int i = 0; i < rows.size(); i++) {
                loadRow(rows.get(i), i + 1);
            }
        } catch (IOException e) {
            warnings.add("일정 파일을 읽지 못했어요: " + e.getMessage());
        }
    }

    /** 파일이 있으면 읽고, 없으면 예시 일정 5개를 만들어 바로 저장한다 (F11) */
    public void loadOrSeed(LocalDate today) {
        if (fileExists()) {
            load();
            return;
        }
        schedules.clear();
        warnings.clear();
        schedules.addAll(SampleScheduleFactory.create(today));
        try {
            save();
        } catch (IOException e) {
            warnings.add("예시 일정을 저장하지 못했어요: " + e.getMessage());
        }
    }

    public void save() throws IOException {
        List<List<String>> rows = new ArrayList<>();
        rows.add(ScheduleCsvMapper.HEADER);
        for (Schedule s : schedules) {
            rows.add(ScheduleCsvMapper.toRow(s));
        }
        CsvFileStore.writeAtomically(file, CsvCodec.encode(rows));
    }

    /** 불러올 때 생긴 경고들 (한국어 문장). 없으면 빈 목록 */
    public List<String> getWarnings() {
        return List.copyOf(warnings);
    }

    public List<Schedule> getAll() {
        return Collections.unmodifiableList(schedules);
    }

    public Optional<Schedule> findById(String id) {
        return schedules.stream().filter(s -> s.getId().equals(id)).findFirst();
    }

    public void add(Schedule schedule) {
        schedules.add(schedule);
    }

    public void remove(Schedule schedule) {
        schedules.remove(schedule);
    }

    /** 헤더 줄은 건너뛰고, 잘못된 행은 경고만 남긴다. lineNo는 1부터 센 행 번호 */
    private void loadRow(List<String> row, int lineNo) {
        if (lineNo == 1 && !row.isEmpty() && row.get(0).equals(ScheduleCsvMapper.HEADER.get(0))) {
            return;
        }
        try {
            Schedule s = ScheduleCsvMapper.fromRow(row);
            if (findById(s.getId()).isPresent()) {
                throw new IllegalArgumentException("id가 겹쳐요");
            }
            schedules.add(s);
        } catch (RuntimeException e) {
            warnings.add("일정 파일의 " + lineNo + "번째 줄을 읽지 못해 건너뛰었어요 (" + e.getMessage() + ")");
        }
    }
}
