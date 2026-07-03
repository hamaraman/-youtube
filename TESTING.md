# 🧪 mytube 테스트 가이드

이 프로젝트에서 우리가 만든 자동화 테스트가 뭔지, 왜 있는지, 어떻게 굴러가는지 정리한 문서.

---

## 1. 그래서 테스트가 뭐 하는 건데?

한 줄 요약: **코드가 원래 하려던 일을 계속 잘 하고 있는지 자동으로 계속 검사하는 안전망.**

예를 들어 지금 `AuthService.login()` 은 "잘못된 비밀번호면 예외를 던진다" 는 규칙을 가지고 있어. 어느 날 리팩토링하다 실수로 이 규칙이 깨지면?

- 옛날엔 → 배포하고 유저가 로그인 시도할 때 발견 😱
- 지금은 → `./gradlew test` 순간 빨간불로 알려줌 ✅

즉, **"내가 손댄 게 다른 데를 망가뜨리진 않았을까?"** 하는 걱정을 컴퓨터가 대신 해줌.

---

## 2. 테스트가 어디에 어떻게 놓여 있어?

```
D:\demo\
├── src/
│   ├── main/java/com/example/demo/    ← 실제 서비스 코드
│   │   ├── controller/  (VideoController 등)
│   │   ├── service/     (VideoService 등)
│   │   ├── repository/  (VideoRepository 등)
│   │   └── entity/
│   │
│   └── test/java/com/example/demo/    ← 우리가 만든 테스트
│       ├── config/
│       │   └── JwtUtilTest.java
│       ├── controller/
│       │   ├── CommentControllerMvcTest.java
│       │   ├── PlaylistControllerMvcTest.java
│       │   └── VideoActionControllerMvcTest.java
│       ├── repository/
│       │   └── CommentRepositoryTest.java
│       └── service/
│           ├── AuthServiceTest.java
│           ├── CommentServiceTest.java
│           ├── LoginAttemptServiceTest.java
│           ├── PlaylistServiceTest.java
│           ├── SubscriptionServiceTest.java
│           ├── VideoActionServiceTest.java
│           └── VideoServiceTest.java
│
└── src/test/resources/
    └── application-test.properties  ← 테스트 전용 설정 (H2 인메모리 DB)
```

**규칙**: 테스트 파일은 검사 대상 클래스와 **같은 패키지 이름** 을 씀. `service/AuthService.java` 를 검사하는 파일은 `service/AuthServiceTest.java`.

---

## 3. 우리가 만든 테스트 종류 (3가지 층)

한 프로젝트에는 목적이 다른 여러 층의 테스트가 존재해. 이 프로젝트는 지금 세 층을 갖고 있어.

### 🟩 층 1: 유닛 테스트 (Unit Test)

**검사 범위**: 한 클래스, 한 함수. 다른 건 다 가짜(Mock)로 대체.

**언제 씀**: 비즈니스 로직의 조건 분기 / 예외 / 계산 검사할 때.

**예시**:
```
"AuthService.login() 에 잘못된 비밀번호를 주면
   → ResponseStatusException 이 던져지는가?
   → 실패 카운터가 증가하는가?"
```
이걸 검사하는데 실제 DB 는 필요 없어. `UserRepository` 를 Mockito 로 가짜로 만들어서 "이런 유저가 있다고 치자" 하고 통과시키는 거야.

**속도**: 밀리초 단위. 굉장히 빠름.

이 프로젝트에서 유닛 테스트 파일:
- `JwtUtilTest` — JWT 토큰 만들고 파싱하는 로직
- `LoginAttemptServiceTest` — 5회 실패 시 15분 차단 로직
- `AuthServiceTest` — 회원가입 / 로그인 / 로그아웃 / 비밀번호 재설정
- `VideoActionServiceTest` — 좋아요 / 저장 토글
- `VideoServiceTest` — 영상 조회, 피드, 수정, 삭제, 접근 제어
- `SubscriptionServiceTest` — 채널 구독
- `CommentServiceTest` — 댓글 CRUD, 답글, 좋아요
- `PlaylistServiceTest` — 재생목록 CRUD
- `HistoryServiceTest` — 시청 기록, 24시간 조회수 쿨다운, 진행 위치 저장
- `ProfileServiceTest` — 프로필 조회/수정, 비밀번호 변경 인증 코드 발송/검증
- `RefreshTokenServiceTest` — 리프레시 토큰 생성/검증/만료 처리
- `VideoShareServiceTest` — OG 태그 HTML 생성, XSS 이스케이프, 비공개 차단
- `AdminServiceTest` — 관리자 영상/유저 관리, 자기 자신 삭제 방지, role 변경 검증
- `NotificationServiceTest` — 알림 저장, 자기 자신 알림 차단, 읽음 처리, SSE emitter 등록

### 🟨 층 2: 리포지토리 슬라이스 테스트 (`@DataJpaTest`)

**검사 범위**: JPA 쿼리 메서드 + 실제 DB (인메모리 H2).

**언제 씀**: `@Query` 로 쓴 JPQL 이나 스프링 데이터 규약 메서드가 진짜로 원하는 결과를 뽑는지 확인할 때.

**예시**:
```
"영상에 최상위 댓글 3개 + 답글 2개를 넣으면
   countByVideoIdAndParentIdIsNull() 이 3 을 리턴하는가?"
```
이건 Mock 으론 검사가 안 돼. 진짜 DB 안에서 SQL 이 도는 걸 봐야 하니까. 근데 그렇다고 MariaDB 를 붙일 순 없어서 **H2 인메모리 DB** 를 씀. 테스트 시작할 때 빈 DB 를 만들고, 끝나면 사라짐.

**속도**: 첫 로딩만 몇 초 걸리고 그 뒤론 빠름.

이 프로젝트에서 리포지토리 테스트 파일:
- `CommentRepositoryTest` — 댓글 카운트/조회/캐스케이드 삭제
- `VideoRepositoryTest` — 공개/비공개 필터, 키워드·카테고리 검색, 조회수 정렬, 소유자 필터
- `VideoLikeRepositoryTest` — 좋아요 카운트/존재 여부/유저별 조회, `@Query` 배치 집계
- `SubscriptionRepositoryTest` — 구독 관계 조회, 구독자 수 카운트
- `PlaylistRepositoryTest` — 재생목록 정렬, 재생목록-영상 관계 (카운트/추가/삭제)
- `VideoSaveRepositoryTest` — 저장 여부/유저별 조회, 배치 필터링
- `CommentLikeRepositoryTest` — 댓글 좋아요 카운트/사용자별 조회
- `VideoHistoryRepositoryTest` — 시청 기록 조회, 시간 기반 필터, 캐스케이드 삭제

### 🟪 층 4: 통합 테스트 (`@SpringBootTest` + MockMvc)

**검사 범위**: 실제 스프링 컨텍스트 전체 + H2 인메모리 DB + MockMvc HTTP 요청.

**언제 씀**: 여러 계층이 얽힌 시나리오가 실제로 잘 굴러가는지 확인할 때. 예를 들어 "회원가입 → 로그인 → 세션 유지 → 좋아요 → DB 저장" 흐름 전체.

**차이점**: 컨트롤러 슬라이스는 서비스를 Mock 으로 대체하지만, 통합 테스트는 **진짜 서비스 + 진짜 리포지토리 + 진짜 DB** 를 사용함. 그래서 계층 간 계약 불일치를 잡을 수 있음.

**속도**: 컨텍스트 로딩이 10초 정도. 그래서 시나리오 몇 개만 골라서 커버.

이 프로젝트에서 통합 테스트 파일:
- `FullFlowIntegrationTest` — 회원가입→로그인→me / 좋아요 토글 / 구독 후 댓글 / 자기 자신 구독 차단 / 로그아웃 세션 무효화 / 비공개 영상 접근 차단

### 🟦 층 3: 컨트롤러 슬라이스 테스트 (`@WebMvcTest` + MockMvc)

**검사 범위**: HTTP 요청 → 컨트롤러 → 응답 JSON 까지의 웹 계약.

**언제 씀**: URL, HTTP 메서드, 상태 코드, JSON 형식이 프론트가 기대하는 대로 나오는지 확인할 때.

**예시**:
```
"미로그인 상태로 POST /api/videos/1/like 를 호출하면
   → 401 상태 코드가 오는가?
   → 응답 JSON 에 { success: false, message: ... } 형식이 들어있는가?"
```
서버를 진짜로 띄우진 않고, `MockMvc` 라는 가짜 서블릿 환경에서 요청을 재현함. 서비스 레이어는 유닛 테스트처럼 Mock 으로 대체.

**속도**: 첫 로딩 몇 초, 그 뒤 빠름.

이 프로젝트에서 컨트롤러 테스트 파일:
- `VideoActionControllerMvcTest` — 좋아요 / 저장 REST API
- `PlaylistControllerMvcTest` — 재생목록 REST API
- `CommentControllerMvcTest` — 댓글 REST API
- `AuthControllerMvcTest` — 회원가입 / 로그인 / 비밀번호 재설정 / 토큰 재발급 REST API
- `SubscriptionControllerMvcTest` — 구독 토글 / 채널 검색 / 내 구독 REST API
- `VideoControllerMvcTest` — 영상 목록/피드/상세/채널 프로필/스튜디오/수정/삭제/썸네일 업로드 REST API
- `HistoryControllerMvcTest` — 시청 기록 / 진행 위치 / 내 시청 목록 REST API
- `ProfileControllerMvcTest` — 프로필 조회/수정 / 비밀번호 변경 REST API
- `NotificationControllerMvcTest` — 알림 SSE 스트림 / 목록 / 읽음 처리 REST API
- `AdminControllerMvcTest` — 관리자 전용 영상/유저 관리 REST API, 403 권한 체크
- `VideoShareControllerMvcTest` — 공유 페이지 HTML 응답, origin 구성, 404 에러 페이지

---

## 4. 세 층은 왜 다 필요해?

한 층만 있으면 놓치는 게 있음:

| 검사 대상 | 유닛 | 리포지토리 | 컨트롤러 |
|---|:---:|:---:|:---:|
| "이 함수가 이 상황에서 예외 던지는가?" | ✅ | | |
| "SQL 쿼리가 원하는 데이터를 뽑는가?" | | ✅ | |
| "HTTP 401 을 정확히 이 JSON 형식으로 주는가?" | | | ✅ |
| "타인 댓글 삭제 시 서비스 로직이 막는가?" | ✅ | | |
| "타인 댓글 삭제 요청이 실제로 403 을 받는가?" | | | ✅ |

**유닛** 은 로직 자체, **리포지토리** 는 데이터 접근, **컨트롤러** 는 HTTP 계약. 각자 담당이 달라.

---

## 5. 실행 방법

### 전체 테스트 돌리기
```powershell
./gradlew test
```

### 특정 클래스만
```powershell
./gradlew test --tests "com.example.demo.service.AuthServiceTest"
```

### 특정 그룹만 (Nested 클래스)
```powershell
./gradlew test --tests "com.example.demo.service.AuthServiceTest`$Login"
```
> PowerShell 은 `$` 앞에 백틱 필요

### 결과 리포트 열기
테스트 끝나면 여기서 예쁜 HTML 리포트 볼 수 있어:
```
D:\demo\build\reports\tests\test\index.html
```

---

## 6. 현재 커버리지 (2026-07-03 기준)

전체 테스트 수: **418개, 실패 0**

| 파일 | 종류 | 개수 |
|---|---|:---:|
| JwtUtilTest | 유닛 | 7 |
| LoginAttemptServiceTest | 유닛 | 6 |
| AuthServiceTest | 유닛 (Nested 5) | 27 |
| VideoActionServiceTest | 유닛 | 8 |
| VideoServiceTest | 유닛 (Nested 10) | 40 |
| SubscriptionServiceTest | 유닛 | 7 |
| CommentServiceTest | 유닛 (Nested 6) | 28 |
| PlaylistServiceTest | 유닛 | 18 |
| HistoryServiceTest | 유닛 (Nested 5) | 21 |
| ProfileServiceTest | 유닛 (Nested 4) | 21 |
| RefreshTokenServiceTest | 유닛 | 9 |
| VideoShareServiceTest | 유닛 | 9 |
| AdminServiceTest | 유닛 (Nested 6) | 15 |
| NotificationServiceTest | 유닛 (Nested 6) | 12 |
| CommentRepositoryTest | 리포지토리 | 6 |
| VideoRepositoryTest | 리포지토리 | 13 |
| VideoLikeRepositoryTest | 리포지토리 | 7 |
| SubscriptionRepositoryTest | 리포지토리 | 5 |
| PlaylistRepositoryTest | 리포지토리 | 6 |
| VideoSaveRepositoryTest | 리포지토리 | 4 |
| CommentLikeRepositoryTest | 리포지토리 | 5 |
| VideoHistoryRepositoryTest | 리포지토리 | 5 |
| VideoActionControllerMvcTest | 컨트롤러 | 5 |
| PlaylistControllerMvcTest | 컨트롤러 | 14 |
| CommentControllerMvcTest | 컨트롤러 | 11 |
| AuthControllerMvcTest | 컨트롤러 | 15 |
| SubscriptionControllerMvcTest | 컨트롤러 | 8 |
| VideoControllerMvcTest | 컨트롤러 | 22 |
| HistoryControllerMvcTest | 컨트롤러 | 11 |
| ProfileControllerMvcTest | 컨트롤러 | 10 |
| NotificationControllerMvcTest | 컨트롤러 | 10 |
| AdminControllerMvcTest | 컨트롤러 | 16 |
| VideoShareControllerMvcTest | 컨트롤러 | 6 |
| **FullFlowIntegrationTest** | **통합** | **10** |
| DemoApplicationTests | 컨텍스트 로딩 | 1 |

---

## 7. 자주 쓰는 도구 짧게 정리

| 도구 | 하는 일 |
|---|---|
| **JUnit 5** | 테스트의 뼈대. `@Test`, `@BeforeEach`, `@Nested` 같은 어노테이션 제공. |
| **AssertJ** | `assertThat(x).isEqualTo(y)` 같은 읽기 좋은 검증 문법. |
| **Mockito** | 가짜 객체 생성. `when(repo.findById(1L)).thenReturn(...)` 처럼 씀. |
| **MockMvc** | 서버 안 띄우고 HTTP 요청 시뮬레이션. |
| **H2** | 테스트용 인메모리 DB. MariaDB 대신 씀. |

---

## 8. 새 테스트 추가할 때 체크리스트

1. **어떤 층에서 검사할지** 정하기 (로직? DB? HTTP?)
2. `src/test/java/.../XxxTest.java` 만들기 (검사 대상과 같은 패키지)
3. 유닛이면 `@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks`
4. 리포지토리면 `@DataJpaTest` + `@ActiveProfiles("test")`
5. 컨트롤러면 `@WebMvcTest(XxxController.class)` + `@MockitoBean`
6. `./gradlew test` 로 초록불 확인
7. 커밋할 때 README/이 문서도 필요하면 업데이트

---

## 9. Spring Boot 4 관련 주의사항

버전이 4.0.6 이라 몇 가지 최신 이슈:

- `@DataJpaTest` 위치가 `org.springframework.boot.data.jpa.test.autoconfigure` (구버전 아님)
- `@WebMvcTest` 위치가 `org.springframework.boot.webmvc.test.autoconfigure`
- **Jackson 3.x** 사용 → `com.fasterxml.jackson.*` 대신 `tools.jackson.*`
- `@MockBean` 은 deprecated → `@MockitoBean` 씀
- `@WebMvcTest` 슬라이스 로딩 시 `JwtFilter`, `VideoAccessFilter` 가 딸려오면 의존성 에러 → `excludeFilters` 로 제외

---

이 문서는 상황 바뀌면 계속 업데이트하면 됨. 새 테스트 추가하거나 규칙이 바뀌면 여기 기록해두면 미래의 나(또는 다른 사람)가 편함.
