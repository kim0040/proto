package proto.ui.component;

import proto.entity.Occurrence;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * [경계 boundary] 시간표(TimelineGrid)가 일정 열기·삭제 말고 옮기기·새로 만들기·날짜 고르기를 요청하는 통로.
 * 시간표 컴포넌트가 UiContext나 ScheduleMover를 직접 알지 못하게 하려는 연결 틈새(인터페이스)이다. WeekView가 구현한다.
 * 컴포넌트는 "이 일정을 여기로 옮겨 줘" 하고 요청만 하고, 확인 창·controller 호출·알림은 구현하는 쪽이 한다.
 */
public interface TimelineHandler extends RowHandler {

    /** 일정을 새 날짜·시간으로 옮긴다. 실제로 바뀌었으면 true */
    boolean move(Occurrence occurrence, LocalDate date, LocalTime start, LocalTime end);

    /** 이 날짜·시간으로 새 일정 창을 연다 (빈 곳 끌기·두 번 누르기) */
    void create(LocalDate date, LocalTime start, LocalTime end);

    /** 날짜 칸이나 일정을 눌렀다: 고른 날을 바꾼다 */
    void selectDay(LocalDate day);
}
