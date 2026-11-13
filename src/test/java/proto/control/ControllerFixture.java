package proto.control;

import proto.TestData;
import proto.entity.Occurrence;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.repository.CategoryRepository;
import proto.repository.ScheduleRepository;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static proto.TestData.d;
import static proto.TestData.t;

/** controller 테스트용 준비물: 임시 폴더의 저장소, 움직이는 시계, 알림을 기록하는 리스너 */
final class ControllerFixture {

    final Path scheduleFile;
    final Path categoryFile;
    final TestData.MutableClock clock;
    final ScheduleRepository scheduleRepo;
    final CategoryRepository categoryRepo;
    final ScheduleController schedules;
    final CategoryController categories;
    final Recorder recorder = new Recorder();

    ControllerFixture(Path dir, String now) {
        scheduleFile = dir.resolve("schedules.csv");
        categoryFile = dir.resolve("categories.csv");
        clock = new TestData.MutableClock(now);
        scheduleRepo = new ScheduleRepository(scheduleFile);
        categoryRepo = new CategoryRepository(categoryFile);
        categoryRepo.loadOrSeed();
        schedules = new ScheduleController(scheduleRepo, categoryRepo, clock);
        categories = new CategoryController(categoryRepo, scheduleRepo, schedules);
        schedules.addListener(recorder);
    }

    /** 일정 변화를 세어 두는 리스너 */
    static final class Recorder implements ScheduleListener {
        int changed;
        int statesChanged;
        final List<String> storageErrors = new ArrayList<>();

        @Override
        public void schedulesChanged() {
            changed++;
        }

        @Override
        public void statesChanged() {
            statesChanged++;
        }

        @Override
        public void storageFailed(String message) {
            storageErrors.add(message);
        }
    }

    static ScheduleDraft draft(String title, String date, String start, String end) {
        return new ScheduleDraft(title, d(date), t(start), t(end), "class", "", RepeatRule.NONE, null,
                ReminderOption.NONE);
    }

    static ScheduleDraft weekly(String title, String date, String start, String end) {
        return new ScheduleDraft(title, d(date), t(start), t(end), "class", "", RepeatRule.WEEKLY, null,
                ReminderOption.NONE);
    }

    static ScheduleDraft withReminder(ScheduleDraft base, ReminderOption reminder) {
        return new ScheduleDraft(base.title(), base.date(), base.startTime(), base.endTime(), base.categoryId(),
                base.memo(), base.repeatRule(), base.repeatEndDate(), reminder);
    }

    Occurrence occurrenceOn(String title, String date) {
        LocalDate day = d(date);
        return schedules.getOccurrences(day, day, null, null).stream()
                .filter(o -> o.title().equals(title)).findFirst().orElseThrow();
    }

    /** "제목@날짜 시작" 형태로 줄여 본 결과 목록 (비교하기 쉽게) */
    List<String> summary(String from, String to) {
        return schedules.getOccurrences(d(from), d(to), null, null).stream()
                .map(o -> o.title() + "@" + o.date() + " " + o.startTime() + (o.completed() ? " 완료" : ""))
                .toList();
    }
}
