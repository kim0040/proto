package proto.logic;

import proto.entity.Category;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.entity.Schedule;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * [논리 application logic] 처음 실행할 때 보여 줄 예시 일정 5개를 오늘 날짜 기준으로 만든다 (F11).
 * 오늘의 상태가 골고루 보이도록 지연·완료·예정이 섞여 있고, 메모에 쉼표와 줄바꿈이 들어간 일정도 있다.
 */
public final class SampleScheduleFactory {

    private SampleScheduleFactory() {
    }

    public static List<Schedule> create(LocalDate today) {
        Schedule teamMeeting = sample("팀플 회의", today, 18, 19, "team", "", RepeatRule.WEEKLY,
                ReminderOption.TEN_MINUTES);
        Schedule lecture = sample("알고리즘 수업", today, 9, 10, "class",
                "교재 3장, 연습문제 풀기\n다음 주 퀴즈 예정", RepeatRule.NONE, ReminderOption.NONE);
        lecture.setCompletedOn(today, true);
        Schedule overdue = sample("도서관 책 반납", today, 7, 8, Category.PERSONAL_ID, "",
                RepeatRule.NONE, ReminderOption.NONE);
        Schedule workout = sample("운동", today, 21, 22, Category.PERSONAL_ID, "", RepeatRule.NONE,
                ReminderOption.FIVE_MINUTES);
        Schedule report = sample("과제 제출", today.plusDays(1), 14, 15, "assignment", "보고서 PDF로 제출",
                RepeatRule.NONE, ReminderOption.THIRTY_MINUTES);
        return List.of(teamMeeting, lecture, overdue, workout, report);
    }

    private static Schedule sample(String title, LocalDate date, int startHour, int endHour,
                                   String categoryId, String memo, RepeatRule repeat,
                                   ReminderOption reminder) {
        return new Schedule(UUID.randomUUID().toString(), title, date, LocalTime.of(startHour, 0),
                LocalTime.of(endHour, 0), categoryId, memo, repeat, null, reminder);
    }
}
