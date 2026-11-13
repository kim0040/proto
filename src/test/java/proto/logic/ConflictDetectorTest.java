package proto.logic;

import org.junit.jupiter.api.Test;
import proto.entity.Occurrence;
import proto.entity.RepeatRule;
import proto.entity.Schedule;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static proto.TestData.d;
import static proto.TestData.occ;
import static proto.TestData.repeating;
import static proto.TestData.single;

class ConflictDetectorTest {

    private static List<Occurrence> conflicts(Schedule candidate, Schedule... existing) {
        return ConflictDetector.findConflicts(candidate, List.of(existing), o -> false);
    }

    @Test
    void touchingEdgesAreNotAConflict() {
        assertFalse(ConflictDetector.overlaps(occ("2026-11-09", "09:00", "10:00"), occ("2026-11-09", "10:00", "11:00")));
        assertFalse(ConflictDetector.overlaps(occ("2026-11-09", "10:00", "11:00"), occ("2026-11-09", "09:00", "10:00")));
    }

    @Test
    void partialOverlapAndNestingAreConflicts() {
        assertTrue(ConflictDetector.overlaps(occ("2026-11-09", "09:00", "10:00"), occ("2026-11-09", "09:59", "11:00")));
        assertTrue(ConflictDetector.overlaps(occ("2026-11-09", "09:00", "12:00"), occ("2026-11-09", "10:00", "11:00")));
        assertTrue(ConflictDetector.overlaps(occ("2026-11-09", "10:00", "11:00"), occ("2026-11-09", "09:00", "12:00")));
    }

    @Test
    void differentDatesNeverConflict() {
        assertFalse(ConflictDetector.overlaps(occ("2026-11-09", "09:00", "10:00"), occ("2026-11-10", "09:00", "10:00")));
    }

    @Test
    void singleCandidateReturnsOnlyOverlappingOnesSortedByStart() {
        Schedule late = single("late", "late", "2026-11-09", "11:00", "12:00");
        Schedule early = single("early", "early", "2026-11-09", "09:30", "10:30");
        Schedule touching = single("touch", "touch", "2026-11-09", "12:00", "13:00");
        Schedule otherDay = single("day", "day", "2026-11-10", "10:00", "11:00");
        Schedule candidate = single("new", "new", "2026-11-09", "10:00", "12:00");
        List<Occurrence> result = conflicts(candidate, late, early, touching, otherDay);
        assertEquals(List.of("early", "late"), result.stream().map(Occurrence::title).toList());
    }

    @Test
    void repeatingCandidateIsCheckedOnlyWithin28DaysFromItsStart() {
        // 후보: 2026-11-09(월)부터 매주 09:00~10:00 → 검사 구간은 11-09 ~ 12-06
        Schedule candidate = repeating("new", "new", "2026-11-09", "09:00", "10:00", RepeatRule.WEEKLY, null);
        Schedule inside = single("in", "in", "2026-11-30", "09:30", "10:30");
        Schedule lastDay = single("last", "last", "2026-12-06", "09:30", "10:30"); // 일요일: 요일이 달라 후보와 안 겹침
        Schedule outside = single("out", "out", "2026-12-07", "09:30", "10:30"); // 29번째 날: 검사 밖
        Schedule early = single("early", "early", "2026-11-16", "09:59", "11:00");
        List<Occurrence> result = conflicts(candidate, inside, lastDay, outside, early);
        assertEquals(List.of(d("2026-11-16"), d("2026-11-30")), result.stream().map(Occurrence::date).toList());
    }

    @Test
    void dailyCandidateIncludesDay27AndExcludesDay28() {
        // 2026-11-09부터 28일: 11-09(0) ~ 12-06(27). 12-07은 창 밖
        Schedule candidate = repeating("new", "new", "2026-11-09", "09:00", "10:00", RepeatRule.DAILY, null);
        Schedule onDay27 = single("in", "in", "2026-12-06", "09:30", "10:30");
        Schedule onDay28 = single("out", "out", "2026-12-07", "09:30", "10:30");
        assertEquals(List.of(d("2026-12-06")),
                conflicts(candidate, onDay27, onDay28).stream().map(Occurrence::date).toList());
    }

    @Test
    void repeatingExistingSeriesIsExpandedWithinTheWindowAndIgnoredOccurrencesAreSkipped() {
        Schedule weekly = repeating("w", "weekly", "2026-11-09", "09:30", "09:45", RepeatRule.WEEKLY, null);
        Schedule candidate = repeating("new", "new", "2026-11-09", "09:00", "10:00", RepeatRule.WEEKLY, null);
        List<Occurrence> all = ConflictDetector.findConflicts(candidate, List.of(weekly), o -> false);
        // 11-09, 11-16, 11-23, 11-30 (12-07은 검사 구간 밖)
        assertEquals(4, all.size());
        List<Occurrence> skipped = ConflictDetector.findConflicts(candidate, List.of(weekly),
                o -> o.date().isAfter(d("2026-11-15")));
        assertEquals(List.of(d("2026-11-09")), skipped.stream().map(Occurrence::date).toList());
    }
}
