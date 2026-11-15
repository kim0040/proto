package proto.logic;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 명령 메뉴의 "날짜로 이동": 말로 쓴 날짜를 기준일(2026-11-16 월요일)에서 읽는다 */
class DateQueryParserTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);

    private static Optional<LocalDate> parse(String text) {
        return DateQueryParser.parse(text, TODAY);
    }

    private static void expect(String text, String iso) {
        assertEquals(Optional.of(LocalDate.parse(iso)), parse(text), text);
    }

    private static void expectNone(String text) {
        assertTrue(parse(text).isEmpty(), text + " 는 날짜가 아니어야 해요");
    }

    @Test
    void 오늘_내일_같은_낱말() {
        expect("오늘", "2026-11-16");
        expect("내일", "2026-11-17");
        expect("모레", "2026-11-18");
        expect("글피", "2026-11-19");
        expect("어제", "2026-11-15");
        expect("그저께", "2026-11-14");
    }

    @Test
    void 몇_일_몇_주_뒤와_전_띄어쓰기는_무시() {
        expect("3일 뒤", "2026-11-19");
        expect("3일뒤", "2026-11-19");
        expect("10일 후", "2026-11-26");
        expect("2일 전", "2026-11-14");
        expect("2주 뒤", "2026-11-30");
        expect("1주일 후", "2026-11-23");
        expect("일주일 뒤", "2026-11-23");
        expect("2개월 뒤", "2027-01-16");
        expect("1달 전", "2026-10-16");
    }

    @Test
    void 다음_주와_지난_달() {
        expect("다음 주", "2026-11-23");
        expect("다음주", "2026-11-23");
        expect("지난 주", "2026-11-09");
        expect("다음 달", "2026-12-16");
        expect("지난달", "2026-10-16");
    }

    @Test
    void 요일은_앞으로_처음_오는_날이고_주를_붙이면_그_주의_요일() {
        expect("월요일", "2026-11-16");
        expect("금요일", "2026-11-20");
        expect("일요일", "2026-11-22");
        expect("이번 주 금요일", "2026-11-20");
        expect("다음 주 월요일", "2026-11-23");
        expect("다음 주 금요일", "2026-11-27");
        expect("지난 주 수요일", "2026-11-11");
    }

    @Test
    void 월일은_올해_일만은_이번_달() {
        expect("11월 20일", "2026-11-20");
        expect("11월20일", "2026-11-20");
        expect("1월 5일", "2026-01-05");
        expect("11/20", "2026-11-20");
        expect("12-25", "2026-12-25");
        expect("11.20", "2026-11-20");
        expect("20일", "2026-11-20");
        expect("5일", "2026-11-05");
    }

    @Test
    void 연월일_여러_모양() {
        expect("2026-11-20", "2026-11-20");
        expect("2027.1.5", "2027-01-05");
        expect("2026/12/31", "2026-12-31");
        expect("2026년 11월 20일", "2026-11-20");
        expect("20261120", "2026-11-20");
    }

    @Test
    void 없는_날짜와_알_수_없는_글은_빈_값() {
        expectNone("2월 30일");
        expectNone("31일");
        expectNone("13월 1일");
        expectNone("2026-02-29");
        expectNone("회의");
        expectNone("");
        expectNone("   ");
        expectNone("99999일 뒤");
        expectNone("0일");
    }

    @Test
    void null은_빈_값() {
        assertTrue(DateQueryParser.parse(null, TODAY).isEmpty());
        assertTrue(DateQueryParser.parse("내일", null).isEmpty());
    }

    @Test
    void 월말_달_더하기는_말일로_맞춘다() {
        assertEquals(Optional.of(LocalDate.of(2026, 2, 28)),
                DateQueryParser.parse("1개월 뒤", LocalDate.of(2026, 1, 31)));
    }
}
