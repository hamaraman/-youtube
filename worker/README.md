# 로컬 GPU 트랜스코딩 워커

서버 CPU 대신 **집 PC의 GPU**로 영상 변환을 처리하는 워커입니다.

## 동작 방식

```
[업로드] 사용자 → 서버
   서버: 원본을 R2(videos/{uuid}.mp4)에 올림 → 즉시 원본 화질로 재생 가능
   서버: DB에 변환 작업(PENDING) 큐만 쌓음  (서버는 ffmpeg 안 돌림)

[변환] 집 PC 워커(이 스크립트)
   서버에 폴링 → 작업 claim
   R2에서 원본 다운로드 → 로컬 GPU(NVENC)로 main + 1080/720/480/360 변환
   (+ 썸네일 없으면 자동 생성)
   결과를 R2에 업로드 → 서버에 결과 URL 통보 → DB 갱신, 상태 DONE
```

PC가 꺼져 있으면 작업은 서버 DB 큐에 남아 대기하다가, 워커를 다시 켜면 순서대로 처리됩니다.
(점유된 채 3시간 넘게 멈춘 작업은 서버가 자동으로 다시 대기 상태로 회수합니다.)

## 서버 설정

서버 환경변수(.env)에 추가:

```
TRANSCODE_MODE=worker
WORKER_TOKEN=<길고 랜덤한 비밀문자열>
```

> `worker` 모드는 R2가 설정돼 있어야 동작합니다. R2 미설정 시 자동으로 기존 서버 변환(`server`)으로 폴백합니다.

## 워커 실행 (집 PC)

사전 준비: Python 3.9+, ffmpeg(`ffmpeg`/`ffprobe`가 PATH에 있어야 함), GPU 드라이버.

```bash
cd worker
pip install -r requirements.txt
cp .env.example .env      # Windows: copy .env.example .env
# .env 편집: SERVER_URL, WORKER_TOKEN(서버와 동일), R2_* (서버와 동일)
python transcode_worker.py
```

정상 실행 시:

```
[워커 시작] id=home-rtx server=https://mytube.it.com hw=nvenc poll=10s
```

영상을 업로드하면 워커 콘솔에 작업 로그가 찍히고, 변환이 끝나면 사이트에 해상도별 화질이 나타납니다.

## NVENC 확인

`HW_ACCEL=nvenc`로 GPU 인코딩이 되는지 빠르게 확인:

```bash
ffmpeg -hide_banner -encoders | findstr nvenc      # Windows
ffmpeg -hide_banner -encoders | grep nvenc         # Linux/Mac
```

`h264_nvenc`가 보이면 OK. 안 보이면 NVIDIA 드라이버/ffmpeg 빌드를 확인하세요.
GPU가 없으면 `HW_ACCEL=none`(CPU libx264)으로도 동작합니다.

## 24시간 상시 운영(선택)

PC를 켜둘 때만 변환되므로, 항상 돌리려면 Windows 작업 스케줄러 / NSSM 서비스 등록 또는
Linux systemd 서비스로 등록해 부팅 시 자동 실행하면 됩니다.
