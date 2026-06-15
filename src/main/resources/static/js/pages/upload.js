/* MyTube - pages/upload.js : 업로드 페이지 */

function initUploadPage() {
    const uploadForm = document.getElementById("uploadForm");
    if (!uploadForm) return;

    const authMe = getAuthMe();

    const uploadTitle = document.getElementById("uploadTitle");
    const uploadChannel = document.getElementById("uploadChannel");
    const uploadAvatar = document.getElementById("uploadAvatar");
    const uploadCategory = document.getElementById("uploadCategory");
    const uploadDuration = document.getElementById("uploadDuration");
    const uploadDescription = document.getElementById("uploadDescription");
    const uploadEmbedUrl = document.getElementById("uploadEmbedUrl");
    const uploadThumbnailUrl = document.getElementById("uploadThumbnailUrl");
    const uploadThumbnailFile = document.getElementById("uploadThumbnailFile");
    const uploadVideoFile = document.getElementById("uploadVideoFile");
    const thumbnailPreview = document.getElementById("thumbnailPreview");
    const thumbnailPreviewEmpty = document.getElementById("thumbnailPreviewEmpty");
    const summaryThumbnail = document.getElementById("summaryThumbnail");
    const summaryThumbnailEmpty = document.getElementById("summaryThumbnailEmpty");

    const selectedVideoName = document.getElementById("selectedVideoName");

    const summaryTitle = document.getElementById("summaryTitle");
    const summaryChannel = document.getElementById("summaryChannel");
    const summaryVideoFile = document.getElementById("summaryVideoFile");
    const summaryCategory = document.getElementById("summaryCategory");
    const summaryDuration = document.getElementById("summaryDuration");
    const summaryVisibility = document.getElementById("summaryVisibility");

    const stepIndicators = [...document.querySelectorAll("[data-step-indicator]")];
    const stepPanels = [...document.querySelectorAll("[data-step-panel]")];
    const prevBtn = document.getElementById("uploadPrevBtn");
    const nextBtn = document.getElementById("uploadNextBtn");
    const submitBtn = document.getElementById("uploadSubmitBtn");

    let currentStep = 1;
    let isSubmitting = false;
    let thumbnailPreviewSrc = "";

    if (authMe.loggedIn && authMe.user) {
        if (uploadChannel && !uploadChannel.value.trim()) {
            uploadChannel.value =
                authMe.user.channelName ||
                authMe.user.nickname ||
                authMe.user.username ||
                "";
        }

        if (uploadAvatar && !uploadAvatar.value.trim()) {
            uploadAvatar.value = authMe.user.profileImage || "";
        }
    }

    function ensureErrorElement(target, key) {
        const errorId = `uploadError_${key}`;
        let errorEl = document.getElementById(errorId);

        if (!errorEl) {
            errorEl = document.createElement("p");
            errorEl.className = "upload-error-text";
            errorEl.id = errorId;
            target.insertAdjacentElement("afterend", errorEl);
        }

        return errorEl;
    }

    function setFieldError(inputEl, message, key, anchorEl = null) {
        const target = anchorEl || inputEl;
        if (!target) return;

        const errorEl = ensureErrorElement(target, key);
        errorEl.textContent = message || "";

        if (inputEl) {
            inputEl.classList.toggle("is-invalid", Boolean(message));
        }
    }

    function clearFieldError(inputEl, key) {
        const errorEl = document.getElementById(`uploadError_${key}`);
        if (errorEl) errorEl.textContent = "";
        if (inputEl) inputEl.classList.remove("is-invalid");
    }

    function validateTitle() {
        const value = uploadTitle?.value.trim() || "";
        if (!value) {
            setFieldError(uploadTitle, "제목을 입력해줘.", "title");
            return false;
        }
        clearFieldError(uploadTitle, "title");
        return true;
    }

    function validateDescription() {
        const value = uploadDescription?.value.trim() || "";
        if (!value) {
            setFieldError(uploadDescription, "설명을 입력해줘.", "description");
            return false;
        }
        clearFieldError(uploadDescription, "description");
        return true;
    }

    function validateChannel() {
        const value = uploadChannel?.value.trim() || "";
        if (!value) {
            setFieldError(uploadChannel, "채널명을 입력해줘.", "channel");
            return false;
        }
        clearFieldError(uploadChannel, "channel");
        return true;
    }

    function validateDurationField() {
        const value = uploadDuration?.value.trim() || "";
        if (!value) {
            setFieldError(uploadDuration, "영상 길이를 확인해줘.", "duration");
            return false;
        }
        clearFieldError(uploadDuration, "duration");
        return true;
    }

    function validateVideoFile() {
        const fileBox = uploadVideoFile?.closest(".upload-file-box") || uploadVideoFile;
        const hasFile = Boolean(uploadVideoFile?.files?.[0]);
        const hasEmbedUrl = Boolean(uploadEmbedUrl?.value?.trim());

        if (!hasFile && !hasEmbedUrl) {
            setFieldError(fileBox, "영상 파일을 선택하거나 YouTube URL을 입력해줘.", "videoFile", fileBox);
            return false;
        }

        clearFieldError(fileBox, "videoFile");
        return true;
    }

    function validateThumbnailField() {
        const fileBox = uploadThumbnailFile?.closest(".upload-file-box") || uploadThumbnailFile;
        const hasThumbnailUrl = Boolean(uploadThumbnailUrl?.value.trim());
        const hasThumbnailFile = Boolean(uploadThumbnailFile?.files?.[0]);
        const hasVideoFile = Boolean(uploadVideoFile?.files?.[0]);

        // 영상 파일이 있으면 썸네일 없어도 자동 생성
        if (!hasThumbnailUrl && !hasThumbnailFile && !hasVideoFile) {
            setFieldError(fileBox, "썸네일 URL을 입력하거나 파일을 선택해줘.", "thumbnail", fileBox);
            return false;
        }

        clearFieldError(fileBox, "thumbnail");
        return true;
    }

    function setThumbnailPreview(src) {
        thumbnailPreviewSrc = src || "";

        if (thumbnailPreviewSrc) {
            if (thumbnailPreview) {
                thumbnailPreview.src = thumbnailPreviewSrc;
                thumbnailPreview.hidden = false;
            }
            if (thumbnailPreviewEmpty) {
                thumbnailPreviewEmpty.hidden = true;
            }

            if (summaryThumbnail) {
                summaryThumbnail.src = thumbnailPreviewSrc;
                summaryThumbnail.hidden = false;
            }
            if (summaryThumbnailEmpty) {
                summaryThumbnailEmpty.hidden = true;
            }
        } else {
            if (thumbnailPreview) {
                thumbnailPreview.removeAttribute("src");
                thumbnailPreview.hidden = true;
            }
            if (thumbnailPreviewEmpty) {
                thumbnailPreviewEmpty.hidden = false;
            }

            if (summaryThumbnail) {
                summaryThumbnail.removeAttribute("src");
                summaryThumbnail.hidden = true;
            }
            if (summaryThumbnailEmpty) {
                summaryThumbnailEmpty.hidden = false;
            }
        }
    }

    function updateSummary() {
        const checkedVisibility = document.querySelector('input[name="uploadVisibility"]:checked');

        if (summaryTitle) summaryTitle.textContent = uploadTitle.value.trim() || "미입력";
        if (summaryChannel) summaryChannel.textContent = uploadChannel.value.trim() || "미입력";
        if (summaryVideoFile) summaryVideoFile.textContent = uploadVideoFile.files?.[0]?.name || "선택된 파일 없음";
        if (summaryCategory) summaryCategory.textContent = uploadCategory.value || "미선택";
        if (summaryDuration) summaryDuration.textContent = uploadDuration.value.trim() || "미입력";
        if (summaryVisibility) summaryVisibility.textContent = checkedVisibility ? checkedVisibility.value : "공개";
    }

    function setStep(step) {
        currentStep = step;

        stepIndicators.forEach((indicator, index) => {
            indicator.classList.toggle("is-active", index + 1 === step);
            indicator.classList.toggle("is-complete", index + 1 < step);
        });

        stepPanels.forEach((panel) => {
            panel.classList.toggle("is-active", Number(panel.dataset.stepPanel) === step);
        });

        if (prevBtn) prevBtn.style.visibility = step === 1 ? "hidden" : "visible";
        if (nextBtn) nextBtn.hidden = step === 3;
        if (submitBtn) {
            submitBtn.hidden = step !== 3;
            submitBtn.disabled = false;
            submitBtn.textContent = "업로드 완료";
        }
    }

    function validateStep(step) {
        if (step === 2) {
            const videoOk = validateVideoFile();
            const thumbOk = validateThumbnailField();
            return videoOk && thumbOk;
        }

        if (step === 3) {
            const titleOk = validateTitle();
            const descOk = validateDescription();
            const channelOk = validateChannel();
            const durationOk = validateDurationField();

            return titleOk && descOk && channelOk && durationOk;
        }

        return true;
    }

    async function handleUploadSubmit(event) {
        if (event) event.preventDefault();
        if (isSubmitting) return;

        const titleOk = validateTitle();
        const descOk = validateDescription();
        const channelOk = validateChannel();
        const durationOk = validateDurationField();
        const videoOk = validateVideoFile();
        const thumbOk = validateThumbnailField();

        if (!(titleOk && descOk && channelOk && durationOk && videoOk && thumbOk)) {
            return;
        }

        const title = uploadTitle.value.trim();
        const description = uploadDescription.value.trim();
        const channel = uploadChannel.value.trim();
        const avatar = uploadAvatar?.value.trim() || "";
        const category = uploadCategory.value;
        const duration = uploadDuration.value.trim();
        const embedUrl = normalizeEmbedUrl(uploadEmbedUrl.value);
        const thumbnailUrl = uploadThumbnailUrl.value.trim();
        const visibility =
            document.querySelector('input[name="uploadVisibility"]:checked')?.value || "공개";

        const videoFile = uploadVideoFile.files?.[0];
        const thumbnailFile = uploadThumbnailFile.files?.[0];

        isSubmitting = true;
        if (submitBtn) {
            submitBtn.disabled = true;
            submitBtn.textContent = "업로드 중...";
        }

        const progressWrap = document.getElementById("uploadProgressWrap");
        const progressBar = document.getElementById("uploadProgressBar");
        const progressText = document.getElementById("uploadProgressText");
        if (progressWrap) progressWrap.style.display = "block";

        const progressFileName = document.getElementById("uploadProgressFileName");
        const progressFileSize = document.getElementById("uploadProgressFileSize");
        if (progressFileName) progressFileName.textContent = videoFile ? videoFile.name : (thumbnailFile ? thumbnailFile.name : "");
        if (progressFileSize && videoFile) {
            const mb = (videoFile.size / (1024 * 1024)).toFixed(1);
            const gb = (videoFile.size / (1024 * 1024 * 1024)).toFixed(2);
            progressFileSize.textContent = videoFile.size >= 1024 * 1024 * 1024 ? `${gb} GB` : `${mb} MB`;
        }

        const formData = new FormData();
        formData.append("title", title);
        formData.append("description", description);
        formData.append("channel", channel);
        formData.append("avatar", avatar);
        formData.append("category", category);
        formData.append("duration", duration);
        formData.append("visibility", visibility);
        formData.append("embedUrl", embedUrl);
        if (videoFile) {
            formData.append("videoFile", videoFile);
        }

        if (thumbnailFile) {
            formData.append("thumbnailFile", thumbnailFile);
        } else if (autoSelectedThumbBlob) {
            formData.append("thumbnailFile", autoSelectedThumbBlob, "auto_thumb.jpg");
        } else {
            formData.append("thumbnailUrl", thumbnailUrl);
        }

        try {
            const result = await new Promise((resolve, reject) => {
                const xhr = new XMLHttpRequest();
                xhr.open("POST", "/api/upload");
                const progressLabel = document.getElementById("uploadProgressLabel");
                const convertNote = document.getElementById("uploadConvertNote");
                const progressSpeed = document.getElementById("uploadProgressSpeed");
                const uploadStartTime = Date.now();

                function fmtBytes(b) {
                    if (b >= 1024 * 1024 * 1024) return (b / (1024 * 1024 * 1024)).toFixed(2) + " GB";
                    if (b >= 1024 * 1024) return (b / (1024 * 1024)).toFixed(1) + " MB";
                    return (b / 1024).toFixed(0) + " KB";
                }
                function fmtEta(sec) {
                    if (sec < 60) return `${sec}초`;
                    const m = Math.floor(sec / 60), s = sec % 60;
                    return s > 0 ? `${m}분 ${s}초` : `${m}분`;
                }

                xhr.upload.onprogress = (e) => {
                    if (e.lengthComputable) {
                        const pct = Math.round((e.loaded / e.total) * 100);
                        if (progressBar) progressBar.style.width = pct + "%";
                        if (progressText) progressText.textContent = pct + "%";
                        if (pct >= 100) {
                            if (progressLabel) progressLabel.textContent = "변환 중 (백그라운드)...";
                            if (progressSpeed) progressSpeed.textContent = "";
                            if (convertNote) convertNote.style.display = "block";
                            if (submitBtn) submitBtn.textContent = "변환 중...";
                        } else {
                            const elapsed = (Date.now() - uploadStartTime) / 1000;
                            if (elapsed > 0.5) {
                                const speed = e.loaded / elapsed;
                                const remaining = e.total - e.loaded;
                                const etaSec = Math.round(remaining / speed);
                                if (progressSpeed) progressSpeed.textContent = `${fmtBytes(speed)}/s · 남은 시간 ${fmtEta(etaSec)}`;
                            }
                            if (submitBtn) submitBtn.textContent = `업로드 중... ${pct}%`;
                        }
                    } else {
                        const mb = (e.loaded / (1024 * 1024)).toFixed(1);
                        if (progressText) progressText.textContent = `${mb}MB 전송됨`;
                        if (submitBtn) submitBtn.textContent = `업로드 중... ${mb}MB`;
                    }
                };
                xhr.onload = () => {
                    try {
                        const data = JSON.parse(xhr.responseText);
                        if (xhr.status >= 200 && xhr.status < 300 && data.success) {
                            resolve(data);
                        } else {
                            reject(new Error(data.message || "업로드 실패"));
                        }
                    } catch {
                        reject(new Error("서버 응답을 처리할 수 없어."));
                    }
                };
                xhr.onerror = () => reject(new Error("네트워크 오류가 발생했어."));
                xhr.send(formData);
            });

            if (progressBar) progressBar.style.width = "100%";
            if (progressText) progressText.textContent = "완료!";
            const hadVideoFile = !!document.getElementById("uploadVideoFile")?.files?.[0];
            setPendingToast(hadVideoFile
                ? "업로드 완료! 해상도 변환 중이에요. 스튜디오에서 진행 상황을 확인해보세요."
                : "업로드가 완료되었습니다.");
            navigateTo("studio.html");
        } catch (error) {
            if (progressWrap) progressWrap.style.display = "none";
            alert(error.message || "업로드 중 오류가 발생했어.");
            isSubmitting = false;
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.textContent = "업로드 완료";
            }

            async function fetchMyHistoryVideos() {
                const response = await fetch("/api/my-history");
                if (!response.ok) {
                    throw new Error("시청 기록을 불러오지 못했어.");
                }
                return response.json();
            }
        }
    }

    prevBtn?.addEventListener("click", () => {
        if (currentStep > 1) setStep(currentStep - 1);
    });

    nextBtn?.addEventListener("click", () => {
        const targetStep = currentStep + 1;
        if (!validateStep(targetStep)) return;
        setStep(targetStep);
    });

    uploadTitle?.addEventListener("input", () => {
        validateTitle();
        updateSummary();
    });

    uploadDescription?.addEventListener("input", () => {
        validateDescription();
        updateSummary();
    });

    uploadChannel?.addEventListener("input", () => {
        validateChannel();
        updateSummary();
    });

    uploadDuration?.addEventListener("input", () => {
        validateDurationField();
        updateSummary();
    });

    uploadThumbnailUrl?.addEventListener("input", () => {
        const value = uploadThumbnailUrl.value.trim();
        if (value) {
            setThumbnailPreview(value);
        } else if (!uploadThumbnailFile.files?.length) {
            setThumbnailPreview("");
        }

        validateThumbnailField();
        updateSummary();
    });

    let autoSelectedThumbBlob = null;

    async function extractVideoThumbnails(file, count = 3) {
        return new Promise((resolve) => {
            const url = URL.createObjectURL(file);
            const video = document.createElement("video");
            video.src = url;
            video.muted = true;
            video.playsInline = true;
            video.preload = "metadata";

            video.addEventListener("loadedmetadata", async () => {
                const duration = video.duration;
                const positions = [0.1, 0.35, 0.6].map(p => Math.max(p * duration, 0.5));
                const results = [];

                for (const t of positions) {
                    await new Promise(res => {
                        video.currentTime = t;
                        video.addEventListener("seeked", () => {
                            const canvas = document.createElement("canvas");
                            const scale = 640 / video.videoWidth;
                            canvas.width = 640;
                            canvas.height = Math.round(video.videoHeight * scale);
                            canvas.getContext("2d").drawImage(video, 0, 0, canvas.width, canvas.height);
                            canvas.toBlob(blob => { results.push({ blob, dataUrl: canvas.toDataURL("image/jpeg", 0.85) }); res(); }, "image/jpeg", 0.85);
                        }, { once: true });
                    });
                }

                video.src = "";
                URL.revokeObjectURL(url);
                resolve(results);
            });

            video.addEventListener("error", () => { video.src = ""; URL.revokeObjectURL(url); resolve([]); });
        });
    }

    function renderThumbCandidates(candidates) {
        const section = document.getElementById("thumbCandidatesSection");
        const loading = document.getElementById("thumbCandidatesLoading");
        const list = document.getElementById("thumbCandidatesList");
        if (!section || !list) return;

        loading.style.display = "none";
        list.innerHTML = "";
        autoSelectedThumbBlob = null;

        candidates.forEach((c, i) => {
            const div = document.createElement("div");
            div.className = "thumb-candidate";
            div.innerHTML = `<img src="${c.dataUrl}" alt="썸네일 후보 ${i + 1}"><div class="thumb-candidate-label">${["처음", "중간", "후반"][i]}</div>`;
            div.addEventListener("click", () => {
                list.querySelectorAll(".thumb-candidate").forEach(el => el.classList.remove("is-selected"));
                div.classList.add("is-selected");
                autoSelectedThumbBlob = c.blob;
                setThumbnailPreview(c.dataUrl);
            });
            list.appendChild(div);
        });

        // 첫 번째 자동 선택
        if (candidates.length > 0) {
            list.querySelector(".thumb-candidate")?.click();
        }
    }

    uploadVideoFile?.addEventListener("change", async () => {
        const file = uploadVideoFile.files?.[0];
        if (selectedVideoName) {
            selectedVideoName.textContent = file ? file.name : "선택된 파일 없음";
        }

        validateVideoFile();

        const section = document.getElementById("thumbCandidatesSection");
        const loading = document.getElementById("thumbCandidatesLoading");
        const list = document.getElementById("thumbCandidatesList");

        if (!file) {
            uploadDuration.value = "";
            validateDurationField();
            updateSummary();
            if (section) section.style.display = "none";
            autoSelectedThumbBlob = null;
            return;
        }

        try {
            const durationSeconds = await readVideoDuration(file);
            uploadDuration.value = formatDuration(durationSeconds);
        } catch {
            uploadDuration.value = "";
        }

        // 썸네일 후보 추출
        if (section && loading && list) {
            section.style.display = "block";
            loading.style.display = "block";
            list.innerHTML = "";
            try {
                const candidates = await extractVideoThumbnails(file);
                renderThumbCandidates(candidates);
            } catch {
                loading.style.display = "none";
            }
        }

        validateDurationField();
        updateSummary();
    });

    uploadThumbnailFile?.addEventListener("change", () => {
        const file = uploadThumbnailFile.files?.[0];

        if (!file) {
            if (!uploadThumbnailUrl.value.trim()) {
                setThumbnailPreview("");
            }
            validateThumbnailField();
            return;
        }

        const reader = new FileReader();
        reader.onload = (event) => {
            setThumbnailPreview(String(event.target?.result || ""));
        };
        reader.readAsDataURL(file);

        validateThumbnailField();
    });

    [uploadAvatar, uploadCategory, uploadEmbedUrl].forEach((el) => {
        if (!el) return;
        el.addEventListener("input", updateSummary);
        el.addEventListener("change", updateSummary);
    });

    document.querySelectorAll('input[name="uploadVisibility"]').forEach((radio) => {
        radio.addEventListener("change", updateSummary);
    });

    uploadForm.addEventListener("submit", handleUploadSubmit);

    validateTitle();
    validateDescription();
    validateChannel();
    validateDurationField();
    validateVideoFile();
    validateThumbnailField();
    updateSummary();
    setStep(1);
}
