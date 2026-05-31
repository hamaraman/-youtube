# MyTube

YouTube를 모티브로 만든 영상 스트리밍 웹 서비스입니다.

🌐 **배포 주소:** https://www.mytube.it.com

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| 백엔드 | Spring Boot 4.0, Java 21, JPA/Hibernate |
| DB | MariaDB |
| 스토리지 | Cloudflare R2 (S3 호환) |
| 프론트엔드 | HTML / CSS / Vanilla JS |
| 인프라 | Oracle Cloud VM (Ubuntu), Nginx, Let's Encrypt |
| 배포 | GitHub Actions (self-hosted runner) |

---

## 주요 기능

### 영상
- 영상 업로드 (mp4, 로컬 → R2 자동 업로드)
- FFmpeg 백그라운드 변환 (1080p / 720p / 480p / 360p 해상도)
- YouTube URL embed 지원
- 영상 공개/비공개 설정
- 조회수 집계 (로그인: 중복 방지, 비로그인: 매 요청 +1)
- 시청 기록

### 사용자
- 회원가입 / 로그인 / 로그아웃
- 이메일 비밀번호 재설정 (Gmail SMTP, DB 토큰, 10분 만료)
- 프로필 수정 (닉네임, 채널명, 이미지, 배너, 소개글)

### 채널 & 구독
- 채널 구독 / 취소
- 구독 피드 — 구독한 채널 최신 영상 모아보기
- 사이드바 구독 채널 목록
- 타인 채널 페이지 (`/user.html?id=`) — 배너, 아바타, 영상 목록

### 검색
- 영상 제목 / 채널명 / 설명 검색
- 채널 검색 — 검색 결과에 채널 카드 표시, 바로 구독 가능
- 카테고리 필터

### 소셜
- 댓글 / 대댓글
- 댓글 수정 / 삭제
- 댓글 좋아요 👍
- 영상 좋아요 / 저장

### 알림
- 벨 아이콘 + 읽지 않은 수 뱃지 (30초 폴링)
- 4가지 알림 타입:
  - 🎬 구독 채널이 새 영상 업로드
  - 🔔 내 채널을 누군가 구독
  - 💬 내 영상에 댓글 / 답글
  - ❤️ 내 영상 / 댓글에 좋아요
- 댓글 알림 클릭 → 드롭다운에서 댓글 바로 확인
- 영상 알림 클릭 → 영상 페이지로 이동

### 무한 스크롤
- 홈 피드, 구독 피드, 채널 페이지 모두 무한 스크롤
- 페이지당 12개, IntersectionObserver 기반

### 관리자
- Admin 패널 (`/admin.html`)
- 영상 일괄 삭제, 유저 관리
- DB 기반 역할 관리 (`USER` / `ADMIN`) — 관리자 패널에서 권한 변경 가능

---

## 스크린샷

| 홈 | 시청 | 구독 피드 |
|-----|------|---------|
| 영상 그리드 + 카테고리 필터 | 멀티 해상도 플레이어 + 댓글 | 구독 채널 최신 영상 |

---

## 로컬 실행

```bash
# 1. application-local.properties 설정
spring.datasource.url=jdbc:mariadb://localhost:3307/mytube
spring.datasource.username=root
spring.datasource.password=yourpassword

# 2. 빌드 및 실행
./gradlew build -x test
java -jar build/libs/demo-0.0.1-SNAPSHOT.jar
```

---

## 배포 구조

```
GitHub Push
    → GitHub Actions (self-hosted runner on Oracle VM)
    → ./gradlew build
    → systemctl restart youtube-clone
```

- **서버:** Oracle Cloud VM (Ubuntu 22.04)
- **리버스 프록시:** Nginx → localhost:8080
- **SSL:** Let's Encrypt (certbot 자동 갱신)
- **스토리지:** Cloudflare R2 (영상, 썸네일, 변환 파일)
