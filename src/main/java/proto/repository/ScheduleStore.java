package proto.repository;

import proto.entity.Schedule;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * [저장소 repository] 일정 목록을 읽고 고치고 저장하는 통로.
 * 오늘은 {@link ScheduleRepository}(CSV)가 이 역할을 하고, controller와 Main은 구현 클래스가 아니라 이 인터페이스만 본다.
 * 서버 저장소가 나중에 같은 메소드를 구현해도 화면과 controller는 바꾸지 않는다.
 */
public interface ScheduleStore {

    /**
     * 저장소에서 다시 읽는다. 저장된 일정이 없으면 빈 목록.
     * 읽지 못한 내용은 {@link #getWarnings()}에 남긴다.
     */
    void load();

    /** 저장된 일정이 있으면 읽고, 없으면 예시 일정 5개(오늘 고정 시각)를 만들어 바로 저장한다 */
    void loadOrSeed(LocalDate today);

    /** 저장된 일정이 있으면 읽고, 없으면 지금 시각 기준 예시 일정 5개를 만들어 바로 저장한다 */
    void loadOrSeed(LocalDateTime now);

    /** 메모리에 있는 일정 전체를 저장한다 */
    void save() throws IOException;

    /** 불러오거나 저장할 때 생긴 경고 (한국어 문장). 없으면 빈 목록 */
    List<String> getWarnings();

    /** 메모리에 있는 일정. 고칠 수 없는 목록 */
    List<Schedule> getAll();

    Optional<Schedule> findById(String id);

    void add(Schedule schedule);

    void remove(Schedule schedule);
}
