package proto;

import proto.entity.Occurrence;
import proto.entity.ReminderOption;
import proto.entity.RepeatRule;
import proto.entity.Schedule;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** 테스트에서 일정과 시계를 짧게 만들기 위한 도우미 */
public final class TestData {

    private TestData() {
    }

    public static LocalDate d(String iso) {
        return LocalDate.parse(iso);
    }

    public static LocalTime t(String hhmm) {
        return LocalTime.parse(hhmm);
    }

    /** 반복 없는 일정 */
    public static Schedule single(String id, String title, String date, String start, String end) {
        return new Schedule(id, title, d(date), t(start), t(end), "class", "", RepeatRule.NONE, null,
                ReminderOption.NONE);
    }

    /** 반복 일정 (repeatEnd가 null이면 끝없이 반복) */
    public static Schedule repeating(String id, String title, String date, String start, String end,
                                     RepeatRule rule, String repeatEnd) {
        return new Schedule(id, title, d(date), t(start), t(end), "class", "", rule,
                repeatEnd == null ? null : d(repeatEnd), ReminderOption.NONE);
    }

    public static Occurrence occ(String date, String start, String end) {
        return new Occurrence("id", d(date), "t", t(start), t(end), "class", "", RepeatRule.NONE,
                ReminderOption.NONE, false);
    }

    /** 테스트가 시각을 마음대로 옮길 수 있는 시계 (UTC) */
    public static final class MutableClock extends Clock {
        private LocalDateTime now;

        public MutableClock(String isoDateTime) {
            this.now = LocalDateTime.parse(isoDateTime);
        }

        public void set(String isoDateTime) {
            this.now = LocalDateTime.parse(isoDateTime);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.toInstant(ZoneOffset.UTC);
        }
    }
}
