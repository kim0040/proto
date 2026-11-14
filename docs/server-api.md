# 서버 연동 (현재 사용 안 함)

이 문서는 나중에 서버를 붙일 때를 위해 넣어 둔 계약이다. **지금은 앱이 이 경로를 타지 않는다.**

`data/server.properties`가 없거나 `enabled`가 `true`가 아니면 일정과 카테고리는 지금처럼 이 컴퓨터의 `data/schedules.csv`, `data/categories.csv`에만 저장된다. 앱은 `server.properties`를 스스로 만들지 않고, 서버에 접속하지도 않는다.

서버를 쓰는 클래스는 전부 `proto.server`에 있다. 저장소를 고르는 갈림길은 `proto.app.StoreFactory`의 `enableServerIfConfigured` 하나다.

## 없애는 방법

1. `src/main/java/proto/server` 패키지를 통째로 지운다.
2. `StoreFactory.create`가 CSV 저장소(`ScheduleRepository`, `CategoryRepository`)를 바로 돌려주도록 `enableServerIfConfigured`를 지운다.
3. 이 문서를 지운다.

controller와 화면은 `ScheduleStore`, `CategoryStore`만 본다. 위 두 곳을 지우면 서버 클래스를 가리키는 코드는 남지 않는다. 테스트의 `src/test/java/proto/server`도 같이 지우면 된다.

## 켜는 방법

실행 위치의 `data/server.properties`를 직접 만든다. 예:

```properties
enabled=true
baseUrl=http://127.0.0.1:8080
token=비밀토큰
timeoutSeconds=5
```

| 키 | 의미 |
| --- | --- |
| `enabled` | `true`일 때만 서버를 쓴다. 없거나 `false`면 끈다. |
| `baseUrl` | `http://` 또는 `https://`로 시작하는 주소. 호스트가 있고 포트가 1~65535여야 하며 `?`, `#`, 사용자 정보는 받지 않는다. 끝의 `/`는 떼고 붙인다. |
| `token` | `Authorization: Bearer` 뒤에 붙일 값. 비워 둘 수 있다. 눈에 보이는 ASCII(0x21~0x7E)만 받고, 공백·제어 문자·한글이 있으면 서버를 끈다. |
| `timeoutSeconds` | 연결·요청 제한(초). 없으면 5. 1~120만 받는다. |

파일은 UTF-8이다. 값이 깨졌거나 주소·제한 시간을 읽지 못하면 예외 없이 서버를 끄고, `ServerSettings.warning()`에 이유만 남긴다. 그때도 CSV만 쓴다. 그 이유는 시작할 때 일정 저장소의 경고로 화면에 알린다.

## REST 계약

기반 주소 뒤에 경로를 붙인다. 본문은 UTF-8 JSON. 요청마다 다음 헤더를 보낸다.

```http
Accept: application/json
Authorization: Bearer <token>
```

PUT에는 `Content-Type: application/json`을 더한다. 리다이렉트는 따라가지 않는다(토큰이 다른 주소로 새지 않게).

### 일정

`GET /api/v1/schedules` — 일정 전체 스냅샷.

`PUT /api/v1/schedules` — 일정 전체를 이 본문으로 바꾼다.

```json
{
  "schedules": [
    {
      "id": "id-1",
      "title": "팀플 회의",
      "date": "2026-11-09",
      "start": "09:30",
      "end": "10:45",
      "categoryId": "team",
      "memo": "메모 1줄\n둘째",
      "repeat": "WEEKLY",
      "repeatEnd": "2026-12-31",
      "reminder": "THIRTY_MINUTES",
      "completedDates": ["2026-11-09", "2026-11-16"],
      "excludedDates": ["2026-11-23"]
    }
  ]
}
```

날짜는 `yyyy-MM-dd`, 시간은 `HH:mm`(초가 있으면 `HH:mm:ss`도 읽는다). `repeat`는 `NONE`, `DAILY`, `WEEKLY`, `MONTHLY`. `reminder`는 `NONE`, `FIVE_MINUTES`, `TEN_MINUTES`, `THIRTY_MINUTES`. 반복이 없으면 `repeatEnd`는 `null`이다. 완료·제외 날짜가 없으면 빈 배열 `[]`이다.

### 카테고리

`GET /api/v1/categories` — 카테고리 전체 스냅샷.

`PUT /api/v1/categories` — 카테고리 전체를 이 본문으로 바꾼다.

```json
{
  "categories": [
    {"id": "class", "name": "수업"},
    {"id": "personal", "name": "개인"}
  ]
}
```

`id`가 `personal`인 항목이 "개인"이다. 이름은 앞뒤 공백을 뺀 1~20자이고, 같은 이름(대소문자 무시)은 한 번만 있다.

### 상태 코드

| 코드 | 뜻 | 앱이 하는 일 |
| --- | --- | --- |
| 200 | GET 성공. 본문이 위 JSON | 그 스냅샷으로 이 컴퓨터의 CSV를 바꾼다 |
| 204 또는 그 외 2xx | PUT 성공. 본문은 없어도 된다 | 경고 없이 끝낸다 |
| 400 | JSON이 계약과 다름 | 로컬 CSV를 유지하고 경고 |
| 401 | 토큰이 없음·틀림 | 로컬 CSV를 유지하고 경고 |
| 404 | 경로가 없음 | 로컬 CSV를 유지하고 경고 |
| 500 | 서버 오류 | 로컬 CSV를 유지하고 경고 |

앱은 2xx가 아니면 본문을 해석하지 않고, PUT의 응답 본문은 2xx여도 읽지 않는다. `timeoutSeconds`는 응답 본문을 다 받을 때까지 한 번에 적용하고, 본문은 5MB까지만 받으며, UTF-8이 깨진 본문과 객체·배열이 64겹보다 깊은 JSON은 거절한다. 연결 실패, 시간 초과, 깨진 JSON도 같은 방식으로 로컬을 유지한다.

불러올 때 서버에 닿지 못하면 경고 문장은 다음이다.

- 일정: `서버에 연결하지 못해 이 컴퓨터에 저장된 일정으로 열었어요`
- 카테고리: `서버에 연결하지 못해 이 컴퓨터에 저장된 카테고리로 열었어요`

저장한 뒤 올리기가 실패하면 CSV는 이미 써 둔 상태이고, 경고만 더한다.

- 일정: `서버에 일정을 올리지 못했어요. 이 컴퓨터에는 저장되어 있어요`
- 카테고리: `서버에 카테고리를 올리지 못했어요. 이 컴퓨터에는 저장되어 있어요`

이 문장들은 저장소의 `getWarnings()`에 쌓인다. 화면은 시작할 때 `ScheduleController.reportLoadWarnings()`로 그 목록을 보여 주고, 저장한 뒤 새로 생긴 경고(올리기 실패)는 controller가 `storageFailed`로 한 번씩 알린다. 서버의 카테고리 이름이 덧붙인 "개인"과 겹치면 그 스냅샷은 받지 않고 이 컴퓨터 목록을 유지한다.

## 지금 동기화 방식과 한계

한 번에 목록 전체를 주고받는 스냅샷이고, 나중에 쓴 쪽이 이긴다.

- 불러오기가 성공하면 서버 목록이 이 컴퓨터의 메모리와 CSV를 통째로 대체한다. 서버 목록이 비어 있으면 이 컴퓨터의 일정(또는 카테고리)도 빈다.
- 저장은 항상 CSV를 먼저 쓴 다음, 그 전체 목록을 PUT 한다. PUT이 실패해도 CSV는 되돌리지 않는다.
- 일정과 카테고리는 요청이 따로다. 한쪽만 성공하면 둘은 잠깐 어긋날 수 있다.
- 두 컴퓨터가 각자 고친 뒤 올리면, 나중에 PUT한 목록만 남고 먼저 올린 수정은 사라진다. 항목 단위로 합치지 않는다.
- 카테고리 스냅샷에 "개인"(`id`가 `personal`)이 없으면, 이 컴퓨터 목록에만 "개인"을 붙인 뒤 CSV에 저장한다. 그 추가를 서버에 다시 올리지는 않는다. 다음 저장 때 전체 PUT에 포함된다.
- 충돌을 사용자에게 고르게 하지 않는다. 버전 번호도 없다.

## 나중에 진짜 서버에 필요할 것

- 사용자별 계정. 지금 토큰은 설정 파일에 적는 공유 비밀이라 사람을 구분하지 못한다.
- 일정·카테고리마다 버전 또는 `ETag`. 서버 버전이 내가 봤던 것보다 새면 409로 거절하고, 앱이 충돌을 보여 준 뒤 합치게 한다.
- 바뀐 항목만 주고받는 증분 동기화. 매번 전체를 갈아끼우면 빈 스냅샷 한 번이 모든 기기의 데이터를 지운다.
- 삭제한 id를 남기는 기록. 없으면 "없다"와 "아직 안 받아 봤다"를 구분하지 못한다.
- 기기 식별. 어느 컴퓨터의 저장이 이겼는지 나중에 볼 수 있어야 한다.
