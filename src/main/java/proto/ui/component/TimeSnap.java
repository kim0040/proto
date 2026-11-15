package proto.ui.component;

import java.time.LocalTime;

/**
 * [경계 boundary] 시간표 끌기에서 쓰는 분 단위 계산 모음 (15분 맞춤, 하루 안으로 가두기). 화면 요소 없이 숫자만 다룬다.
 * TimelineDrag(끌기)와 TimelineBlock(Alt+방향키), ScheduleMover(옮기기)가 같은 규칙을 쓰도록 한 곳에 둔다 (F2, F3).
 * 일정은 자정을 넘길 수 없고 끝 시간은 23:59가 가장 늦으므로, 하루의 끝(24:00)은 1440분으로 세고 시각으로 바꿀 때 23:59로 돌려놓는다.
 */
public final class TimeSnap {

    /** 맞춤 단위(분) */
    public static final int STEP = 15;
    /** 하루의 끝(24:00)을 분으로 센 값 */
    public static final int DAY = 1440;

    /** 시작·끝 분. 끝은 시작보다 항상 크다 */
    public record Range(int start, int end) {
        public int length() {
            return end - start;
        }
    }

    private TimeSnap() {
    }

    /** 시각을 0~1439분으로 센다 */
    public static int minuteOf(LocalTime t) {
        return t.getHour() * 60 + t.getMinute();
    }

    /** 끝 시각을 분으로 센다. 23:59는 하루의 끝(1440)으로 본다 */
    public static int endMinuteOf(LocalTime t) {
        int m = minuteOf(t);
        return m >= DAY - 1 ? DAY : m;
    }

    /** 분을 시각으로 바꾼다. 1440 이상은 23:59, 0 미만은 00:00 */
    public static LocalTime timeOf(int minute) {
        if (minute >= DAY) {
            return LocalTime.of(23, 59);
        }
        int m = Math.max(0, minute);
        return LocalTime.of(m / 60, m % 60);
    }

    /** 가까운 15분 단위로 맞춘 분 변화량 */
    public static int roundStep(int minutes) {
        return Math.floorDiv(minutes + STEP / 2, STEP) * STEP;
    }

    /** 15분 단위로 내림 */
    public static int floorStep(int minute) {
        return Math.floorDiv(minute, STEP) * STEP;
    }

    /** 15분 단위로 올림 */
    public static int ceilStep(int minute) {
        return -Math.floorDiv(-minute, STEP) * STEP;
    }

    /** 길이를 지킨 채 delta분(15분 단위로 맞춤) 옮긴 구간. 하루 안(00:00~24:00)으로 가둔다. 원래 분이 15분 단위가 아니어도 그 어긋남은 그대로 둔다 */
    public static Range moved(int start, int end, int delta) {
        return shifted(start, end, roundStep(delta));
    }

    /** 맞춤 없이 delta분 옮긴 구간 (키보드 이동). 하루 안으로 가둔다 */
    public static Range shifted(int start, int end, int delta) {
        int length = end - start;
        int next = Math.max(0, Math.min(DAY - length, start + delta));
        return new Range(next, next + length);
    }

    /** 끝을 delta분(15분 단위로 맞춤) 옮긴 구간. 최소 15분, 하루의 끝을 넘지 않는다 */
    public static Range resized(int start, int end, int delta) {
        int next = Math.max(start + STEP, Math.min(DAY, end + roundStep(delta)));
        return new Range(start, next);
    }

    /** 빈 곳에서 a분부터 b분까지(순서 무관) 끌어 만든 구간. 시작은 내림, 끝은 올림, 최소 15분 */
    public static Range created(int a, int b) {
        int lo = Math.max(0, Math.min(a, b));
        int hi = Math.min(DAY, Math.max(a, b));
        int start = floorStep(lo);
        int end = Math.max(ceilStep(hi), start + STEP);
        if (end > DAY) {
            end = DAY;
            start = DAY - STEP;
        }
        return new Range(start, end);
    }

    /** 빈 곳을 두 번 눌렀을 때: 그 시각을 15분 단위로 내림해 1시간. 하루를 넘으면 끝을 24:00에 맞춘다 */
    public static Range hourAt(int minute) {
        int start = floorStep(Math.max(0, Math.min(DAY - 1, minute)));
        int end = start + 60;
        if (end > DAY) {
            return new Range(DAY - 60, DAY);
        }
        return new Range(start, end);
    }
}
