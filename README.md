# 일정 관리 (프로토타입)

대학교 "소프트웨어시스템분석및설계"(UML) 팀 프로젝트용 **일정관리 프로토타입**입니다. Java 17 + Swing(FlatLaf) 데스크톱 앱이고, DB·서버·외부 API 없이 CSV 파일에만 저장합니다.
일정 등록·수정·삭제·완료, 반복, 카테고리, 검색, 알림, 빈 시간 찾기, 주간 통계를 갖췄고, 이 앱을 직접 써 보며 유스케이스를 뽑는 용도입니다.
(계획은 [프로토타입-계획서.md](프로토타입-계획서.md), 구현 규칙은 [AI-구현-지시서.md](AI-구현-지시서.md)를 보세요.)

## 필요한 것

- **JDK 17 이상** (Gradle 래퍼가 들어 있어 Gradle은 따로 설치하지 않아도 됩니다)
- Windows 10/11 또는 macOS

## 실행·테스트

| 하는 일 | macOS / Linux | Windows |
| --- | --- | --- |
| 앱 실행 | `./gradlew run` | `gradlew.bat run` |
| 테스트 | `./gradlew test` | `gradlew.bat test` |
| 화면 캡처 | `./gradlew screenshots` | `gradlew.bat screenshots` |

- `screenshots`는 임시 데이터로 여러 창 크기와 밝은/어두운 테마의 모든 화면을 `screenshots/`에 PNG로 저장합니다 (git에는 올라가지 않음).
- 앱은 프로젝트 루트에서 실행되어 `data/` 폴더가 루트에 생깁니다.
- 앱을 두 번 켜면 두 번째 창은 "일정 관리가 이미 실행 중이에요." 안내만 하고 끝납니다.

## 데이터가 저장되는 곳

| 파일 | 내용 |
| --- | --- |
| `data/schedules.csv` | 일정 (제목·메모에 쉼표·줄바꿈이 있어도 안전하게 저장) |
| `data/categories.csv` | 카테고리 |
| `data/schedules-backup-yyyyMMdd-HHmmss.csv`, `data/categories-backup-…csv` | 불러올 때 읽지 못한 줄이 있으면 덮어쓰기 전에 원본을 이 이름으로 보관 |
| `data/app.lock` | 단일 실행 잠금 (지우지 않아도 됨) |

- 처음 실행해서 파일이 없으면 예시 일정 5개(매주 반복 "팀플 회의" 포함)를 만듭니다.
- 원본을 백업하지 못하면 그 파일에는 저장하지 않고 "저장하는 중 문제가 생겼어요" 알림을 띄웁니다.
- 창 위치·테마·시작 화면 같은 환경설정은 CSV가 아니라 사용자별 Java 환경설정(`java.util.prefs`)에 저장됩니다.

## 단축키

`Ctrl`은 Windows, `⌘`는 macOS입니다.

| 키 | 동작 |
| --- | --- |
| `Ctrl/⌘ + N` | 새 일정 (달력 보기에서는 고른 날짜로) |
| `Ctrl/⌘ + F` | 검색창으로 이동 |
| `Ctrl/⌘ + 1` / `2` / `3` / `4` | 오늘 / 이번 주 / 달력 / 주간 통계 |
| `Ctrl/⌘ + ,` | 설정 |
| `Esc` | 검색창: 글 지우기 → (좁은 화면에서) 검색창 접기 · 대화상자: 닫기(취소) · 서랍 사이드바: 닫기 |
| 달력·미니 달력에서 `←` `→` `↑` `↓` | 하루·한 주씩 이동 |
| 달력·미니 달력에서 `PageUp` / `PageDown`, `Home` | 한 달 이동, 오늘로 |
| 달력 격자에서 `Enter` / `Space` | 그날에 새 일정 / 그날 고르기 |
| 일정 줄에서 `Enter` / `Delete`·`Backspace` | 수정 열기 / 삭제 |
| 날짜 입력칸에서 `←→↑↓`, `PageUp/Down` | 날짜 조정 (±1일, ±7일, ±1달) |

## 패키지 구조 (`src/main/java/proto`)

| 패키지 | UML 분류 | 하는 일 |
| --- | --- | --- |
| `app` | 시작점 | `Main`: 잠금·저장소·controller·타이머를 만들어 연결 (`app.dev`는 스크린샷 실행기) |
| `entity` | 엔티티 (entity) | `Schedule`, `Occurrence`, `Category`, `RepeatRule` 등 데이터 |
| `control` | 제어 (control) | `ScheduleController`, `CategoryController`, `ReminderTimer`, `StateRefreshTimer`. 화면이 부르는 유일한 문 |
| `logic` | 애플리케이션 로직 (application logic) | 검증·상태 계산·반복 펼치기·겹침/빈 시간/통계 계산. 화면 없이 테스트 가능 |
| `repository` | 저장소 (repository) | 일정·카테고리 목록을 들고 CSV로 저장·불러오기 (`ScheduleStore`·`CategoryStore` 인터페이스) |
| `server` | 서버 대비 (현재 사용 안 함) | 아래 "서버 대비 패키지" 참고 |
| `ui` | 경계 (boundary) | Swing 화면 (`ui.view`, `ui.dialog`, `ui.component`, `ui.theme`, `ui.motion`, `ui.layout`, `ui.dev`) |

규칙: ui는 entity·repository를 직접 건드리지 않고 controller를 거치며, controller는 리스너(인터페이스)로 ui에 결과를 알립니다.
상세 표는 문서를 보세요.

## 문서

- [docs/기능-흐름표.md](docs/기능-흐름표.md): F1~F15의 시작하는 쪽, 기본·대안 흐름, 사전·사후 조건 (유스케이스 명세의 재료)
- [docs/클래스-목록표.md](docs/클래스-목록표.md): 클래스 | 분류 | 역할 | 협력하는 클래스
- [docs/server-api.md](docs/server-api.md): 서버 대비 API 설명

## 기능

F1 등록 · F2 조회(오늘/이번 주/달력) · F3 수정 · F4 삭제 · F5 완료 · F6 상태 자동 계산 · F7 카테고리 · F8 검색 · F9 반복 · F10 알림 · F11 자동 저장 ·
추가: F12 빈 시간 찾기 · F13 일정 겹침 경고 · F14 주간 통계 · F15 알림 다시 알리기 · 설정 화면 · 화면 크기에 따라 바뀌는 배치 · 단일 실행 잠금 · 불러오기 문제 시 CSV 백업.

## 글꼴 라이선스

화면 글꼴은 **Pretendard**이며 SIL Open Font License 1.1이 적용됩니다. 라이선스 전문은 `src/main/resources/fonts/Pretendard-LICENSE.txt`에 있습니다.

## 서버 대비 패키지(`proto.server`)

나중에 서버를 붙일 때를 대비해 **따로 떼어 둔** 패키지입니다. 기본값은 꺼짐이라 지금 앱은 이 코드를 타지 않고, 서버에 접속하지도 않습니다.

- 켜는 법: `data/server.properties`에 `enabled=true`와 `baseUrl`을 적으면 "이 컴퓨터 우선 + 서버 동기화"로 동작합니다 (서버가 꺼져 있어도 CSV로 계속 동작).
- 갈림길은 `proto.app.StoreFactory` 하나이고, controller는 `ScheduleStore`·`CategoryStore` 인터페이스만 봅니다.
- 안 쓰기로 하면 `src/main/java/proto/server`, `src/test/java/proto/server`, `StoreFactory`의 서버 분기, `docs/server-api.md`를 지우면 됩니다.
- 수업용 클래스 다이어그램에서는 빼도 됩니다. 클래스 목록표에서도 마지막에 따로 모아 두었습니다.
- 자세한 내용: [docs/server-api.md](docs/server-api.md)
