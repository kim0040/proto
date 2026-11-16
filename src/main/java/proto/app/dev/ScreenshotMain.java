package proto.app.dev;

import proto.control.CategoryController;
import proto.control.ReminderTimer;
import proto.control.ScheduleController;
import proto.control.ScheduleDraft;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.repository.CategoryRepository;
import proto.repository.ScheduleRepository;
import proto.ui.dev.ScreenshotTool;

import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * [애플리케이션 app] 개발용 캡처 실행기: 임시 폴더와 고정 시계(목요일 14:20)로 저장소·controller를 만들고 예시 일정을 더 채운 뒤
 * {@link ScreenshotTool#capture}를 부른다. 화면(ui) 패키지가 저장소를 만들지 않도록 여기(app)에 둔다.
 * 앱 실행에는 쓰이지 않는다 (Gradle screenshots 작업이 호출).
 */
public final class ScreenshotMain {

    private ScreenshotMain() {
    }

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "screenshots");
        Path data = Files.createTempDirectory("proto-shots");
        Clock clock = Clock.fixed(LocalDateTime.of(2026, 11, 12, 14, 20).atZone(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
        ScheduleRepository scheduleRepo = new ScheduleRepository(data.resolve("schedules.csv"));
        CategoryRepository categoryRepo = new CategoryRepository(data.resolve("categories.csv"));
        categoryRepo.loadOrSeed();
        scheduleRepo.loadOrSeed(LocalDate.now(clock));
        ScheduleController schedules = new ScheduleController(scheduleRepo, categoryRepo, clock);
        CategoryController categories = new CategoryController(categoryRepo, scheduleRepo, schedules);
        ReminderTimer reminders = new ReminderTimer(schedules, clock);
        SwingUtilities.invokeAndWait(() -> seed(schedules, LocalDate.now(clock)));
        ScreenshotTool.capture(schedules, categories, reminders, out);
        System.out.println("저장했어요: " + out.toAbsolutePath());
        System.exit(0);
    }

    private static void seed(ScheduleController c, LocalDate today) {
        add(c, "디자인 패턴 수업", today, 10, 30, 12, 0, "class", "싱글턴, 팩토리 복습", RepeatRule.WEEKLY);
        add(c, "점심 약속", today, 12, 30, 13, 30, "personal", "", RepeatRule.NONE);
        add(c, "스터디 모임", today, 14, 0, 15, 30, "team", "발표 자료 최종 점검, 역할 나누기", RepeatRule.NONE);
        add(c, "소프트웨어 설계 과제 제출 준비", today, 16, 0, 17, 30, "assignment", "UML 클래스 다이어그램 작성", RepeatRule.NONE);
        for (int i = -3; i <= 10; i += 1) {
            LocalDate day = today.plusDays(i);
            if (i % 3 == 0 && !day.equals(today)) {
                add(c, "자료구조 수업", day, 9, 0, 10, 30, "class", "", RepeatRule.NONE);
            }
            if (i % 4 == 1) {
                add(c, "과제 마감", day, 23, 0, 23, 30, "assignment", "", RepeatRule.NONE);
            }
        }
        for (int k = 0; k < 4; k++) {
            add(c, "팀플 자료 조사 " + (k + 1), today.plusDays(5), 9 + k * 2, 0, 10 + k * 2, 0, "team", "", RepeatRule.NONE);
        }
    }

    private static void add(ScheduleController c, String title, LocalDate date, int startHour, int startMinute,
                            int endHour, int endMinute, String categoryId, String memo, RepeatRule repeat) {
        c.create(new ScheduleDraft(title, date, LocalTime.of(startHour, startMinute), LocalTime.of(endHour, endMinute),
                categoryId, memo, repeat, null, ReminderOption.NONE));
    }
}
