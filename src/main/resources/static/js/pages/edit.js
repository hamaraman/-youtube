/* MyTube - pages/edit.js : 동영상 수정 페이지 */

async function initEditPage() {
    const editForm = document.getElementById("editForm");
    if (!editForm) return;

    const params = new URLSearchParams(window.location.search);
    const rawId = params.get("id");
    const pageMain = document.querySelector("main") || document.body;

    if (!rawId || Number.isNaN(Number(rawId))) {
        pageMain.innerHTML = `
            <section class="upload-page">
                <div class="upload-wizard-card">
                    <h2 class="upload-wizard-title">잘못된 수정 경로</h2>
                    <p style="color:#aaa; margin-top:12px;">수정할 영상 ID가 없거나 잘못됐어.</p>
                    <div style="margin-top:20px; display:flex; gap:12px;">
                        <a href="channel.html" class="upload-cancel upload-link-btn">채널로 돌아가기</a>
                        <a href="index.html" class="upload-cancel upload-link-btn">홈으로 가기</a>
                    </div>
                </div>
            </section>
        `;
        return;
    }

    const videoId = Number(rawId);
    const video = await fetchVideoById(videoId);

    if (!video) {
        pageMain.innerHTML = `
            <section class="upload-page">
                <div class="upload-wizard-card">
                    <h2 class="upload-wizard-title">수정할 영상을 찾지 못했어</h2>
                    <p style="color:#aaa; margin-top:12px;">삭제됐거나 권한이 없을 수 있어.</p>
                    <div style="margin-top:20px; display:flex; gap:12px;">
                        <a href="channel.html" class="upload-cancel upload-link-btn">채널로 돌아가기</a>
                        <a href="index.html" class="upload-cancel upload-link-btn">홈으로 가기</a>
                    </div>
                </div>
            </section>
        `;
        return;
    }

    const editTitle = document.getElementById("editTitle");
    const editDescription = document.getElementById("editDescription");
    const editThumbnail = document.getElementById("editThumbnail");
    const editThumbnailFile = document.getElementById("editThumbnailFile");
    const editPreviewImage = document.getElementById("editPreviewImage");
    const editEmbedUrl = document.getElementById("editEmbedUrl");
    const editChannel = document.getElementById("editChannel");
    const editAvatar = document.getElementById("editAvatar");
    const editCategory = document.getElementById("editCategory");
    const editDuration = document.getElementById("editDuration");
    const editSubmitBtn = document.getElementById("editSubmitBtn");

    editTitle.value = video.title || "";
    editDescription.value = video.description || "";
    editThumbnail.value = video.thumbnail || "";
    editEmbedUrl.value = video.embedUrl || "";
    editChannel.value = video.channel || "";
    editAvatar.value = video.avatar || "";
    editCategory.value = video.category || "코딩";
    editDuration.value = video.duration || "";

    document.querySelectorAll('input[name="editVisibility"]').forEach((radio) => {
        radio.checked = radio.value === (video.visibility || "공개");
    });

    if (editPreviewImage && video.thumbnail) {
        editPreviewImage.src = video.thumbnail;
    }

    let selectedThumbnailFile = null;
    if (editThumbnailFile) {
        editThumbnailFile.addEventListener("change", () => {
            const file = editThumbnailFile.files && editThumbnailFile.files[0];
            selectedThumbnailFile = file || null;
            if (file && editPreviewImage) {
                editPreviewImage.src = URL.createObjectURL(file);
            }
        });
    }

    let selectedVideoFile = null;
    const editVideoFileBtn = document.getElementById("editVideoFileBtn");
    const editVideoFileInput = document.getElementById("editVideoFile");
    const editVideoFileName = document.getElementById("editVideoFileName");
    if (editVideoFileBtn && editVideoFileInput) {
        editVideoFileBtn.addEventListener("click", () => editVideoFileInput.click());
        editVideoFileInput.addEventListener("change", () => {
            const file = editVideoFileInput.files && editVideoFileInput.files[0];
            selectedVideoFile = file || null;
            if (editVideoFileName) {
                editVideoFileName.textContent = file ? file.name : "";
            }
        });
    }

    editForm.addEventListener(
        "submit",
        async (event) => {
            event.preventDefault();

            const payload = {
                title: editTitle.value.trim(),
                description: editDescription.value.trim(),
                thumbnail: editThumbnail.value.trim(),
                embedUrl: normalizeEmbedUrl(editEmbedUrl.value),
                channel: editChannel.value.trim(),
                avatar: editAvatar.value.trim(),
                category: editCategory.value,
                duration: editDuration.value.trim(),
                visibility:
                    document.querySelector('input[name="editVisibility"]:checked')?.value || "공개"
            };

            if (!payload.title || !payload.description || !payload.channel || !payload.duration) {
                alert("제목, 설명, 채널명, 영상 길이를 입력해줘.");
                return;
            }

            editSubmitBtn.disabled = true;
            editSubmitBtn.textContent = "저장 중...";

            try {
                await updateVideoById(videoId, payload);
                if (selectedThumbnailFile) {
                    await replaceVideoThumbnail(videoId, selectedThumbnailFile);
                }

                if (selectedVideoFile) {
                    const statusBox = document.getElementById("videoEncodeStatus");
                    if (statusBox) statusBox.style.display = "";
                    editSubmitBtn.textContent = "업로드 중...";
                    await uploadVideoFileWithProgress(videoId, selectedVideoFile, statusBox);
                    editSubmitBtn.textContent = "인코딩 중...";
                    await pollEncodeStatus(videoId, statusBox);
                    setPendingToast("영상 교체가 완료되었습니다.");
                    window.location.href = getVideoUrl(videoId);
                } else {
                    setPendingToast("영상 수정이 완료되었습니다.");
                    window.location.href = getVideoUrl(videoId);
                }
            } catch (error) {
                alert(error.message || "수정 중 오류가 발생했어.");
                editSubmitBtn.disabled = false;
                editSubmitBtn.textContent = "수정 저장";
            }
        },
        { once: true }
    );
}

function uploadVideoFileWithProgress(videoId, file, statusEl) {
    return new Promise((resolve, reject) => {
        if (statusEl) {
            statusEl.innerHTML = `
                <div class="encode-status-box">
                    <div class="encode-status-row">
                        <div class="encode-spinner"></div>
                        <span class="encode-status-label">영상 업로드 중...</span>
                    </div>
                    <div class="upload-progress-wrap">
                        <div class="upload-progress-bar">
                            <div class="upload-progress-fill" id="uploadProgressFill"></div>
                        </div>
                    </div>
                </div>
            `;
        }
        const formData = new FormData();
        formData.append("videoFile", file);
        const xhr = new XMLHttpRequest();
        xhr.open("POST", `/api/videos/${videoId}/replace-video`);
        xhr.upload.addEventListener("progress", (e) => {
            if (e.lengthComputable) {
                const pct = Math.round((e.loaded / e.total) * 100);
                const fill = document.getElementById("uploadProgressFill");
                if (fill) fill.style.width = pct + "%";
                const lbl = statusEl && statusEl.querySelector(".encode-status-label");
                if (lbl) lbl.textContent = `영상 업로드 중... ${pct}%`;
            }
        });
        xhr.addEventListener("load", () => {
            if (xhr.status >= 200 && xhr.status < 300) {
                resolve();
            } else {
                let msg = "업로드 실패";
                try { msg = JSON.parse(xhr.responseText).error || msg; } catch {}
                reject(new Error(msg));
            }
        });
        xhr.addEventListener("error", () => reject(new Error("업로드 중 네트워크 오류")));
        xhr.send(formData);
    });
}

function pollEncodeStatus(videoId, statusEl) {
    const STEPS = ["QUEUED", "CONVERTING", "1080p", "720p", "480p", "360p", "UPLOADING", "DONE"];
    const LABELS = {
        QUEUED: "대기 중",
        CONVERTING: "H.264 변환 중",
        "1080p": "1080p 인코딩",
        "720p": "720p 인코딩",
        "480p": "480p 인코딩",
        "360p": "360p 인코딩",
        UPLOADING: "클라우드 업로드 중",
        DONE: "완료"
    };

    function renderSteps(current) {
        const currentIdx = STEPS.indexOf(current);
        return STEPS.map((s, i) => {
            const cls = i < currentIdx ? "encode-step is-done"
                : i === currentIdx ? "encode-step is-active"
                : "encode-step";
            return `<span class="${cls}">${LABELS[s] || s}</span>`;
        }).join("");
    }

    return new Promise((resolve, reject) => {
        function tick() {
            fetch(`/api/videos/${videoId}/encode-status`)
                .then(r => r.json())
                .then(data => {
                    const status = data.status || "QUEUED";
                    if (statusEl) {
                        const isError = status.startsWith("ERROR");
                        statusEl.innerHTML = `
                            <div class="encode-status-box${status === "DONE" ? " is-done" : isError ? " is-error" : ""}">
                                <div class="encode-status-row">
                                    ${status !== "DONE" && !isError ? '<div class="encode-spinner"></div>' : ""}
                                    <span class="encode-status-label">${isError ? "오류: " + status.slice(6) : (LABELS[status] || status)}</span>
                                </div>
                                ${!isError ? `<div class="encode-steps">${renderSteps(status)}</div>` : ""}
                            </div>
                        `;
                    }
                    if (status === "DONE") {
                        resolve();
                    } else if (status.startsWith("ERROR")) {
                        reject(new Error(status.slice(6) || "인코딩 오류"));
                    } else {
                        setTimeout(tick, 3000);
                    }
                })
                .catch(() => setTimeout(tick, 5000));
        }
        tick();
    });
}
