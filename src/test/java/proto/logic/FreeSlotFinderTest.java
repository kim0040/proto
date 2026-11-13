package proto.logic;

import org.junit.jupiter.api.Test;
import proto.entity.TimeSlot;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static proto.TestData.d;
import static proto.TestData.occ;
import static proto.TestData.t;

class FreeSlotFinderTest {

    private static final LocalDateTime NOW = LocalDateTime.parse("2026-11-09T10:00:00");

    private static TimeSlot slot(String date, String start, String end) {
        return new TimeSlot(d(date), t(start), t(end));
    }

    @Test
    void overlappingAndNestedBusyIntervalsAreMerged() {
        // 10:00-11:00, 10:30-12:00, 11:00-11:30(포개짐) → 10:00~12:00 하나로 합쳐짐, 그리고 14:00-15:00
        var busy = List.of(occ("2026-11-10", "10:30", "12:00"), occ("2026-11-10", "10:00", "11:00"),
                occ("2026-11-10", "11:00", "11:30"), occ("2026-11-10", "14:00", "15:00"));
        assertEquals(List.of(slot("2026-11-10", "09:00", "10:00"), slot("2026-11-10", "12:00", "14:00"),
                        slot("2026-11-10", "15:00", "22:00")),
                FreeSlotFinder.find(busy, d("2026-11-10"), 60, t("09:00"), t("22:00"), NOW));
    }

    @Test
    void gapsShorterThanRequestedMinutesAreDropped() {
        var busy = List.of(occ("2026-11-10", "10:00", "12:00"), occ("2026-11-10", "14:00", "15:00"));
        // 09:00~10:00(60분)은 61분에는 모자라고, 12:00~14:00(120분)과 15:00~22:00(420분)만 남음
        assertEquals(List.of(slot("2026-11-10", "12:00", "14:00"), slot("2026-11-10", "15:00", "22:00")),
                FreeSlotFinder.find(busy, d("2026-11-10"), 61, t("09:00"), t("22:00"), NOW));
    }

    @Test
    void gapExactlyAsLongAsRequestedIsIncluded() {
        var busy = List.of(occ("2026-11-10", "10:00", "11:00"));
        assertEquals(List.of(slot("2026-11-10", "09:00", "10:00"), slot("2026-11-10", "11:00", "22:00")),
                FreeSlotFinder.find(busy, d("2026-11-10"), 60, t("09:00"), t("22:00"), NOW));
    }

    @Test
    void nothingFreeWhenBusyCoversTheWholeWindow() {
        var busy = List.of(occ("2026-11-10", "08:00", "23:00"));
        assertTrue(FreeSlotFinder.find(busy, d("2026-11-10"), 15, t("09:00"), t("22:00"), NOW).isEmpty());
    }

    @Test
    void wholeWindowIsFreeWhenNoSchedules() {
        assertEquals(List.of(slot("2026-11-10", "09:00", "22:00")),
                FreeSlotFinder.find(List.of(), d("2026-11-10"), 480, t("09:00"), t("22:00"), NOW));
    }

    @Test
    void busyStartingBeforeWindowOnlyCutsTheStart() {
        var busy = List.of(occ("2026-11-10", "08:00", "09:30"), occ("2026-11-10", "21:00", "23:00"));
        assertEquals(List.of(slot("2026-11-10", "09:30", "21:00")),
                FreeSlotFinder.find(busy, d("2026-11-10"), 15, t("09:00"), t("22:00"), NOW));
    }

    @Test
    void todayStartsAtNowRoundedUpToNextFiveMinutes() {
        var busy = List.of(occ("2026-11-09", "11:00", "12:00"));
        LocalDateTime now = LocalDateTime.parse("2026-11-09T10:02:30");
        assertEquals(List.of(slot("2026-11-09", "10:05", "11:00"), slot("2026-11-09", "12:00", "22:00")),
                FreeSlotFinder.find(busy, d("2026-11-09"), 15, t("09:00"), t("22:00"), now));
    }

    @Test
    void exactFiveMinuteMarkIsNotRoundedButOneSecondLaterIs() {
        assertEquals(List.of(slot("2026-11-09", "10:05", "22:00")), FreeSlotFinder.find(List.of(),
                d("2026-11-09"), 15, t("09:00"), t("22:00"), LocalDateTime.parse("2026-11-09T10:05:00")));
        assertEquals(List.of(slot("2026-11-09", "10:10", "22:00")), FreeSlotFinder.find(List.of(),
                d("2026-11-09"), 15, t("09:00"), t("22:00"), LocalDateTime.parse("2026-11-09T10:05:01")));
    }

    @Test
    void todayBeforeWindowStartKeepsTheWindowStart() {
        assertEquals(List.of(slot("2026-11-09", "09:00", "22:00")), FreeSlotFinder.find(List.of(),
                d("2026-11-09"), 15, t("09:00"), t("22:00"), LocalDateTime.parse("2026-11-09T07:12")));
    }

    @Test
    void emptyWhenWindowIsEmptyOrNowIsPastIt() {
        assertTrue(FreeSlotFinder.find(List.of(), d("2026-11-10"), 15, t("22:00"), t("09:00"), NOW).isEmpty());
        assertTrue(FreeSlotFinder.find(List.of(), d("2026-11-10"), 15, t("10:00"), t("10:00"), NOW).isEmpty());
        assertTrue(FreeSlotFinder.find(List.of(), d("2026-11-09"), 15, t("09:00"), t("22:00"),
                LocalDateTime.parse("2026-11-09T22:30")).isEmpty());
        assertTrue(FreeSlotFinder.find(List.of(), d("2026-11-09"), 15, t("09:00"), t("23:59"),
                LocalDateTime.parse("2026-11-09T23:58:30")).isEmpty());
    }
}
