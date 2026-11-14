package proto.logic;

import proto.entity.Category;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.entity.Schedule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * [논리 application logic] 처음 실행할 때 보여 줄 예시 일정 5개를 지금 시각 기준으로 만든다 (F11).
 * F11(자동 저장)의 첫 실행에서 쓴다. ScheduleRepository가 schedules.csv 파일이 없을 때 이 예시들을 넣고 바로 저장한다.
 * 카테고리 id("class", "team", "assignment")는 기본 카테고리 목록과 맞춰 둔 값이다.
 * 오늘의 상태가 골고루 보이도록 완료·지연·예정이 섞여 있고, 메모에 쉼표와 줄바꿈이 들어간 일정도 있다.
 */
public final class SampleScheduleFactory {

    /** 이 시각 범위(08:00 이상 20:00 미만)에서는 지금 시각 앞뒤로 예시를 놓는다 */
    private static final LocalTime RELATIVE_FROM = LocalTime.of(8, 0);
    private static final LocalTime RELATIVE_UNTIL = LocalTime.of(20, 0);
    private static final String LECTURE_MEMO = "교재 3장, 연습문제 풀기\n다음 주 퀴즈 예정";

    private SampleScheduleFactory() {
    }

    /**
     * 08:00~20:00 사이면 오늘 일정을 지금 시각 앞뒤로 놓는다: 끝나고 완료한 수업, 끝났지만 미완료(지연)인 일정,
     * 오늘 이따가 할 일정 2개(그중 매주 "팀플 회의"), 내일 일정 1개. 그 밖의 시각이면 고정 시각 예시를 쓴다.
     * 지금이 08:00 이전이거나 20:00 이후이면 "앞뒤로 놓기"가 어색하므로(전날이나 다음 날로 넘어갈 수 있다) 아래의 고정 시각 예시로 돌린다.
     */
    public static List<Schedule> create(LocalDateTime now) {
        LocalTime time = now.toLocalTime();
        if (time.isBefore(RELATIVE_FROM) || !time.isBefore(RELATIVE_UNTIL)) {
            return create(now.toLocalDate());
        }
        LocalDate today = now.toLocalDate();
        // 10분 단위로 내림한 기준 시각: base ≤ now < base + 10분
        LocalTime base = time.withSecond(0).withNano(0).minusMinutes(time.getMinute() % 10);
        Schedule lecture = sample("알고리즘 수업", today, base.minusHours(2), base.minusHours(1), "class",
                LECTURE_MEMO, RepeatRule.NONE, ReminderOption.NONE);
        lecture.setCompletedOn(today, true);
        Schedule overdue = sample("도서관 책 반납", today, base.minusHours(1), base.minusMinutes(30),
                Category.PERSONAL_ID, "", RepeatRule.NONE, ReminderOption.NONE);
        Schedule workout = sample("운동", today, base.plusHours(1), base.plusHours(2), Category.PERSONAL_ID, "",
                RepeatRule.NONE, ReminderOption.FIVE_MINUTES);
        Schedule teamMeeting = sample("팀플 회의", today, base.plusHours(2), base.plusHours(3), "team", "",
                RepeatRule.WEEKLY, ReminderOption.TEN_MINUTES);
        return List.of(teamMeeting, lecture, overdue, workout, tomorrowReport(today));
    }

    /** 고정 시각 예시 (07:00 반납, 09:00 완료한 수업, 18:00 매주 팀플 회의, 21:00 운동, 내일 14:00 과제) */
    public static List<Schedule> create(LocalDate today) {
        Schedule teamMeeting = sample("팀플 회의", today, hour(18), hour(19), "team", "", RepeatRule.WEEKLY,
                ReminderOption.TEN_MINUTES);
        Schedule lecture = sample("알고리즘 수업", today, hour(9), hour(10), "class", LECTURE_MEMO,
                RepeatRule.NONE, ReminderOption.NONE);
        lecture.setCompletedOn(today, true);
        Schedule overdue = sample("도서관 책 반납", today, hour(7), hour(8), Category.PERSONAL_ID, "",
                RepeatRule.NONE, ReminderOption.NONE);
        Schedule workout = sample("운동", today, hour(21), hour(22), Category.PERSONAL_ID, "", RepeatRule.NONE,
                ReminderOption.FIVE_MINUTES);
        return List.of(teamMeeting, lecture, overdue, workout, tomorrowReport(today));
    }

    private static Schedule tomorrowReport(LocalDate today) {
        return sample("과제 제출", today.plusDays(1), hour(14), hour(15), "assignment", "보고서 PDF로 제출",
                RepeatRule.NONE, ReminderOption.THIRTY_MINUTES);
    }

    private static LocalTime hour(int h) {
        return LocalTime.of(h, 0);
    }

    private static Schedule sample(String title, LocalDate date, LocalTime start, LocalTime end,
                                   String categoryId, String memo, RepeatRule repeat,
                                   ReminderOption reminder) {
        return new Schedule(UUID.randomUUID().toString(), title, date, start, end, categoryId, memo, repeat,
                null, reminder);
    }
}
