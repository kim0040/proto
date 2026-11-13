package proto.repository;

import proto.entity.Schedule;
import proto.logic.SampleScheduleFactory;
import proto.logic.ScheduleValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * [저장소 repository] 일정(Schedule) 목록을 메모리에 들고 있고, CSV 파일(data/schedules.csv)로 저장·불러온다 (F11).
 * 읽을 수 없는 줄(형식 오류, F1 규칙 위반)은 건너뛰고 경고로 남긴다. 파일 경로는 생성자로 받는다.
 * 불러오면서 문제가 하나라도 생기면 원본을 같은 폴더에 백업한 뒤에야 덮어쓸 수 있고,
 * 백업마저 실패하면 이 파일에는 저장하지 않는다 (읽지 못한 기록이 조용히 사라지지 않게).
 */
public class ScheduleRepository implements ScheduleStore {

    private final Path file;
    private final Clock clock;
    private final List<Schedule> schedules = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private boolean saveBlocked;

    public ScheduleRepository(Path file) {
        this(file, Clock.systemDefaultZone());
    }

    /** @param clock 백업 파일 이름의 시각에 쓴다 */
    public ScheduleRepository(Path file, Clock clock) {
        this.file = file;
        this.clock = clock;
    }

    public boolean fileExists() {
        return Files.exists(file);
    }

    /**
     * 파일에서 다시 읽는다. 파일이 없으면 빈 목록. 읽지 못한 줄은 건너뛰고 {@link #getWarnings()}에 남긴다.
     * 파일 전체를 읽지 못했거나 CSV 구조가 깨졌으면 아무 일정도 불러오지 않는다.
     * 문제가 있으면 원본을 백업하고, 경고 문장에 백업 파일 이름을 넣는다.
     */
    @Override
    public void load() {
        warnings.clear();
        saveBlocked = false;
        if (!fileExists()) {
            schedules.clear();
            return;
        }
        LoadProblems problems = new LoadProblems();
        List<Schedule> loaded = readFile(problems);
        schedules.clear();
        schedules.addAll(loaded);
        saveBlocked = !problems.preserveOriginal(file, LocalDateTime.now(clock), warnings);
    }

    /** 파일이 있으면 읽고, 없으면 예시 일정 5개(오늘 고정 시각)를 만들어 바로 저장한다 (F11) */
    @Override
    public void loadOrSeed(LocalDate today) {
        seedOrLoad(() -> SampleScheduleFactory.create(today));
    }

    /** 파일이 있으면 읽고, 없으면 지금 시각 기준 예시 일정 5개를 만들어 바로 저장한다 (F11) */
    @Override
    public void loadOrSeed(LocalDateTime now) {
        seedOrLoad(() -> SampleScheduleFactory.create(now));
    }

    private void seedOrLoad(Supplier<List<Schedule>> samples) {
        if (fileExists()) {
            load();
            return;
        }
        schedules.clear();
        warnings.clear();
        saveBlocked = false;
        schedules.addAll(samples.get());
        try {
            save();
        } catch (IOException e) {
            warnings.add("예시 일정을 저장하지 못했어요: " + e.getMessage());
        }
    }

    /** 전체를 파일에 쓴다. 불러올 때 원본을 백업하지 못했으면 덮어쓰지 않고 IOException을 던진다 */
    @Override
    public void save() throws IOException {
        if (saveBlocked) {
            throw LoadProblems.saveBlocked(file);
        }
        List<List<String>> rows = new ArrayList<>();
        rows.add(ScheduleCsvMapper.HEADER);
        for (Schedule s : schedules) {
            rows.add(ScheduleCsvMapper.toRow(s));
        }
        CsvFileStore.writeAtomically(file, CsvCodec.encode(rows));
    }

    /** 불러올 때 생긴 경고들 (한국어 문장). 없으면 빈 목록 */
    @Override
    public List<String> getWarnings() {
        return List.copyOf(warnings);
    }

    @Override
    public List<Schedule> getAll() {
        return Collections.unmodifiableList(schedules);
    }

    @Override
    public Optional<Schedule> findById(String id) {
        return schedules.stream().filter(s -> s.getId().equals(id)).findFirst();
    }

    @Override
    public void add(Schedule schedule) {
        schedules.add(schedule);
    }

    @Override
    public void remove(Schedule schedule) {
        schedules.remove(schedule);
    }

    /** 파일 전체를 임시 목록으로 읽는다. 읽기 실패·구조 오류면 빈 목록 */
    private List<Schedule> readFile(LoadProblems problems) {
        List<List<String>> rows;
        try {
            rows = CsvCodec.parse(CsvFileStore.read(file));
        } catch (CsvSyntaxException e) {
            problems.add("일정 파일 " + e.lineNumber() + "번째 줄의 따옴표가 맞지 않아 파일 전체를 읽지 못했어요");
            return List.of();
        } catch (IOException e) {
            problems.add("일정 파일을 읽지 못했어요: " + e.getMessage());
            return List.of();
        }
        List<Schedule> loaded = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            loadRow(rows.get(i), i + 1, loaded, problems);
        }
        return loaded;
    }

    /** 헤더 줄은 건너뛰고, 잘못된 행(형식 오류, F1 규칙 위반, id 중복)은 문제로 남긴다. lineNo는 1부터 센 행 번호 */
    private static void loadRow(List<String> row, int lineNo, List<Schedule> loaded, LoadProblems problems) {
        if (lineNo == 1 && !row.isEmpty() && row.get(0).equals(ScheduleCsvMapper.HEADER.get(0))) {
            return;
        }
        try {
            Schedule s = ScheduleCsvMapper.fromRow(row);
            List<String> errors = ScheduleValidator.validate(s);
            if (!errors.isEmpty()) {
                throw new IllegalArgumentException(String.join(", ", errors));
            }
            if (loaded.stream().anyMatch(other -> other.getId().equals(s.getId()))) {
                throw new IllegalArgumentException("id가 겹쳐요");
            }
            loaded.add(s);
        } catch (RuntimeException e) {
            problems.add("일정 파일 " + lineNo + "번째 줄을 읽지 못해 건너뛰었어요 (" + e.getMessage() + ")");
        }
    }
}
