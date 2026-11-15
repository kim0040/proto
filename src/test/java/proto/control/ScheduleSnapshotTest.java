package proto.control;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import proto.entity.Occurrence;
import proto.entity.Schedule;
import proto.repository.ScheduleRepository;
import proto.repository.ScheduleStore;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static proto.TestData.d;
import static proto.control.ControllerFixture.draft;

/** 되돌릴 때 저장소에는 달라진 일정만 빼고 더하는지 (통째로 비우고 다시 넣지 않는지) */
class ScheduleSnapshotTest {

    @TempDir
    Path dir;
    ControllerFixture f;
    CountingStore store;
    ScheduleController c;

    @BeforeEach
    void setUp() {
        f = new ControllerFixture(dir, "2026-11-09T10:00");
        store = new CountingStore(new ScheduleRepository(dir.resolve("counted.csv")));
        c = new ScheduleController(store, f.categoryRepo, f.clock);
        for (String title : List.of("a", "b", "c")) {
            assertTrue(c.create(draft(title, "2026-11-10", "09:00", "10:00")).success());
        }
        store.reset();
    }

    private Occurrence on10(String title) {
        return c.getOccurrences(d("2026-11-10"), d("2026-11-10"), null, null).stream()
                .filter(o -> o.title().equals(title)).findFirst().orElseThrow();
    }

    @Test
    void undoOfOneChangedScheduleReplacesOnlyThatOne() {
        c.setCompleted(on10("b"), true);
        store.reset();

        c.undo();

        assertEquals(List.of("b"), store.removed);
        assertEquals(List.of("b"), store.added);
        assertEquals(3, store.getAll().size());
    }

    @Test
    void undoOfCreateOnlyRemovesAndUndoOfDeleteOnlyAdds() {
        c.undo(); // "c" 등록 되돌리기
        assertEquals(List.of("c"), store.removed);
        assertEquals(List.of(), store.added);

        c.redo();
        c.delete(on10("a"), EditScope.THIS_ONLY);
        store.reset();
        c.undo();
        assertEquals(List.of(), store.removed);
        assertEquals(List.of("a"), store.added);
    }

    @Test
    void restoringTheCurrentStateTouchesNothing() {
        ScheduleSnapshot.of(store).restoreInto(store);
        assertEquals(List.of(), store.removed);
        assertEquals(List.of(), store.added);
    }

    @Test
    void sameValuesComparesEveryFieldAndDateSets() {
        Schedule a = store.getAll().get(0);
        Schedule copy = a.copy();
        assertTrue(ScheduleSnapshot.sameValues(a, copy));
        copy.excludeDate(LocalDate.of(2026, 11, 10));
        assertFalse(ScheduleSnapshot.sameValues(a, copy));
        Schedule renamed = a.copy();
        renamed.setMemo("다른 메모");
        assertFalse(ScheduleSnapshot.sameValues(a, renamed));
    }

    /** add/remove 호출을 제목으로 기록하는 저장소 (실제 일은 CSV 저장소에 맡긴다) */
    private static final class CountingStore implements ScheduleStore {
        private final ScheduleStore inner;
        final List<String> added = new ArrayList<>();
        final List<String> removed = new ArrayList<>();

        CountingStore(ScheduleStore inner) {
            this.inner = inner;
        }

        void reset() {
            added.clear();
            removed.clear();
        }

        @Override public void load() { inner.load(); }
        @Override public void loadOrSeed(LocalDate today) { inner.loadOrSeed(today); }
        @Override public void loadOrSeed(LocalDateTime now) { inner.loadOrSeed(now); }
        @Override public void save() throws IOException { inner.save(); }
        @Override public List<String> getWarnings() { return inner.getWarnings(); }
        @Override public List<Schedule> getAll() { return inner.getAll(); }
        @Override public Optional<Schedule> findById(String id) { return inner.findById(id); }

        @Override
        public void add(Schedule schedule) {
            added.add(schedule.getTitle());
            inner.add(schedule);
        }

        @Override
        public void remove(Schedule schedule) {
            removed.add(schedule.getTitle());
            inner.remove(schedule);
        }
    }
}
