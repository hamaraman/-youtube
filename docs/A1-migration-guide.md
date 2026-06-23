# Oracle Ampere A1 이전 가이드 (MyTube)

현재 무료 AMD 인스턴스(1코어/1GB) → **Ampere A1(ARM, 최대 4 OCPU / 24GB, 영구 무료)** 로 옮겨
트랜스코딩 속도를 끌어올리기 위한 단계별 가이드.

> **핵심 안심 포인트**
> - 앱은 **Java JAR**라서 ARM이어도 **그대로 실행**됩니다 (재컴파일 불필요).
> - 영상 파일은 **VM 로컬 MinIO**(`~/mytube/minio-data`)에 있으니, 서버를 옮길 땐 이 디렉터리도 함께 복사해야 합니다.
> - 옮길 핵심 데이터는 ① MariaDB DB ② `.env` 시크릿 ③ Nginx 설정 ④ MinIO 데이터(`~/mytube/minio-data`) ⑤ (남아있다면) 로컬 업로드 파일 정도입니다.
> - 새 서버를 다 만든 뒤 **마지막에 DNS만 바꾸는** 방식이라, 잘못돼도 기존 서버로 되돌리기 쉽습니다.

---

## 현재 운영 환경 요약 (참고)

| 항목 | 값 |
|------|-----|
| 유저 / 호스트 | `ubuntu` / `mytube` |
| 앱 JAR | `/home/ubuntu/mytube/build/libs/demo-0.0.1-SNAPSHOT.jar` |
| systemd 서비스 | `mytube` (`/etc/systemd/system/mytube.service`) |
| 환경변수 파일 | `/home/ubuntu/.env` |
| DB | MariaDB `localhost:3306`, DB명 `mytube` |
| 웹서버 | Nginx → `localhost:8080`, HTTPS(Let's Encrypt) |
| 도메인 | `mytube.it.com`, `www.mytube.it.com` |
| 저장소 | MinIO (영상/썸네일) — VM 로컬 도커, `~/mytube/minio-data` |
| 배포 | GitHub Actions (`.github/workflows/deploy.yml`) → `ORACLE_HOST` 시크릿 |

---

## Phase 0. 시작 전 체크 (기존 서버에서)

기존 서버에 SSH 접속한 상태에서 현재 사양과 옮길 대상을 확인합니다.

```bash
# 현재 사양 (왜 느렸는지 확인용)
nproc; free -h

# 로컬 업로드 파일이 남아있는지 (MinIO로 다 옮겼으면 거의 비어있음)
du -sh /home/ubuntu/mytube/uploads 2>/dev/null

# 자바 버전 (앱은 Java 21 필요)
java -version

# .env 내용 확인 (새 서버로 그대로 복사할 시크릿)
cat /home/ubuntu/.env
```

`.env` 내용은 메모장에 따로 복사해 두세요. 새 서버에서 다시 만들어야 합니다.

---

## Phase 1. A1 인스턴스 생성 (OCI 콘솔)

1. [Oracle Cloud 콘솔](https://cloud.oracle.com) 로그인 → **Compute → Instances → Create instance**
2. **Image and shape** 에서 **Edit**:
   - Image: **Canonical Ubuntu 24.04** (또는 22.04)
   - Shape: **Ampere** 탭 → **VM.Standard.A1.Flex** 선택
   - OCPU: **4**, Memory: **24 GB** (무료 한도 최대치)
3. **Networking**: 기존과 같은 VCN/서브넷 선택 (Public subnet, public IP 할당 켜기)
4. **SSH keys**:
   - **Generate a key pair** 로 새로 만들고 **private key를 반드시 다운로드** 하거나,
   - 기존에 쓰던 공개키(`Add SSH keys → Paste`)를 등록.
   - → 이 키로 새 서버에 접속하게 됩니다. 잘 보관하세요.
5. **Create** 클릭. 생성된 인스턴스의 **Public IP** 를 메모합니다. (이하 `NEW_IP`)

> **A1 용량 부족(Out of capacity) 에러가 나면?**
> 무료 ARM은 인기가 많아 자주 막힙니다. 해결:
> - 다른 **Availability Domain(AD-1/2/3)** 으로 바꿔서 재시도
> - 시간대를 바꿔(새벽 등) 재시도
> - OCPU를 2로 낮춰 시도 (그래도 기존보다 2배)

### 1-1. 방화벽(포트) 열기 — 중요
Oracle은 기본적으로 막혀 있습니다. **두 군데** 다 열어야 합니다.

**(a) OCI 보안 목록(Security List)**: VCN → Subnet → Security List → Ingress Rules 에
`0.0.0.0/0` 소스로 **TCP 22, 80, 443** 추가.

**(b) 인스턴스 내부 iptables** (Oracle Ubuntu 이미지는 자체 방화벽이 있음):
```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

---

## Phase 2. 새 서버 기본 셋업

새 인스턴스에 SSH 접속합니다(다운로드한 키 사용):
```bash
ssh -i <새-키.key> ubuntu@NEW_IP
```

패키지 설치 (ARM64용 자동 설치됨):
```bash
sudo apt update && sudo apt upgrade -y

# Java 21 (Spring Boot 4.x 필요)
sudo apt install -y openjdk-21-jdk

# MariaDB, Nginx, FFmpeg, certbot
sudo apt install -y mariadb-server nginx ffmpeg certbot python3-certbot-nginx

# 버전 확인
java -version        # 21 확인
ffmpeg -version
```

---

## Phase 3. 데이터 이전

### 3-1. MariaDB 데이터 옮기기

**기존 서버에서** DB 덤프 생성:
```bash
# 비밀번호는 .env의 DB_PASSWORD 값 사용
mysqldump -u root -p mytube > ~/mytube_dump.sql
ls -lh ~/mytube_dump.sql
```

**내 PC로 가져왔다가 → 새 서버로 보내기** (또는 서버 간 직접 scp).
내 PC(Windows, L:\demo)에서:
```bash
# 기존 서버에서 덤프 받기
scp -i <기존-키.key> ubuntu@OLD_IP:~/mytube_dump.sql ./
# 새 서버로 보내기
scp -i <새-키.key> ./mytube_dump.sql ubuntu@NEW_IP:~/
```

**새 서버에서** DB 생성 + 복원:
```bash
# DB와 사용자 비밀번호를 기존과 동일하게 맞춤 (.env 재사용 위해)
sudo mysql -e "CREATE DATABASE mytube CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
# root 비밀번호를 기존과 동일하게 설정 (기존 DB_PASSWORD 값으로)
sudo mysql -e "ALTER USER 'root'@'localhost' IDENTIFIED BY '기존_DB_PASSWORD'; FLUSH PRIVILEGES;"

# 덤프 복원
mysql -u root -p mytube < ~/mytube_dump.sql
```

> 앱은 `localhost:3306`에 붙으므로, DB명/비밀번호만 기존과 같게 맞추면 `.env`를 그대로 쓸 수 있습니다.

### 3-2. `.env` 시크릿 옮기기

새 서버에서 `/home/ubuntu/.env` 를 기존 내용 그대로 생성:
```bash
nano /home/ubuntu/.env
# Phase 0에서 복사해 둔 내용 붙여넣기 (DB_*, JWT_SECRET, ADMIN_PASSWORD, MINIO_*, MAIL_* 등)
# 저장: Ctrl+O, Enter, Ctrl+X
chmod 600 /home/ubuntu/.env
```

### 3-3. 앱 JAR 배치

가장 쉬운 방법은 **GitHub Actions로 새로 배포**(Phase 6)하는 것이지만,
먼저 동작 확인을 위해 기존 JAR를 그대로 복사해도 됩니다(ARM에서도 실행됨):
```bash
mkdir -p /home/ubuntu/mytube/build/libs
# 기존 서버 → 내 PC → 새 서버, 또는 직접 scp
scp -i <새-키.key> ./demo-0.0.1-SNAPSHOT.jar \
    ubuntu@NEW_IP:/home/ubuntu/mytube/build/libs/
```

### 3-4. (선택) 로컬 업로드 파일

Phase 0에서 `uploads`에 파일이 남아 있었다면 통째로 복사:
```bash
rsync -avz -e "ssh -i <키>" ubuntu@OLD_IP:/home/ubuntu/mytube/uploads/ \
    /home/ubuntu/mytube/uploads/
```
(영상이 전부 MinIO에 있으면 이 단계는 건너뜀. 단 MinIO 데이터 `~/mytube/minio-data` 는 별도로 복사해야 함)

---

## Phase 4. systemd 서비스 등록

새 서버에서 서비스 파일 생성:
```bash
sudo nano /etc/systemd/system/mytube.service
```
아래 내용 붙여넣기 (기존과 동일):
```ini
[Unit]
Description=MyTube Application
After=network.target

[Service]
User=ubuntu
WorkingDirectory=/home/ubuntu/mytube
EnvironmentFile=/home/ubuntu/.env
ExecStart=/usr/bin/java -jar /home/ubuntu/mytube/build/libs/demo-0.0.1-SNAPSHOT.jar
Restart=on-failure
StandardOutput=append:/home/ubuntu/app.log
StandardError=append:/home/ubuntu/app.log

[Install]
WantedBy=multi-user.target
```
활성화 + 시작:
```bash
sudo systemctl daemon-reload
sudo systemctl enable mytube
sudo systemctl start mytube
sudo systemctl status mytube --no-pager | head -15
tail -f /home/ubuntu/app.log     # 정상 기동 로그 확인 (Ctrl+C로 종료)
```
앱이 8080에서 뜨는지 확인:
```bash
curl -I http://localhost:8080
```

---

## Phase 5. Nginx + HTTPS

### 5-1. Nginx 리버스 프록시
기존 서버의 `/etc/nginx/sites-available/` 설정을 그대로 옮기는 게 가장 안전합니다.
기존 서버에서:
```bash
cat /etc/nginx/sites-enabled/default   # 또는 사용 중인 설정 파일
```
→ 내용을 복사해 새 서버의 같은 파일에 붙여넣기.

> **주의**: 실시간 알림(SSE)용 `location /api/notifications/stream` 블록
> (`proxy_buffering off` 등)도 꼭 함께 옮기세요. 누락하면 알림이 끊깁니다.

```bash
sudo nginx -t && sudo systemctl reload nginx
```

### 5-2. HTTPS 인증서
DNS를 아직 안 바꿨으므로 인증서는 **DNS 전환 후** 발급하는 게 깔끔합니다.
→ Phase 6에서 DNS를 새 IP로 바꾼 뒤:
```bash
sudo certbot --nginx -d mytube.it.com -d www.mytube.it.com
```
(기존 `/etc/letsencrypt` 를 통째로 복사해 와도 되지만, 새로 발급이 더 단순합니다.)

---

## Phase 6. DNS 전환 + 배포 연결

### 6-1. 새 서버 단독 점검 (DNS 바꾸기 전)
내 PC에서 hosts로 새 IP를 임시 지정해 먼저 확인하면 안전합니다.
또는 `curl --resolve` 로 점검:
```bash
curl -I --resolve www.mytube.it.com:80:NEW_IP http://www.mytube.it.com
```

### 6-2. DNS 레코드 변경
도메인 DNS 관리(가비아/Cloudflare 등)에서
`mytube.it.com`, `www.mytube.it.com` 의 **A 레코드를 `NEW_IP` 로 변경**.
TTL을 낮게 설정해 두면 빨리 반영됩니다(5~10분).

전파 확인:
```bash
nslookup www.mytube.it.com
```
`NEW_IP` 로 바뀌면 Phase 5-2의 certbot 실행 → HTTPS 발급.

### 6-3. GitHub Secrets 업데이트
저장소 → **Settings → Secrets and variables → Actions** 에서:
- `ORACLE_HOST` → `NEW_IP`
- `ORACLE_SSH_KEY` → 새 서버 접속용 **private key 전체 내용**으로 교체
- `ORACLE_USERNAME` → `ubuntu` (그대로)

그 다음 `main`에 푸시(또는 Actions 수동 실행)하면 새 서버로 자동 배포됩니다.
→ 빌드된 최신 JAR가 ARM 서버에 올라가고 `systemctl restart mytube` 됩니다.

---

## Phase 7. 검증 & 마무리

- [ ] https://www.mytube.it.com 정상 접속 (자물쇠 표시)
- [ ] 로그인 / 영상 재생 정상
- [ ] **영상 업로드 후 트랜스코딩 속도 체감** (이번 이전의 핵심 목표)
- [ ] 알림(SSE) 동작
- [ ] `nproc` 가 4 인지 새 서버에서 확인
- [ ] 며칠간 문제 없으면 **기존 AMD 인스턴스 종료(Terminate)**

---

## 이전 후 추가 최적화 (선택)

A1으로 코어가 4개가 되면, 코드에서 동시 작업 수를 늘려 throughput을 더 높일 수 있습니다.
`VideoUploadService.java` 의 `BATCH_SEMAPHORE = new Semaphore(2)` 조정,
원본 재인코딩 제거(`-c copy`) 등은 별도로 도와드릴 수 있습니다.

---

## 막히면 자주 보는 곳

```bash
sudo systemctl status mytube            # 서비스 상태
tail -100 /home/ubuntu/app.log          # 앱 로그
sudo journalctl -u mytube -n 100        # systemd 로그
sudo nginx -t                           # nginx 설정 검사
sudo iptables -L -n                     # 방화벽(포트 막힘 점검)
```
