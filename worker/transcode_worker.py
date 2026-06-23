#!/usr/bin/env python3
"""
로컬 GPU 트랜스코딩 워커.

서버(/api/worker)에 폴링하여 변환 작업을 가져온 뒤, MinIO에서 원본을 직접 받아
로컬 GPU(NVENC 등)로 main + 해상도별 변형을 만들고 MinIO에 업로드한 다음
결과 URL을 서버에 통보한다. PC가 켜져 있는 동안에만 동작하며, 꺼져 있으면
작업은 서버 DB 큐에 그대로 남아 대기한다.

실행:
    pip install -r requirements.txt
    cp .env.example .env   # 값 채우기
    python transcode_worker.py
"""

import os
import sys
import time
import socket
import shutil
import tempfile
import subprocess

import requests
import boto3
from botocore.config import Config

try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass

# Windows 한글 콘솔(cp949)에서도 로그의 ✓/✗/한글이 깨지거나 크래시하지 않도록 UTF-8 강제
try:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
except Exception:
    pass


def env(key, default=None, required=False):
    v = os.environ.get(key, default)
    if required and (v is None or str(v).strip() == ""):
        sys.exit(f"[설정 오류] 환경변수 {key} 가 필요합니다 (.env 확인).")
    return v


SERVER_URL    = env("SERVER_URL", required=True).rstrip("/")
WORKER_TOKEN  = env("WORKER_TOKEN", required=True)
WORKER_ID     = env("WORKER_ID", socket.gethostname())

MINIO_ENDPOINT   = env("MINIO_ENDPOINT", required=True)
MINIO_ACCESS_KEY = env("MINIO_ACCESS_KEY", required=True)
MINIO_SECRET_KEY = env("MINIO_SECRET_KEY", required=True)
MINIO_BUCKET     = env("MINIO_BUCKET", required=True)
MINIO_PUBLIC_URL = env("MINIO_PUBLIC_URL", required=True).rstrip("/")

FFMPEG        = env("FFMPEG", "ffmpeg")
FFPROBE       = env("FFPROBE", "ffprobe")
HW            = env("HW_ACCEL", "nvenc").strip().lower()   # nvenc / qsv / amf / none
POLL_INTERVAL = int(env("POLL_INTERVAL", "10"))
WORK_DIR      = env("WORK_DIR", tempfile.gettempdir())

ALL_HEIGHTS = [1080, 720, 480, 360]

s3 = boto3.client(
    "s3",
    endpoint_url=MINIO_ENDPOINT,
    aws_access_key_id=MINIO_ACCESS_KEY,
    aws_secret_access_key=MINIO_SECRET_KEY,
    region_name="auto",
    config=Config(s3={"addressing_style": "path"}),
)


# ---------------------------------------------------------------- ffprobe helpers
def probe_height(path):
    try:
        out = subprocess.run(
            [FFPROBE, "-v", "error", "-select_streams", "v:0",
             "-show_entries", "stream=height", "-of", "csv=p=0", path],
            capture_output=True, text=True, timeout=60,
        ).stdout.strip().splitlines()
        return int(out[0]) if out and out[0].strip() else 1 << 30
    except Exception:
        return 1 << 30


def probe_codec(path):
    try:
        out = subprocess.run(
            [FFPROBE, "-v", "error", "-select_streams", "v:0",
             "-show_entries", "stream=codec_name", "-of", "csv=p=0", path],
            capture_output=True, text=True, timeout=60,
        ).stdout.strip().splitlines()
        return out[0].strip().lower() if out else ""
    except Exception:
        return ""


def probe_duration(path):
    try:
        out = subprocess.run(
            [FFPROBE, "-v", "error", "-show_entries", "format=duration",
             "-of", "csv=p=0", path],
            capture_output=True, text=True, timeout=60,
        ).stdout.strip().splitlines()
        return float(out[0]) if out and out[0].strip() else 0.0
    except Exception:
        return 0.0


# ---------------------------------------------------------------- ffmpeg command
def encoder_args(high_quality):
    q = 23 if high_quality else 26
    if HW == "nvenc":
        return ["-c:v", "h264_nvenc", "-preset", "p4" if high_quality else "p1", "-cq", str(q)]
    if HW == "qsv":
        return ["-c:v", "h264_qsv", "-preset", "medium" if high_quality else "veryfast",
                "-global_quality", str(q)]
    if HW == "amf":
        return ["-c:v", "h264_amf", "-quality", "quality" if high_quality else "speed",
                "-rc", "cqp", "-qp_i", str(q), "-qp_p", str(q)]
    return ["-c:v", "libx264", "-preset", "fast", "-crf", str(q)]


def build_cmd(input_path, heights, main_out, var_outs, copy_main):
    """서버의 buildVariantCommand 와 동일한 출력 구조(메인 + 변형 한 번에)."""
    n = len(heights)
    cmd = [FFMPEG, "-y", "-i", input_path]

    if n > 0:
        if copy_main:
            fc = f"[0:v]split={n}" + "".join(f"[sv{i}]" for i in range(n))
            for i, h in enumerate(heights):
                fc += f";[sv{i}]scale=-2:{h}[ov{i}]"
        else:
            fc = f"[0:v]split={n + 1}[sv_main]" + "".join(f"[sv{i}]" for i in range(n))
            fc += ";[sv_main]null[ov_main]"
            for i, h in enumerate(heights):
                fc += f";[sv{i}]scale=-2:{h}[ov{i}]"
        cmd += ["-filter_complex", fc]

        if copy_main:
            cmd += ["-map", "0:v", "-map", "0:a?", "-c:v", "copy"]
        else:
            cmd += ["-map", "[ov_main]", "-map", "0:a?"] + encoder_args(True)
        cmd += ["-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", main_out]

        for i, h in enumerate(heights):
            cmd += ["-map", f"[ov{i}]", "-map", "0:a?"] + encoder_args(False)
            cmd += ["-c:a", "aac", "-b:a", "96k", "-movflags", "+faststart", var_outs[h]]
    else:
        if copy_main:
            cmd += ["-map", "0:v", "-map", "0:a?", "-c:v", "copy"]
        else:
            cmd += encoder_args(True)
        cmd += ["-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", main_out]

    return cmd


def make_thumbnail(input_path, thumb_path):
    dur = probe_duration(input_path)
    seek = dur * 0.1 if dur > 10 else max(dur * 0.5, 0)
    cmd = [FFMPEG, "-y", "-ss", f"{seek:.2f}", "-i", input_path,
           "-vframes", "1", "-q:v", "2", "-vf", "scale=1280:-2", thumb_path]
    subprocess.run(cmd, capture_output=True, timeout=120)
    return os.path.exists(thumb_path)


# ---------------------------------------------------------------- MinIO helpers
def minio_download(key, dest):
    s3.download_file(MINIO_BUCKET, key, dest)


def minio_upload(local, key, content_type):
    s3.upload_file(local, MINIO_BUCKET, key, ExtraArgs={"ContentType": content_type})
    return f"{MINIO_PUBLIC_URL}/{key}"


# ---------------------------------------------------------------- server API
def api_post(path, **kwargs):
    return requests.post(f"{SERVER_URL}{path}",
                         headers={"X-Worker-Token": WORKER_TOKEN},
                         timeout=30, **kwargs)


def claim_job():
    r = api_post(f"/api/worker/jobs/claim?workerId={WORKER_ID}")
    if r.status_code == 204:
        return None
    if r.status_code == 401:
        sys.exit("[인증 오류] WORKER_TOKEN 이 서버와 일치하지 않습니다.")
    r.raise_for_status()
    return r.json()


def report_result(job_id, result):
    api_post(f"/api/worker/jobs/{job_id}/result", json=result).raise_for_status()


def report_error(job_id, message):
    try:
        api_post(f"/api/worker/jobs/{job_id}/error", json={"message": message[:500]})
    except Exception as e:
        print(f"  ! 에러 통보 실패: {e}")


# ---------------------------------------------------------------- job processing
def process_job(job):
    job_id = job["jobId"]
    uuid = job["uuid"]
    input_key = job["inputKey"]
    need_thumb = job.get("needThumbnail", False)

    work = tempfile.mkdtemp(prefix=f"tj_{uuid}_", dir=WORK_DIR)
    created = []
    try:
        src = os.path.join(work, "src")
        print(f"  · 스토리지에서 원본 다운로드: {input_key}")
        minio_download(input_key, src)

        height = probe_height(src)
        codec = probe_codec(src)
        copy_main = codec == "h264"
        heights = [h for h in ALL_HEIGHTS if h <= height]
        print(f"  · 원본 {height}p / {codec or '?'} → 변형 {heights} (copyMain={copy_main}, hw={HW})")

        main_out = os.path.join(work, f"{uuid}.mp4")
        var_outs = {h: os.path.join(work, f"{uuid}_{h}p.mp4") for h in heights}

        cmd = build_cmd(src, heights, main_out, var_outs, copy_main)
        print("  · ffmpeg 변환 시작...")
        proc = subprocess.run(cmd, capture_output=True, text=True)
        if proc.returncode != 0:
            tail = (proc.stderr or "")[-800:]
            raise RuntimeError(f"ffmpeg exit {proc.returncode}: {tail}")

        result = {"mainUrl": None, "url1080": None, "url720": None,
                  "url480": None, "url360": None, "thumbnailUrl": None}

        print(f"  · main 업로드: videos/{uuid}.mp4")
        result["mainUrl"] = minio_upload(main_out, f"videos/{uuid}.mp4", "video/mp4")

        for h in heights:
            vp = var_outs[h]
            if os.path.exists(vp):
                key = f"videos/{uuid}_{h}p.mp4"
                print(f"  · {h}p 업로드: {key}")
                result[f"url{h}"] = minio_upload(vp, key, "video/mp4")

        if need_thumb:
            thumb = os.path.join(work, f"{uuid}_thumb.jpg")
            if make_thumbnail(main_out, thumb):
                key = f"thumbnails/{uuid}_thumb.jpg"
                print(f"  · 썸네일 업로드: {key}")
                result["thumbnailUrl"] = minio_upload(thumb, key, "image/jpeg")

        report_result(job_id, result)
        print(f"  ✓ 완료 jobId={job_id} videoId={job.get('videoId')}")
    except Exception as e:
        print(f"  ✗ 실패 jobId={job_id}: {e}")
        report_error(job_id, str(e))
    finally:
        shutil.rmtree(work, ignore_errors=True)


def main():
    print(f"[워커 시작] id={WORKER_ID} server={SERVER_URL} hw={HW} poll={POLL_INTERVAL}s")
    while True:
        try:
            job = claim_job()
        except SystemExit:
            raise
        except Exception as e:
            print(f"[폴링 오류] {e} — {POLL_INTERVAL}s 후 재시도")
            time.sleep(POLL_INTERVAL)
            continue

        if job is None:
            time.sleep(POLL_INTERVAL)
            continue

        print(f"[작업 수신] jobId={job['jobId']} uuid={job['uuid']}")
        process_job(job)
        # 작업을 막 끝냈으니 곧바로 다음 작업이 있는지 확인 (대기 없이)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n[종료] 사용자 중단")
