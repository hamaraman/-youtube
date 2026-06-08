const DEFAULT_AVATAR = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI4MCIgaGVpZ2h0PSI4MCI+PGNpcmNsZSBjeD0iNDAiIGN5PSI0MCIgcj0iNDAiIGZpbGw9IiM2MTYxNjEiLz48L3N2Zz4=";
const DEFAULT_THUMBNAIL = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxMjgwIiBoZWlnaHQ9IjcyMCI+PHJlY3Qgd2lkdGg9IjEyODAiIGhlaWdodD0iNzIwIiBmaWxsPSIjMjEyMTIxIi8+PHBvbHlnb24gcG9pbnRzPSI1NjAsMzEwIDU2MCw0MTAgNjgwLDM2MCIgZmlsbD0iIzQyNDI0MiIvPjwvc3ZnPg==";

document.addEventListener("error", function (e) {
    const img = e.target;
    if (img.tagName !== "IMG") return;
    if (img.src === DEFAULT_AVATAR || img.src === DEFAULT_THUMBNAIL) return;
    const isAvatar = img.classList.contains("avatar-image") || img.classList.contains("watch-channel-avatar");
    img.src = isAvatar ? DEFAULT_AVATAR : DEFAULT_THUMBNAIL;
}, true);

const defaultVideos = [];

function escapeHtml(text) {
    return String(text ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

function getAuthMe() {
    return window.__AUTH_ME__ || { loggedIn: false, user: null };
}

function requireAuthRedirect() {
    const me = getAuthMe();
    if (me.loggedIn) return true;

    const next = `${window.location.pathname.split("/").pop() || "index.html"}${window.location.search || ""}`;
    window.location.href = `login.html?next=${encodeURIComponent(next)}`;
    return false;
}

async function fetchUploadedVideos() {
    try {
        const response = await fetch("/api/videos");
        if (!response.ok) return [];
        const data = await response.json();
        return Array.isArray(data) ? data : [];
    } catch {
        return [];
    }
}

async function fetchSubscriptionStatus(channelOwnerId) {
    try {
        const res = await fetch(`/api/users/${channelOwnerId}/subscription-status`);
        if (!res.ok) return { subscribed: false, subscriberCount: 0 };
        return await res.json();
    } catch {
        return { subscribed: false, subscriberCount: 0 };
    }
}

async function toggleSubscription(channelOwnerId) {
    const res = await fetch(`/api/users/${channelOwnerId}/subscribe`, { method: "POST" });
    if (!res.ok) throw new Error("구독 처리 실패");
    return await res.json();
}

async function fetchMyVideos() {
    try {
        const response = await fetch("/api/my-videos");
        if (!response.ok) return [];
        const data = await response.json();
        return Array.isArray(data) ? data : [];
    } catch {
        return [];
    }
}

async function fetchMySavedVideos() {
    try {
        const response = await fetch("/api/my-saved-videos");
        if (!response.ok) return [];
        const data = await response.json();
        return Array.isArray(data) ? data : [];
    } catch {
        return [];
    }
}

async function fetchMyLikedVideos() {
    try {
        const response = await fetch("/api/my-liked-videos");
        if (!response.ok) return [];
        const data = await response.json();
        return Array.isArray(data) ? data : [];
    } catch {
        return [];
    }
}

async function fetchVideoById(id) {
    try {
        const response = await fetch(`/api/videos/${id}`);
        if (!response.ok) return null;
        return await response.json();
    } catch {
        return null;
    }
}

async function addVideoToHistory(videoId) {
    const response = await fetch(`/api/videos/${videoId}/history`, {
        method: "POST"
    });

    const result = await response.json().catch(() => ({}));

    if (!response.ok) {
        throw new Error(result.message || "시청 기록 저장 실패");
    }

    return result;
}

async function fetchMyHistoryVideos() {
    const response = await fetch("/api/my-history");
    if (!response.ok) {
        throw new Error("시청 기록을 불러오지 못했어.");
    }
    return response.json();
}

async function fetchCommentsByVideoId(id, page = 0, size = 10) {
    try {
        const response = await fetch(`/api/videos/${id}/comments?page=${page}&size=${size}`);
        if (!response.ok) return { comments: [], total: 0, hasMore: false };
        return await response.json();
    } catch {
        return { comments: [], total: 0, hasMore: false };
    }
}

async function createCommentByVideoId(id, content) {
    const response = await fetch(`/api/videos/${id}/comments`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({ content })
    });

    const result = await response.json().catch(() => ({}));

    if (!response.ok || !result.success) {
        throw new Error(result.message || "댓글 작성 실패");
    }

    return result.comment;
}

async function updateCommentById(commentId, content) {
    const response = await fetch(`/api/comments/${commentId}`, {
        method: "PUT",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({ content })
    });

    const result = await response.json().catch(() => ({}));

    if (!response.ok || !result.success) {
        throw new Error(result.message || "댓글 수정 실패");
    }

    return result.comment;
}

async function deleteCommentById(commentId) {
    const response = await fetch(`/api/comments/${commentId}`, {
        method: "DELETE"
    });

    const result = await response.json().catch(() => ({}));

    if (!response.ok || !result.success) {
        throw new Error(result.message || "댓글 삭제 실패");
    }

    return result;
}

async function createReplyByCommentId(commentId, content) {
    const response = await fetch(`/api/comments/${commentId}/replies`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ content })
    });
    const result = await response.json().catch(() => ({}));
    if (!response.ok || !result.success) throw new Error(result.message || "답글 작성 실패");
    return result.comment;
}

async function toggleLikeByVideoId(id) {
    const response = await fetch(`/api/videos/${id}/like`, {
        method: "POST"
    });

    const result = await response.json().catch(() => ({}));

    if (!response.ok || !result.success) {
        throw new Error(result.message || "좋아요 처리 실패");
    }

    return result;
}

async function toggleSaveByVideoId(id) {
    const response = await fetch(`/api/videos/${id}/save`, {
        method: "POST"
    });

    const result = await response.json().catch(() => ({}));

    if (!response.ok || !result.success) {
        throw new Error(result.message || "저장 처리 실패");
    }

    return result;
}

async function deleteVideoById(id) {
    const response = await fetch(`/api/videos/${id}`, {
        method: "DELETE"
    });

    const result = await response.json().catch(() => ({}));

    if (!response.ok || !result.success) {
        throw new Error(result.message || "삭제 실패");
    }

    return result;
}

async function updateVideoById(id, payload) {
    const response = await fetch(`/api/videos/${id}`, {
        method: "PUT",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify(payload)
    });

    const result = await response.json().catch(() => ({}));

    if (!response.ok || !result.success) {
        throw new Error(result.message || "수정 실패");
    }

    return result;
}

function makeFeedVideos(uploadedVideos = []) {
    return [...uploadedVideos, ...defaultVideos];
}

function getVideoUrl(id) {
    return `watch.html?v=${id}`;
}

function getEditUrl(id) {
    return `edit.html?id=${id}`;
}

function getShareUrl(videoId) {
    return `${window.location.origin}/share/video/${videoId}`;
}

function showShareModal(videoId, getCurrentTime) {
    document.getElementById("shareModal")?.remove();

    const baseUrl = getShareUrl(videoId);

    function buildUrl(withTime) {
        if (!withTime) return baseUrl;
        const t = Math.floor(getCurrentTime());
        return t > 0 ? `${baseUrl}?t=${t}` : baseUrl;
    }

    const modal = document.createElement("div");
    modal.id = "shareModal";
    modal.className = "share-modal-backdrop";
    modal.innerHTML = `
        <div class="share-modal" role="dialog" aria-modal="true" aria-label="공유">
            <div class="share-modal-header">
                <h3 class="share-modal-title">공유</h3>
                <button class="share-modal-close" id="shareModalClose" type="button" aria-label="닫기">
                    <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor"><path d="M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>
                </button>
            </div>
            <div class="share-modal-url-row">
                <input class="share-modal-input" id="shareModalInput" type="text" readonly value="${escapeHtml(baseUrl)}" />
                <button class="share-modal-copy-btn" id="shareModalCopyBtn" type="button">복사</button>
            </div>
            <label class="share-modal-timestamp">
                <input type="checkbox" id="shareTimestampCheck" />
                <span id="shareTimestampLabel">현재 시간부터 시작</span>
            </label>
        </div>
    `;
    document.body.appendChild(modal);
    requestAnimationFrame(() => modal.classList.add("show"));

    const input = modal.querySelector("#shareModalInput");
    const copyBtn = modal.querySelector("#shareModalCopyBtn");
    const check = modal.querySelector("#shareTimestampCheck");
    const label = modal.querySelector("#shareTimestampLabel");

    const t = Math.floor(getCurrentTime());
    if (t > 0) {
        const mins = Math.floor(t / 60);
        const secs = t % 60;
        label.textContent = `현재 시간부터 시작 (${mins > 0 ? mins + "분 " : ""}${secs}초)`;
    }

    check.addEventListener("change", () => {
        input.value = buildUrl(check.checked);
    });

    copyBtn.addEventListener("click", async () => {
        const url = input.value;
        try {
            const copied = await copyTextToClipboard(url);
            if (copied) {
                copyBtn.textContent = "복사됨!";
                copyBtn.classList.add("copied");
                setTimeout(() => {
                    copyBtn.textContent = "복사";
                    copyBtn.classList.remove("copied");
                }, 2000);
            } else {
                prompt("이 링크를 복사해줘.", url);
            }
        } catch {
            prompt("이 링크를 복사해줘.", url);
        }
    });

    function closeModal() {
        modal.classList.remove("show");
        modal.addEventListener("transitionend", () => modal.remove(), { once: true });
    }

    modal.querySelector("#shareModalClose").addEventListener("click", closeModal);
    modal.addEventListener("click", (e) => { if (e.target === modal) closeModal(); });
    document.addEventListener("keydown", function onKey(e) {
        if (e.key === "Escape") { closeModal(); document.removeEventListener("keydown", onKey); }
    });
}

async function copyTextToClipboard(text) {
    if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(text);
        return true;
    }

    const textarea = document.createElement("textarea");
    textarea.value = text;
    textarea.style.position = "fixed";
    textarea.style.left = "-9999px";
    textarea.style.top = "0";
    document.body.appendChild(textarea);
    textarea.focus();
    textarea.select();

    try {
        const ok = document.execCommand("copy");
        document.body.removeChild(textarea);
        return ok;
    } catch {
        document.body.removeChild(textarea);
        return false;
    }
}

let toastTimer = null;
const PENDING_TOAST_KEY = "youtube_clone_pending_toast";

function ensureRuntimeUi() {
    let toastContainer = document.querySelector(".toast-container");
    let toast = document.getElementById("toastMessage");

    if (!toastContainer || !toast) {
        toastContainer = document.createElement("div");
        toastContainer.className = "toast-container";

        toast = document.createElement("div");
        toast.className = "toast";
        toast.id = "toastMessage";

        toastContainer.appendChild(toast);
        document.body.appendChild(toastContainer);
    }

    let modalBackdrop = document.getElementById("confirmModalBackdrop");
    let modalMessage = document.getElementById("confirmModalMessage");
    let modalCancel = document.getElementById("confirmModalCancel");
    let modalConfirm = document.getElementById("confirmModalConfirm");

    if (!modalBackdrop) {
        modalBackdrop = document.createElement("div");
        modalBackdrop.className = "confirm-modal-backdrop";
        modalBackdrop.id = "confirmModalBackdrop";
        modalBackdrop.innerHTML = `
            <div class="confirm-modal" role="dialog" aria-modal="true" aria-labelledby="confirmModalTitle">
                <h3 class="confirm-modal-title" id="confirmModalTitle">정말 삭제할까요?</h3>
                <p class="confirm-modal-message" id="confirmModalMessage"></p>
                <div class="confirm-modal-actions">
                    <button type="button" class="confirm-modal-btn cancel" id="confirmModalCancel">취소</button>
                    <button type="button" class="confirm-modal-btn danger" id="confirmModalConfirm">삭제</button>
                </div>
            </div>
        `;
        document.body.appendChild(modalBackdrop);

        modalMessage = document.getElementById("confirmModalMessage");
        modalCancel = document.getElementById("confirmModalCancel");
        modalConfirm = document.getElementById("confirmModalConfirm");
    }

    if (!document.getElementById("runtime-ui-style")) {
        const style = document.createElement("style");
        style.id = "runtime-ui-style";
        style.textContent = `
            .toast-container {
                position: fixed;
                left: 50%;
                bottom: 24px;
                transform: translateX(-50%);
                z-index: 2200;
                pointer-events: none;
            }
            .toast {
                min-width: 220px;
                max-width: 420px;
                padding: 12px 16px;
                border-radius: 12px;
                background: rgba(28, 28, 28, 0.96);
                border: 1px solid rgba(255, 255, 255, 0.08);
                color: #fff;
                font-size: 14px;
                text-align: center;
                box-shadow: 0 10px 28px rgba(0, 0, 0, 0.32);
                opacity: 0;
                transform: translateY(10px);
                transition: opacity 0.22s ease, transform 0.22s ease;
            }
            .toast.show {
                opacity: 1;
                transform: translateY(0);
            }
            .saved-card,
            .liked-card {
                display: flex;
                flex-direction: column;
                gap: 10px;
            }
            .saved-card-actions,
            .liked-card-actions {
                display: flex;
                justify-content: flex-end;
            }
            .saved-remove-btn,
            .liked-remove-btn {
                border: 1px solid #303030;
                background: #181818;
                color: #fff;
                border-radius: 999px;
                height: 36px;
                padding: 0 14px;
                font-size: 13px;
                cursor: pointer;
                transition: background 0.18s ease, border-color 0.18s ease;
            }
            .saved-remove-btn:hover,
            .liked-remove-btn:hover {
                background: #272727;
                border-color: #3a3a3a;
            }
            .confirm-modal-backdrop {
                position: fixed;
                inset: 0;
                background: rgba(0, 0, 0, 0.58);
                display: flex;
                align-items: center;
                justify-content: center;
                padding: 20px;
                z-index: 2100;
                opacity: 0;
                pointer-events: none;
                transition: opacity 0.18s ease;
            }
            .confirm-modal-backdrop.show {
                opacity: 1;
                pointer-events: auto;
            }
            .confirm-modal {
                width: 100%;
                max-width: 420px;
                background: #181818;
                border: 1px solid #2d2d2d;
                border-radius: 20px;
                padding: 22px 20px 18px;
                box-shadow: 0 20px 60px rgba(0, 0, 0, 0.45);
                transform: translateY(10px) scale(0.98);
                transition: transform 0.18s ease;
            }
            .confirm-modal-backdrop.show .confirm-modal {
                transform: translateY(0) scale(1);
            }
            .confirm-modal-title {
                margin: 0 0 10px;
                font-size: 20px;
                color: #fff;
            }
            .confirm-modal-message {
                margin: 0;
                color: #b8b8b8;
                font-size: 14px;
                line-height: 1.65;
                white-space: pre-line;
                word-break: break-word;
            }
            .confirm-modal-actions {
                display: flex;
                justify-content: flex-end;
                gap: 10px;
                margin-top: 20px;
            }
            .confirm-modal-btn {
                min-width: 88px;
                height: 38px;
                padding: 0 14px;
                border: 1px solid #303030;
                border-radius: 999px;
                background: #202020;
                color: #fff;
                cursor: pointer;
                font-size: 14px;
            }
            .confirm-modal-btn:hover {
                background: #282828;
            }
            .confirm-modal-btn.danger {
                background: #fff;
                color: #111;
                border-color: #fff;
                font-weight: 700;
            }
            .watch-description-box {
                background: #181818;
                border-radius: 14px;
                padding: 14px 16px;
                margin-top: 14px;
                line-height: 1.7;
                white-space: pre-line;
            }
            .watch-description-box.is-collapsed .watch-description-text {
                display: -webkit-box;
                -webkit-line-clamp: 3;
                -webkit-box-orient: vertical;
                overflow: hidden;
            }
            .watch-description-toggle {
                margin-top: 10px;
                border: none;
                background: transparent;
                color: #fff;
                font-size: 13px;
                font-weight: 700;
                cursor: pointer;
                padding: 0;
            }
            .watch-description-meta {
                display: inline-block;
                margin-bottom: 8px;
                font-weight: 700;
            }
            .upload-error-text {
                            margin-top: 6px;
                color: #ff7b7b;
                font-size: 13px;
                line-height: 1.5;
            }
            .upload-input.is-invalid,
            .upload-select.is-invalid,
            .upload-textarea.is-invalid,
            .upload-file-box.is-invalid {
                border-color: #a33a3a !important;
                box-shadow: 0 0 0 1px rgba(163, 58, 58, 0.25);
            }
        `;
        document.head.appendChild(style);
    }

    return {
        toast,
        modalBackdrop,
        modalMessage,
        modalCancel,
        modalConfirm
    };
}

function showToast(message) {
    const { toast } = ensureRuntimeUi();
    if (!toast) return;

    toast.textContent = message;
    toast.classList.add("show");

    if (toastTimer) {
        clearTimeout(toastTimer);
    }

    toastTimer = setTimeout(() => {
        toast.classList.remove("show");
    }, 2200);
}

function setPendingToast(message) {
    sessionStorage.setItem(PENDING_TOAST_KEY, message);
}

function consumePendingToast() {
    const pending = sessionStorage.getItem(PENDING_TOAST_KEY);
    if (!pending) return;

    sessionStorage.removeItem(PENDING_TOAST_KEY);
    requestAnimationFrame(() => {
        showToast(pending);
    });
}

function confirmAction(message, confirmText = "삭제") {
    const { modalBackdrop, modalMessage, modalCancel, modalConfirm } = ensureRuntimeUi();

    return new Promise((resolve) => {
        let settled = false;

        function cleanup(result) {
            if (settled) return;
            settled = true;

            modalBackdrop.classList.remove("show");
            modalConfirm.textContent = confirmText;

            modalBackdrop.removeEventListener("click", onBackdropClick);
            modalCancel.removeEventListener("click", onCancel);
            modalConfirm.removeEventListener("click", onConfirm);
            document.removeEventListener("keydown", onKeydown);

            resolve(result);
        }

        function onCancel() {
            cleanup(false);
        }

        function onConfirm() {
            cleanup(true);
        }

        function onBackdropClick(event) {
            if (event.target === modalBackdrop) {
                cleanup(false);
            }
        }

        function onKeydown(event) {
            if (event.key === "Escape") {
                cleanup(false);
            }
        }

        modalMessage.textContent = message;
        modalConfirm.textContent = confirmText;
        modalBackdrop.classList.add("show");

        modalBackdrop.addEventListener("click", onBackdropClick);
        modalCancel.addEventListener("click", onCancel);
        modalConfirm.addEventListener("click", onConfirm);
        document.addEventListener("keydown", onKeydown);
    });
}

function getViewCountKey(videoId) {
    return `youtube_clone_view_count_${videoId}`;
}

function extractInitialViewCount(video) {
    if (typeof video.views === "number") return video.views;

    const match = String(video.views || "").replaceAll(",", "").match(/\d+/);
    return match ? Number(match[0]) : 0;
}

function loadViewCount(video) {
    if (typeof video.viewCount === "number") return video.viewCount;

    const raw = localStorage.getItem(getViewCountKey(video.id));
    if (raw !== null) {
        const parsed = Number(raw);
        return Number.isNaN(parsed) ? extractInitialViewCount(video) : parsed;
    }

    const initial = extractInitialViewCount(video);
    localStorage.setItem(getViewCountKey(video.id), String(initial));
    return initial;
}

function incrementViewCount(video) {
    const next = loadViewCount(video) + 1;
    localStorage.setItem(getViewCountKey(video.id), String(next));
    return next;
}

function formatCount(count) {
    return new Intl.NumberFormat("ko-KR").format(count);
}

function normalizeEmbedUrl(url) {
    const value = String(url || "").trim();
    if (!value) return "";

    if (value.includes("youtube.com/embed/")) return value;

    if (value.includes("youtube.com/watch?v=")) {
        const videoId = value.split("watch?v=")[1].split("&")[0];
        return `https://www.youtube.com/embed/${videoId}`;
    }

    if (value.includes("youtu.be/")) {
        const videoId = value.split("youtu.be/")[1].split("?")[0];
        return `https://www.youtube.com/embed/${videoId}`;
    }

    return value;
}

function tokenize(text) {
    return String(text || "")
        .toLowerCase()
        .replace(/[^a-z0-9가-힣\s]/g, " ")
        .split(/\s+/)
        .filter((token) => token.length >= 2);
}

// 세션마다 다른 추천 순서를 위한 랜덤 시드 (탭 열 때 한 번만 생성)
const _REC_SHUFFLE = Math.random();

function getRecommendationScore(baseVideo, targetVideo) {
    if (baseVideo.id === targetVideo.id) return -Infinity;

    let score = 0;

    const baseCategory = String(baseVideo.category || "").trim();
    const targetCategory = String(targetVideo.category || "").trim();

    // 카테고리 일치 (빈 카테고리는 점수 없음)
    if (baseCategory && targetCategory && baseCategory === targetCategory) {
        score += 50;
    }

    // 같은 채널
    if (String(baseVideo.channel || "").trim() === String(targetVideo.channel || "").trim()
            && String(baseVideo.channel || "").trim()) {
        score += 25;
    }

    // 키워드 겹침 (제목 + 설명 + 카테고리)
    const baseTokens = new Set([
        ...tokenize(baseVideo.title),
        ...tokenize(baseVideo.description),
        ...tokenize(baseVideo.category),
    ]);
    const targetTokens = [
        ...tokenize(targetVideo.title),
        ...tokenize(targetVideo.description),
        ...tokenize(targetVideo.category),
    ];
    targetTokens.forEach((t) => { if (baseTokens.has(t)) score += 5; });

    // 조회수 (로그 스케일, 최대 약 40점)
    const views = loadViewCount(targetVideo);
    if (views > 0) score += Math.log10(views + 1) * 10;

    // 좋아요 수 보너스 (로그 스케일, 최대 약 15점)
    const likes = Number(targetVideo.likeCount || 0);
    if (likes > 0) score += Math.log10(likes + 1) * 5;

    // 세션 내 다양성 (±3점 노이즈, id 기반으로 세션마다 다른 순서)
    score += ((Number(targetVideo.id) * 2654435761 + Math.floor(_REC_SHUFFLE * 1e9)) % 100) / 33;

    return score;
}

function getRecommendedVideos(baseVideo, allVideos, limit = 12) {
    return [...allVideos]
        .filter((video) => video.id !== baseVideo.id)
        .map((video) => ({
            ...video,
            _recScore: getRecommendationScore(baseVideo, video),
            _recTag: (() => {
                const bc = String(baseVideo.category || "").trim();
                const tc = String(video.category || "").trim();
                if (bc && tc && bc === tc) return "카테고리";
                if (String(baseVideo.channel || "").trim() &&
                    String(baseVideo.channel || "").trim() === String(video.channel || "").trim()) return "채널";
                return null;
            })(),
        }))
        .sort((a, b) => b._recScore - a._recScore || loadViewCount(b) - loadViewCount(a))
        .slice(0, limit);
}

function createVideoCard(video) {
    const viewCount = loadViewCount(video);
    const likeCount = Number(video.likeCount || 0);
    const commentCount = Number(video.commentCount || 0);
    const stats = [
        `조회수 ${formatCount(viewCount)}회`,
        likeCount > 0 ? `👍 ${formatCount(likeCount)}` : null,
        commentCount > 0 ? `💬 ${formatCount(commentCount)}` : null,
    ].filter(Boolean).join(" · ");

    return `
    <article class="card">
      <a href="${getVideoUrl(video.id)}" class="card-link">
        <div class="thumbnail-wrap">
          <img class="thumbnail-image" src="${escapeHtml(video.thumbnail)}" alt="${escapeHtml(video.title)}" />
          <span class="duration">${escapeHtml(video.duration || "0:00")}</span>
          <button type="button" class="card-pl-btn" data-video-id="${video.id}" title="재생목록에 추가" aria-label="재생목록에 추가">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M3 5h12v2H3V5zm0 4h12v2H3V9zm0 4h8v2H3v-2zm13 3v-6l5 3-5 3z"/></svg>
          </button>
        </div>
        <div class="meta">
          <img class="avatar-image" src="${escapeHtml(video.avatar || DEFAULT_AVATAR)}" alt="${escapeHtml(video.channel)}" />
          <div class="text">
            <h3>${escapeHtml(video.title)}</h3>
            <p class="channel-name">${escapeHtml(video.channel)}</p>
            <p class="video-info">${stats} · ${escapeHtml(video.date || "방금 전")}</p>
          </div>
        </div>
      </a>
    </article>
  `;
}

function createSavedVideoCard(video) {
    const viewCount = loadViewCount(video);
    const likeCount = Number(video.likeCount || 0);
    const commentCount = Number(video.commentCount || 0);
    const stats = [
        `조회수 ${formatCount(viewCount)}회`,
        likeCount > 0 ? `👍 ${formatCount(likeCount)}` : null,
        commentCount > 0 ? `💬 ${formatCount(commentCount)}` : null,
    ].filter(Boolean).join(" · ");

    return `
    <article class="card saved-card" data-saved-card-id="${video.id}">
      <a href="${getVideoUrl(video.id)}" class="card-link">
        <div class="thumbnail-wrap">
          <img class="thumbnail-image" src="${escapeHtml(video.thumbnail)}" alt="${escapeHtml(video.title)}" />
          <span class="duration">${escapeHtml(video.duration || "0:00")}</span>
        </div>
        <div class="meta">
          <img class="avatar-image" src="${escapeHtml(video.avatar || DEFAULT_AVATAR)}" alt="${escapeHtml(video.channel)}" />
          <div class="text">
            <h3>${escapeHtml(video.title)}</h3>
            <p class="channel-name">${escapeHtml(video.channel)}</p>
            <p class="video-info">${stats} · ${escapeHtml(video.date || "방금 전")}</p>
          </div>
        </div>
      </a>
      <div class="saved-card-actions">
        <button type="button" class="saved-remove-btn" data-unsave-id="${video.id}">저장 해제</button>
      </div>
    </article>
  `;
}

function createLikedVideoCard(video) {
    const viewCount = loadViewCount(video);
    const likeCount = Number(video.likeCount || 0);
    const commentCount = Number(video.commentCount || 0);
    const stats = [
        `조회수 ${formatCount(viewCount)}회`,
        likeCount > 0 ? `👍 ${formatCount(likeCount)}` : null,
        commentCount > 0 ? `💬 ${formatCount(commentCount)}` : null,
    ].filter(Boolean).join(" · ");

    return `
    <article class="card liked-card" data-liked-card-id="${video.id}">
      <a href="${getVideoUrl(video.id)}" class="card-link">
        <div class="thumbnail-wrap">
          <img class="thumbnail-image" src="${escapeHtml(video.thumbnail)}" alt="${escapeHtml(video.title)}" />
          <span class="duration">${escapeHtml(video.duration || "0:00")}</span>
        </div>
        <div class="meta">
          <img class="avatar-image" src="${escapeHtml(video.avatar || DEFAULT_AVATAR)}" alt="${escapeHtml(video.channel)}" />
          <div class="text">
            <h3>${escapeHtml(video.title)}</h3>
            <p class="channel-name">${escapeHtml(video.channel)}</p>
            <p class="video-info">${stats} · ${escapeHtml(video.date || "방금 전")}</p>
          </div>
        </div>
      </a>
      <div class="liked-card-actions">
        <button type="button" class="liked-remove-btn" data-unlike-id="${video.id}">좋아요 취소</button>
      </div>
    </article>
  `;
}

function createRecommendCard(video) {
    const viewCount = loadViewCount(video);
    const tag = video._recTag
        ? `<span class="recommend-tag">${escapeHtml(video._recTag)}</span>`
        : "";

    return `
    <a class="recommend-card" href="${getVideoUrl(video.id)}">
      <div class="recommend-thumb">
        <img src="${escapeHtml(video.thumbnail)}" alt="${escapeHtml(video.title)}" />
        <span class="recommend-duration">${escapeHtml(video.duration || "0:00")}</span>
      </div>
      <div class="recommend-info">
        <h4 class="recommend-title">${escapeHtml(video.title)}</h4>
        <p class="recommend-meta">${escapeHtml(video.channel)}${tag}</p>
        <p class="recommend-meta">조회수 ${formatCount(viewCount)}회 · ${escapeHtml(video.date || "방금 전")}</p>
      </div>
    </a>
  `;
}

function createManageCard(video) {
    const viewCount = loadViewCount(video);
    const likeCount = Number(video.likeCount || 0);
    const commentCount = Number(video.commentCount || 0);

    return `
    <article class="studio-row" data-id="${video.id}">
      <div class="studio-video-cell">
        <a href="${getVideoUrl(video.id)}" class="studio-thumb">
          <img src="${escapeHtml(video.thumbnail)}" alt="${escapeHtml(video.title)}" />
          <span class="studio-duration">${escapeHtml(video.duration || "0:00")}</span>
        </a>

        <div class="studio-video-info">
          <h4 class="studio-video-title">${escapeHtml(video.title)}</h4>
          <p class="studio-video-meta">${escapeHtml(video.channel)} · 조회수 ${formatCount(viewCount)}회 · ${escapeHtml(video.date || "방금 전")}</p>
          <p class="studio-video-desc">${escapeHtml(video.description || "")}</p>
        </div>
      </div>

      <div class="studio-cell-text">${escapeHtml(video.category || "미분류")}</div>
      <div class="studio-number">${formatCount(viewCount)}</div>
      <div class="studio-number">${formatCount(likeCount)}</div>
      <div class="studio-number">${formatCount(commentCount)}</div>
      <div>
        <span class="studio-status-badge">${escapeHtml(video.visibility || "공개")}</span>
      </div>
      <div class="studio-actions">
        <a href="${getVideoUrl(video.id)}" class="studio-action-btn edit">영상 보기</a>
        <button type="button" class="studio-action-btn edit" data-copy-id="${video.id}">링크 복사</button>
        <a href="${getEditUrl(video.id)}" class="studio-action-btn edit">수정</a>
        <button type="button" class="studio-action-btn delete" data-delete-id="${video.id}">삭제</button>
      </div>
    </article>
  `;
}

function createPlayerMarkup(video, resolutionOptions) {
    if (video.videoUrl) {
        const hasThumbnail = video.thumbnail && !video.thumbnail.startsWith("data:");
        const thumbHtml = hasThumbnail
            ? `<img class="cp-thumb-cover" id="cpThumbCover" src="${escapeHtml(video.thumbnail)}" alt="">`
            : "";
        const hasQuality = resolutionOptions && resolutionOptions.length > 0;
        const qualityOptionsHtml = hasQuality
            ? resolutionOptions.map((opt, i) =>
                `<div class="cp-set-option${i === 0 ? " active" : ""}" data-src="${escapeHtml(opt.src)}" data-label="${escapeHtml(opt.label)}">${escapeHtml(opt.label)}</div>`
              ).join("")
            : "";
        const settingsMenuHtml = `
          <div class="cp-settings-wrap" id="cpSettingsWrap">
            <button class="cp-btn" id="cpSettingsBtn" type="button" title="설정">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M19.14 12.94c.04-.3.06-.61.06-.94s-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.49.49 0 0 0-.59-.22l-2.39.96a7 7 0 0 0-1.62-.94l-.36-2.54a.484.484 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54a7.21 7.21 0 0 0-1.62.94l-2.39-.96a.48.48 0 0 0-.59.22L2.74 8.87a.48.48 0 0 0 .12.61l2.03 1.58c-.05.3-.07.62-.07.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.36 1.04.67 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54a7.21 7.21 0 0 0 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32a.48.48 0 0 0-.12-.61l-2.03-1.58zM12 15.6a3.6 3.6 0 1 1 0-7.2 3.6 3.6 0 0 1 0 7.2z"/></svg>
            </button>
            <div class="cp-settings-menu" id="cpSettingsMenu">
              <div class="cp-set-panel" id="cpSetMain">
                <div class="cp-set-item" id="cpSetSpeedItem">
                  <span>재생 속도</span><span class="cp-set-val" id="cpSetSpeedVal">보통</span>
                </div>
                ${hasQuality ? `<div class="cp-set-item" id="cpSetQualItem">
                  <span>화질</span><span class="cp-set-val" id="cpSetQualVal">${escapeHtml(resolutionOptions[0]?.label || "원본")}</span>
                </div>` : ""}
              </div>
              <div class="cp-set-panel" id="cpSetSpeedPanel" style="display:none">
                <div class="cp-set-back" id="cpSetSpeedBack">재생 속도</div>
                ${["0.5","0.75","1","1.25","1.5","2"].map(s =>
                  `<div class="cp-set-option${s==="1"?" active":""}" data-speed="${s}">${s==="1"?"보통":s+"x"}</div>`
                ).join("")}
              </div>
              ${hasQuality ? `<div class="cp-set-panel" id="cpSetQualPanel" style="display:none">
                <div class="cp-set-back" id="cpSetQualBack">화질</div>
                ${qualityOptionsHtml}
              </div>` : ""}
            </div>
          </div>`;
        return `<div class="custom-player" id="customPlayer">
          <video class="player-video" preload="metadata" src="${escapeHtml(video.videoUrl)}"></video>
          ${thumbHtml}
          <div class="cp-controls">
            <div class="cp-progress-wrap">
              <input type="range" class="cp-progress" id="cpProgress" min="0" max="100" step="0.1" value="0">
              <div class="cp-time-tooltip" id="cpTimeTooltip"></div>
            </div>
            <div class="cp-bar">
              <div class="cp-bar-left">
                <button class="cp-btn" id="cpPlay" type="button">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
                </button>
                <div class="cp-vol-wrap">
                  <button class="cp-btn" id="cpVol" type="button">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02z"/></svg>
                  </button>
                  <input type="range" class="cp-volume" id="cpVolume" min="0" max="1" step="0.05" value="1">
                </div>
                <span class="cp-time" id="cpTime">0:00 / 0:00</span>
              </div>
              <div class="cp-bar-right">
                ${settingsMenuHtml}
                <button class="cp-btn" id="cpMini" type="button" title="미니 플레이어">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M21 3H3c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H3V5h18v14zm-10-7h8v5h-8z"/></svg>
                </button>
                <button class="cp-btn" id="cpFs" type="button">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z"/></svg>
                </button>
              </div>
            </div>
          </div>
        </div>`;
    }

    if (video.embedUrl) {
        return `<iframe class="player-iframe"
          src="${escapeHtml(video.embedUrl)}"
          title="${escapeHtml(video.title)}"
          allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
          allowfullscreen
          referrerpolicy="strict-origin-when-cross-origin"></iframe>`;
    }

    return `<img src="${escapeHtml(video.thumbnail)}" alt="${escapeHtml(video.title)}" />`;
}

// ── 미니 플레이어 ──────────────────────────────────────────
const MP_KEY = '__miniPlayer__';
function getMpState() { try { return JSON.parse(localStorage.getItem(MP_KEY)); } catch { return null; } }
function setMpState(s) { localStorage.setItem(MP_KEY, JSON.stringify(s)); }
function clearMpState() { localStorage.removeItem(MP_KEY); }

function navigateTo(url, ms) {
    const t = ms ?? 180;
    document.body.style.transition = `opacity ${t}ms ease`;
    document.body.style.opacity = "0";
    setTimeout(() => { window.location.href = url; }, t);
}

function initResumeBar() {
    const state = getMpState();
    if (!state || !state.videoId) return;

    const params = new URLSearchParams(window.location.search);
    const vid = params.get('v') || params.get('id') || params.get('videoId');
    if (vid && String(vid) === String(state.videoId)) return;
    if (document.getElementById('resumeBar')) return;

    const t = Math.floor(state.currentTime || 0);
    const duration = state.duration || 0;
    const progress = duration > 0 ? Math.min(100, (t / duration) * 100).toFixed(1) : 0;
    const mins = Math.floor(t / 60);
    const secs = t % 60;
    const timeStr = mins > 0 ? `${mins}분 ${secs}초` : `${secs}초`;

    const bar = document.createElement('div');
    bar.id = 'resumeBar';
    bar.className = 'resume-bar';
    bar.innerHTML = `
        <div class="resume-bar-thumb-wrap">
            <img class="resume-bar-thumb" src="${escapeHtml(state.thumbnail || '')}" alt="" />
            ${duration > 0 ? `<div class="resume-bar-thumb-progress" style="width:${progress}%"></div>` : ''}
        </div>
        <div class="resume-bar-info">
            <div class="resume-bar-title">${escapeHtml(state.title || '')}</div>
            <div class="resume-bar-meta">${escapeHtml(state.channel || '')} · ${timeStr} 지점</div>
            ${duration > 0 ? `<div class="resume-bar-track"><div class="resume-bar-fill" style="width:${progress}%"></div></div>` : ''}
        </div>
        <button class="resume-bar-play" id="resumeBarPlay" type="button">이어보기</button>
        <button class="resume-bar-close" id="resumeBarClose" type="button" aria-label="닫기">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>
        </button>
    `;
    document.body.appendChild(bar);
    requestAnimationFrame(() => bar.classList.add('show'));

    bar.querySelector('#resumeBarPlay').addEventListener('click', () => {
        window.location.href = `watch.html?v=${state.videoId}&t=${t}`;
    });
    bar.querySelector('#resumeBarClose').addEventListener('click', () => {
        clearMpState();
        bar.classList.remove('show');
        bar.addEventListener('transitionend', () => bar.remove(), { once: true });
    });
}

function initMiniPlayerWatcher(videoData) {
    const player = document.getElementById("customPlayer");
    if (!player) return;
    const video = player.querySelector("video.player-video");
    if (!video) return;
    const miniBtn = document.getElementById("cpMini");

    const saveState = (playing) => setMpState({
        active: true,
        videoId: videoData.id,
        videoUrl: video.src || videoData.videoUrl,
        title: videoData.title,
        thumbnail: videoData.thumbnail,
        channel: videoData.channel,
        currentTime: video.currentTime,
        duration: video.duration || 0,
        playing: playing
    });

    let saveInterval;
    video.addEventListener('play', () => {
        saveState(true);
        clearInterval(saveInterval);
        saveInterval = setInterval(() => saveState(!video.paused), 3000);
    });
    video.addEventListener('pause', () => saveState(false));
    video.addEventListener('ended', () => { clearInterval(saveInterval); clearMpState(); });

    // 이어보기 버튼: 상태 저장 후 이전 페이지로 (이어보기 바로 이어짐)
    miniBtn?.addEventListener('click', (e) => {
        e.stopPropagation();
        saveState(!video.paused);
        const prev = document.referrer;
        const dest = (prev && !prev.includes('watch.html')) ? prev : 'index.html';
        navigateTo(dest);
    });
}
// ──────────────────────────────────────────────────────────

function initCustomPlayer() {
    const player = document.getElementById("customPlayer");
    if (!player) return;
    const video = player.querySelector("video.player-video");
    if (!video) return;

    const playBtn   = document.getElementById("cpPlay");
    const progress  = document.getElementById("cpProgress");
    const timeEl    = document.getElementById("cpTime");
    const volBtn    = document.getElementById("cpVol");
    const volSlider = document.getElementById("cpVolume");
    const fsBtn     = document.getElementById("cpFs");

    const I_PLAY   = `<svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>`;
    const I_PAUSE  = `<svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>`;
    const I_VOL    = `<svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02z"/></svg>`;
    const I_MUTE   = `<svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M16.5 12c0-1.77-1.02-3.29-2.5-4.03v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51C20.63 14.91 21 13.5 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3L3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06c1.38-.31 2.63-.95 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4L9.91 6.09 12 8.18V4z"/></svg>`;
    const I_FS     = `<svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z"/></svg>`;
    const I_EXIT_FS= `<svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M5 16h3v3h2v-5H5v2zm3-8H5v2h5V5H8v3zm6 11h2v-3h3v-2h-5v5zm2-11V5h-2v5h5V8h-3z"/></svg>`;

    function fmt(s) {
        s = Math.floor(s || 0);
        return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
    }

    // 첫 재생 시 썸네일 커버 페이드아웃
    const thumbCover = document.getElementById("cpThumbCover");
    if (thumbCover) {
        video.addEventListener("playing", () => {
            thumbCover.style.transition = "opacity 0.3s ease";
            thumbCover.style.opacity = "0";
            thumbCover.addEventListener("transitionend", () => thumbCover.remove(), { once: true });
        }, { once: true });
    }

    // 플레이어 클릭 → 재생/일시정지, 더블클릭 → 전체화면
    let clickTimer;
    player.addEventListener("click", (e) => {
        if (e.target.closest(".cp-controls")) return;
        clearTimeout(clickTimer);
        clickTimer = setTimeout(() => {
            video.paused ? video.play() : video.pause();
        }, 220);
    });
    player.addEventListener("dblclick", (e) => {
        if (e.target.closest(".cp-controls")) return;
        clearTimeout(clickTimer);
        document.fullscreenElement ? document.exitFullscreen() : player.requestFullscreen();
    });

    // 모바일 터치 지원
    let lastTapTime = 0;
    let tapTimer;
    player.addEventListener("touchstart", (e) => {
        bumpControls();

        if (e.target.closest(".cp-controls")) return;

        const now = Date.now();
        const timeSinceLast = now - lastTapTime;
        lastTapTime = now;

        if (timeSinceLast < 300) {
            // 더블탭 → 전체화면
            clearTimeout(tapTimer);
            document.fullscreenElement ? document.exitFullscreen() : player.requestFullscreen();
        } else {
            // 싱글탭 → 컨트롤 보이면 재생/일시정지, 안 보이면 컨트롤만 표시
            tapTimer = setTimeout(() => {
                if (player.classList.contains("show-controls")) {
                    video.paused ? video.play() : video.pause();
                }
            }, 220);
        }
    }, { passive: true });

    playBtn?.addEventListener("click", (e) => {
        e.stopPropagation();
        video.paused ? video.play() : video.pause();
    });
    video.addEventListener("play",  () => { if (playBtn) playBtn.innerHTML = I_PAUSE; });
    video.addEventListener("pause", () => { if (playBtn) playBtn.innerHTML = I_PLAY; });

    // 프로그레스바
    video.addEventListener("timeupdate", () => {
        if (!video.duration) return;
        if (progress) progress.value = (video.currentTime / video.duration) * 100;
        if (timeEl) timeEl.textContent = `${fmt(video.currentTime)} / ${fmt(video.duration)}`;
    });
    video.addEventListener("loadedmetadata", () => {
        if (timeEl) timeEl.textContent = `0:00 / ${fmt(video.duration)}`;
    });
    progress?.addEventListener("input", () => {
        if (video.duration) video.currentTime = (Number(progress.value) / 100) * video.duration;
    });

    // 볼륨
    volBtn?.addEventListener("click", (e) => {
        e.stopPropagation();
        video.muted = !video.muted;
        if (volBtn) volBtn.innerHTML = video.muted ? I_MUTE : I_VOL;
        if (volSlider) volSlider.value = video.muted ? 0 : video.volume;
    });
    volSlider?.addEventListener("input", (e) => {
        e.stopPropagation();
        video.volume = Number(volSlider.value);
        video.muted = video.volume === 0;
        if (volBtn) volBtn.innerHTML = video.muted ? I_MUTE : I_VOL;
    });

    // 전체화면
    fsBtn?.addEventListener("click", (e) => {
        e.stopPropagation();
        document.fullscreenElement ? document.exitFullscreen() : player.requestFullscreen();
    });
    document.addEventListener("fullscreenchange", () => {
        if (fsBtn) fsBtn.innerHTML = document.fullscreenElement ? I_EXIT_FS : I_FS;
    });

    // 컨트롤 자동 숨김
    let hideTimer;
    function bumpControls() {
        player.classList.add("show-controls");
        clearTimeout(hideTimer);
        if (!video.paused) hideTimer = setTimeout(() => player.classList.remove("show-controls"), 3000);
    }
    player.addEventListener("mousemove", bumpControls);
    player.addEventListener("mouseenter", bumpControls);
    video.addEventListener("pause", () => player.classList.add("show-controls"));
    video.addEventListener("play", bumpControls);

    // 프로그레스바 호버 툴팁
    const tooltip = document.getElementById("cpTimeTooltip");
    if (progress && tooltip) {
        progress.addEventListener("mousemove", (e) => {
            if (!video.duration) return;
            const rect = progress.getBoundingClientRect();
            const ratio = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
            tooltip.textContent = fmt(ratio * video.duration);
            const tipHalf = tooltip.offsetWidth / 2;
            const left = Math.max(tipHalf, Math.min(rect.width - tipHalf, e.clientX - rect.left));
            tooltip.style.left = left + "px";
            tooltip.classList.add("is-visible");
        });
        progress.addEventListener("mouseleave", () => {
            tooltip.classList.remove("is-visible");
        });
    }

    // 통합 설정 메뉴 (속도 + 화질)
    const settingsBtn   = document.getElementById("cpSettingsBtn");
    const settingsMenu  = document.getElementById("cpSettingsMenu");
    const settingsWrap  = document.getElementById("cpSettingsWrap");
    const setMain       = document.getElementById("cpSetMain");
    const setSpeedPanel = document.getElementById("cpSetSpeedPanel");
    const setQualPanel  = document.getElementById("cpSetQualPanel");
    const setSpeedVal   = document.getElementById("cpSetSpeedVal");
    const setQualVal    = document.getElementById("cpSetQualVal");

    const panels = [setMain, setSpeedPanel, setQualPanel];
    function showPanel(panel, goingBack) {
        panels.forEach(p => {
            if (!p) return;
            p.style.display = "none";
            p.classList.remove("is-entering", "is-entering-back");
        });
        if (!panel) return;
        panel.style.display = "block";
        void panel.offsetWidth;
        panel.classList.add(goingBack ? "is-entering-back" : "is-entering");
    }

    if (settingsBtn && settingsMenu) {
        settingsBtn.addEventListener("click", (e) => {
            e.stopPropagation();
            const opening = !settingsMenu.classList.contains("is-open");
            settingsMenu.classList.toggle("is-open");
            if (opening) showPanel(setMain);
        });
        document.addEventListener("click", (e) => {
            if (settingsWrap && !settingsWrap.contains(e.target)) settingsMenu.classList.remove("is-open");
        });

        // 메인 → 속도 패널
        document.getElementById("cpSetSpeedItem")?.addEventListener("click", () => showPanel(setSpeedPanel));
        document.getElementById("cpSetSpeedBack")?.addEventListener("click", () => showPanel(setMain, true));

        // 속도 옵션
        setSpeedPanel?.querySelectorAll(".cp-set-option").forEach(item => {
            item.addEventListener("click", (e) => {
                e.stopPropagation();
                const speed = parseFloat(item.dataset.speed);
                video.playbackRate = speed;
                const label = speed === 1 ? "보통" : speed + "x";
                if (setSpeedVal) setSpeedVal.textContent = label;
                setSpeedPanel.querySelectorAll(".cp-set-option").forEach(b => b.classList.remove("active"));
                item.classList.add("active");
                settingsMenu.classList.remove("is-open");
            });
        });

        // 메인 → 화질 패널
        document.getElementById("cpSetQualItem")?.addEventListener("click", () => showPanel(setQualPanel));
        document.getElementById("cpSetQualBack")?.addEventListener("click", () => showPanel(setMain, true));

        // 화질 옵션
        setQualPanel?.querySelectorAll(".cp-set-option").forEach(item => {
            item.addEventListener("click", (e) => {
                e.stopPropagation();
                const src = item.dataset.src;
                if (!src || item.classList.contains("active")) { settingsMenu.classList.remove("is-open"); return; }
                const t = video.currentTime;
                const wasPaused = video.paused;
                const canvas = document.createElement("canvas");
                canvas.width = video.videoWidth || 1280;
                canvas.height = video.videoHeight || 720;
                canvas.getContext("2d").drawImage(video, 0, 0, canvas.width, canvas.height);
                canvas.style.cssText = "position:absolute;inset:0;width:100%;height:100%;z-index:1;pointer-events:none;";
                player.appendChild(canvas);
                const removeCanvas = () => canvas.remove();
                video.src = src;
                video.load();
                video.addEventListener("loadedmetadata", () => {
                    video.currentTime = t;
                    if (!wasPaused) video.play().catch(() => {});
                }, { once: true });
                video.addEventListener("seeked", removeCanvas, { once: true });
                setTimeout(removeCanvas, 2000);
                if (setQualVal) setQualVal.textContent = item.dataset.label || item.textContent;
                setQualPanel.querySelectorAll(".cp-set-option").forEach(b => b.classList.remove("active"));
                item.classList.add("active");
                settingsMenu.classList.remove("is-open");
            });
        });
    }

    // 키보드 단축키
    document.addEventListener("keydown", (e) => {
        if (!document.getElementById("customPlayer")) return;
        const tag = document.activeElement?.tagName;
        if (tag === "INPUT" || tag === "TEXTAREA" || document.activeElement?.isContentEditable) return;

        switch (e.key) {
            case " ":
            case "k":
                e.preventDefault();
                video.paused ? video.play() : video.pause();
                break;
            case "ArrowLeft":
            case "j":
                e.preventDefault();
                video.currentTime = Math.max(0, video.currentTime - 5);
                break;
            case "ArrowRight":
            case "l":
                e.preventDefault();
                video.currentTime = Math.min(video.duration || 0, video.currentTime + 5);
                break;
            case "ArrowUp":
                e.preventDefault();
                video.volume = Math.min(1, video.volume + 0.1);
                video.muted = false;
                if (volBtn) volBtn.innerHTML = I_VOL;
                if (volSlider) volSlider.value = video.volume;
                break;
            case "ArrowDown":
                e.preventDefault();
                video.volume = Math.max(0, video.volume - 0.1);
                if (video.volume === 0) { video.muted = true; if (volBtn) volBtn.innerHTML = I_MUTE; }
                if (volSlider) volSlider.value = video.volume;
                break;
            case "f":
            case "F":
                e.preventDefault();
                document.fullscreenElement ? document.exitFullscreen() : player.requestFullscreen();
                break;
            case "m":
            case "M":
                e.preventDefault();
                video.muted = !video.muted;
                if (volBtn) volBtn.innerHTML = video.muted ? I_MUTE : I_VOL;
                if (volSlider) volSlider.value = video.muted ? 0 : video.volume;
                break;
        }
    });

    // 기존 디버그/에러 로깅 유지
    video.addEventListener("error", () => {
        const code = video.error?.code;
        const msg = ["", "ABORTED", "NETWORK", "DECODE", "SRC_NOT_SUPPORTED"][code] || "UNKNOWN";
        console.error("[Player] 영상 로드 실패:", msg, video.src);
    });
    video.addEventListener("loadedmetadata", () => {
        const w = video.videoWidth, h = video.videoHeight;
        console.log("[Player] 메타데이터 로드 완료:", w + "x" + h, video.src);
        if (w === 0 && h === 0) {
            const box = player.closest(".player-box");
            if (box) box.innerHTML = `<div style="display:flex;flex-direction:column;align-items:center;justify-content:center;min-height:200px;color:#aaa;gap:10px;padding:24px;aspect-ratio:16/9;"><span style="font-size:36px;">⚠️</span><span style="font-size:15px;text-align:center;">영상 트랙이 없습니다.<br>오디오 전용이거나 손상된 파일입니다.</span></div>`;
        }
    });
    video.addEventListener("playing", () => console.log("[Player] 재생 시작:", video.src));
    video.addEventListener("stalled", () => console.warn("[Player] 데이터 로드 정지 (stalled):", video.src));
    console.log("[Player] src =", video.src);
}

function createReplyItemHtml(r) {
    const rLetter = String(r.author || "?").trim().charAt(0) || "?";
    return `
    <div class="comment-item reply-item" data-comment-id="${r.id}">
      <div class="comment-avatar reply-avatar">${escapeHtml(rLetter)}</div>
      <div class="comment-content">
        <div class="comment-author-row">
          <span class="comment-author">${escapeHtml(r.author || "사용자")}</span>
          <span class="comment-time">${escapeHtml(r.time || "방금 전")}</span>
        </div>
        <p class="comment-text" data-comment-text>${escapeHtml(r.text || "")}</p>
        <div style="display:flex;gap:8px;margin-top:6px;flex-wrap:wrap;align-items:center;">
          <button type="button" class="comment-like-btn${r.isLiked ? " is-liked" : ""}" data-comment-like="${r.id}" data-liked="${r.isLiked ? "true" : "false"}">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M1 21h4V9H1v12zm22-11c0-1.1-.9-2-2-2h-6.31l.95-4.57.03-.32c0-.41-.17-.79-.44-1.06L14.17 1 7.59 7.59C7.22 7.95 7 8.45 7 9v10c0 1.1.9 2 2 2h9c.83 0 1.54-.5 1.84-1.22l3.02-7.05c.09-.23.14-.47.14-.73v-2z"/></svg>
            <span class="comment-like-count">${r.likeCount > 0 ? r.likeCount : ""}</span>
          </button>
        </div>
        ${(r.isMine || r.mine) ? `
          <div class="comment-owner-actions" style="display:flex;gap:8px;margin-top:6px;flex-wrap:wrap;">
            <button type="button" class="comment-btn-edit" data-comment-edit="${r.id}" style="border:none;background:transparent;color:#aaa;cursor:pointer;padding:0;font-size:13px;">수정</button>
            <button type="button" class="comment-btn-delete" data-comment-delete="${r.id}" style="border:none;background:transparent;color:#ff8d8d;cursor:pointer;padding:0;font-size:13px;">삭제</button>
          </div>
          <div class="comment-edit-box" data-comment-edit-box="${r.id}" hidden style="margin-top:10px;">
            <input type="text" class="comment-input" data-comment-edit-input="${r.id}" value="${escapeHtml(r.text || "")}" maxlength="300" />
            <div class="comment-form-actions" style="margin-top:8px;">
              <button type="button" class="comment-btn cancel" data-comment-edit-cancel="${r.id}">취소</button>
              <button type="button" class="comment-btn submit" data-comment-edit-save="${r.id}">저장</button>
            </div>
          </div>` : ""}
      </div>
    </div>`;
}

function createCommentItem(comment) {
    const firstLetter = String(comment.author || "?").trim().charAt(0) || "?";
    const replies = comment.replies || [];
    const isMine = comment.isMine || comment.mine;

    return `
    <div class="comment-item" data-comment-id="${comment.id}">
      <div class="comment-avatar">${escapeHtml(firstLetter)}</div>
      <div class="comment-content">
        <div class="comment-author-row">
          <span class="comment-author">${escapeHtml(comment.author || "사용자")}</span>
          <span class="comment-time">${escapeHtml(comment.time || "방금 전")}</span>
        </div>
        <p class="comment-text" data-comment-text>${escapeHtml(comment.text || "")}</p>
        <div style="display:flex;gap:10px;margin-top:8px;flex-wrap:wrap;align-items:center;">
          <button type="button" class="comment-like-btn${comment.isLiked ? " is-liked" : ""}" data-comment-like="${comment.id}" data-liked="${comment.isLiked ? "true" : "false"}">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M1 21h4V9H1v12zm22-11c0-1.1-.9-2-2-2h-6.31l.95-4.57.03-.32c0-.41-.17-.79-.44-1.06L14.17 1 7.59 7.59C7.22 7.95 7 8.45 7 9v10c0 1.1.9 2 2 2h9c.83 0 1.54-.5 1.84-1.22l3.02-7.05c.09-.23.14-.47.14-.73v-2z"/></svg>
            <span class="comment-like-count">${comment.likeCount > 0 ? comment.likeCount : ""}</span>
          </button>
          <button type="button" class="comment-reply-btn" data-reply-to="${comment.id}" style="border:none;background:transparent;color:#aaa;cursor:pointer;padding:0;font-size:13px;">답글</button>
          ${isMine ? `
            <button type="button" class="comment-btn-edit" data-comment-edit="${comment.id}" style="border:none;background:transparent;color:#aaa;cursor:pointer;padding:0;font-size:13px;">수정</button>
            <button type="button" class="comment-btn-delete" data-comment-delete="${comment.id}" style="border:none;background:transparent;color:#ff8d8d;cursor:pointer;padding:0;font-size:13px;">삭제</button>` : ""}
        </div>
        ${isMine ? `
          <div class="comment-edit-box" data-comment-edit-box="${comment.id}" hidden style="margin-top:10px;">
            <input type="text" class="comment-input" data-comment-edit-input="${comment.id}" value="${escapeHtml(comment.text || "")}" maxlength="300" />
            <div class="comment-form-actions" style="margin-top:10px;">
              <button type="button" class="comment-btn cancel" data-comment-edit-cancel="${comment.id}">취소</button>
              <button type="button" class="comment-btn submit" data-comment-edit-save="${comment.id}">저장</button>
            </div>
          </div>` : ""}
        <div class="reply-form-wrap" data-reply-form="${comment.id}" hidden style="margin-top:10px;">
          <input type="text" class="comment-input" data-reply-input="${comment.id}" placeholder="답글 추가..." maxlength="300" />
          <div class="comment-form-actions" style="margin-top:8px;">
            <button type="button" class="comment-btn cancel" data-reply-cancel="${comment.id}">취소</button>
            <button type="button" class="comment-btn submit" data-reply-submit="${comment.id}" disabled>답글</button>
          </div>
        </div>
        <div class="replies-list" data-replies-container="${comment.id}">
          ${replies.map(createReplyItemHtml).join("")}
        </div>
      </div>
    </div>`;
}

function renderCommentList(commentListEl, commentsCountEl, comments, serverTotal, hasMore, onLoadMore) {
    if (!commentListEl || !commentsCountEl) return;

    const displayTotal = serverTotal != null
        ? serverTotal
        : comments.reduce((sum, c) => sum + 1 + (c.replies?.length || 0), 0);
    commentsCountEl.textContent = `${formatCount(displayTotal)}개의 댓글`;

    if (comments.length === 0) {
        commentListEl.innerHTML = `<p class="comment-empty">아직 댓글이 없습니다.</p>`;
        return;
    }

    let html = comments.map(createCommentItem).join("");
    if (hasMore && onLoadMore) {
        html += `<button class="comment-load-more" id="commentLoadMore" type="button">댓글 더 보기</button>`;
    }
    commentListEl.innerHTML = html;

    if (hasMore && onLoadMore) {
        document.getElementById("commentLoadMore")?.addEventListener("click", onLoadMore);
    }
}

function readVideoDuration(file) {
    return new Promise((resolve, reject) => {
        const tempVideo = document.createElement("video");
        const objectUrl = URL.createObjectURL(file);

        const cleanup = () => URL.revokeObjectURL(objectUrl);
        const timer = setTimeout(() => { cleanup(); reject(new Error("timeout")); }, 8000);

        tempVideo.preload = "metadata";

        tempVideo.onloadedmetadata = () => {
            clearTimeout(timer);
            const duration = tempVideo.duration;
            cleanup();
            resolve(duration);
        };

        tempVideo.onerror = () => {
            clearTimeout(timer);
            cleanup();
            reject(new Error("duration read failed"));
        };

        tempVideo.src = objectUrl;
    });
}

function formatDuration(seconds) {
    const totalSeconds = Math.floor(seconds);
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const secs = totalSeconds % 60;

    if (hours > 0) {
        return `${hours}:${String(minutes).padStart(2, "0")}:${String(secs).padStart(2, "0")}`;
    }

    return `${minutes}:${String(secs).padStart(2, "0")}`;
}

async function initSubscriptionSidebar() {
    const sidebar = document.querySelector(".sidebar");
    if (!sidebar) return;

    const authMe = getAuthMe();
    if (!authMe?.loggedIn) return;
    const currentPage = document.body.dataset.page || "";

    // 구독 피드 링크 주입 (subscriptions.html 외 모든 페이지)
    if (currentPage !== "subscriptions") {
        const firstSection = sidebar.querySelector(".sidebar-section");
        if (firstSection && !firstSection.querySelector('a[href="subscriptions.html"]')) {
            firstSection.insertAdjacentHTML("beforeend", `
                <a href="subscriptions.html" class="sidebar-link">
                    <span class="sidebar-icon">
                        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M10 18v-2h4v2h-4Zm-4-5v-2h12v2H6Zm-3-5V6h18v2H3Z"/></svg>
                    </span>
                    <span class="sidebar-label">구독</span>
                </a>
            `);
        }
    }

    // 재생목록 링크 주입
    const secondSection = sidebar.querySelectorAll(".sidebar-section")[1];
    if (secondSection && !secondSection.querySelector('a[href="playlist.html"]')) {
        const historyLink = secondSection.querySelector('a[href="history.html"]');
        const plLink = `
            <a href="playlist.html" class="sidebar-link${currentPage === "playlist" ? " is-active" : ""}">
                <span class="sidebar-icon">
                    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 5h12v2H3V5zm0 4h12v2H3V9zm0 4h8v2H3v-2zm13 3v-6l5 3-5 3z"/></svg>
                </span>
                <span class="sidebar-label">재생목록</span>
            </a>`;
        if (historyLink) historyLink.insertAdjacentHTML("afterend", plLink);
        else secondSection.insertAdjacentHTML("beforeend", plLink);
    }

}

async function initUserPage() {
    const params = new URLSearchParams(window.location.search);
    const userId = params.get("id");
    if (!userId) { window.location.href = "index.html"; return; }

    const bannerWrap = document.getElementById("userBannerWrap");
    const avatarEl   = document.getElementById("userAvatar");
    const nameEl     = document.getElementById("userChannelName");
    const metaEl     = document.getElementById("userMeta");
    const bioEl      = document.getElementById("userBio");
    const subBtn     = document.getElementById("userSubscribeBtn");
    const grid       = document.getElementById("userVideoGrid");
    const loader     = document.getElementById("userScrollLoader");
    const sentinel   = document.getElementById("userScrollSentinel");
    const emptyEl    = document.getElementById("userVideoEmpty");

    let channelInfo = {};

    // 프로필 로드
    try {
        const res = await fetch(`/api/users/${userId}/channel`);
        if (!res.ok) { window.location.href = "index.html"; return; }
        const ch = await res.json();
        channelInfo = ch;

        document.title = `${ch.channelName} - MyTube`;

        if (ch.bannerImage) {
            bannerWrap.innerHTML = `<img class="user-banner" src="${escapeHtml(ch.bannerImage)}" alt="배너">`;
        } else {
            bannerWrap.innerHTML = `<div class="user-banner-placeholder"></div>`;
        }

        if (ch.profileImage) {
            avatarEl.innerHTML = `<img src="${escapeHtml(ch.profileImage)}" alt="${escapeHtml(ch.channelName)}">`;
        } else {
            avatarEl.textContent = String(ch.channelName || "?").charAt(0).toUpperCase();
        }

        nameEl.textContent = ch.channelName || "채널";
        metaEl.textContent = `구독자 ${formatCount(ch.subscriberCount)}명 · 영상 ${ch.videoCount}개`;
        if (ch.bio) { bioEl.textContent = ch.bio; bioEl.style.display = "block"; }

        if (!ch.isMe) {
            subBtn.style.display = "block";
            subBtn.textContent = ch.subscribed ? "구독 중" : "구독";
            subBtn.classList.toggle("subscribed", ch.subscribed);
            subBtn.addEventListener("click", async () => {
                const r = await fetch(`/api/users/${userId}/subscribe`, { method: "POST" });
                if (!r.ok) return;
                const data = await r.json();
                subBtn.textContent = data.subscribed ? "구독 중" : "구독";
                subBtn.classList.toggle("subscribed", data.subscribed);
                metaEl.textContent = `구독자 ${formatCount(data.subscriberCount)}명 · 영상 ${ch.videoCount}개`;
            });
        }
    } catch { window.location.href = "index.html"; return; }

    // 탭
    const tabPanels = {
        videos: document.getElementById("userTabVideos"),
        playlists: document.getElementById("userTabPlaylists"),
        about: document.getElementById("userTabAbout"),
    };
    let playlistsLoaded = false;

    function showTab(name) {
        Object.values(tabPanels).forEach(p => p?.classList.remove("is-active"));
        tabPanels[name]?.classList.add("is-active");
        if (name === "playlists" && !playlistsLoaded) {
            playlistsLoaded = true;
            loadUserPlaylists();
        }
        if (name === "about") {
            renderAboutSection("userAboutSection", channelInfo);
        }
    }

    initTabBar("userTabsBar", showTab);

    async function loadUserPlaylists() {
        try {
            const res = await fetch(`/api/playlists/user/${userId}`);
            if (!res.ok) return;
            const list = await res.json();
            renderPlaylistGrid("userPlaylistGrid", "userPlaylistEmpty", list);
        } catch {}
    }

    // 영상 무한 스크롤
    let currentPage = 0, isLoading = false, hasMore = true;

    async function loadPage() {
        if (isLoading || !hasMore) return;
        isLoading = true;
        if (loader) loader.style.display = "flex";
        try {
            const res = await fetch(`/api/videos/feed?ownerId=${userId}&page=${currentPage}&size=12`);
            if (!res.ok) throw new Error();
            const data = await res.json();
            if (currentPage === 0 && !data.videos.length) {
                if (emptyEl) emptyEl.style.display = "block";
            }
            if (data.videos.length) {
                grid.insertAdjacentHTML("beforeend", data.videos.map(createVideoCard).join(""));
            }
            hasMore = data.hasMore;
            currentPage++;
        } catch { hasMore = false; }
        finally { isLoading = false; if (loader) loader.style.display = "none"; }
    }

    if (sentinel) {
        new IntersectionObserver(entries => {
            if (entries[0].isIntersecting) loadPage();
        }, { rootMargin: "300px" }).observe(sentinel);
    }

    await loadPage();
}

async function initPlaylistPage() {
    const authMe = getAuthMe();
    if (!authMe?.loggedIn) { window.location.href = "login.html?next=playlist.html"; return; }

    const plList = document.getElementById("plList");
    const plEmpty = document.getElementById("plEmpty");
    const plListView = document.getElementById("plListView");
    const plDetailView = document.getElementById("plDetailView");
    const plDetailTitle = document.getElementById("plDetailTitle");
    const plVideoList = document.getElementById("plVideoList");
    const plDetailEmpty = document.getElementById("plDetailEmpty");
    const plModal = document.getElementById("plModal");
    const plNameInput = document.getElementById("plNameInput");

    async function loadPlaylists() {
        const res = await fetch("/api/playlists/me");
        if (!res.ok) return;
        const list = await res.json();
        if (!list.length) { plEmpty.style.display = "block"; plList.innerHTML = ""; return; }
        plEmpty.style.display = "none";
        plList.innerHTML = list.map(p => `
            <div class="pl-card" data-pl-id="${p.id}">
                <div class="pl-thumb">
                    ${p.thumbnail ? `<img src="${escapeHtml(p.thumbnail)}" alt="${escapeHtml(p.name)}">` : `<div class="pl-thumb-empty">🎵</div>`}
                    <span class="pl-count">${p.videoCount}개</span>
                </div>
                <div class="pl-info">
                    <div class="pl-name">${escapeHtml(p.name)}</div>
                    <div class="pl-meta">영상 ${p.videoCount}개</div>
                    <button type="button" class="pl-delete-btn" data-delete-id="${p.id}">삭제</button>
                </div>
            </div>`).join("");

        plList.querySelectorAll(".pl-card").forEach(card => {
            card.addEventListener("click", e => {
                if (e.target.closest(".pl-delete-btn")) return;
                openDetail(Number(card.dataset.plId), card.querySelector(".pl-name").textContent);
            });
        });
        plList.querySelectorAll(".pl-delete-btn").forEach(btn => {
            btn.addEventListener("click", async e => {
                e.stopPropagation();
                if (!confirm("재생목록을 삭제할까요?")) return;
                await fetch(`/api/playlists/${btn.dataset.deleteId}`, { method: "DELETE" });
                loadPlaylists();
            });
        });
    }

    async function openDetail(id, name) {
        plListView.style.display = "none";
        plDetailView.classList.add("is-open");
        plDetailTitle.textContent = name;
        const res = await fetch(`/api/playlists/${id}/videos`);
        if (!res.ok) return;
        const data = await res.json();
        const videos = data.videos || [];
        if (!videos.length) { plDetailEmpty.style.display = "block"; plVideoList.innerHTML = ""; return; }
        plDetailEmpty.style.display = "none";
        plVideoList.innerHTML = videos.map(v => `
            <div class="pl-video-item">
                <a href="watch.html?v=${v.id}" class="pl-video-link">
                    <div class="pl-video-thumb"><img src="${escapeHtml(v.thumbnail)}" alt="${escapeHtml(v.title)}"></div>
                </a>
                <div class="pl-video-info">
                    <a href="watch.html?v=${v.id}" class="pl-video-link">
                        <div class="pl-video-title">${escapeHtml(v.title)}</div>
                        <div class="pl-video-meta">${escapeHtml(v.channel)} · 조회수 ${formatCount(v.viewCount)}회 · ${escapeHtml(v.date)}</div>
                    </a>
                </div>
                <button type="button" class="pl-video-remove" data-pl-id="${id}" data-video-id="${v.id}">제거</button>
            </div>`).join("");

        plVideoList.querySelectorAll(".pl-video-remove").forEach(btn => {
            btn.addEventListener("click", async () => {
                await fetch(`/api/playlists/${btn.dataset.plId}/videos/${btn.dataset.videoId}`, { method: "DELETE" });
                openDetail(id, name);
            });
        });
    }

    document.getElementById("plBackBtn")?.addEventListener("click", () => {
        plDetailView.classList.remove("is-open");
        plListView.style.display = "";
        loadPlaylists();
    });

    document.getElementById("createPlBtn")?.addEventListener("click", () => {
        plNameInput.value = "";
        plModal.classList.add("is-open");
        plNameInput.focus();
    });
    document.getElementById("plModalCancel")?.addEventListener("click", () => plModal.classList.remove("is-open"));
    document.getElementById("plModalConfirm")?.addEventListener("click", async () => {
        const name = plNameInput.value.trim();
        if (!name) return;
        await fetch("/api/playlists", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ name }) });
        plModal.classList.remove("is-open");
        loadPlaylists();
    });
    plNameInput?.addEventListener("keydown", e => { if (e.key === "Enter") document.getElementById("plModalConfirm").click(); });

    loadPlaylists();
}

async function initSubscriptionPage() {
    const grid = document.getElementById("subFeedGrid");
    const loader = document.getElementById("subFeedLoader");
    const sentinel = document.getElementById("subFeedSentinel");
    const emptyEl = document.getElementById("subFeedEmpty");
    const searchForm = document.getElementById("subFeedSearchForm");
    const searchInput = document.getElementById("subFeedSearchInput");
    const channelStrip = document.getElementById("subChannelStrip");

    if (!grid) return;

    const authMe = getAuthMe();
    if (!authMe?.loggedIn) {
        window.location.href = "login.html?next=subscriptions.html";
        return;
    }

    // 구독 채널 아바타 스트립
    if (channelStrip) {
        try {
            const res = await fetch("/api/users/me/subscriptions");
            if (res.ok) {
                const subs = await res.json();
                channelStrip.innerHTML = subs.map(s => {
                    const name = s.channelName || "채널";
                    const initial = name.charAt(0).toUpperCase();
                    const avatarInner = s.profileImage
                        ? `<img src="${escapeHtml(s.profileImage)}" alt="${escapeHtml(name)}">`
                        : escapeHtml(initial);
                    return `
                        <a href="user.html?id=${s.channelOwnerId}" class="sub-channel-item" title="${escapeHtml(name)}">
                            <div class="sub-channel-avatar">${avatarInner}</div>
                            <span class="sub-channel-name">${escapeHtml(name)}</span>
                        </a>`;
                }).join("");
            }
        } catch {}
    }

    const url = new URL(window.location.href);
    let filterChannel = url.searchParams.get("channel") || "";
    let currentPage = 0;
    let isLoading = false;
    let hasMore = true;

    if (searchInput && filterChannel) searchInput.value = filterChannel;

    function showLoader(show) {
        if (loader) loader.style.display = show ? "flex" : "none";
    }

    async function loadPage() {
        if (isLoading || !hasMore) return;
        isLoading = true;
        showLoader(true);

        try {
            const params = new URLSearchParams({ page: currentPage, size: 12 });
            if (filterChannel.trim()) params.set("keyword", filterChannel.trim());

            const res = await fetch(`/api/videos/subscriptions?${params}`);
            if (!res.ok) throw new Error();
            const data = await res.json();

            if (currentPage === 0) {
                grid.innerHTML = "";
                if (emptyEl) emptyEl.hidden = data.videos.length > 0;
            }

            if (data.videos.length > 0) {
                grid.insertAdjacentHTML("beforeend", data.videos.map(createVideoCard).join(""));
            }

            hasMore = data.hasMore;
            currentPage++;
        } catch {
            hasMore = false;
        } finally {
            isLoading = false;
            showLoader(false);
        }
    }

    function resetAndLoad() {
        currentPage = 0;
        hasMore = true;
        isLoading = false;
        grid.innerHTML = "";
        if (emptyEl) emptyEl.hidden = true;
        loadPage();
    }

    if (sentinel) {
        const observer = new IntersectionObserver((entries) => {
            if (entries[0].isIntersecting) loadPage();
        }, { rootMargin: "300px" });
        observer.observe(sentinel);
    }

    searchForm?.addEventListener("submit", (e) => {
        e.preventDefault();
        filterChannel = searchInput?.value || "";
        const nextUrl = new URL(window.location.href);
        if (filterChannel) nextUrl.searchParams.set("channel", filterChannel);
        else nextUrl.searchParams.delete("channel");
        window.history.pushState({}, "", nextUrl);
        resetAndLoad();
    });

    await loadPage();
}

function initNotifications() {
    const wrap = document.getElementById("notifWrap");
    if (!wrap) return;

    const btn = document.getElementById("notifBtn");
    const badge = document.getElementById("notifBadge");
    const dropdown = document.getElementById("notifDropdown");
    const list = document.getElementById("notifList");
    const readAllBtn = document.getElementById("notifReadAll");
    const mainHeader = document.getElementById("notifMainHeader");
    const commentView = document.getElementById("notifCommentView");
    const commentList = document.getElementById("notifCommentList");
    const backBtn = document.getElementById("notifBackBtn");
    const goVideoLink = document.getElementById("notifGoVideo");

    let open = false;

    async function fetchNotifs() {
        try {
            const res = await fetch("/api/notifications");
            if (!res.ok) return null;
            return await res.json();
        } catch {
            return null;
        }
    }

    function timeAgo(isoStr) {
        const diff = Date.now() - new Date(isoStr).getTime();
        const m = Math.floor(diff / 60000);
        if (m < 1) return "방금 전";
        if (m < 60) return `${m}분 전`;
        const h = Math.floor(m / 60);
        if (h < 24) return `${h}시간 전`;
        return `${Math.floor(h / 24)}일 전`;
    }

    function typeIcon(type) {
        if (type === "LIKE")         return `<div class="notif-type-icon">❤️</div>`;
        if (type === "COMMENT")      return `<div class="notif-type-icon">💬</div>`;
        if (type === "COMMENT_LIKE") return `<div class="notif-type-icon">💬</div>`;
        if (type === "SUBSCRIBE")    return `<div class="notif-type-icon">🔔</div>`;
        return `<div class="notif-type-icon">🎬</div>`;
    }

    function showMainView() {
        list.style.display = "block";
        if (mainHeader) mainHeader.style.display = "flex";
        if (commentView) commentView.style.display = "none";
    }

    async function showCommentView(videoId) {
        if (!commentView || !commentList) return;
        list.style.display = "none";
        if (mainHeader) mainHeader.style.display = "none";
        commentView.style.display = "block";
        if (goVideoLink) goVideoLink.href = `watch.html?v=${videoId}`;
        commentList.innerHTML = `<div class="notif-comment-loading">불러오는 중...</div>`;

        try {
            const res = await fetch(`/api/videos/${videoId}/comments`);
            if (!res.ok) throw new Error();
            const comments = await res.json();
            if (!comments.length) {
                commentList.innerHTML = `<div class="notif-comment-empty">댓글이 없어요</div>`;
                return;
            }
            commentList.innerHTML = comments.map((c) => `
                <div class="notif-comment-item">
                    <span class="notif-comment-author">${c.author}</span>
                    <span class="notif-comment-text">${c.text}</span>
                    <span class="notif-comment-time">${c.time}</span>
                </div>
            `).join("");
        } catch {
            commentList.innerHTML = `<div class="notif-comment-empty">불러오지 못했어요</div>`;
        }
    }

    function renderList(notifications) {
        showMainView();
        if (!notifications || !notifications.length) {
            list.innerHTML = '<div class="notif-empty">알림이 없어요</div>';
            return;
        }
        list.innerHTML = notifications.map((n) => `
            <div class="notif-item${n.read ? "" : " unread"}"
                 data-id="${n.id}"
                 data-type="${n.type || ""}"
                 data-video="${n.relatedVideoId || ""}">
                <div class="notif-thumb-wrap">
                    ${n.thumbnail
                        ? `<img class="notif-thumb" src="${n.thumbnail}" alt="">`
                        : `<div class="notif-thumb"></div>`}
                    ${typeIcon(n.type)}
                </div>
                <div class="notif-msg">
                    ${n.message}
                    <div class="notif-time">${timeAgo(n.createdAt)}</div>
                </div>
            </div>
        `).join("");

        list.querySelectorAll(".notif-item").forEach((item) => {
            item.addEventListener("click", async () => {
                const id = item.dataset.id;
                const type = item.dataset.type;
                const videoId = item.dataset.video;

                await fetch(`/api/notifications/${id}/read`, { method: "POST" });
                badge.style.display = "none";

                if (type === "COMMENT" || type === "COMMENT_LIKE") {
                    if (videoId) await showCommentView(videoId);
                } else if (videoId) {
                    window.location.href = `watch.html?v=${videoId}`;
                } else {
                    await refresh();
                }
            });
        });
    }

    backBtn?.addEventListener("click", async () => {
        showMainView();
        const data = await fetchNotifs();
        if (data) renderList(data.notifications);
    });

    async function refresh() {
        const data = await fetchNotifs();
        if (!data) return;

        if (data.unreadCount > 0) {
            badge.textContent = data.unreadCount > 99 ? "99+" : data.unreadCount;
            badge.style.display = "flex";
        } else {
            badge.style.display = "none";
        }

        if (open && list.style.display !== "none") renderList(data.notifications);
    }

    btn.addEventListener("click", async (e) => {
        e.stopPropagation();
        open = !open;
        dropdown.style.display = open ? "block" : "none";
        if (open) {
            showMainView();
            const data = await fetchNotifs();
            if (data) renderList(data.notifications);
        }
    });

    document.addEventListener("click", (e) => {
        if (!wrap.contains(e.target)) {
            open = false;
            dropdown.style.display = "none";
        }
    });

    readAllBtn?.addEventListener("click", async () => {
        await fetch("/api/notifications/read-all", { method: "POST" });
        const data = await fetchNotifs();
        if (data) {
            badge.style.display = "none";
            renderList(data.notifications);
        }
    });

    refresh();
    setInterval(refresh, 30000);
}

function initGlobalTopSearch() {
    if (page === "search") return;

    const searchForms = document.querySelectorAll(".search-form");
    if (!searchForms.length) return;

    searchForms.forEach((form) => {
        form.addEventListener("submit", (event) => {
            event.preventDefault();

            const input = form.querySelector('input[type="text"]');
            const keyword = input?.value.trim() || "";
            const targetUrl = new URL("search.html", window.location.href);

            if (keyword) {
                targetUrl.searchParams.set("q", keyword);
            }

            window.location.href = targetUrl.toString();
        });
    });
}

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

        if (!hasThumbnailUrl && !hasThumbnailFile) {
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

    uploadVideoFile?.addEventListener("change", async () => {
        const file = uploadVideoFile.files?.[0];
        if (selectedVideoName) {
            selectedVideoName.textContent = file ? file.name : "선택된 파일 없음";
        }

        validateVideoFile();

        if (!file) {
            uploadDuration.value = "";
            validateDurationField();
            updateSummary();
            return;
        }

        try {
            const durationSeconds = await readVideoDuration(file);
            uploadDuration.value = formatDuration(durationSeconds);
        } catch {
            uploadDuration.value = "";
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

async function initSearchPage() {
    const videoGrid = document.getElementById("videoGrid");
    const channelResults = document.getElementById("channelResults");
    const channelResultsList = document.getElementById("channelResultsList");
    const scrollLoader = document.getElementById("scrollLoader");
    const scrollSentinel = document.getElementById("scrollSentinel");
    const searchEmptyState = document.getElementById("searchEmptyState");
    const searchEmptyTitle = document.getElementById("searchEmptyTitle");
    const searchEmptyText = document.getElementById("searchEmptyText");
    const searchKeywordLabel = document.getElementById("searchKeywordLabel");
    const searchResultCount = document.getElementById("searchResultCount");
    const searchPageInput = document.getElementById("searchPageInput");
    const searchPageForm = document.getElementById("searchPageForm");

    if (!videoGrid) return;

    const url = new URL(window.location.href);
    let currentKeyword = url.searchParams.get("q") || "";
    let currentPage = 0;
    let isLoading = false;
    let hasMore = true;
    let totalVideoCount = 0;

    if (searchPageInput) searchPageInput.value = currentKeyword;
    updateHeader();

    function updateHeader() {
        if (searchKeywordLabel) {
            if (currentKeyword.trim()) {
                searchKeywordLabel.innerHTML = `<span>${escapeHtml(currentKeyword)}</span> 검색 결과`;
            } else {
                searchKeywordLabel.textContent = "검색어를 입력해주세요";
            }
        }
    }

    function showLoader(show) {
        if (scrollLoader) scrollLoader.style.display = show ? "flex" : "none";
    }

    async function loadChannels(keyword) {
        if (!channelResults || !channelResultsList) return;
        if (!keyword.trim()) {
            channelResults.style.display = "none";
            channelResultsList.innerHTML = "";
            return;
        }
        try {
            const res = await fetch(`/api/users/search?keyword=${encodeURIComponent(keyword.trim())}`);
            if (!res.ok) return;
            const channels = await res.json();
            if (!channels.length) {
                channelResults.style.display = "none";
                return;
            }
            channelResults.style.display = "block";
            channelResultsList.innerHTML = channels.map(ch => {
                const name = ch.channelName || ch.username;
                const initial = String(name).charAt(0).toUpperCase();
                const avatar = ch.profileImage
                    ? `<img src="${escapeHtml(ch.profileImage)}" alt="${escapeHtml(name)}" class="ch-card-avatar-img">`
                    : `<span class="ch-card-avatar-text">${escapeHtml(initial)}</span>`;
                const subCount = formatCount(ch.subscriberCount || 0);
                const vidCount = ch.videoCount || 0;
                return `
                <div class="ch-card" data-channel-id="${ch.id}">
                    <a href="user.html?id=${ch.id}" class="ch-card-link">
                        <div class="ch-card-avatar">${avatar}</div>
                        <div class="ch-card-info">
                            <span class="ch-card-name">${escapeHtml(name)}</span>
                            <span class="ch-card-meta">구독자 ${subCount}명 · 영상 ${vidCount}개</span>
                        </div>
                    </a>
                    <button class="ch-card-sub-btn${ch.subscribed ? " is-subscribed" : ""}"
                            data-ch-id="${ch.id}" data-subscribed="${ch.subscribed}">
                        ${ch.subscribed ? "구독 중" : "구독"}
                    </button>
                </div>`;
            }).join("");

            channelResultsList.querySelectorAll(".ch-card-sub-btn").forEach(btn => {
                btn.addEventListener("click", async () => {
                    const chId = btn.dataset.chId;
                    const res = await fetch(`/api/users/${chId}/subscribe`, { method: "POST" });
                    if (!res.ok) return;
                    const data = await res.json();
                    btn.dataset.subscribed = data.subscribed ? "true" : "false";
                    btn.classList.toggle("is-subscribed", data.subscribed);
                    btn.textContent = data.subscribed ? "구독 중" : "구독";
                });
            });
        } catch {}
    }

    async function loadPage() {
        if (isLoading || !hasMore) return;
        isLoading = true;
        showLoader(true);

        try {
            const params = new URLSearchParams({ page: currentPage, size: 12 });
            if (currentKeyword.trim()) params.set("keyword", currentKeyword.trim());

            const res = await fetch(`/api/videos/feed?${params}`);
            if (!res.ok) throw new Error();
            const data = await res.json();

            if (currentPage === 0) {
                videoGrid.innerHTML = "";
                totalVideoCount = 0;
            }

            if (data.videos.length > 0) {
                videoGrid.insertAdjacentHTML("beforeend", data.videos.map(createVideoCard).join(""));
                totalVideoCount += data.videos.length;
            }

            hasMore = data.hasMore;
            currentPage++;

            if (!hasMore && currentPage > 0) {
                if (searchResultCount) {
                    searchResultCount.textContent = totalVideoCount > 0
                        ? `영상 ${totalVideoCount}개`
                        : "";
                }
                if (searchEmptyState) {
                    searchEmptyState.hidden = totalVideoCount > 0;
                    if (totalVideoCount === 0) {
                        if (searchEmptyTitle) searchEmptyTitle.textContent = currentKeyword.trim()
                            ? "검색 결과가 없습니다"
                            : "검색어를 입력하면 영상을 찾을 수 있어요";
                        if (searchEmptyText) searchEmptyText.textContent = currentKeyword.trim()
                            ? "다른 검색어로 다시 시도해봐."
                            : "";
                    }
                }
            }
        } catch {
            hasMore = false;
        } finally {
            isLoading = false;
            showLoader(false);
        }
    }

    function resetAndLoad() {
        currentPage = 0;
        hasMore = true;
        isLoading = false;
        totalVideoCount = 0;
        videoGrid.innerHTML = "";
        if (searchEmptyState) searchEmptyState.hidden = true;
        if (searchResultCount) searchResultCount.textContent = "";
        updateHeader();
        loadChannels(currentKeyword);
        loadPage();
    }

    if (scrollSentinel) {
        const observer = new IntersectionObserver((entries) => {
            if (entries[0].isIntersecting) loadPage();
        }, { rootMargin: "300px" });
        observer.observe(scrollSentinel);
    }

    searchPageForm?.addEventListener("submit", (e) => {
        e.preventDefault();
        const newKeyword = searchPageInput?.value.trim() || "";
        const nextUrl = new URL(window.location.href);
        if (newKeyword) nextUrl.searchParams.set("q", newKeyword);
        else nextUrl.searchParams.delete("q");
        window.history.pushState({}, "", nextUrl);
        currentKeyword = newKeyword;
        resetAndLoad();
    });

    window.addEventListener("popstate", () => {
        currentKeyword = new URL(window.location.href).searchParams.get("q") || "";
        if (searchPageInput) searchPageInput.value = currentKeyword;
        resetAndLoad();
    });

    await Promise.all([loadChannels(currentKeyword), loadPage()]);
}

async function initHomePage() {
    const videoGrid = document.getElementById("videoGrid");
    const categoryBar = document.getElementById("categoryBar");
    const homeEmptyState = document.getElementById("homeEmptyState");
    const scrollLoader = document.getElementById("scrollLoader");
    const scrollSentinel = document.getElementById("scrollSentinel");
    const channelResults = document.getElementById("channelResults");
    const channelResultsList = document.getElementById("channelResultsList");

    if (!videoGrid) return;

    let currentKeyword = "";
    let selectedCategory = "";
    let currentPage = 0;
    let isLoading = false;
    let hasMore = true;

    function showLoader(show) {
        if (scrollLoader) scrollLoader.style.display = show ? "flex" : "none";
    }

    async function loadCategories() {
        try {
            const res = await fetch("/api/videos/categories");
            if (!res.ok) return;
            const categories = await res.json();
            buildCategoryBar(categories);
        } catch {}
    }

    function buildCategoryBar(categories) {
        if (!categoryBar) return;
        const chips = [{ label: "전체", value: "" }, ...categories.map((c) => ({ label: c, value: c }))];
        categoryBar.innerHTML = chips.map((chip) => `
            <button class="category-chip${chip.value === selectedCategory ? " is-active" : ""}"
                    data-category="${chip.value}">
                ${chip.label}
            </button>
        `).join("");
        categoryBar.querySelectorAll(".category-chip").forEach((btn) => {
            btn.addEventListener("click", () => {
                selectedCategory = btn.dataset.category;
                categoryBar.querySelectorAll(".category-chip").forEach((b) => b.classList.remove("is-active"));
                btn.classList.add("is-active");
                resetAndLoad();
            });
        });
    }

    async function loadPage() {
        if (isLoading || !hasMore) return;
        isLoading = true;
        showLoader(true);

        try {
            const params = new URLSearchParams({ page: currentPage, size: 12 });
            if (currentKeyword.trim()) params.set("keyword", currentKeyword.trim());
            if (selectedCategory) params.set("category", selectedCategory);

            const res = await fetch(`/api/videos/feed?${params}`);
            if (!res.ok) throw new Error();
            const data = await res.json();

            if (currentPage === 0) {
                videoGrid.innerHTML = "";
                if (homeEmptyState) {
                    homeEmptyState.hidden = data.videos.length > 0;
                    const titleEl = homeEmptyState.querySelector(".home-empty-title");
                    const textEl = homeEmptyState.querySelector(".home-empty-text");
                    if (currentKeyword.trim() || selectedCategory) {
                        if (titleEl) titleEl.textContent = "검색 결과가 없습니다";
                        if (textEl) textEl.textContent = "다른 검색어나 카테고리로 다시 시도해봐.";
                    } else {
                        if (titleEl) titleEl.textContent = "표시할 영상이 없습니다";
                        if (textEl) textEl.textContent = "영상을 업로드하고 첫 번째 영상의 주인공이 되어봐.";
                    }
                }
            }

            if (data.videos.length > 0) {
                videoGrid.insertAdjacentHTML("beforeend", data.videos.map(createVideoCard).join(""));
            }

            hasMore = data.hasMore;
            currentPage++;
        } catch {
            hasMore = false;
        } finally {
            isLoading = false;
            showLoader(false);
        }
    }

    async function loadChannels(keyword) {
        if (!channelResults || !channelResultsList) return;
        if (!keyword.trim()) {
            channelResults.style.display = "none";
            channelResultsList.innerHTML = "";
            return;
        }
        try {
            const res = await fetch(`/api/users/search?keyword=${encodeURIComponent(keyword.trim())}`);
            if (!res.ok) return;
            const channels = await res.json();
            if (!channels.length) {
                channelResults.style.display = "none";
                return;
            }
            channelResults.style.display = "block";
            channelResultsList.innerHTML = channels.map(ch => {
                const name = ch.channelName || ch.username;
                const initial = String(name).charAt(0).toUpperCase();
                const avatar = ch.profileImage
                    ? `<img src="${escapeHtml(ch.profileImage)}" alt="${escapeHtml(name)}" class="ch-card-avatar-img">`
                    : `<span class="ch-card-avatar-text">${escapeHtml(initial)}</span>`;
                const subCount = formatCount(ch.subscriberCount || 0);
                const vidCount = ch.videoCount || 0;
                return `
                <div class="ch-card" data-channel-id="${ch.id}">
                    <a href="user.html?id=${ch.id}" class="ch-card-link">
                        <div class="ch-card-avatar">${avatar}</div>
                        <div class="ch-card-info">
                            <span class="ch-card-name">${escapeHtml(name)}</span>
                            <span class="ch-card-meta">구독자 ${subCount}명 · 영상 ${vidCount}개</span>
                        </div>
                    </a>
                    <button class="ch-card-sub-btn${ch.subscribed ? " is-subscribed" : ""}"
                            data-ch-id="${ch.id}" data-subscribed="${ch.subscribed}">
                        ${ch.subscribed ? "구독 중" : "구독"}
                    </button>
                </div>`;
            }).join("");

            channelResultsList.querySelectorAll(".ch-card-sub-btn").forEach(btn => {
                btn.addEventListener("click", async () => {
                    const chId = btn.dataset.chId;
                    const res = await fetch(`/api/users/${chId}/subscribe`, { method: "POST" });
                    if (!res.ok) return;
                    const data = await res.json();
                    btn.dataset.subscribed = data.subscribed ? "true" : "false";
                    btn.classList.toggle("is-subscribed", data.subscribed);
                    btn.textContent = data.subscribed ? "구독 중" : "구독";
                });
            });
        } catch {}
    }

    function resetAndLoad() {
        currentPage = 0;
        hasMore = true;
        isLoading = false;
        videoGrid.innerHTML = "";
        if (homeEmptyState) homeEmptyState.hidden = true;
        loadChannels(currentKeyword);
        loadPage();
    }

    if (scrollSentinel) {
        const observer = new IntersectionObserver((entries) => {
            if (entries[0].isIntersecting) loadPage();
        }, { rootMargin: "300px" });
        observer.observe(scrollSentinel);
    }

    await loadCategories();
    await loadPage();
}

async function initSavedPage() {
    const savedVideoGrid = document.getElementById("savedVideoGrid");
    const savedEmptyState = document.getElementById("savedEmptyState");
    const savedCountText = document.getElementById("savedCountText");
    const savedKicker = document.getElementById("savedKicker");
    const savedTitle = document.getElementById("savedTitle");
    const savedSearchForm = document.getElementById("savedSearchForm");
    const savedSearchInput = document.getElementById("savedSearchInput");

    if (!savedVideoGrid) return;

    const authMe = getAuthMe();

    if (!authMe.loggedIn) {
        savedVideoGrid.innerHTML = "";
        if (savedEmptyState) {
            savedEmptyState.hidden = false;
            savedEmptyState.textContent = "로그인 후 저장한 영상을 볼 수 있어.";
        }
        if (savedCountText) savedCountText.textContent = "저장한 영상 0개";
        return;
    }

    let savedVideos = await fetchMySavedVideos();
    let currentKeyword = "";

    function filterSavedVideos(videos, keyword) {
        const normalizedKeyword = keyword.trim().toLowerCase();
        if (!normalizedKeyword) return videos;

        return videos.filter((video) => {
            const title = String(video.title || "").toLowerCase();
            const channel = String(video.channel || "").toLowerCase();
            const description = String(video.description || "").toLowerCase();
            const category = String(video.category || "").toLowerCase();

            return (
                title.includes(normalizedKeyword) ||
                channel.includes(normalizedKeyword) ||
                description.includes(normalizedKeyword) ||
                category.includes(normalizedKeyword)
            );
        });
    }

    function updateSavedMeta(filteredCount) {
        const trimmedKeyword = currentKeyword.trim();

        if (!trimmedKeyword) {
            if (savedKicker) savedKicker.textContent = "보관함";
            if (savedTitle) savedTitle.textContent = "저장한 영상";
            if (savedCountText) savedCountText.textContent = `저장한 영상 ${formatCount(filteredCount)}개`;
            return;
        }

        if (savedKicker) savedKicker.textContent = "검색 결과";
        if (savedTitle) savedTitle.textContent = `"${trimmedKeyword}" 저장한 영상 검색 결과`;
        if (savedCountText) savedCountText.textContent = `${formatCount(filteredCount)}개의 영상을 찾았어`;
    }

    function updateEmptyState() {
        const trimmedKeyword = currentKeyword.trim();
        if (!savedEmptyState) return;

        if (savedVideos.length === 0) {
            savedEmptyState.textContent = "아직 저장한 영상이 없습니다. 마음에 드는 영상을 저장해봐.";
            return;
        }

        if (!trimmedKeyword) {
            savedEmptyState.textContent = "표시할 저장한 영상이 없습니다.";
            return;
        }

        savedEmptyState.textContent = `"${trimmedKeyword}"에 대한 저장한 영상 검색 결과가 없습니다.`;
    }

    function bindUnsaveButtons() {
        const buttons = savedVideoGrid.querySelectorAll("[data-unsave-id]");

        buttons.forEach((button) => {
            button.addEventListener("click", async (event) => {
                event.preventDefault();
                event.stopPropagation();

                const videoId = Number(button.dataset.unsaveId);

                try {
                    await toggleSaveByVideoId(videoId);
                    savedVideos = savedVideos.filter((video) => Number(video.id) !== videoId);
                    showToast("저장이 해제되었습니다.");
                    renderSavedVideos();
                } catch (error) {
                    alert(error.message || "저장 해제 중 오류가 발생했어.");
                }
            });
        });
    }

    function renderSavedVideos() {
        const filteredVideos = filterSavedVideos(savedVideos, currentKeyword);

        updateSavedMeta(filteredVideos.length);
        updateEmptyState();

        if (filteredVideos.length === 0) {
            savedVideoGrid.innerHTML = "";
            if (savedEmptyState) savedEmptyState.hidden = false;
            return;
        }

        if (savedEmptyState) savedEmptyState.hidden = true;
        savedVideoGrid.innerHTML = filteredVideos.map(createSavedVideoCard).join("");
        bindUnsaveButtons();
    }

    savedSearchForm?.addEventListener("submit", (event) => {
        event.preventDefault();
        currentKeyword = savedSearchInput?.value || "";
        renderSavedVideos();
    });

    renderSavedVideos();
}

async function initLikedPage() {
    const likedVideoGrid = document.getElementById("likedVideoGrid");
    const likedEmptyState = document.getElementById("likedEmptyState");
    const likedCountText = document.getElementById("likedCountText");
    const likedKicker = document.getElementById("likedKicker");
    const likedTitle = document.getElementById("likedTitle");
    const likedSearchForm = document.getElementById("likedSearchForm");
    const likedSearchInput = document.getElementById("likedSearchInput");

    if (!likedVideoGrid) return;

    const authMe = getAuthMe();

    if (!authMe.loggedIn) {
        likedVideoGrid.innerHTML = "";
        if (likedEmptyState) {
            likedEmptyState.hidden = false;
            likedEmptyState.textContent = "로그인 후 좋아요한 영상을 볼 수 있어.";
        }
        if (likedCountText) likedCountText.textContent = "좋아요한 영상 0개";
        return;
    }

    let likedVideos = await fetchMyLikedVideos();
    let currentKeyword = "";

    function filterLikedVideos(videos, keyword) {
        const normalizedKeyword = keyword.trim().toLowerCase();
        if (!normalizedKeyword) return videos;

        return videos.filter((video) => {
            const title = String(video.title || "").toLowerCase();
            const channel = String(video.channel || "").toLowerCase();
            const description = String(video.description || "").toLowerCase();
            const category = String(video.category || "").toLowerCase();

            return (
                title.includes(normalizedKeyword) ||
                channel.includes(normalizedKeyword) ||
                description.includes(normalizedKeyword) ||
                category.includes(normalizedKeyword)
            );
        });
    }

    function updateLikedMeta(filteredCount) {
        const trimmedKeyword = currentKeyword.trim();

        if (!trimmedKeyword) {
            if (likedKicker) likedKicker.textContent = "보관함";
            if (likedTitle) likedTitle.textContent = "좋아요한 영상";
            if (likedCountText) likedCountText.textContent = `좋아요한 영상 ${formatCount(filteredCount)}개`;
            return;
        }

        if (likedKicker) likedKicker.textContent = "검색 결과";
        if (likedTitle) likedTitle.textContent = `"${trimmedKeyword}" 좋아요한 영상 검색 결과`;
        if (likedCountText) likedCountText.textContent = `${formatCount(filteredCount)}개의 영상을 찾았어`;
    }

    function updateEmptyState() {
        const trimmedKeyword = currentKeyword.trim();
        if (!likedEmptyState) return;

        if (likedVideos.length === 0) {
            likedEmptyState.textContent = "아직 좋아요한 영상이 없습니다. 마음에 드는 영상에 좋아요를 눌러봐.";
            return;
        }

        if (!trimmedKeyword) {
            likedEmptyState.textContent = "표시할 좋아요한 영상이 없습니다.";
            return;
        }

        likedEmptyState.textContent = `"${trimmedKeyword}"에 대한 좋아요한 영상 검색 결과가 없습니다.`;
    }

    function bindUnlikeButtons() {
        const buttons = likedVideoGrid.querySelectorAll("[data-unlike-id]");

        buttons.forEach((button) => {
            button.addEventListener("click", async (event) => {
                event.preventDefault();
                event.stopPropagation();

                const videoId = Number(button.dataset.unlikeId);

                try {
                    await toggleLikeByVideoId(videoId);
                    likedVideos = likedVideos.filter((video) => Number(video.id) !== videoId);
                    showToast("좋아요가 취소되었습니다.");
                    renderLikedVideos();
                } catch (error) {
                    alert(error.message || "좋아요 취소 중 오류가 발생했어.");
                }
            });
        });
    }

    function renderLikedVideos() {
        const filteredVideos = filterLikedVideos(likedVideos, currentKeyword);

        updateLikedMeta(filteredVideos.length);
        updateEmptyState();

        if (filteredVideos.length === 0) {
            likedVideoGrid.innerHTML = "";
            if (likedEmptyState) likedEmptyState.hidden = false;
            return;
        }

        if (likedEmptyState) likedEmptyState.hidden = true;
        likedVideoGrid.innerHTML = filteredVideos.map(createLikedVideoCard).join("");
        bindUnlikeButtons();
    }

    likedSearchForm?.addEventListener("submit", (event) => {
        event.preventDefault();
        currentKeyword = likedSearchInput?.value || "";
        renderLikedVideos();
    });

    renderLikedVideos();
}



async function initWatchPage() {
    const watchMain = document.getElementById("watchMain");
    function bindCommentActionButtons(){}
    if (!watchMain) return;

    const authMe = getAuthMe();
    const watchRecommendList = document.getElementById("watchRecommendList");
    const params = new URLSearchParams(window.location.search);
    const rawVideoId = params.get("v") || params.get("id");
    const videoId = Number(rawVideoId);

    const uploadedVideos = await fetchUploadedVideos();
    const allVideos = makeFeedVideos(uploadedVideos);

    let currentVideo = allVideos.find((video) => video.id === videoId) || allVideos[0];
    if (!currentVideo) {
        watchMain.innerHTML = `<p style="color:#aaa;">영상을 찾을 수 없습니다.</p>`;
        return;
    }

    try {
        await addVideoToHistory(currentVideo.id);
    } catch (error) {
        console.warn("시청 기록 저장 실패:", error);
    }

    const updatedViewCount = incrementViewCount(currentVideo);

    let isLiked = Boolean(currentVideo.likedByMe);
    let likeCount = Number(currentVideo.likeCount || 0);
    let isSubscribed = false;
    let subscriberCount = 0;
    let isSaved = Boolean(currentVideo.savedByMe);

    const isOwnVideo = authMe.loggedIn && authMe.user && String(authMe.user.id) === String(currentVideo.ownerId);

    if (currentVideo.ownerId && !isOwnVideo) {
        try {
            const subStatus = await fetchSubscriptionStatus(currentVideo.ownerId);
            isSubscribed = Boolean(subStatus.subscribed);
            subscriberCount = Number(subStatus.subscriberCount || 0);
        } catch {}
    }

    let comments = [];
    let commentPage = 0;
    let commentHasMore = false;
    let commentTotal = 0;

    const initCommentData = await fetchCommentsByVideoId(currentVideo.id, 0);
    comments = initCommentData.comments || [];
    commentHasMore = initCommentData.hasMore || false;
    commentTotal = Number(initCommentData.total || 0);

    const recommendVideos = getRecommendedVideos(currentVideo, allVideos, 12);

    const descriptionText = String(currentVideo.description || "");
    const shouldCollapseDescription = descriptionText.length > 140 || descriptionText.includes("\n");

    const resolutionOptions = [];
    if (currentVideo.videoUrl) {
        resolutionOptions.push({ label: "원본", src: currentVideo.videoUrl });
        if (currentVideo.videoUrl1080) resolutionOptions.push({ label: "1080p", src: currentVideo.videoUrl1080 });
        if (currentVideo.videoUrl720) resolutionOptions.push({ label: "720p", src: currentVideo.videoUrl720 });
        if (currentVideo.videoUrl480) resolutionOptions.push({ label: "480p", src: currentVideo.videoUrl480 });
        if (currentVideo.videoUrl360) resolutionOptions.push({ label: "360p", src: currentVideo.videoUrl360 });
    }
    watchMain.innerHTML = `
    <div class="player-box">${createPlayerMarkup(currentVideo, resolutionOptions)}</div>
    <h1 class="watch-title">${escapeHtml(currentVideo.title)}</h1>

    <div class="watch-meta-row">
      <div class="watch-channel-box">
        <img class="watch-channel-avatar" src="${escapeHtml(currentVideo.avatar || DEFAULT_AVATAR)}" alt="${escapeHtml(currentVideo.channel)}" />
        <div class="watch-channel-text">
          <strong>${escapeHtml(currentVideo.channel)}</strong>
          <span id="subscriberCount">구독자 ${formatCount(subscriberCount)}명</span>
        </div>
        ${!isOwnVideo ? `<button class="watch-action-btn ${isSubscribed ? "" : "primary"}" id="subscribeBtn" type="button">${isSubscribed ? "구독중" : "구독"}</button>` : ""}
      </div>

      <div class="watch-actions">
        <button class="watch-action-btn ${isLiked ? "active" : ""}" id="likeBtn" type="button">
          좋아요 ${formatCount(likeCount)}
        </button>
        <button class="watch-action-btn" id="shareBtn" type="button">공유</button>
        <button class="watch-action-btn ${isSaved ? "active" : ""}" id="saveBtn" type="button">
          ${isSaved ? "저장됨" : "저장"}
        </button>
      </div>
    </div>

    <div class="watch-description-box ${shouldCollapseDescription ? "is-collapsed" : ""}" id="watchDescriptionBox">
      <span class="watch-description-meta">조회수 ${formatCount(updatedViewCount)}회 · ${escapeHtml(currentVideo.date || "방금 전")}</span>
      <div class="watch-description-text" id="watchDescriptionText">${escapeHtml(descriptionText || "설명이 없습니다.")}</div>
      ${shouldCollapseDescription ? '<button type="button" class="watch-description-toggle" id="watchDescriptionToggle">더보기</button>' : ""}
    </div>

    <section class="comments-section">
      <div class="comments-header">
        <h2 id="commentsCount"></h2>
      </div>

      <div class="comment-form">
        <div class="comment-form-avatar">${escapeHtml((authMe.user?.nickname || authMe.user?.username || "G").charAt(0).toUpperCase())}</div>
        <div class="comment-form-body">
          <input type="text" class="comment-input" id="commentInput" placeholder="${authMe.loggedIn ? "댓글 추가..." : "로그인 후 댓글을 입력할 수 있어"}" maxlength="300" ${authMe.loggedIn ? "" : "readonly"} />
          <div class="comment-form-actions">
            <button class="comment-btn cancel" id="commentCancelBtn" type="button">취소</button>
            <button class="comment-btn submit" id="commentSubmitBtn" type="button" disabled>댓글</button>
          </div>
        </div>
      </div>

      <div class="comment-list" id="commentList"></div>
    </section>
  `;

    if (watchRecommendList) {
        watchRecommendList.innerHTML = recommendVideos.map(createRecommendCard).join("");
    }

    initCustomPlayer();
    initMiniPlayerWatcher(currentVideo);

    const startTime = parseFloat(params.get("t"));
    const fromMini  = params.get("autoplay") === "1";

    // 미니 플레이어에서 왔을 때: FLIP 애니메이션 (우측 하단에서 커지는 효과)
    if (fromMini) {
        const player = document.getElementById("customPlayer");
        if (player) {
            const rect = player.getBoundingClientRect();
            const mW = 320, mH = Math.round(320 * 9 / 16);
            const sx = mW / rect.width;
            const sy = mH / rect.height;
            const dx = (window.innerWidth  - 24 - mW / 2) - (rect.left + rect.width  / 2);
            const dy = (window.innerHeight - 24 - mH / 2) - (rect.top  + rect.height / 2);

            player.style.cssText += `
                transform-origin: center center;
                transform: translate(${dx}px, ${dy}px) scale(${sx}, ${sy});
                transition: none;
                border-radius: 12px;
                overflow: hidden;
                z-index: 100;
            `;
            requestAnimationFrame(() => requestAnimationFrame(() => {
                player.style.transition = "transform 0.42s cubic-bezier(0.4, 0, 0.2, 1), border-radius 0.42s";
                player.style.transform  = "";
                player.style.borderRadius = "";
                player.addEventListener("transitionend", () => {
                    player.style.zIndex = player.style.transition = "";
                }, { once: true });
            }));
        }
    }

    if (startTime > 0) {
        const playerVideo = document.querySelector("#customPlayer video.player-video");
        if (playerVideo) {
            const doSeek = () => {
                playerVideo.currentTime = startTime;
                if (fromMini) {
                    playerVideo.addEventListener("seeked", () => {
                        playerVideo.play().catch(() => {});
                    }, { once: true });
                }
            };
            if (playerVideo.readyState >= 1) doSeek();
            else playerVideo.addEventListener("loadedmetadata", doSeek, { once: true });
        }
    }

    // 이어보기: 저장된 재생 위치 확인 후 토스트 표시
    if (authMe.loggedIn && !fromMini && !(startTime > 0)) {
        const savedPos = await fetchVideoProgress(currentVideo.id);
        if (savedPos > 5) {
            const playerBox = document.querySelector(".player-box");
            if (playerBox) {
                const mins = Math.floor(savedPos / 60);
                const secs = Math.floor(savedPos % 60);
                const timeStr = mins > 0 ? `${mins}분 ${secs}초` : `${secs}초`;

                const toast = document.createElement("div");
                toast.className = "resume-toast";
                toast.innerHTML = `
                    <span class="resume-toast-text">${timeStr}에서 이어보기</span>
                    <button class="resume-toast-btn resume-btn" type="button">이어보기</button>
                    <button class="resume-toast-btn dismiss-btn" type="button">처음부터</button>
                `;
                playerBox.appendChild(toast);

                toast.querySelector(".resume-btn").addEventListener("click", () => {
                    const pv = document.querySelector("#customPlayer video.player-video");
                    if (pv) {
                        const doSeek = () => { pv.currentTime = savedPos; };
                        if (pv.readyState >= 1) doSeek();
                        else pv.addEventListener("loadedmetadata", doSeek, { once: true });
                    }
                    toast.remove();
                });
                toast.querySelector(".dismiss-btn").addEventListener("click", () => toast.remove());
                setTimeout(() => { if (toast.parentNode) toast.remove(); }, 6000);
            }
        }
    }

    // 재생 위치 자동 저장 (로그인 상태일 때)
    if (authMe.loggedIn) {
        const pv = document.querySelector("#customPlayer video.player-video");
        if (pv) {
            let lastSaved = 0;
            const doSave = () => {
                const pos = pv.currentTime;
                if (pos > 1 && Math.abs(pos - lastSaved) > 3) {
                    lastSaved = pos;
                    saveVideoProgress(currentVideo.id, pos);
                }
            };
            setInterval(doSave, 15000);
            pv.addEventListener("pause", doSave);
            document.addEventListener("visibilitychange", () => {
                if (document.visibilityState === "hidden") doSave();
            });
            window.addEventListener("beforeunload", doSave);
        }
    }

    const subscribeBtn = document.getElementById("subscribeBtn");
    const likeBtn = document.getElementById("likeBtn");
    const shareBtn = document.getElementById("shareBtn");
    const saveBtn = document.getElementById("saveBtn");
    const commentInput = document.getElementById("commentInput");
    const commentSubmitBtn = document.getElementById("commentSubmitBtn");
    const commentCancelBtn = document.getElementById("commentCancelBtn");
    const commentList = document.getElementById("commentList");
    const commentsCount = document.getElementById("commentsCount");
    const descriptionBox = document.getElementById("watchDescriptionBox");
    const descriptionToggle = document.getElementById("watchDescriptionToggle");

    function refreshLikeButton() {
        if (!likeBtn) return;
        likeBtn.classList.toggle("active", isLiked);
        likeBtn.textContent = `좋아요 ${formatCount(likeCount)}`;
    }

    function refreshSubscribeButton() {
        if (!subscribeBtn) return;
        subscribeBtn.textContent = isSubscribed ? "구독중" : "구독";
        subscribeBtn.classList.toggle("primary", !isSubscribed);
    }

    function refreshSaveButton() {
        if (!saveBtn) return;
        saveBtn.textContent = isSaved ? "저장됨" : "저장";
        saveBtn.classList.toggle("active", isSaved);
    }

    async function loadMoreComments() {
        const btn = document.getElementById("commentLoadMore");
        if (btn) { btn.disabled = true; btn.textContent = "불러오는 중..."; }
        commentPage++;
        const data = await fetchCommentsByVideoId(currentVideo.id, commentPage);
        comments = [...comments, ...(data.comments || [])];
        commentHasMore = data.hasMore || false;
        refreshComments();
    }

    function refreshComments() {
        renderCommentList(commentList, commentsCount, comments, commentTotal, commentHasMore, loadMoreComments);
        bindCommentActionButtons();
    }

    function findCommentOrReply(id) {
        for (const c of comments) {
            if (Number(c.id) === id) return c;
            for (const r of (c.replies || [])) {
                if (Number(r.id) === id) return r;
            }
        }
        return null;
    }

    function bindCommentActionButtons() {
        const editButtons = commentList?.querySelectorAll("[data-comment-edit]") || [];
        const deleteButtons = commentList?.querySelectorAll("[data-comment-delete]") || [];
        const cancelButtons = commentList?.querySelectorAll("[data-comment-edit-cancel]") || [];
        const saveButtons = commentList?.querySelectorAll("[data-comment-edit-save]") || [];
        const replyBtns = commentList?.querySelectorAll("[data-reply-to]") || [];
        const replyCancels = commentList?.querySelectorAll("[data-reply-cancel]") || [];
        const replySubmits = commentList?.querySelectorAll("[data-reply-submit]") || [];
        const replyInputs = commentList?.querySelectorAll("[data-reply-input]") || [];
        const likeBtns = commentList?.querySelectorAll("[data-comment-like]") || [];

        likeBtns.forEach(btn => {
            btn.addEventListener("click", async () => {
                const commentId = btn.dataset.commentLike;
                const res = await fetch(`/api/comments/${commentId}/like`, { method: "POST" });
                if (!res.ok) return;
                const data = await res.json();
                btn.dataset.liked = data.liked ? "true" : "false";
                btn.classList.toggle("is-liked", data.liked);
                const countEl = btn.querySelector(".comment-like-count");
                if (countEl) countEl.textContent = data.likeCount > 0 ? data.likeCount : "";
            });
        });

        // 답글 버튼 토글
        replyBtns.forEach(btn => {
            btn.addEventListener("click", () => {
                const parentId = btn.dataset.replyTo;
                const form = commentList.querySelector(`[data-reply-form="${parentId}"]`);
                if (!form) return;
                if (!authMe.loggedIn) { requireAuthRedirect(); return; }
                form.hidden = !form.hidden;
                if (!form.hidden) form.querySelector(`[data-reply-input]`)?.focus();
            });
        });

        // 답글 입력 → 버튼 활성화
        replyInputs.forEach(input => {
            const parentId = input.dataset.replyInput;
            const submitBtn = commentList.querySelector(`[data-reply-submit="${parentId}"]`);
            input.addEventListener("input", () => {
                if (submitBtn) submitBtn.disabled = input.value.trim() === "";
            });
        });

        // 답글 취소
        replyCancels.forEach(btn => {
            btn.addEventListener("click", () => {
                const parentId = btn.dataset.replyCancel;
                const form = commentList.querySelector(`[data-reply-form="${parentId}"]`);
                const input = commentList.querySelector(`[data-reply-input="${parentId}"]`);
                if (form) form.hidden = true;
                if (input) input.value = "";
                const submitBtn = commentList.querySelector(`[data-reply-submit="${parentId}"]`);
                if (submitBtn) submitBtn.disabled = true;
            });
        });

        // 답글 제출
        replySubmits.forEach(btn => {
            btn.addEventListener("click", async () => {
                const parentId = Number(btn.dataset.replySubmit);
                const input = commentList.querySelector(`[data-reply-input="${parentId}"]`);
                const text = input?.value.trim() || "";
                if (!text) return;
                btn.disabled = true;
                try {
                    const newReply = await createReplyByCommentId(parentId, text);
                    const parent = comments.find(c => Number(c.id) === parentId);
                    if (parent) {
                        parent.replies = parent.replies || [];
                        parent.replies.push(newReply);
                    }
                    commentTotal++;
                    const form = commentList.querySelector(`[data-reply-form="${parentId}"]`);
                    if (form) form.hidden = true;
                    if (input) input.value = "";
                    refreshComments();
                } catch (e) {
                    alert(e.message || "답글 작성 중 오류가 발생했습니다.");
                    btn.disabled = false;
                }
            });
        });

        editButtons.forEach((button) => {
            button.addEventListener("click", () => {
                const commentId = Number(button.dataset.commentEdit);
                const editBox = commentList.querySelector(`[data-comment-edit-box="${commentId}"]`);
                if (editBox) editBox.hidden = false;
            });
        });

        cancelButtons.forEach((button) => {
            button.addEventListener("click", () => {
                const commentId = Number(button.dataset.commentEditCancel);
                const editBox = commentList.querySelector(`[data-comment-edit-box="${commentId}"]`);
                const input = commentList.querySelector(`[data-comment-edit-input="${commentId}"]`);
                const original = findCommentOrReply(commentId);
                if (input && original) {
                    input.value = original.text || "";
                }

                if (editBox) editBox.hidden = true;
            });
        });

        saveButtons.forEach((button) => {
            button.addEventListener("click", async () => {
                const commentId = Number(button.dataset.commentEditSave);
                const input = commentList.querySelector(`[data-comment-edit-input="${commentId}"]`);
                const editBox = commentList.querySelector(`[data-comment-edit-box="${commentId}"]`);
                const nextText = input?.value.trim() || "";

                if (!nextText) {
                    alert("댓글 내용을 입력해줘.");
                    return;
                }

                try {
                    const updatedComment = await updateCommentById(commentId, nextText);
                    comments = comments.map(item => {
                        if (Number(item.id) === commentId) return { ...updatedComment, replies: item.replies || [] };
                        item.replies = (item.replies || []).map(r => Number(r.id) === commentId ? updatedComment : r);
                        return item;
                    });
                    refreshComments();
                } catch (error) {
                    alert(error.message || "댓글 수정 중 오류가 발생했어.");
                    if (editBox) editBox.hidden = false;
                }
            });
        });

        deleteButtons.forEach((button) => {
            button.addEventListener("click", async () => {
                const commentId = Number(button.dataset.commentDelete);
                const ok = await confirmAction("이 댓글을 삭제할까요?", "삭제");
                if (!ok) return;

                try {
                    await deleteCommentById(commentId);
                    const deleted = comments.find(item => Number(item.id) === commentId);
                    const deletedReplies = deleted?.replies?.length || 0;
                    comments = comments.filter(item => Number(item.id) !== commentId);
                    comments = comments.map(item => {
                        const before = item.replies?.length || 0;
                        item.replies = (item.replies || []).filter(r => Number(r.id) !== commentId);
                        if (item.replies.length < before) commentTotal--;
                        return item;
                    });
                    if (deleted) commentTotal -= (1 + deletedReplies);
                    refreshComments();
                    showToast("댓글이 삭제되었습니다.");
                } catch (error) {
                    alert(error.message || "댓글 삭제 중 오류가 발생했어.");
                }
            });
        });
    }

    subscribeBtn?.addEventListener("click", async () => {
        if (!requireAuthRedirect()) return;
        try {
            const result = await toggleSubscription(currentVideo.ownerId);
            isSubscribed = Boolean(result.subscribed);
            subscriberCount = Number(result.subscriberCount || 0);
            refreshSubscribeButton();
            const countEl = document.getElementById("subscriberCount");
            if (countEl) countEl.textContent = `구독자 ${formatCount(subscriberCount)}명`;
        } catch {
            showToast("구독 처리 중 오류가 발생했습니다.");
        }
    });

    likeBtn?.addEventListener("click", async () => {
        if (!requireAuthRedirect()) return;

        try {
            const result = await toggleLikeByVideoId(currentVideo.id);
            isLiked = Boolean(result.liked);
            likeCount = Number(result.likeCount || 0);
            refreshLikeButton();
        } catch (error) {
            alert(error.message || "좋아요 처리 중 오류가 발생했어.");
        }
    });

    shareBtn?.addEventListener("click", () => {
        const pv = document.querySelector("#customPlayer video.player-video");
        const getCurrentTime = () => pv ? pv.currentTime : 0;
        showShareModal(currentVideo.id, getCurrentTime);
    });

    saveBtn?.addEventListener("click", async () => {
        if (!requireAuthRedirect()) return;

        try {
            const result = await toggleSaveByVideoId(currentVideo.id);
            isSaved = Boolean(result.saved);
            refreshSaveButton();
            showToast(isSaved ? "영상이 저장되었습니다." : "저장이 해제되었습니다.");
        } catch (error) {
            alert(error.message || "저장 처리 중 오류가 발생했어.");
        }
    });

    descriptionToggle?.addEventListener("click", () => {
        const collapsed = descriptionBox.classList.toggle("is-collapsed");
        descriptionToggle.textContent = collapsed ? "더보기" : "접기";
    });

    commentInput?.addEventListener("focus", () => {
        if (!authMe.loggedIn) {
            requireAuthRedirect();
        }
    });

    commentInput?.addEventListener("input", () => {
        if (!commentSubmitBtn) return;

        if (!authMe.loggedIn) {
            commentSubmitBtn.disabled = true;
            return;
        }

        commentSubmitBtn.disabled = commentInput.value.trim() === "";
    });

    commentInput?.addEventListener("keydown", (event) => {
        if (!authMe.loggedIn) {
            requireAuthRedirect();
            return;
        }

        if (event.key === "Enter" && !event.shiftKey) {
            event.preventDefault();

            if (!commentSubmitBtn?.disabled) {
                commentSubmitBtn.click();
            }
        }
    });

    commentCancelBtn?.addEventListener("click", () => {
        if (commentInput) commentInput.value = "";
        if (commentSubmitBtn) commentSubmitBtn.disabled = true;
    });

    commentSubmitBtn?.addEventListener("click", async () => {
        if (!requireAuthRedirect()) return;

        const text = commentInput.value.trim();
        if (!text) return;

        try {
            const createdComment = await createCommentByVideoId(currentVideo.id, text);
            comments.unshift(createdComment);
            commentTotal++;
            refreshComments();
            commentInput.value = "";
            commentSubmitBtn.disabled = true;
        } catch (error) {
            alert(error.message || "댓글 작성 중 오류가 발생했어.");
        }
    });

    refreshLikeButton();
    refreshSubscribeButton();
    refreshSaveButton();
    refreshComments();
}


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
                setPendingToast("영상 수정이 완료되었습니다.");
                window.location.href = getVideoUrl(videoId);
            } catch (error) {
                alert(error.message || "수정 중 오류가 발생했어.");
                editSubmitBtn.disabled = false;
                editSubmitBtn.textContent = "수정 저장";
            }
        },
        { once: true }
    );
}

function initTabBar(barId, onSwitch) {
    const bar = document.getElementById(barId);
    if (!bar) return;
    bar.querySelectorAll(".ch-tab-btn").forEach(btn => {
        btn.addEventListener("click", () => {
            bar.querySelectorAll(".ch-tab-btn").forEach(b => b.classList.remove("is-active"));
            btn.classList.add("is-active");
            onSwitch(btn.dataset.tab);
        });
    });
}

function renderPlaylistGrid(containerId, emptyId, playlists) {
    const grid = document.getElementById(containerId);
    const empty = document.getElementById(emptyId);
    if (!grid) return;
    if (!playlists.length) {
        if (empty) empty.hidden = false;
        grid.innerHTML = "";
        return;
    }
    if (empty) empty.hidden = true;
    grid.innerHTML = playlists.map(p => `
        <a href="playlist.html?id=${p.id}" class="ch-pl-card" style="text-decoration:none;">
            <div class="ch-pl-thumb">
                ${p.thumbnail
                    ? `<img src="${escapeHtml(p.thumbnail)}" alt="${escapeHtml(p.name)}">`
                    : `<div class="ch-pl-thumb-empty"><svg viewBox="0 0 24 24" style="width:40px;height:40px;fill:#555"><path d="M4 6h16v2H4V6zm0 5h16v2H4v-2zm0 5h16v2H4v-2z"/></svg></div>`}
                <span class="ch-pl-count">${p.videoCount}개</span>
            </div>
            <div class="ch-pl-info">
                <div class="ch-pl-name">${escapeHtml(p.name)}</div>
                <div class="ch-pl-meta">영상 ${p.videoCount}개</div>
            </div>
        </a>`).join("");
}

function renderAboutSection(containerId, info) {
    const el = document.getElementById(containerId);
    if (!el) return;
    const rows = [];
    if (info.bio) rows.push({ label: "소개", value: info.bio });
    rows.push({ label: "구독자", value: `${formatCount(info.subscriberCount || 0)}명` });
    rows.push({ label: "영상 수", value: `${info.videoCount || 0}개` });
    if (info.joinDate) rows.push({ label: "가입일", value: info.joinDate });
    el.innerHTML = rows.map(r => `
        <div class="ch-about-row">
            <span class="ch-about-label">${escapeHtml(r.label)}</span>
            <span class="ch-about-value">${escapeHtml(r.value)}</span>
        </div>`).join("");
}

async function initChannelPage() {
    const channelVideoGrid = document.getElementById("channelVideoGrid");
    const channelEmptyState = document.getElementById("channelEmptyState");

    const authMe = getAuthMe();
    const heroTitle = document.querySelector(".channel-hero-text h1");
    const heroDesc = document.querySelector(".channel-hero-text p");
    const avatarLg = document.querySelector(".channel-avatar-lg");

    let channelInfo = { subscriberCount: 0, videoCount: 0, bio: "" };

    if (authMe.loggedIn && authMe.user) {
        const displayName = authMe.user.channelName || authMe.user.nickname || authMe.user.username || "내";
        if (heroTitle) heroTitle.textContent = `${displayName} 채널`;
        if (heroDesc) { heroDesc.style.display = "none"; }
        if (avatarLg) avatarLg.textContent = String(displayName).charAt(0).toUpperCase();

        channelInfo = {
            subscriberCount: authMe.user.subscriberCount || 0,
            videoCount: 0,
            bio: authMe.user.bio || "",
        };
    }

    let playlistsLoaded = false;

    const tabPanels = {
        videos: document.getElementById("tabVideos"),
        playlists: document.getElementById("tabPlaylists"),
        about: document.getElementById("tabAbout"),
    };

    function showTab(name) {
        Object.values(tabPanels).forEach(p => p?.classList.remove("is-active"));
        tabPanels[name]?.classList.add("is-active");
        if (name === "playlists" && !playlistsLoaded) {
            playlistsLoaded = true;
            loadMyPlaylists();
        }
        if (name === "about") {
            renderAboutSection("channelAboutSection", channelInfo);
        }
    }

    initTabBar("channelTabsBar", showTab);

    async function loadMyPlaylists() {
        try {
            const res = await fetch("/api/playlists/me");
            if (!res.ok) return;
            const list = await res.json();
            renderPlaylistGrid("channelPlaylistGrid", "channelPlaylistEmpty", list);
        } catch {}
    }

    if (!channelVideoGrid) return;

    try {
        const videos = await fetchMyVideos();
        channelInfo.videoCount = videos ? videos.length : 0;
        if (!videos || videos.length === 0) {
            if (channelEmptyState) channelEmptyState.hidden = false;
            return;
        }
        channelVideoGrid.innerHTML = videos.map(v => createVideoCard(v)).join("");
    } catch {
        if (channelEmptyState) channelEmptyState.hidden = false;
    }
}

async function initStudioPage() {
    const channelManageList = document.getElementById("channelManageList");
    if (!channelManageList) return;

    if (!requireAuthRedirect()) return;

    const authMe = getAuthMe();
    const headerAvatar = document.getElementById("studioHeaderAvatar");
    if (headerAvatar && authMe.loggedIn && authMe.user) {
        const displayName = authMe.user.channelName || authMe.user.nickname || authMe.user.username || "?";
        if (authMe.user.profileImage) {
            headerAvatar.innerHTML = `<img src="${escapeHtml(authMe.user.profileImage)}" style="width:32px;height:32px;object-fit:cover;border-radius:50%;" onerror="this.parentElement.textContent='${escapeHtml(displayName[0])}'" alt="">`;
        } else {
            headerAvatar.textContent = displayName[0].toUpperCase();
        }
    }

    const studioManageTable = document.getElementById("studioManageTable");
    const channelEmptyState = document.getElementById("channelEmptyState");
    const channelEmptyTitle = document.getElementById("channelEmptyTitle");
    const channelEmptyText = document.getElementById("channelEmptyText");
    const channelSearchInput = document.getElementById("channelSearchInput");
    const channelVisibilityFilter = document.getElementById("channelVisibilityFilter");
    const channelSortSelect = document.getElementById("channelSortSelect");

    let uploadedVideosCache = [];
    let currentFilter = "전체";

    function normalizeVideos(videos) {
        return videos.map(video => ({
            ...video,
            title: video.title || "제목 없음",
            description: video.description || "",
            category: video.category || "미분류",
            visibility: video.visibility || "공개",
            thumbnail: video.thumbnail || DEFAULT_THUMBNAIL,
            channel: video.channel || "채널명",
            date: video.date || "방금 전",
            duration: video.duration || "0:00",
            likeCount: Number(video.likeCount || 0),
            commentCount: Number(video.commentCount || 0)
        }));
    }

    function getFilteredVideos(videos) {
        if (!channelSearchInput) return videos;
        const keyword = channelSearchInput.value.trim().toLowerCase();
        return videos.filter(video => {
            const matchesKeyword = keyword === "" ||
                video.title.toLowerCase().includes(keyword) ||
                video.description.toLowerCase().includes(keyword) ||
                video.category.toLowerCase().includes(keyword);
            const matchesVisibility = currentFilter === "전체" || video.visibility === currentFilter;
            return matchesKeyword && matchesVisibility;
        });
    }

    function getSortedVideos(videos) {
        if (!channelSortSelect) return videos;
        const sortType = channelSortSelect.value;
        const copied = [...videos];
        copied.sort((a, b) => {
            if (sortType === "latest") return Number(b.id) - Number(a.id);
            if (sortType === "oldest") return Number(a.id) - Number(b.id);
            if (sortType === "views") return loadViewCount(b) - loadViewCount(a);
            if (sortType === "likes") return Number(b.likeCount || 0) - Number(a.likeCount || 0);
            if (sortType === "comments") return Number(b.commentCount || 0) - Number(a.commentCount || 0);
            if (sortType === "title") return a.title.localeCompare(b.title, "ko");
            return 0;
        });
        return copied;
    }

    function bindCopyButtons(currentVideos) {
        channelManageList.querySelectorAll("[data-copy-id]").forEach(button => {
            button.addEventListener("click", async () => {
                const id = Number(button.dataset.copyId);
                const target = currentVideos.find(v => v.id === id);
                if (!target) return;
                const shareUrl = getShareUrl(id);
                try {
                    const copied = await copyTextToClipboard(shareUrl);
                    if (copied) {
                        showToast(`"${target.title}" 링크가 복사되었습니다.`);
                    } else {
                        prompt("이 링크를 복사해줘.", shareUrl);
                    }
                } catch {
                    prompt("이 링크를 복사해줘.", shareUrl);
                }
            });
        });
    }

    function bindDeleteButtons(currentVideos) {
        channelManageList.querySelectorAll("[data-delete-id]").forEach(button => {
            button.addEventListener("click", async () => {
                const id = Number(button.dataset.deleteId);
                const target = currentVideos.find(v => v.id === id);
                if (!target) return;
                const ok = await confirmAction(`"${target.title}" 영상을 삭제할까요?\n삭제 후에는 되돌릴 수 없습니다.`);
                if (!ok) return;
                try {
                    await deleteVideoById(id);
                    localStorage.removeItem(getViewCountKey(id));
                    showToast("영상이 삭제되었습니다.");
                    await loadAndRender();
                } catch (error) {
                    alert(error.message || "삭제 중 오류가 발생했어.");
                }
            });
        });
    }

    function updateEmptyState(filteredVideos) {
        const keyword = channelSearchInput ? channelSearchInput.value.trim() : "";
        const hasKeyword = keyword !== "";
        const isFiltered = hasKeyword || currentFilter !== "전체";
        const hasAnyVideos = uploadedVideosCache.length > 0;

        if (!hasAnyVideos) {
            if (studioManageTable) studioManageTable.hidden = true;
            if (channelEmptyState) channelEmptyState.hidden = false;
            if (channelEmptyTitle) channelEmptyTitle.textContent = "아직 업로드한 영상이 없습니다";
            if (channelEmptyText) channelEmptyText.textContent = "첫 영상을 업로드해서 내 채널을 채워보자.";
            return;
        }

        if (filteredVideos.length === 0 && isFiltered) {
            if (studioManageTable) studioManageTable.hidden = true;
            if (channelEmptyState) channelEmptyState.hidden = false;
            if (hasKeyword && currentFilter !== "전체") {
                if (channelEmptyTitle) channelEmptyTitle.textContent = "조건에 맞는 영상이 없습니다";
                if (channelEmptyText) channelEmptyText.textContent = `"${keyword}" 검색과 "${currentFilter}" 필터에 맞는 영상이 없어.`;
            } else if (hasKeyword) {
                if (channelEmptyTitle) channelEmptyTitle.textContent = "검색 결과가 없습니다";
                if (channelEmptyText) channelEmptyText.textContent = `"${keyword}"와 일치하는 영상이 없어.`;
            } else {
                if (channelEmptyTitle) channelEmptyTitle.textContent = "필터 결과가 없습니다";
                if (channelEmptyText) channelEmptyText.textContent = `현재 "${currentFilter}" 상태의 영상이 없어.`;
            }
            return;
        }

        if (studioManageTable) studioManageTable.hidden = false;
        if (channelEmptyState) channelEmptyState.hidden = true;
    }

    function renderFilteredList() {
        const filteredVideos = getFilteredVideos(uploadedVideosCache);
        const sortedVideos = getSortedVideos(filteredVideos);
        updateEmptyState(sortedVideos);
        if (sortedVideos.length === 0) {
            channelManageList.innerHTML = "";
            return;
        }
        channelManageList.innerHTML = sortedVideos.map(createManageCard).join("");
        bindCopyButtons(sortedVideos);
        bindDeleteButtons(sortedVideos);
    }

    function renderStudioStats(videos) {
        const statsEl = document.getElementById("studioStats");
        if (!statsEl) return;
        const totalViews = videos.reduce((s, v) => s + Number(v.viewCount || 0), 0);
        const totalLikes = videos.reduce((s, v) => s + Number(v.likeCount || 0), 0);
        const totalComments = videos.reduce((s, v) => s + Number(v.commentCount || 0), 0);
        let subscriberVal = "0";
        if (authMe.user?.subscriberCount !== undefined) {
            subscriberVal = formatCount(Number(authMe.user.subscriberCount));
        } else if (authMe.user?.subscribers) {
            subscriberVal = String(authMe.user.subscribers).replace(/구독자/g, "").trim();
        }
        const items = [
            { label: "총 조회수", value: formatCount(totalViews) },
            { label: "영상 수",   value: videos.length },
            { label: "구독자",    value: subscriberVal },
            { label: "총 좋아요", value: formatCount(totalLikes) },
            { label: "총 댓글",  value: formatCount(totalComments) },
        ];
        statsEl.innerHTML = items.map(s => `
            <div class="studio-stat-card">
                <span class="studio-stat-label">${s.label}</span>
                <span class="studio-stat-value">${s.value}</span>
            </div>`).join("");
    }

    function renderAnalyticsExtra(videos) {
        const el = document.getElementById("studioAnalyticsExtra");
        if (!el) return;

        const totalViews = videos.reduce((s, v) => s + Number(v.viewCount || 0), 0);
        const totalLikes = videos.reduce((s, v) => s + Number(v.likeCount || 0), 0);
        const totalComments = videos.reduce((s, v) => s + Number(v.commentCount || 0), 0);
        const engagementRate = totalViews > 0
            ? (((totalLikes + totalComments) / totalViews) * 100).toFixed(1)
            : "0.0";

        const top5 = [...videos]
            .sort((a, b) => Number(b.viewCount || 0) - Number(a.viewCount || 0))
            .slice(0, 5);

        const catMap = new Map();
        videos.forEach(v => {
            const cat = v.category || "미분류";
            catMap.set(cat, (catMap.get(cat) || 0) + Number(v.viewCount || 0));
        });
        const categories = Array.from(catMap.entries())
            .sort((a, b) => b[1] - a[1])
            .slice(0, 5);
        const maxCat = Math.max(...categories.map(c => c[1]), 1);

        const videoRowsHtml = top5.length === 0
            ? `<p class="sa-empty">영상이 없습니다.</p>`
            : top5.map((v, i) => `
                <div class="sa-rank-row">
                    <span class="sa-rank-num">${i + 1}</span>
                    <span class="sa-rank-title">${escapeHtml(v.title || "제목 없음")}</span>
                    <span class="sa-rank-views">${formatCount(Number(v.viewCount || 0))}회</span>
                </div>`).join("");

        const catRowsHtml = categories.length === 0
            ? `<p class="sa-empty">데이터가 없습니다.</p>`
            : categories.map(([label, value]) => {
                const w = Math.max(4, (value / maxCat) * 100);
                return `
                <div class="sa-bar-row">
                    <div class="sa-bar-meta">
                        <span>${escapeHtml(label)}</span>
                        <strong>${formatCount(value)}</strong>
                    </div>
                    <div class="sa-bar-track">
                        <div class="sa-bar-fill" style="width:${w}%"></div>
                    </div>
                </div>`;
            }).join("");

        el.innerHTML = `
            <div class="sa-grid">
                <section class="sa-panel">
                    <p class="sa-panel-title">조회수 TOP 5</p>
                    ${videoRowsHtml}
                </section>
                <section class="sa-panel">
                    <p class="sa-panel-title">카테고리별 조회수</p>
                    ${catRowsHtml}
                </section>
            </div>
            <section class="sa-panel sa-engagement">
                <div class="sa-engagement-item">
                    <span class="sa-engagement-label">참여율</span>
                    <strong class="sa-engagement-value">${engagementRate}%</strong>
                </div>
                <div class="sa-engagement-item">
                    <span class="sa-engagement-label">좋아요</span>
                    <strong class="sa-engagement-value">${formatCount(totalLikes)}</strong>
                </div>
                <div class="sa-engagement-item">
                    <span class="sa-engagement-label">댓글</span>
                    <strong class="sa-engagement-value">${formatCount(totalComments)}</strong>
                </div>
            </section>`;
    }

    function renderViewsChart(videos) {
        const chartEl = document.getElementById("studioChart");
        if (!chartEl) return;
        const top = [...videos]
            .sort((a, b) => Number(b.viewCount || 0) - Number(a.viewCount || 0))
            .slice(0, 7);
        if (top.length === 0) {
            chartEl.innerHTML = `<p class="studio-chart-empty">업로드된 영상이 없어 차트를 표시할 수 없습니다.</p>`;
            return;
        }
        const maxViews = Math.max(...top.map(v => Number(v.viewCount || 0)), 1);
        const W = 560, H = 200;
        const padL = 52, padR = 16, padT = 12, padB = 50;
        const chartW = W - padL - padR;
        const chartH = H - padT - padB;
        const barGap = 10;
        const barW = Math.floor((chartW - barGap * (top.length - 1)) / top.length);
        let grid = "";
        for (let i = 0; i <= 4; i++) {
            const val = Math.round(maxViews * i / 4);
            const y = padT + chartH - (chartH * i / 4);
            grid += `<line x1="${padL}" y1="${y}" x2="${W - padR}" y2="${y}" stroke="#222" stroke-width="1"/>
            <text x="${padL - 6}" y="${y + 4}" text-anchor="end" fill="#666" font-size="10">${formatCount(val)}</text>`;
        }
        let bars = "";
        top.forEach((v, i) => {
            const views = Number(v.viewCount || 0);
            const bh = Math.max(2, (views / maxViews) * chartH);
            const x = padL + i * (barW + barGap);
            const y = padT + chartH - bh;
            const label = (v.title || "").length > 7 ? (v.title || "").slice(0, 7) + "…" : (v.title || "");
            bars += `
            <rect x="${x}" y="${y}" width="${barW}" height="${bh}" rx="3" fill="#3ea6ff">
                <title>${escapeHtml(v.title || "")}: ${formatCount(views)}회</title>
            </rect>
            <text x="${x + barW / 2}" y="${padT + chartH + 14}" text-anchor="middle" fill="#999" font-size="10">${escapeHtml(label)}</text>
            ${views > 0 ? `<text x="${x + barW / 2}" y="${y - 4}" text-anchor="middle" fill="#bbb" font-size="9">${formatCount(views)}</text>` : ""}`;
        });
        chartEl.innerHTML = `
        <svg viewBox="0 0 ${W} ${H}" style="width:100%;overflow:visible">
            ${grid}
            <line x1="${padL}" y1="${padT}" x2="${padL}" y2="${padT + chartH}" stroke="#333" stroke-width="1"/>
            <line x1="${padL}" y1="${padT + chartH}" x2="${W - padR}" y2="${padT + chartH}" stroke="#333" stroke-width="1"/>
            ${bars}
        </svg>`;
    }

    async function loadAndRender() {
        try {
            const res = await fetch("/api/studio/videos");
            if (res.status === 401) {
                requireAuthRedirect();
                return;
            }
            if (!res.ok) {
                channelManageList.innerHTML = `<p class="studio-error">영상을 불러오는 중 오류가 발생했습니다.</p>`;
                return;
            }
            const data = await res.json();
            const videos = Array.isArray(data) ? data : [];
            renderStudioStats(videos);
            renderViewsChart(videos);
            renderAnalyticsExtra(videos);
            uploadedVideosCache = normalizeVideos(videos);
            renderFilteredList();
        } catch {
            channelManageList.innerHTML = `<p class="studio-error">영상을 불러오는 중 오류가 발생했습니다.</p>`;
        }
    }

    if (channelSearchInput) channelSearchInput.addEventListener("input", renderFilteredList);
    if (channelSortSelect) channelSortSelect.addEventListener("change", renderFilteredList);

    if (channelVisibilityFilter) {
        const filterButtons = channelVisibilityFilter.querySelectorAll("[data-filter]");
        filterButtons.forEach(button => {
            button.addEventListener("click", () => {
                currentFilter = button.dataset.filter;
                filterButtons.forEach(btn => btn.classList.toggle("is-active", btn === button));
                renderFilteredList();
            });
        });
    }

    document.querySelectorAll(".studio-tab-btn").forEach(btn => {
        btn.addEventListener("click", () => {
            document.querySelectorAll(".studio-tab-btn").forEach(b => b.classList.remove("is-active"));
            document.querySelectorAll(".studio-tab-panel").forEach(p => p.classList.remove("is-active"));
            btn.classList.add("is-active");
            const panel = document.getElementById(`studioTab${btn.dataset.tab.charAt(0).toUpperCase() + btn.dataset.tab.slice(1)}`);
            if (panel) panel.classList.add("is-active");
        });
    });

    channelManageList.innerHTML = `<p class="studio-loading">불러오는 중...</p>`;
    await loadAndRender();
}

const page = document.body.dataset.page;

(async function initPage() {
    if (window.__AUTH_READY__) {
        await window.__AUTH_READY__;
    }

    consumePendingToast();
    initGlobalTopSearch();
    initResumeBar();
    initNotifications();
    initSubscriptionSidebar();

    if (page === "upload") initUploadPage();
    if (page === "home") initHomePage();
    if (page === "search") initSearchPage();
    if (page === "saved") initSavedPage();
    if (page === "liked") initLikedPage();
    if (page === "watch") await initWatchPage();
    if (page === "edit") initEditPage();
    if (page === "channel") initChannelPage();
    if (page === "history") initHistoryPage();
    if (page === "studio") initStudioPage();
    if (page === "subscriptions") initSubscriptionPage();
    if (page === "user") initUserPage();
    if (page === "playlist") initPlaylistPage();
})();

/* =========================================================
   시청 기록 페이지 오류 수정 패치
   - 기존 script.js 맨 아래에 그대로 추가
   - 기존 코드 삭제하지 말 것
========================================================= */

function getHistoryStorageKeyPatch() {
    return "youtube_clone_watch_history";
}

function normalizeHistoryVideoPatch(video) {
    if (!video) return null;

    const id = video.id ?? video.videoId;

    if (id === undefined || id === null || id === "") {
        return null;
    }

    return {
        ...video,
        id,
        title: video.title || "제목 없는 영상",
        channel: video.channel || "알 수 없는 채널",
        subscribers: video.subscribers || "구독자 0명",
        views: video.views || "조회수 0회",
        date: video.date || "방금 전",
        duration: video.duration || "0:00",
        category: video.category || "미분류",
        thumbnail: video.thumbnail || DEFAULT_THUMBNAIL,
        avatar: video.avatar || DEFAULT_AVATAR,
        description: video.description || "",
        embedUrl: video.embedUrl || "",
        videoUrl: video.videoUrl || "",
        videoUrl1080: video.videoUrl1080 || "",
        videoUrl720: video.videoUrl720 || "",
        videoUrl480: video.videoUrl480 || "",
        videoUrl360: video.videoUrl360 || "",
        visibility: video.visibility || "공개",
        likeCount: Number(video.likeCount || 0),
        commentCount: Number(video.commentCount || 0),
        likedByMe: Boolean(video.likedByMe),
        savedByMe: Boolean(video.savedByMe),
        watchedAt: video.watchedAt || video.createdAt || new Date().toISOString()
    };
}

function getLocalWatchHistory() {
    const saved = localStorage.getItem(getHistoryStorageKeyPatch());

    if (!saved) {
        return [];
    }

    try {
        const parsed = JSON.parse(saved);

        if (!Array.isArray(parsed)) {
            return [];
        }

        return parsed
            .map(normalizeHistoryVideoPatch)
            .filter(Boolean);
    } catch (error) {
        console.warn("브라우저 시청 기록을 불러오지 못했습니다:", error);
        return [];
    }
}

function saveLocalWatchHistory(history) {
    const normalizedHistory = history
        .map(normalizeHistoryVideoPatch)
        .filter(Boolean)
        .slice(0, 100);

    localStorage.setItem(
        getHistoryStorageKeyPatch(),
        JSON.stringify(normalizedHistory)
    );
}

function recordLocalWatchHistory(video) {
    const normalizedVideo = normalizeHistoryVideoPatch({
        ...video,
        watchedAt: new Date().toISOString()
    });

    if (!normalizedVideo) {
        return;
    }

    const history = getLocalWatchHistory();

    const filteredHistory = history.filter((item) => {
        return String(item.id) !== String(normalizedVideo.id);
    });

    saveLocalWatchHistory([
        normalizedVideo,
        ...filteredHistory
    ]);
}

async function addVideoToHistory(videoId) {
    let serverResult = null;

    try {
        const response = await fetch(`/api/videos/${videoId}/history`, {
            method: "POST"
        });

        serverResult = await response.json().catch(() => ({}));

        if (!response.ok) {
            throw new Error(serverResult.message || "서버 시청 기록 저장 실패");
        }
    } catch (error) {
        console.warn("서버 시청 기록 저장 실패, 브라우저 기록으로 대체합니다:", error);
    }

    try {
        let targetVideo = null;

        if (typeof fetchVideoById === "function") {
            targetVideo = await fetchVideoById(videoId);
        }

        if (!targetVideo) {
            const uploadedVideos =
                typeof fetchUploadedVideos === "function"
                    ? await fetchUploadedVideos()
                    : [];

            const allVideos =
                typeof makeFeedVideos === "function"
                    ? makeFeedVideos(uploadedVideos)
                    : [
                        ...(Array.isArray(uploadedVideos) ? uploadedVideos : []),
                        ...(Array.isArray(defaultVideos) ? defaultVideos : [])
                    ];

            targetVideo = allVideos.find((video) => {
                return String(video.id) === String(videoId);
            });
        }

        if (targetVideo) {
            recordLocalWatchHistory(targetVideo);
        } else {
            recordLocalWatchHistory({
                id: videoId,
                title: "시청한 영상",
                channel: "알 수 없는 채널",
                thumbnail: DEFAULT_THUMBNAIL,
                duration: "0:00",
                date: "방금 전"
            });
        }
    } catch (error) {
        console.warn("브라우저 시청 기록 저장 실패:", error);
    }

    return serverResult || {
        success: true,
        localSaved: true
    };
}

async function fetchVideoProgress(videoId) {
    try {
        const resp = await fetch(`/api/videos/${videoId}/progress`);
        if (!resp.ok) return 0;
        const data = await resp.json();
        return Number(data.position || 0);
    } catch {
        return 0;
    }
}

function saveVideoProgress(videoId, position) {
    fetch(`/api/videos/${videoId}/progress`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ position })
    }).catch(() => {});
}

async function fetchMyHistoryVideos() {
    let serverVideos = [];

    try {
        const response = await fetch("/api/my-history");

        if (response.ok) {
            const data = await response.json();

            if (Array.isArray(data)) {
                serverVideos = data;
            }
        }
    } catch (error) {
        console.warn("서버 시청 기록을 불러오지 못했습니다:", error);
    }

    const localVideos = getLocalWatchHistory();
    const mergedVideos = [];
    const usedIds = new Set();

    [...serverVideos, ...localVideos].forEach((video) => {
        const normalizedVideo = normalizeHistoryVideoPatch(video);

        if (!normalizedVideo) {
            return;
        }

        const key = String(normalizedVideo.id);

        if (usedIds.has(key)) {
            return;
        }

        usedIds.add(key);
        mergedVideos.push(normalizedVideo);
    });

    mergedVideos.sort((a, b) => {
        const aTime = new Date(a.watchedAt || 0).getTime();
        const bTime = new Date(b.watchedAt || 0).getTime();

        return bTime - aTime;
    });

    return mergedVideos;
}

/* =========================================================
   시청 기록 강제 복구 패치 v2
   - 기존 코드 삭제하지 말고 script.js 맨 아래에 추가
   - 사이드바 패치보다도 아래에 붙일 것
========================================================= */

(function () {
    const HISTORY_KEY = "youtube_clone_watch_history";
    const OLD_HISTORY_KEYS = [
        "youtube_clone_watch_history",
        "youtube_watch_history",
        "youtube_clone_history"
    ];

    function safeEscape(text) {
        if (typeof escapeHtml === "function") {
            return escapeHtml(text);
        }

        return String(text ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#39;");
    }

    function safeFormatCount(count) {
        if (typeof formatCount === "function") {
            return formatCount(count);
        }

        return new Intl.NumberFormat("ko-KR").format(Number(count || 0));
    }

    function getCurrentPage() {
        const page = document.body.dataset.page || "";

        if (page) return page;

        const filename = window.location.pathname.split("/").pop() || "";

        if (filename.includes("watch")) return "watch";
        if (filename.includes("history")) return "history";

        return "";
    }

    function getVideoIdFromUrl(urlValue = window.location.href) {
        try {
            const url = new URL(urlValue, window.location.href);
            return url.searchParams.get("v") || url.searchParams.get("id");
        } catch {
            return null;
        }
    }

    function normalizeHistoryVideo(video) {
        if (!video) return null;

        const id = video.id ?? video.videoId ?? video._id;

        if (id === undefined || id === null || id === "") {
            return null;
        }

        return {
            id,
            title: video.title || "제목 없는 영상",
            channel: video.channel || "알 수 없는 채널",
            subscribers: video.subscribers || "구독자 0명",
            views: video.views || "조회수 0회",
            date: video.date || "방금 전",
            duration: video.duration || "0:00",
            category: video.category || "미분류",
            thumbnail:
                video.thumbnail ||
                video.thumbnailUrl ||
                DEFAULT_THUMBNAIL,
            avatar:
                video.avatar ||
                video.profileImage ||
                DEFAULT_AVATAR,
            description: video.description || "",
            embedUrl: video.embedUrl || "",
            videoUrl: video.videoUrl || "",
            videoUrl720: video.videoUrl720 || "",
            videoUrl480: video.videoUrl480 || "",
            videoUrl360: video.videoUrl360 || "",
            visibility: video.visibility || "공개",
            likeCount: Number(video.likeCount || 0),
            commentCount: Number(video.commentCount || 0),
            likedByMe: Boolean(video.likedByMe),
            savedByMe: Boolean(video.savedByMe),
            watchedAt: video.watchedAt || new Date().toISOString()
        };
    }

    function readHistoryFromKey(key) {
        const raw = localStorage.getItem(key);

        if (!raw) return [];

        try {
            const parsed = JSON.parse(raw);

            if (!Array.isArray(parsed)) return [];

            return parsed
                .map(normalizeHistoryVideo)
                .filter(Boolean);
        } catch {
            return [];
        }
    }

    function getLocalHistory() {
        const merged = [];
        const usedIds = new Set();

        OLD_HISTORY_KEYS.forEach((key) => {
            readHistoryFromKey(key).forEach((video) => {
                const idKey = String(video.id);

                if (usedIds.has(idKey)) return;

                usedIds.add(idKey);
                merged.push(video);
            });
        });

        merged.sort((a, b) => {
            return new Date(b.watchedAt || 0).getTime() - new Date(a.watchedAt || 0).getTime();
        });

        return merged;
    }

    function saveLocalHistory(history) {
        const normalized = history
            .map(normalizeHistoryVideo)
            .filter(Boolean)
            .slice(0, 100);

        localStorage.setItem(HISTORY_KEY, JSON.stringify(normalized));
    }
    function recordHistory(video) {
        const normalized = normalizeHistoryVideo({
            ...video,
            watchedAt: new Date().toISOString()
        });

        if (!normalized) return;

        const history = getLocalHistory();
        const filtered = history.filter((item) => String(item.id) !== String(normalized.id));

        saveLocalHistory([
            normalized,
            ...filtered
        ]);
    }

    function extractVideoFromCard(anchor, videoId) {
        const card =
            anchor.closest(".card") ||
            anchor.closest(".recommend-card") ||
            anchor.closest(".studio-row") ||
            anchor.closest(".video-card") ||
            anchor.closest("article") ||
            anchor;

        const titleEl =
            card.querySelector("h3") ||
            card.querySelector("h4") ||
            card.querySelector(".recommend-title") ||
            card.querySelector(".studio-video-title");

        const channelEl =
            card.querySelector(".channel-name") ||
            card.querySelector(".recommend-meta") ||
            card.querySelector(".studio-video-meta");

        const thumbnailEl = card.querySelector("img.thumbnail-image") || card.querySelector("img");
        const durationEl = card.querySelector(".duration") || card.querySelector(".recommend-duration");

        return normalizeHistoryVideo({
            id: videoId,
            title: titleEl ? titleEl.textContent.trim() : "시청한 영상",
            channel: channelEl ? channelEl.textContent.trim().split("·")[0].trim() : "알 수 없는 채널",
            thumbnail: thumbnailEl ? thumbnailEl.src : "",
            avatar: thumbnailEl ? thumbnailEl.src : "",
            duration: durationEl ? durationEl.textContent.trim() : "0:00",
            date: "방금 전"
        });
    }

    async function findVideoById(videoId) {
        if (!videoId) return null;

        try {
            if (typeof fetchVideoById === "function") {
                const video = await fetchVideoById(videoId);

                if (video) {
                    return normalizeHistoryVideo(video);
                }
            }
        } catch {}

        try {
            const uploadedVideos =
                typeof fetchUploadedVideos === "function"
                    ? await fetchUploadedVideos()
                    : [];

            const allVideos =
                typeof makeFeedVideos === "function"
                    ? makeFeedVideos(uploadedVideos)
                    : [
                        ...(Array.isArray(uploadedVideos) ? uploadedVideos : []),
                        ...(Array.isArray(window.defaultVideos) ? window.defaultVideos : [])
                    ];

            const found = allVideos.find((video) => String(video.id) === String(videoId));

            if (found) {
                return normalizeHistoryVideo(found);
            }
        } catch {}

        return null;
    }

    function extractVideoFromWatchDom(videoId) {
        const titleEl = document.querySelector(".watch-title");
        const channelEl = document.querySelector(".watch-channel-text strong");
        const subscriberEl = document.querySelector(".watch-channel-text span");
        const avatarEl = document.querySelector(".watch-channel-avatar");
        const descEl = document.querySelector("#watchDescriptionText");
        const thumbnailEl =
            document.querySelector(".player-box img") ||
            document.querySelector(".thumbnail-image");

        return normalizeHistoryVideo({
            id: videoId,
            title: titleEl ? titleEl.textContent.trim() : "시청한 영상",
            channel: channelEl ? channelEl.textContent.trim() : "알 수 없는 채널",
            subscribers: subscriberEl ? subscriberEl.textContent.trim() : "구독자 0명",
            avatar: avatarEl ? avatarEl.src : "",
            thumbnail: thumbnailEl ? thumbnailEl.src : "",
            description: descEl ? descEl.textContent.trim() : "",
            duration: "0:00",
            date: "방금 전"
        });
    }

    async function recordCurrentWatchPage() {
        if (getCurrentPage() !== "watch") return;

        const videoId = getVideoIdFromUrl();

        if (!videoId) return;

        let video = await findVideoById(videoId);

        if (!video) {
            video = extractVideoFromWatchDom(videoId);
        }

        if (video) {
            recordHistory(video);
        }
    }

    function bindClickRecordBeforeMove() {
        if (document.body.dataset.historyClickRecordPatched === "true") return;

        document.body.dataset.historyClickRecordPatched = "true";

        document.addEventListener(
            "click",
            (event) => {
                const anchor = event.target.closest('a[href*="watch.html"]');

                if (!anchor) return;

                const videoId = getVideoIdFromUrl(anchor.href);

                if (!videoId) return;

                const video = extractVideoFromCard(anchor, videoId);

                if (video) {
                    recordHistory(video);
                }
            },
            true
        );
    }

    function ensureHistoryStyle() {
        if (document.getElementById("history-force-patch-style")) return;

        const style = document.createElement("style");
        style.id = "history-force-patch-style";
        style.textContent = `
            .history-force-toolbar {
                display: flex;
                justify-content: flex-end;
                margin-bottom: 18px;
            }

            .history-force-clear {
                height: 38px;
                padding: 0 16px;
                border: 1px solid #303030;
                border-radius: 999px;
                background: #181818;
                color: #fff;
                cursor: pointer;
            }

            .history-force-clear:hover {
                background: #272727;
            }

            .history-force-grid {
                display: grid;
                grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
                gap: 24px 16px;
                width: 100%;
            }

            .history-force-card {
                display: flex;
                flex-direction: column;
                gap: 10px;
            }

            .history-force-card a {
                color: inherit;
                text-decoration: none;
            }

            .history-force-date {
                margin-top: 6px;
                color: #aaa;
                font-size: 13px;
            }

            .history-force-empty {
                min-height: 260px;
                display: flex;
                flex-direction: column;
                align-items: center;
                justify-content: center;
                color: #aaa;
                text-align: center;
                gap: 10px;
            }

            .history-force-empty h2 {
                margin: 0;
                color: #fff;
                font-size: 22px;
            }

            .history-force-empty p {
                margin: 0;
                font-size: 14px;
            }
        `;
        document.head.appendChild(style);
    }
    function createHistoryCard(video) {
        const item = normalizeHistoryVideo(video);

        if (!item) return "";

        let viewCount = 0;

        try {
            if (typeof loadViewCount === "function") {
                viewCount = loadViewCount(item);
            }
        } catch {}

        const videoUrl =
            typeof getVideoUrl === "function"
                ? getVideoUrl(item.id)
                : `watch.html?v=${item.id}`;

        const watchedAt = item.watchedAt
            ? new Date(item.watchedAt).toLocaleString("ko-KR")
            : "방금 전";

        return `
            <article class="card history-force-card">
                <a href="${safeEscape(videoUrl)}" class="card-link">
                    <div class="thumbnail-wrap">
                        <img class="thumbnail-image" src="${safeEscape(item.thumbnail)}" alt="${safeEscape(item.title)}" />
                        <span class="duration">${safeEscape(item.duration || "0:00")}</span>
                    </div>

                    <div class="meta">
                        <img class="avatar-image" src="${safeEscape(item.avatar)}" alt="${safeEscape(item.channel)}" />

                        <div class="text">
                            <h3>${safeEscape(item.title)}</h3>
                            <p class="channel-name">${safeEscape(item.channel)}</p>
                            <p class="video-info">조회수 ${safeFormatCount(viewCount)}회 · ${safeEscape(item.date || "방금 전")}</p>
                            <p class="history-force-date">시청한 날짜: ${safeEscape(watchedAt)}</p>
                        </div>
                    </div>
                </a>
            </article>
        `;
    }

    async function getHistoryVideos() {
        let serverVideos = [];

        try {
            const response = await fetch("/api/my-history");

            if (response.ok) {
                const data = await response.json();

                if (Array.isArray(data)) {
                    serverVideos = data;
                }
            }
        } catch {}

        const localVideos = getLocalHistory();
        const merged = [];
        const usedIds = new Set();

        [...localVideos, ...serverVideos].forEach((video) => {
            const item = normalizeHistoryVideo(video);

            if (!item) return;

            const idKey = String(item.id);

            if (usedIds.has(idKey)) return;

            usedIds.add(idKey);
            merged.push(item);
        });

        merged.sort((a, b) => {
            return new Date(b.watchedAt || 0).getTime() - new Date(a.watchedAt || 0).getTime();
        });

        return merged;
    }

    async function renderHistoryPageForce() {
        if (getCurrentPage() !== "history") return;

        ensureHistoryStyle();

        const main =
            document.querySelector("main") ||
            document.querySelector(".main") ||
            document.body;

        let grid =
            document.getElementById("historyVideoGrid") ||
            document.getElementById("historyList") ||
            document.querySelector("[data-video-grid]") ||
            document.querySelector(".history-list") ||
            document.querySelector(".video-grid") ||
            document.querySelector(".content-grid");

        if (!grid) {
            grid = document.createElement("div");
            grid.id = "historyVideoGrid";
            main.appendChild(grid);
        }

        grid.classList.add("history-force-grid");

        let toolbar = document.getElementById("historyForceToolbar");

        if (!toolbar) {
            toolbar = document.createElement("div");
            toolbar.id = "historyForceToolbar";
            toolbar.className = "history-force-toolbar";
            toolbar.innerHTML = `
                <button type="button" class="history-force-clear" id="historyForceClearBtn">
                    시청 기록 삭제
                </button>
            `;
            grid.insertAdjacentElement("beforebegin", toolbar);
        }

        const clearBtn = document.getElementById("historyForceClearBtn");

        clearBtn.onclick = () => {
            OLD_HISTORY_KEYS.forEach((key) => localStorage.removeItem(key));
            grid.innerHTML = `
                <div class="history-force-empty">
                    <h2>시청 기록이 없습니다.</h2>
                    <p>영상을 시청하면 이곳에 기록이 표시됩니다.</p>
                </div>
            `;
            toolbar.hidden = true;
        };

        const videos = await getHistoryVideos();

        if (!videos.length) {
            toolbar.hidden = true;
            grid.innerHTML = `
                <div class="history-force-empty">
                    <h2>시청 기록이 없습니다.</h2>
                    <p>영상을 한 번 시청한 뒤 다시 확인해줘.</p>
                </div>
            `;
            return;
        }

        toolbar.hidden = false;
        grid.innerHTML = videos.map(createHistoryCard).join("");
    }

    function bootHistoryForcePatch() {
        bindClickRecordBeforeMove();

        if (getCurrentPage() === "watch") {
            setTimeout(recordCurrentWatchPage, 300);
            setTimeout(recordCurrentWatchPage, 900);
            setTimeout(recordCurrentWatchPage, 1500);
        }

        if (getCurrentPage() === "history") {
            setTimeout(renderHistoryPageForce, 100);
            setTimeout(renderHistoryPageForce, 700);
            setTimeout(renderHistoryPageForce, 1400);
        }
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", bootHistoryForcePatch);
    } else {
        bootHistoryForcePatch();
    }
})();
/* =========================================================
   저장한 영상 / 좋아요한 영상 강제 복구 패치 v1
   - 기존 코드 삭제하지 말고 script.js 맨 아래에 추가
   - 시청 기록 강제 복구 패치 v2보다 아래에 붙일 것
========================================================= */

(function () {
    const SAVED_KEY = "youtube_clone_local_saved_videos";
    const LIKED_KEY = "youtube_clone_local_liked_videos";

    function safeEscape(text) {
        if (typeof escapeHtml === "function") {
            return escapeHtml(text);
        }

        return String(text ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#39;");
    }

    function safeFormatCount(count) {
        if (typeof formatCount === "function") {
            return formatCount(count);
        }

        return new Intl.NumberFormat("ko-KR").format(Number(count || 0));
    }

    function getCurrentPage() {
        const page = document.body.dataset.page || "";

        if (page) return page;

        const filename = window.location.pathname.split("/").pop() || "";

        if (filename.includes("watch")) return "watch";
        if (filename.includes("saved")) return "saved";
        if (filename.includes("liked")) return "liked";

        return "";
    }

    function getVideoIdFromUrl(urlValue = window.location.href) {
        try {
            const url = new URL(urlValue, window.location.href);
            return url.searchParams.get("v") || url.searchParams.get("id");
        } catch {
            return null;
        }
    }

    function normalizeVideo(video) {
        if (!video) return null;

        const id = video.id ?? video.videoId ?? video._id;

        if (id === undefined || id === null || id === "") {
            return null;
        }

        return {
            id,
            title: video.title || "제목 없는 영상",
            channel: video.channel || "알 수 없는 채널",
            subscribers: video.subscribers || "구독자 0명",
            views: video.views || "조회수 0회",
            date: video.date || "방금 전",
            duration: video.duration || "0:00",
            category: video.category || "미분류",
            thumbnail:
                video.thumbnail ||
                video.thumbnailUrl ||
                DEFAULT_THUMBNAIL,
            avatar:
                video.avatar ||
                video.profileImage ||
                DEFAULT_AVATAR,
            description: video.description || "",
            embedUrl: video.embedUrl || "",
            videoUrl: video.videoUrl || "",
            videoUrl720: video.videoUrl720 || "",
            videoUrl480: video.videoUrl480 || "",
            videoUrl360: video.videoUrl360 || "",
            visibility: video.visibility || "공개",
            likeCount: Number(video.likeCount || 0),
            commentCount: Number(video.commentCount || 0),
            likedByMe: Boolean(video.likedByMe),
            savedByMe: Boolean(video.savedByMe),
            savedAt: video.savedAt || new Date().toISOString(),
            likedAt: video.likedAt || new Date().toISOString()
        };
    }

    function readVideoList(key) {
        const raw = localStorage.getItem(key);

        if (!raw) return [];

        try {
            const parsed = JSON.parse(raw);

            if (!Array.isArray(parsed)) return [];

            return parsed
                .map(normalizeVideo)
                .filter(Boolean);
        } catch {
            return [];
        }
    }

    function saveVideoList(key, videos) {
        const normalized = videos
            .map(normalizeVideo)
            .filter(Boolean)
            .slice(0, 100);

        localStorage.setItem(key, JSON.stringify(normalized));
    }

    function addVideoToLocalList(key, video, timeKey) {
        const normalized = normalizeVideo({
            ...video,
            [timeKey]: new Date().toISOString()
        });

        if (!normalized) return;

        const list = readVideoList(key);
        const filtered = list.filter((item) => String(item.id) !== String(normalized.id));

        saveVideoList(key, [
            normalized,
            ...filtered
        ]);
    }

    function removeVideoFromLocalList(key, videoId) {
        const list = readVideoList(key);
        const filtered = list.filter((item) => String(item.id) !== String(videoId));

        saveVideoList(key, filtered);
    }

    async function findVideoById(videoId) {
        if (!videoId) return null;

        try {
            if (typeof fetchVideoById === "function") {
                const video = await fetchVideoById(videoId);

                if (video) {
                    return normalizeVideo(video);
                }
            }
        } catch {}

        try {
            const uploadedVideos =
                typeof fetchUploadedVideos === "function"
                    ? await fetchUploadedVideos()
                    : [];

            const allVideos =
                typeof makeFeedVideos === "function"
                    ? makeFeedVideos(uploadedVideos)
                    : [
                        ...(Array.isArray(uploadedVideos) ? uploadedVideos : []),
                        ...(typeof defaultVideos !== "undefined" && Array.isArray(defaultVideos) ? defaultVideos : [])
                    ];

            const found = allVideos.find((video) => String(video.id) === String(videoId));

            if (found) {
                return normalizeVideo(found);
            }
        } catch {}

        return null;
    }

    function extractVideoFromWatchDom(videoId) {
        const titleEl = document.querySelector(".watch-title");
        const channelEl = document.querySelector(".watch-channel-text strong");
        const subscriberEl = document.querySelector(".watch-channel-text span");
        const avatarEl = document.querySelector(".watch-channel-avatar");
        const descEl = document.querySelector("#watchDescriptionText");
        const thumbnailEl =
            document.querySelector(".player-box img") ||
            document.querySelector(".thumbnail-image");

        return normalizeVideo({
            id: videoId,
            title: titleEl ? titleEl.textContent.trim() : "시청한 영상",
            channel: channelEl ? channelEl.textContent.trim() : "알 수 없는 채널",
            subscribers: subscriberEl ? subscriberEl.textContent.trim() : "구독자 0명",
            avatar: avatarEl ? avatarEl.src : "",
            thumbnail: thumbnailEl ? thumbnailEl.src : "",
            description: descEl ? descEl.textContent.trim() : "",
            duration: "0:00",
            date: "방금 전"
        });
    }

    async function getCurrentWatchVideo() {
        const videoId = getVideoIdFromUrl();

        if (!videoId) return null;

        let video = await findVideoById(videoId);

        if (!video) {
            video = extractVideoFromWatchDom(videoId);
        }

        return video;
    }

    function bindWatchLikeSaveBackup() {
        if (document.body.dataset.likeSaveBackupPatched === "true") return;

        document.body.dataset.likeSaveBackupPatched = "true";

        document.addEventListener(
            "click",
            async (event) => {
                const saveBtn = event.target.closest("#saveBtn");
                const likeBtn = event.target.closest("#likeBtn");
                const unsaveBtn = event.target.closest("[data-unsave-id]");
                const unlikeBtn = event.target.closest("[data-unlike-id]");

                if (unsaveBtn) {
                    removeVideoFromLocalList(SAVED_KEY, unsaveBtn.dataset.unsaveId);
                    return;
                }

                if (unlikeBtn) {
                    removeVideoFromLocalList(LIKED_KEY, unlikeBtn.dataset.unlikeId);
                    return;
                }

                if (!saveBtn && !likeBtn) return;

                const video = await getCurrentWatchVideo();

                if (!video) return;

                if (saveBtn) {
                    const isAlreadySaved =
                        saveBtn.classList.contains("active") ||
                        saveBtn.textContent.includes("저장됨");

                    if (isAlreadySaved) {
                        removeVideoFromLocalList(SAVED_KEY, video.id);
                    } else {
                        addVideoToLocalList(SAVED_KEY, {
                            ...video,
                            savedByMe: true
                        }, "savedAt");
                    }
                }

                if (likeBtn) {
                    const isAlreadyLiked = likeBtn.classList.contains("active");

                    if (isAlreadyLiked) {
                        removeVideoFromLocalList(LIKED_KEY, video.id);
                    } else {
                        addVideoToLocalList(LIKED_KEY, {
                            ...video,
                            likedByMe: true
                        }, "likedAt");
                    }
                }
            },
            true
        );
    }

    function filterVideos(videos, keyword) {
        const normalizedKeyword = String(keyword || "").trim().toLowerCase();

        if (!normalizedKeyword) return videos;

        return videos.filter((video) => {
            const title = String(video.title || "").toLowerCase();
            const channel = String(video.channel || "").toLowerCase();
            const description = String(video.description || "").toLowerCase();
            const category = String(video.category || "").toLowerCase();

            return (
                title.includes(normalizedKeyword) ||
                channel.includes(normalizedKeyword) ||
                description.includes(normalizedKeyword) ||
                category.includes(normalizedKeyword)
            );
        });
    }

    function createLocalVideoCard(video, type) {
        const item = normalizeVideo(video);

        if (!item) return "";

        let viewCount = 0;

        try {
            if (typeof loadViewCount === "function") {
                viewCount = loadViewCount(item);
            }
        } catch {}

        const videoUrl =
            typeof getVideoUrl === "function"
                ? getVideoUrl(item.id)
                : `watch.html?v=${item.id}`;

        const removeAttr =
            type === "saved"
                ? `data-local-unsave-id="${safeEscape(item.id)}"`
                : `data-local-unlike-id="${safeEscape(item.id)}"`;

        const removeText = type === "saved" ? "저장 해제" : "좋아요 취소";

        const cardClass = type === "saved" ? "saved-card" : "liked-card";
        const actionsClass = type === "saved" ? "saved-card-actions" : "liked-card-actions";
        const buttonClass = type === "saved" ? "saved-remove-btn" : "liked-remove-btn";

        return `
            <article class="card ${cardClass}" data-local-card-id="${safeEscape(item.id)}">
                <a href="${safeEscape(videoUrl)}" class="card-link">
                    <div class="thumbnail-wrap">
                        <img class="thumbnail-image" src="${safeEscape(item.thumbnail)}" alt="${safeEscape(item.title)}" />
                        <span class="duration">${safeEscape(item.duration || "0:00")}</span>
                    </div>

                    <div class="meta">
                        <img class="avatar-image" src="${safeEscape(item.avatar)}" alt="${safeEscape(item.channel)}" />

                        <div class="text">
                            <h3>${safeEscape(item.title)}</h3>
                            <p class="channel-name">${safeEscape(item.channel)}</p>
                            <p class="video-info">조회수 ${safeFormatCount(viewCount)}회 · ${safeEscape(item.date || "방금 전")}</p>
                        </div>
                    </div>
                </a>

                <div class="${actionsClass}">
                    <button type="button" class="${buttonClass}" ${removeAttr}>${removeText}</button>
                </div>
            </article>
        `;
    }

    function ensurePatchStyle() {
        if (document.getElementById("like-save-force-patch-style")) return;

        const style = document.createElement("style");
        style.id = "like-save-force-patch-style";
        style.textContent = `
            .local-list-empty {
                min-height: 260px;
                display: flex;
                flex-direction: column;
                align-items: center;
                justify-content: center;
                gap: 10px;
                color: #aaa;
                text-align: center;
            }

            .local-list-empty h2 {
                margin: 0;
                color: #fff;
                font-size: 22px;
            }

            .local-list-empty p {
                margin: 0;
                font-size: 14px;
            }
        `;
        document.head.appendChild(style);
    }

    async function getMergedSavedVideos() {
        let serverVideos = [];

        try {
            if (typeof fetchMySavedVideos === "function") {
                const result = await fetchMySavedVideos();

                if (Array.isArray(result)) {
                    serverVideos = result;
                }
            }
        } catch {}

        const localVideos = readVideoList(SAVED_KEY);
        const merged = [];
        const usedIds = new Set();

        [...localVideos, ...serverVideos].forEach((video) => {
            const item = normalizeVideo(video);

            if (!item) return;

            const key = String(item.id);

            if (usedIds.has(key)) return;

            usedIds.add(key);
            merged.push(item);
        });

        return merged;
    }

    async function getMergedLikedVideos() {
        let serverVideos = [];

        try {
            if (typeof fetchMyLikedVideos === "function") {
                const result = await fetchMyLikedVideos();

                if (Array.isArray(result)) {
                    serverVideos = result;
                }
            }
        } catch {}

        const localVideos = readVideoList(LIKED_KEY);
        const merged = [];
        const usedIds = new Set();

        [...localVideos, ...serverVideos].forEach((video) => {
            const item = normalizeVideo(video);

            if (!item) return;

            const key = String(item.id);

            if (usedIds.has(key)) return;

            usedIds.add(key);
            merged.push(item);
        });

        return merged;
    }
    function bindLocalRemoveButtons(grid, type, rerender) {
        const selector = type === "saved" ? "[data-local-unsave-id]" : "[data-local-unlike-id]";
        const key = type === "saved" ? SAVED_KEY : LIKED_KEY;

        grid.querySelectorAll(selector).forEach((button) => {
            button.addEventListener("click", (event) => {
                event.preventDefault();
                event.stopPropagation();

                const videoId =
                    type === "saved"
                        ? button.dataset.localUnsaveId
                        : button.dataset.localUnlikeId;

                removeVideoFromLocalList(key, videoId);

                if (typeof showToast === "function") {
                    showToast(type === "saved" ? "저장이 해제되었습니다." : "좋아요가 취소되었습니다.");
                }

                rerender();
            });
        });
    }

    async function renderSavedPageForce() {
        if (getCurrentPage() !== "saved") return;

        ensurePatchStyle();

        const grid = document.getElementById("savedVideoGrid");
        const emptyState = document.getElementById("savedEmptyState");
        const countText = document.getElementById("savedCountText");
        const kicker = document.getElementById("savedKicker");
        const title = document.getElementById("savedTitle");
        const searchForm = document.getElementById("savedSearchForm");
        const searchInput = document.getElementById("savedSearchInput");

        if (!grid) return;

        let currentKeyword = searchInput?.value || "";

        async function render() {
            const videos = await getMergedSavedVideos();
            const filtered = filterVideos(videos, currentKeyword);

            if (kicker) kicker.textContent = currentKeyword.trim() ? "검색 결과" : "보관함";
            if (title) title.textContent = currentKeyword.trim() ? `"${currentKeyword.trim()}" 저장한 영상 검색 결과` : "저장한 영상";
            if (countText) countText.textContent = `저장한 영상 ${safeFormatCount(filtered.length)}개`;

            if (!filtered.length) {
                grid.innerHTML = `
                    <div class="local-list-empty">
                        <h2>저장한 영상이 없습니다.</h2>
                        <p>영상 페이지에서 저장 버튼을 누르면 이곳에 표시됩니다.</p>
                    </div>
                `;

                if (emptyState) emptyState.hidden = true;
                return;
            }

            if (emptyState) emptyState.hidden = true;

            grid.innerHTML = filtered.map((video) => createLocalVideoCard(video, "saved")).join("");
            bindLocalRemoveButtons(grid, "saved", render);
        }

        if (searchForm && searchForm.dataset.localSavedSearchPatched !== "true") {
            searchForm.dataset.localSavedSearchPatched = "true";

            searchForm.addEventListener("submit", (event) => {
                event.preventDefault();
                currentKeyword = searchInput?.value || "";
                render();
            });
        }

        await render();
    }

    async function renderLikedPageForce() {
        if (getCurrentPage() !== "liked") return;

        ensurePatchStyle();

        const grid = document.getElementById("likedVideoGrid");
        const emptyState = document.getElementById("likedEmptyState");
        const countText = document.getElementById("likedCountText");
        const kicker = document.getElementById("likedKicker");
        const title = document.getElementById("likedTitle");
        const searchForm = document.getElementById("likedSearchForm");
        const searchInput = document.getElementById("likedSearchInput");

        if (!grid) return;

        let currentKeyword = searchInput?.value || "";

        async function render() {
            const videos = await getMergedLikedVideos();
            const filtered = filterVideos(videos, currentKeyword);

            if (kicker) kicker.textContent = currentKeyword.trim() ? "검색 결과" : "보관함";
            if (title) title.textContent = currentKeyword.trim() ? `"${currentKeyword.trim()}" 좋아요한 영상 검색 결과` : "좋아요한 영상";
            if (countText) countText.textContent = `좋아요한 영상 ${safeFormatCount(filtered.length)}개`;

            if (!filtered.length) {
                grid.innerHTML = `
                    <div class="local-list-empty">
                        <h2>좋아요한 영상이 없습니다.</h2>
                        <p>영상 페이지에서 좋아요 버튼을 누르면 이곳에 표시됩니다.</p>
                    </div>
                `;

                if (emptyState) emptyState.hidden = true;
                return;
            }

            if (emptyState) emptyState.hidden = true;

            grid.innerHTML = filtered.map((video) => createLocalVideoCard(video, "liked")).join("");
            bindLocalRemoveButtons(grid, "liked", render);
        }

        if (searchForm && searchForm.dataset.localLikedSearchPatched !== "true") {
            searchForm.dataset.localLikedSearchPatched = "true";

            searchForm.addEventListener("submit", (event) => {
                event.preventDefault();
                currentKeyword = searchInput?.value || "";
                render();
            });
        }

        await render();
    }

    function bootLikeSavePatch() {
        bindWatchLikeSaveBackup();

        if (getCurrentPage() === "saved") {
            setTimeout(renderSavedPageForce, 100);
            setTimeout(renderSavedPageForce, 700);
            setTimeout(renderSavedPageForce, 1400);
        }

        if (getCurrentPage() === "liked") {
            setTimeout(renderLikedPageForce, 100);
            setTimeout(renderLikedPageForce, 700);
            setTimeout(renderLikedPageForce, 1400);
        }
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", bootLikeSavePatch);
    } else {
        bootLikeSavePatch();
    }
})();
/* =========================================================
   공통 상단바 / 사이드바 자동 복구 패치 v1
   - 기존 코드 삭제하지 말고 script.js 맨 아래에 추가
   - history / saved / liked / channel / upload 페이지 레이아웃 보강
========================================================= */

(function () {
    const PATCH_NAME = "commonLayoutRecoveryPatchV1";

    function getPageName() {
        const bodyPage = document.body.dataset.page || "";
        if (bodyPage) return bodyPage;

        const filename = window.location.pathname.split("/").pop() || "index.html";

        if (filename.includes("history")) return "history";
        if (filename.includes("saved")) return "saved";
        if (filename.includes("liked")) return "liked";
        if (filename.includes("channel")) return "channel";
        if (filename.includes("upload")) return "upload";
        if (filename.includes("watch")) return "watch";
        if (filename.includes("edit")) return "edit";
        if (filename.includes("login")) return "login";
        if (filename.includes("signup")) return "signup";

        return "home";
    }

    function shouldApplyLayoutPatch() {
        return false;
    }

    function getActiveLabel() {
        const page = getPageName();

        if (page === "history") return "시청 기록";
        if (page === "saved") return "저장한 영상";
        if (page === "liked") return "좋아요 표시한 동영상";
        if (page === "channel") return "내 채널";
        if (page === "upload") return "업로드";
        if (page === "edit") return "내 채널";

        return "홈";
    }

    function createTopbarMarkup() {
        return `
            <header class="topbar" id="topbar">
                <div class="topbar-left">
                    <button class="icon-btn menu-btn" id="menuBtn" type="button" aria-label="메뉴">
                        <svg viewBox="0 0 24 24" aria-hidden="true">
                            <path d="M4 6h16M4 12h16M4 18h16"></path>
                        </svg>
                    </button>

                    <a href="index.html" class="logo-wrap" aria-label="MyTube 홈">
                        <span class="logo-badge">
                            <svg viewBox="0 0 24 24" aria-hidden="true" fill="none">
                                <rect x="4" y="7" width="16" height="10" rx="2" stroke="white" stroke-width="1.4"/>
                                <rect x="2" y="9.5" width="2" height="2" rx="0.5" fill="white"/>
                                <rect x="2" y="12.5" width="2" height="2" rx="0.5" fill="white"/>
                                <rect x="20" y="9.5" width="2" height="2" rx="0.5" fill="white"/>
                                <rect x="20" y="12.5" width="2" height="2" rx="0.5" fill="white"/>
                                <path d="M10.5 10l4 2-4 2v-4z" fill="white"/>
                            </svg>
                        </span>
                        <span class="logo-text">MY</span>
                    </a>
                </div>

                <div class="topbar-center">
                    <form class="search-form common-layout-search-form">
                        <input type="text" placeholder="검색" autocomplete="off" />
                        <button class="search-btn" type="submit" aria-label="검색">
                            <svg viewBox="0 0 24 24" aria-hidden="true">
                                <path d="M9.5 3a6.5 6.5 0 0 1 5.17 10.44l4.45 4.44-1.41 1.41-4.44-4.45A6.5 6.5 0 1 1 9.5 3Zm0 2a4.5 4.5 0 1 0 0 9 4.5 4.5 0 0 0 0-9Z"></path>
                            </svg>
                        </button>
                    </form>

                    <button class="icon-btn mic-btn" type="button" aria-label="음성 검색">
                        <svg viewBox="0 0 24 24" aria-hidden="true">
                            <path d="M12 14a3 3 0 0 0 3-3V6a3 3 0 0 0-6 0v5a3 3 0 0 0 3 3Zm5-3a5 5 0 0 1-10 0H5a7 7 0 0 0 6 6.92V21h2v-3.08A7 7 0 0 0 19 11h-2Z"></path>
                        </svg>
                    </button>
                </div>

                <div class="topbar-right">
                    <a class="icon-btn" href="upload.html" aria-label="업로드">
                        <svg viewBox="0 0 24 24" aria-hidden="true">
                            <path d="M14 10.5V6l5 5-5 5v-4.5H5v-2h9Z"></path>
                        </svg>
                    </a>

                    <div class="notif-wrap" id="notifWrap">
                        <button class="icon-btn notif-btn" id="notifBtn" aria-label="알림">
                            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 22a2.5 2.5 0 0 0 2.45-2h-4.9A2.5 2.5 0 0 0 12 22Zm7-6v-5a7 7 0 1 0-14 0v5l-2 2v1h18v-1l-2-2Z"/></svg>
                            <span class="notif-badge" id="notifBadge" style="display:none"></span>
                        </button>
                        <div class="notif-dropdown" id="notifDropdown" style="display:none">
                            <div class="notif-header"><span>알림</span><button class="notif-read-all" id="notifReadAll">모두 읽음</button></div>
                            <div class="notif-list" id="notifList"></div>
                        </div>
                    </div>

                    <a href="channel.html" class="profile" aria-label="내 채널">T</a>
                </div>
            </header>
        `;
    }

    function createSidebarMarkup() {
        const activeLabel = getActiveLabel();

        const items = [
            {
                label: "홈",
                icon: "⌂",
                href: "index.html"
            },
            {
                label: "Shorts",
                icon: "▶",
                href: ""
            },
            {
                label: "구독",
                icon: "▣",
                href: ""
            },
            {
                divider: true
            },
            {
                label: "시청 기록",
                icon: "↺",
                href: "history.html"
            },
            {
                label: "좋아요 표시한 동영상",
                icon: "👍",
                href: "liked.html"
            },
            {
                label: "저장한 영상",
                icon: "▤",
                href: "saved.html"
            },
            {
                label: "내 채널",
                icon: "◉",
                href: "channel.html"
            },
            {
                label: "업로드",
                icon: "＋",
                href: "upload.html"
            }
        ];

        const itemMarkup = items.map((item) => {
            if (item.divider) {
                return `<div class="sidebar-divider"></div>`;
            }

            const activeClass = item.label === activeLabel ? " active" : "";

            if (item.href) {
                return `
                    <a href="${item.href}" class="sidebar-item${activeClass}">
                        <span class="sidebar-icon">${item.icon}</span>
                        <span class="sidebar-label">${item.label}</span>
                    </a>
                `;
            }

            return `
                <div class="sidebar-item${activeClass}" data-common-layout-ready-menu="${item.label}">
                    <span class="sidebar-icon">${item.icon}</span>
                    <span class="sidebar-label">${item.label}</span>
                </div>
            `;
        }).join("");

        return `
            <aside class="sidebar" id="sidebar">
                <nav class="sidebar-inner" aria-label="사이드바 메뉴">
                    ${itemMarkup}
                </nav>
            </aside>
        `;
    }

    function ensureStyle() {
        if (document.getElementById("common-layout-recovery-style")) return;

        const style = document.createElement("style");
        style.id = "common-layout-recovery-style";
        style.textContent = `
            body.common-layout-recovered {
                padding-top: 56px;
            }

            body.common-layout-recovered .main {
                margin-left: 240px;
                padding: 24px 24px 32px;
                min-width: 0;
                transition: margin-left 0.2s ease;
            }

            body.common-layout-recovered .main.expanded {
                margin-left: 72px;
            }

            body.common-layout-recovered .sidebar {
                z-index: 999;
            }

            body.common-layout-recovered .sidebar a.sidebar-item {
                text-decoration: none;
                color: inherit;
            }

            body.common-layout-recovered .sidebar-item {
                cursor: pointer;
            }

            body.common-layout-recovered .sidebar-item.active {
                background: #272727;
                color: #fff;
            }

            body.common-layout-recovered .sidebar-item:focus-visible,
            body.common-layout-recovered .profile:focus-visible {
                outline: 2px solid #3ea6ff;
                outline-offset: 2px;
            }

            @media (max-width: 768px) {
                body.common-layout-recovered .main,
                body.common-layout-recovered .main.expanded {
                    margin-left: 0;
                    padding: 20px 16px 32px;
                }

                body.common-layout-recovered .sidebar {
                    transform: translateX(-100%);
                    width: 240px;
                    transition: transform 0.2s ease;
                }

                body.common-layout-recovered .sidebar.is-mobile-open {
                    transform: translateX(0);
                }

                body.common-layout-recovered.sidebar-mobile-open::after {
                    content: "";
                    position: fixed;
                    inset: 56px 0 0 0;
                    background: rgba(0, 0, 0, 0.45);
                    z-index: 998;
                }
            }
        `;

        document.head.appendChild(style);
    }

    function ensureTopbar() {
        const existingTopbar = document.getElementById("topbar") || document.querySelector(".topbar");

        if (existingTopbar) {
            if (!existingTopbar.id) existingTopbar.id = "topbar";
            return existingTopbar;
        }

        document.body.insertAdjacentHTML("afterbegin", createTopbarMarkup());

        return document.getElementById("topbar") || document.querySelector(".topbar");
    }

    function ensureSidebar() {
        const existingSidebar = document.getElementById("sidebar") || document.querySelector(".sidebar");

        if (existingSidebar) {
            if (!existingSidebar.id) existingSidebar.id = "sidebar";
            return existingSidebar;
        }

        const topbar = document.getElementById("topbar") || document.querySelector(".topbar");

        if (topbar) {
            topbar.insertAdjacentHTML("afterend", createSidebarMarkup());
        } else {
            document.body.insertAdjacentHTML("afterbegin", createSidebarMarkup());
        }

        return document.getElementById("sidebar") || document.querySelector(".sidebar");
    }

    function ensureMain() {
        let main =
            document.getElementById("main") ||
            document.querySelector("main") ||
            document.querySelector(".main");

        if (!main) {
            main = document.createElement("main");
            main.id = "main";
            main.className = "main";

            const children = Array.from(document.body.children);

            children.forEach((child) => {
                const isLayoutElement =                    child.classList.contains("topbar") ||
                    child.classList.contains("sidebar") ||
                    child.tagName === "SCRIPT" ||
                    child.tagName === "STYLE";

                if (!isLayoutElement) {
                    main.appendChild(child);
                }
            });

            document.body.appendChild(main);
        }

        main.id = main.id || "main";
        main.classList.add("main");

        return main;
    }

    function markActiveSidebarItem() {
        const sidebar = document.getElementById("sidebar") || document.querySelector(".sidebar");
        if (!sidebar) return;

        const activeLabel = getActiveLabel();

        sidebar.querySelectorAll(".sidebar-item").forEach((item) => {
            const labelEl = item.querySelector(".sidebar-label");
            const label = labelEl ? labelEl.textContent.trim() : item.textContent.trim();

            item.classList.toggle("active", label === activeLabel);
        });
    }

    function bindMenuButton() {
        const menuBtn = document.getElementById("menuBtn");
        const sidebar = document.getElementById("sidebar") || document.querySelector(".sidebar");
        const main = document.getElementById("main") || document.querySelector(".main");

        if (!menuBtn || !sidebar) return;

        if (
            menuBtn.dataset.commonLayoutMenuBound === "true" ||
            menuBtn.dataset.historyLayoutFixed === "true" ||
            menuBtn.dataset.sidebarTogglePatched === "true"
        ) {
            return;
        }

        menuBtn.dataset.commonLayoutMenuBound = "true";

        menuBtn.addEventListener("click", () => {
            if (window.innerWidth <= 768) {
                sidebar.classList.toggle("is-mobile-open");
                document.body.classList.toggle("sidebar-mobile-open");
                return;
            }

            sidebar.classList.toggle("collapsed");

            if (main) {
                main.classList.toggle("expanded");
            }
        });
    }

    function bindSearchForm() {
        const forms = document.querySelectorAll(".common-layout-search-form");

        forms.forEach((form) => {
            if (form.dataset.commonLayoutSearchBound === "true") return;

            form.dataset.commonLayoutSearchBound = "true";

            form.addEventListener("submit", (event) => {
                event.preventDefault();

                const input = form.querySelector('input[type="text"]');
                const keyword = input ? input.value.trim() : "";

                const url = new URL("index.html", window.location.href);

                if (keyword) {
                    url.searchParams.set("q", keyword);
                }

                window.location.href = url.toString();
            });
        });
    }

    function bindReadyMenus() {
        const menus = document.querySelectorAll("[data-common-layout-ready-menu]");

        menus.forEach((menu) => {
            if (menu.dataset.commonLayoutReadyBound === "true") return;

            menu.dataset.commonLayoutReadyBound = "true";
            menu.setAttribute("role", "button");
            menu.setAttribute("tabindex", "0");

            const label = menu.dataset.commonLayoutReadyMenu || "해당";

            function showReadyMessage() {
                if (typeof showToast === "function") {
                    showToast(`${label} 페이지는 아직 준비 중입니다.`);
                } else {
                    alert(`${label} 페이지는 아직 준비 중입니다.`);
                }
            }

            menu.addEventListener("click", showReadyMessage);

            menu.addEventListener("keydown", (event) => {
                if (event.key !== "Enter" && event.key !== " ") return;

                event.preventDefault();
                showReadyMessage();
            });
        });
    }

    function bindTopbarButtons() {
        const micButtons = document.querySelectorAll(".mic-btn");

        micButtons.forEach((button) => {
            if (button.dataset.commonLayoutMicBound === "true") return;

            button.dataset.commonLayoutMicBound = "true";

            button.addEventListener("click", () => {
                if (typeof showToast === "function") {
                    showToast("음성 검색 기능은 아직 준비 중입니다.");
                } else {
                    alert("음성 검색 기능은 아직 준비 중입니다.");
                }
            });
        });
    }

    function recoverCommonLayout() {
        if (!shouldApplyLayoutPatch()) return;

        if (document.body.dataset[PATCH_NAME] === "true") {
            return;
        }

        document.body.dataset[PATCH_NAME] = "true";
        document.body.classList.add("common-layout-recovered");

        ensureStyle();
        ensureTopbar();
        ensureSidebar();
        ensureMain();
        markActiveSidebarItem();
        bindMenuButton();
        bindSearchForm();
        bindReadyMenus();
        bindTopbarButtons();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", recoverCommonLayout);
    } else {
        recoverCommonLayout();
    }
})();
/* =========================================================
   업로드 페이지 상단바 / 사이드바 제거 패치 v1
   - upload.html에서는 상단바, 사이드바 제거
   - 업로드 화면을 단독 작업 화면처럼 정리
   - 기존 코드 삭제하지 말고 script.js 맨 아래에 추가
========================================================= */

(function () {
    function isUploadPage() {
        const page = document.body.dataset.page || "";
        const filename = window.location.pathname.split("/").pop() || "";

        return page === "upload" || filename.includes("upload");
    }

    function ensureUploadCleanStyle() {
        if (document.getElementById("upload-clean-layout-style")) return;

        const style = document.createElement("style");
        style.id = "upload-clean-layout-style";
        style.textContent = `
            body.upload-clean-layout {
                padding-top: 0 !important;
                margin: 0 !important;
                background: #0f0f0f !important;
                overflow-x: hidden !important;
            }

            body.upload-clean-layout .topbar,
            body.upload-clean-layout #topbar,
            body.upload-clean-layout .sidebar,
            body.upload-clean-layout #sidebar {
                display: none !important;
            }

            body.upload-clean-layout .main,
            body.upload-clean-layout main,
            body.upload-clean-layout #main {
                margin-left: 0 !important;
                padding: 0 !important;
                width: 100% !important;
                max-width: none !important;
                min-height: 100vh !important;
                box-sizing: border-box !important;
            }

            body.upload-clean-layout .upload-page {
                min-height: 100vh !important;
                padding: 56px 24px !important;
                display: flex !important;
                align-items: flex-start !important;
                justify-content: center !important;
                box-sizing: border-box !important;
            }

            body.upload-clean-layout .upload-wizard-card {
                width: 100% !important;
                max-width: 1280px !important;
                margin: 0 auto !important;
            }

            @media (max-width: 768px) {
                body.upload-clean-layout .upload-page {
                    padding: 28px 16px !important;
                }
            }
        `;

        document.head.appendChild(style);
    }

    function removeUploadLayoutElements() {
        const topbars = document.querySelectorAll(".topbar, #topbar");
        const sidebars = document.querySelectorAll(".sidebar, #sidebar");

        topbars.forEach((element) => {
            element.remove();
        });

        sidebars.forEach((element) => {
            element.remove();
        });
    }

    function resetUploadMainLayout() {
        const main =
            document.getElementById("main") ||
            document.querySelector("main") ||
            document.querySelector(".main");

        if (!main) return;

        main.classList.remove("expanded");
        main.style.marginLeft = "0";
    }

    function runUploadCleanLayoutPatch() {
        if (!isUploadPage()) return;

        document.body.classList.add("upload-clean-layout");

        ensureUploadCleanStyle();
        removeUploadLayoutElements();
        resetUploadMainLayout();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", runUploadCleanLayoutPatch);
    } else {
        runUploadCleanLayoutPatch();
    }

    setTimeout(runUploadCleanLayoutPatch, 100);
    setTimeout(runUploadCleanLayoutPatch, 500);
    setTimeout(runUploadCleanLayoutPatch, 1200);
})();
/* =========================================================
   보관함 3페이지 홈 스타일 통일 패치 v1
   - saved.html / liked.html / history.html 화면 통일
   - 홈 화면과 같은 상단바 / 미니 사이드바 / 카드 그리드 스타일 적용
   - 단, 홈의 추천 영상 박스는 표시하지 않음
   - 기존 코드 삭제하지 말고 script.js 맨 아래에 추가
========================================================= */

(function () {
    const PATCH_ID = "library-pages-home-style-unified-v1";

    const PAGE_CONFIG = {
        history: {
            title: "시청 기록",
            subtitle: "최근에 시청한 영상",
            countLabel: "시청 기록"
        },
        saved: {
            title: "저장한 영상",
            subtitle: "나중에 다시 볼 영상",
            countLabel: "저장한 영상"
        },
        liked: {
            title: "좋아요한 영상",
            subtitle: "좋아요를 누른 영상",
            countLabel: "좋아요한 영상"
        }
    };

    function getPageName() {
        const bodyPage = document.body.dataset.page || "";
        if (PAGE_CONFIG[bodyPage]) return bodyPage;

        const filename = window.location.pathname.split("/").pop() || "";

        if (filename.includes("history")) return "history";
        if (filename.includes("saved")) return "saved";
        if (filename.includes("liked")) return "liked";

        return "";
    }

    function isTargetPage() {
        return false;
    }

    function getConfig() {
        return PAGE_CONFIG[getPageName()];
    }

    function safeNumberText(number) {
        if (typeof formatCount === "function") {
            return formatCount(number);
        }

        return new Intl.NumberFormat("ko-KR").format(Number(number || 0));
    }

    function ensureUnifiedStyle() {
        if (document.getElementById(PATCH_ID + "-style")) return;

        const style = document.createElement("style");
        style.id = PATCH_ID + "-style";

        style.textContent = `
            body.library-home-style-unified {
                margin: 0 !important;
                padding-top: 56px !important;
                background: #0f0f0f !important;
                color: #fff !important;
                overflow-x: hidden !important;
            }

            body.library-home-style-unified .topbar,
            body.library-home-style-unified #topbar {
                position: fixed !important;
                top: 0 !important;
                left: 0 !important;
                right: 0 !important;
                height: 56px !important;
                display: flex !important;
                align-items: center !important;
                justify-content: space-between !important;
                gap: 16px !important;
                padding: 0 16px !important;
                background: #0f0f0f !important;
                border-bottom: 1px solid #242424 !important;
                z-index: 3000 !important;
                box-sizing: border-box !important;
            }

            body.library-home-style-unified .topbar-left,
            body.library-home-style-unified .topbar-center,
            body.library-home-style-unified .topbar-right {
                display: flex !important;
                align-items: center !important;
                gap: 12px !important;
            }

            body.library-home-style-unified .topbar-left {
                min-width: 220px !important;
            }

            body.library-home-style-unified .topbar-center {
                flex: 1 !important;
                justify-content: center !important;
                max-width: 700px !important;
            }

            body.library-home-style-unified .topbar-right {
                min-width: 220px !important;
                justify-content: flex-end !important;
            }

            body.library-home-style-unified .icon-btn {
                width: 40px !important;
                height: 40px !important;
                border: none !important;
                border-radius: 999px !important;
                background: transparent !important;
                color: #fff !important;
                display: inline-flex !important;
                align-items: center !important;
                justify-content: center !important;
                text-decoration: none !important;
                cursor: pointer !important;
                flex: 0 0 auto !important;
            }

            body.library-home-style-unified .icon-btn:hover {
                background: #272727 !important;
            }

            body.library-home-style-unified .icon-btn svg {
                width: 22px !important;
                height: 22px !important;
                fill: currentColor !important;
            }

            body.library-home-style-unified .menu-btn svg path {
                fill: none !important;
                stroke: currentColor !important;
                stroke-width: 2 !important;
                stroke-linecap: round !important;
            }

            body.library-home-style-unified .logo-wrap {
                display: inline-flex !important;
                align-items: center !important;
                gap: 8px !important;
                color: #fff !important;
                text-decoration: none !important;
                font-weight: 800 !important;
                font-size: 20px !important;
                white-space: nowrap !important;
            }

            body.library-home-style-unified .logo-badge {
                width: 34px !important;
                height: 34px !important;
                border-radius: 50% !important;
                background: linear-gradient(135deg, #a855f7 0%, #4f46e5 100%) !important;
                box-shadow: 0 2px 10px rgba(168, 85, 247, 0.45) !important;
                display: inline-flex !important;
                align-items: center !important;
                justify-content: center !important;
                color: #fff !important;
            }

            body.library-home-style-unified .logo-badge svg {
                width: 20px !important;
                height: 20px !important;
                fill: none !important;
            }

            body.library-home-style-unified .logo-text {
                color: #fff !important;
                font-weight: 800 !important;
            }

            body.library-home-style-unified .search-form {
                width: 100% !important;
                max-width: 660px !important;
                height: 40px !important;
                display: flex !important;
                align-items: center !important;
            }

            body.library-home-style-unified .search-form input {
                flex: 1 !important;
                height: 40px !important;
                padding: 0 16px !important;
                border: 1px solid #303030 !important;
                border-right: none !important;
                border-radius: 999px 0 0 999px !important;
                background: #121212 !important;
                color: #fff !important;
                outline: none !important;
                box-sizing: border-box !important;
            }

            body.library-home-style-unified .search-btn {
                width: 64px !important;
                height: 40px !important;
                border: 1px solid #303030 !important;
                border-radius: 0 999px 999px 0 !important;
                background: #222 !important;
                color: #fff !important;
                display: inline-flex !important;
                align-items: center !important;
                justify-content: center !important;
                cursor: pointer !important;
            }

            body.library-home-style-unified .search-btn svg {
                width: 20px !important;
                height: 20px !important;
                fill: currentColor !important;
            }

            body.library-home-style-unified .profile {
                width: 32px !important;
                height: 32px !important;
                border-radius: 50% !important;
                background: #ff6a21 !important;
                color: #fff !important;
                display: inline-flex !important;
                align-items: center !important;
                justify-content: center !important;
                text-decoration: none !important;
                font-weight: 700 !important;
                flex: 0 0 auto !important;
            }

            body.library-home-style-unified .sidebar,
            body.library-home-style-unified #sidebar {
                position: fixed !important;
                top: 56px !important;
                left: 0 !important;
                bottom: 0 !important;
                width: 72px !important;
                height: calc(100vh - 56px) !important;
                padding: 8px 4px !important;
                background: #0f0f0f !important;
                border-right: 1px solid #1f1f1f !important;
                z-index: 2500 !important;
                overflow-y: auto !important;
                box-sizing: border-box !important;
                transform: none !important;
            }

            body.library-home-style-unified .sidebar-inner {
                display: flex !important;
                flex-direction: column !important;
                align-items: center !important;
                gap: 6px !important;
            }

            body.library-home-style-unified .sidebar-item {
                width: 64px !important;
                min-height: 64px !important;
                padding: 8px 4px !important;
                border-radius: 10px !important;
                display: flex !important;
                flex-direction: column !important;
                align-items: center !important;
                justify-content: center !important;
                gap: 5px !important;
                color: #fff !important;
                text-decoration: none !important;
                cursor: pointer !important;
                box-sizing: border-box !important;
                text-align: center !important;
                line-height: 1.1 !important;
                white-space: normal !important;
            }

            body.library-home-style-unified .sidebar-item:hover,
            body.library-home-style-unified .sidebar-item.active {
                background: #272727 !important;
            }

            body.library-home-style-unified .sidebar-icon {
                width: 24px !important;
                height: 24px !important;
                display: inline-flex !important;
                align-items: center !important;
                justify-content: center !important;
                font-size: 18px !important;
                line-height: 1 !important;
                margin: 0 auto !important;
                flex: 0 0 auto !important;
            }

            body.library-home-style-unified .sidebar-label {
                width: 100% !important;
                display: block !important;
                font-size: 10.5px !important;
                font-weight: 700 !important;
                line-height: 1.15 !important;
                text-align: center !important;
                word-break: keep-all !important;
                white-space: normal !important;
                overflow: visible !important;
                text-overflow: unset !important;
            }

            body.library-home-style-unified .sidebar-divider {
                display: none !important;
            }

            body.library-home-style-unified .main,
            body.library-home-style-unified main,
            body.library-home-style-unified #main {
                margin-left: 72px !important;
                padding: 28px 24px 48px !important;
                min-height: calc(100vh - 56px) !important;
                width: auto !important;
                max-width: none !important;
                box-sizing: border-box !important;
                background: #0f0f0f !important;
            }

            body.library-home-style-unified .home-hero,
            body.library-home-style-unified .home-hero-card,
            body.library-home-style-unified .home-result-card,
            body.library-home-style-unified .home-result-panel,
            body.library-home-style-unified .home-top-section,
            body.library-home-style-unified .hero-section,
            body.library-home-style-unified .hero-card,
            body.library-home-style-unified .recommend-hero,
            body.library-home-style-unified .recommend-box,
            body.library-home-style-unified .recommend-panel {
                display: none !important;
            }

            body.library-home-style-unified #savedKicker,
            body.library-home-style-unified #savedTitle,
            body.library-home-style-unified #savedCountText,
            body.library-home-style-unified #likedKicker,
            body.library-home-style-unified #likedTitle,
            body.library-home-style-unified #likedCountText {
                display: none !important;
            }

            body.library-home-style-unified .library-page-unified-header {
                width: 100% !important;
                max-width: 1480px !important;
                margin: 0 auto 24px !important;
                padding: 0 !important;
                display: flex !important;
                align-items: flex-end !important;
                justify-content: space-between !important;
                gap: 16px !important;
                box-sizing: border-box !important;
            }

            body.library-home-style-unified .library-page-unified-kicker {
                margin: 0 0 8px !important;
                color: #aaa !important;
                font-size: 14px !important;
                font-weight: 600 !important;
            }

            body.library-home-style-unified .library-page-unified-title {
                margin: 0 !important;
                color: #fff !important;
                font-size: 32px !important;
                font-weight: 800 !important;
                letter-spacing: -0.04em !important;
                line-height: 1.2 !important;
            }

            body.library-home-style-unified .library-page-unified-count {
                margin: 12px 0 0 !important;
                color: #aaa !important;
                font-size: 14px !important;
            }

            body.library-home-style-unified .library-page-unified-actions {
                display: flex !important;
                align-items: center !important;
                justify-content: flex-end !important;
                gap: 10px !important;
                flex: 0 0 auto !important;
            }

            body.library-home-style-unified .history-force-toolbar,
            body.library-home-style-unified #historyForceToolbar {
                margin: 0 !important;
                display: flex !important;
                justify-content: flex-end !important;
            }

            body.library-home-style-unified .history-force-clear,
            body.library-home-style-unified .history-patch-clear,
            body.library-home-style-unified #historyForceClearBtn {
                height: 38px !important;
                padding: 0 16px !important;
                border: 1px solid #303030 !important;
                border-radius: 999px !important;
                background: #181818 !important;
                color: #fff !important;
                cursor: pointer !important;
                font-size: 14px !important;
            }

            body.library-home-style-unified .history-force-clear:hover,
            body.library-home-style-unified .history-patch-clear:hover,
            body.library-home-style-unified #historyForceClearBtn:hover {
                background: #272727 !important;
            }

            body.library-home-style-unified .library-page-video-grid,
            body.library-home-style-unified #savedVideoGrid,
            body.library-home-style-unified #likedVideoGrid,
            body.library-home-style-unified #historyVideoGrid,
            body.library-home-style-unified #historyList,
            body.library-home-style-unified .history-force-grid,
            body.library-home-style-unified .history-patch-grid {
                width: 100% !important;
                max-width: 1480px !important;
                margin: 0 auto !important;
                display: grid !important;
                grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)) !important;
                gap: 28px 18px !important;
                align-items: start !important;
                box-sizing: border-box !important;
            }

            body.library-home-style-unified .card {
                width: 100% !important;
                min-width: 0 !important;
                background: transparent !important;
                border: none !important;
                box-shadow: none !important;
                display: flex !important;
                flex-direction: column !important;
                gap: 10px !important;
            }

            body.library-home-style-unified .card-link {
                color: inherit !important;
                text-decoration: none !important;
                display: block !important;
            }

            body.library-home-style-unified .thumbnail-wrap,
            body.library-home-style-unified .history-thumbnail-wrap {
                position: relative !important;
                width: 100% !important;
                aspect-ratio: 16 / 9 !important;
                border-radius: 12px !important;
                overflow: hidden !important;
                background: #181818 !important;
            }

            body.library-home-style-unified .thumbnail-image,
            body.library-home-style-unified .history-thumbnail {
                width: 100% !important;
                height: 100% !important;
                display: block !important;
                object-fit: cover !important;
            }

            body.library-home-style-unified .duration,
            body.library-home-style-unified .video-duration,
            body.library-home-style-unified .recommend-duration {
                position: absolute !important;
                right: 8px !important;
                bottom: 8px !important;
                min-width: auto !important;
                padding: 3px 6px !important;
                border-radius: 5px !important;
                background: rgba(0, 0, 0, 0.78) !important;
                color: #fff !important;
                font-size: 12px !important;
                font-weight: 700 !important;
                line-height: 1.2 !important;
            }

            body.library-home-style-unified .meta {
                display: flex !important;
                align-items: flex-start !important;
                gap: 12px !important;
                padding: 0 !important;
            }

            body.library-home-style-unified .avatar-image {
                width: 36px !important;
                height: 36px !important;
                border-radius: 50% !important;
                object-fit: cover !important;
                flex: 0 0 auto !important;
                background: #272727 !important;
            }

            body.library-home-style-unified .text {
                min-width: 0 !important;
                flex: 1 !important;
            }

            body.library-home-style-unified .text h3,
            body.library-home-style-unified .video-title,
            body.library-home-style-unified .history-title {
                margin: 0 0 5px !important;
                color: #fff !important;
                font-size: 16px !important;
                font-weight: 800 !important;
                line-height: 1.35 !important;
                display: -webkit-box !important;
                -webkit-line-clamp: 2 !important;
                -webkit-box-orient: vertical !important;
                overflow: hidden !important;
            }

            body.library-home-style-unified .channel-name,
            body.library-home-style-unified .video-info,
            body.library-home-style-unified .history-channel,
            body.library-home-style-unified .history-meta,
            body.library-home-style-unified .history-force-date,
            body.library-home-style-unified .history-patch-date {
                margin: 0 !important;
                color: #aaa !important;
                font-size: 13px !important;
                line-height: 1.45 !important;
            }

            body.library-home-style-unified .history-force-date,
            body.library-home-style-unified .history-patch-date {
                margin-top: 4px !important;
                color: #8f8f8f !important;
            }

            body.library-home-style-unified .saved-card-actions,
            body.library-home-style-unified .liked-card-actions {
                display: flex !important;
                justify-content: flex-start !important;
                margin-left: 48px !important;
                margin-top: 2px !important;
            }

            body.library-home-style-unified .saved-remove-btn,
            body.library-home-style-unified .liked-remove-btn {
                height: 32px !important;
                padding: 0 12px !important;
                border: 1px solid #303030 !important;
                border-radius: 999px !important;
                background: #181818 !important;
                color: #fff !important;
                font-size: 12px !important;
                cursor: pointer !important;
            }

            body.library-home-style-unified .saved-remove-btn:hover,
            body.library-home-style-unified .liked-remove-btn:hover {
                background: #272727 !important;
            }

            body.library-home-style-unified .local-list-empty,
            body.library-home-style-unified .history-force-empty,
            body.library-home-style-unified .history-patch-empty {
                grid-column: 1 / -1 !important;
                min-height: 320px !important;
                display: flex !important;
                flex-direction: column !important;
                align-items: center !important;
                justify-content: center !important;
                color: #aaa !important;
                text-align: center !important;
                gap: 10px !important;
            }

            body.library-home-style-unified .local-list-empty h2,
            body.library-home-style-unified .history-force-empty h2,
            body.library-home-style-unified .history-patch-empty h2 {
                margin: 0 !important;
                color: #fff !important;
                font-size: 24px !important;
            }

            body.library-home-style-unified .local-list-empty p,
            body.library-home-style-unified .history-force-empty p,
            body.library-home-style-unified .history-patch-empty p {
                margin: 0 !important;
                color: #aaa !important;
                font-size: 14px !important;
            }

            @media (max-width: 900px) {
                            body.library-home-style-unified .topbar-center {
                    display: none !important;
                }

                body.library-home-style-unified .topbar-left,
                body.library-home-style-unified .topbar-right {
                    min-width: auto !important;
                }

                body.library-home-style-unified .sidebar,
                body.library-home-style-unified #sidebar {
                    transform: translateX(-100%) !important;
                    width: 240px !important;
                    padding: 12px !important;
                }

                body.library-home-style-unified .sidebar.is-mobile-open,
                body.library-home-style-unified #sidebar.is-mobile-open {
                    transform: translateX(0) !important;
                }

                body.library-home-style-unified .sidebar .sidebar-item {
                    width: 100% !important;
                    min-height: 42px !important;
                    padding: 0 12px !important;
                    flex-direction: row !important;
                    justify-content: flex-start !important;
                    gap: 18px !important;
                    text-align: left !important;
                }

                body.library-home-style-unified .sidebar .sidebar-label {
                    width: auto !important;
                    font-size: 14px !important;
                    text-align: left !important;
                    white-space: nowrap !important;
                }

                body.library-home-style-unified .main,
                body.library-home-style-unified main,
                body.library-home-style-unified #main {
                    margin-left: 0 !important;
                    padding: 24px 16px 40px !important;
                }

                body.library-home-style-unified .library-page-unified-header {
                    flex-direction: column !important;
                    align-items: flex-start !important;
                }

                body.library-home-style-unified .library-page-video-grid,
                body.library-home-style-unified #savedVideoGrid,
                body.library-home-style-unified #likedVideoGrid,
                body.library-home-style-unified #historyVideoGrid,
                body.library-home-style-unified #historyList,
                body.library-home-style-unified .history-force-grid,
                body.library-home-style-unified .history-patch-grid {
                    grid-template-columns: 1fr !important;
                }
            }

            /* ── 라이트 모드 오버라이드 ── */
            html[data-theme="light"] body.library-home-style-unified {
                background: #f9f9f9 !important;
                color: #0f0f0f !important;
            }
            html[data-theme="light"] body.library-home-style-unified .topbar,
            html[data-theme="light"] body.library-home-style-unified #topbar {
                background: #ffffff !important;
                border-bottom-color: #e5e5e5 !important;
            }
            html[data-theme="light"] body.library-home-style-unified .icon-btn {
                color: #0f0f0f !important;
            }
            html[data-theme="light"] body.library-home-style-unified .icon-btn:hover {
                background: #e8e8e8 !important;
            }
            html[data-theme="light"] body.library-home-style-unified .logo-wrap,
            html[data-theme="light"] body.library-home-style-unified .logo-text {
                color: #0f0f0f !important;
            }
            html[data-theme="light"] body.library-home-style-unified .search-form input {
                background: #ffffff !important;
                border-color: #cccccc !important;
                color: #0f0f0f !important;
            }
            html[data-theme="light"] body.library-home-style-unified .search-btn {
                background: #f2f2f2 !important;
                border-color: #cccccc !important;
                color: #0f0f0f !important;
            }
            html[data-theme="light"] body.library-home-style-unified .sidebar,
            html[data-theme="light"] body.library-home-style-unified #sidebar {
                background: #f9f9f9 !important;
                border-right-color: #e5e5e5 !important;
            }
            html[data-theme="light"] body.library-home-style-unified .sidebar-item {
                color: #0f0f0f !important;
            }
            html[data-theme="light"] body.library-home-style-unified .sidebar-item:hover,
            html[data-theme="light"] body.library-home-style-unified .sidebar-item.active {
                background: #e8e8e8 !important;
            }
            html[data-theme="light"] body.library-home-style-unified .main,
            html[data-theme="light"] body.library-home-style-unified main,
            html[data-theme="light"] body.library-home-style-unified #main {
                background: #f9f9f9 !important;
            }
            html[data-theme="light"] body.library-home-style-unified .text h3,
            html[data-theme="light"] body.library-home-style-unified .video-title,
            html[data-theme="light"] body.library-home-style-unified .history-title,
            html[data-theme="light"] body.library-home-style-unified .library-page-unified-title,
            html[data-theme="light"] body.library-home-style-unified .local-list-empty h2,
            html[data-theme="light"] body.library-home-style-unified .history-force-empty h2,
            html[data-theme="light"] body.library-home-style-unified .history-patch-empty h2 {
                color: #0f0f0f !important;
            }
            html[data-theme="light"] body.library-home-style-unified .channel-name,
            html[data-theme="light"] body.library-home-style-unified .video-info,
            html[data-theme="light"] body.library-home-style-unified .history-channel,
            html[data-theme="light"] body.library-home-style-unified .history-meta,
            html[data-theme="light"] body.library-home-style-unified .library-page-unified-kicker,
            html[data-theme="light"] body.library-home-style-unified .library-page-unified-count,
            html[data-theme="light"] body.library-home-style-unified .local-list-empty,
            html[data-theme="light"] body.library-home-style-unified .local-list-empty p {
                color: #606060 !important;
            }
            html[data-theme="light"] body.library-home-style-unified .thumbnail-wrap,
            html[data-theme="light"] body.library-home-style-unified .history-thumbnail-wrap {
                background: #e5e5e5 !important;
            }
            html[data-theme="light"] body.library-home-style-unified .saved-remove-btn,
            html[data-theme="light"] body.library-home-style-unified .liked-remove-btn,
            html[data-theme="light"] body.library-home-style-unified .history-force-clear,
            html[data-theme="light"] body.library-home-style-unified .history-patch-clear {
                background: #f2f2f2 !important;
                border-color: #cccccc !important;
                color: #0f0f0f !important;
            }
            html[data-theme="light"] body.library-home-style-unified .saved-remove-btn:hover,
            html[data-theme="light"] body.library-home-style-unified .liked-remove-btn:hover,
            html[data-theme="light"] body.library-home-style-unified .history-force-clear:hover,
            html[data-theme="light"] body.library-home-style-unified .history-patch-clear:hover {
                background: #e5e5e5 !important;
            }
        `;

        document.head.appendChild(style);
    }

    function getMainElement() {
        return (
            document.getElementById("main") ||
            document.querySelector("main") ||
            document.querySelector(".main")
        );
    }

    function getGridElement() {
        const page = getPageName();

        if (page === "history") {
            return (
                document.getElementById("historyVideoGrid") ||
                document.getElementById("historyList") ||
                document.querySelector(".history-force-grid") ||
                document.querySelector(".history-patch-grid") ||
                document.querySelector("[data-video-grid]") ||
                document.querySelector(".video-grid") ||
                document.querySelector(".content-grid")
            );
        }

        if (page === "saved") {
            return (
                document.getElementById("savedVideoGrid") ||
                document.querySelector("[data-saved-video-grid]") ||
                document.querySelector(".video-grid") ||
                document.querySelector(".content-grid")
            );
        }

        if (page === "liked") {
            return (
                document.getElementById("likedVideoGrid") ||
                document.querySelector("[data-liked-video-grid]") ||
                document.querySelector(".video-grid") ||
                document.querySelector(".content-grid")
            );
        }

        return null;
    }

    function hideHomeRecommendBox() {
        const badTextList = [
            "추천 영상",
            "지금 볼만한 영상",
            "맞춤 추천",
            "업로드 영상 포함",
            "검색 지원"
        ];

        const candidates = document.querySelectorAll(
            ".home-hero, .home-hero-card, .home-result-card, .home-result-panel, .home-top-section, .hero-section, .hero-card, .recommend-hero, .recommend-box, .recommend-panel, section"
        );

        candidates.forEach((element) => {
            const text = element.textContent || "";
            const hasBadText = badTextList.some((word) => text.includes(word));

            if (!hasBadText) return;

            if (
                element.closest(".topbar") ||
                element.closest(".sidebar") ||
                element.querySelector(".card") ||
                element.id === "savedVideoGrid" ||
                element.id === "likedVideoGrid" ||
                element.id === "historyVideoGrid" ||
                element.id === "historyList"
            ) {
                return;
            }

            element.style.display = "none";
        });
    }

    function hideDuplicateOldTitles() {
        const config = getConfig();
        if (!config) return;

        const titleText = config.title;

        document.querySelectorAll("h1, h2").forEach((heading) => {
            if (heading.closest(".library-page-unified-header")) return;

            const text = heading.textContent.trim();

            if (text === titleText || text.includes(titleText)) {
                heading.style.display = "none";
            }
        });
    }

    function ensureHeader() {
        const config = getConfig();
        const main = getMainElement();
        const grid = getGridElement();

        if (!config || !main) return null;

        let header = document.getElementById("libraryPageUnifiedHeader");

        if (!header) {
            header = document.createElement("section");
            header.id = "libraryPageUnifiedHeader";
            header.className = "library-page-unified-header";

            header.innerHTML = `
                <div class="library-page-unified-text">
                    <p class="library-page-unified-kicker">${config.subtitle}</p>
                    <h1 class="library-page-unified-title">${config.title}</h1>
                    <p class="library-page-unified-count" id="libraryPageUnifiedCount">${config.countLabel} 0개</p>
                </div>

                <div class="library-page-unified-actions" id="libraryPageUnifiedActions"></div>
            `;

            if (grid) {
                grid.insertAdjacentElement("beforebegin", header);
            } else {
                main.insertAdjacentElement("afterbegin", header);
            }
        }

        return header;
    }

    function moveHistoryClearButtonToHeader() {
        if (getPageName() !== "history") return;

        const actions = document.getElementById("libraryPageUnifiedActions");
        if (!actions) return;

        const toolbar =
            document.getElementById("historyForceToolbar") ||
            document.querySelector(".history-force-toolbar");

        const clearButton =
            document.getElementById("historyForceClearBtn") ||
            document.getElementById("historyClearButton") ||
            document.querySelector(".history-force-clear") ||
            document.querySelector(".history-patch-clear");

        if (toolbar && !actions.contains(toolbar)) {
            actions.appendChild(toolbar);
            return;
        }

        if (clearButton && !actions.contains(clearButton)) {
            actions.appendChild(clearButton);
        }
    }

    function updateHeaderCount() {
        const config = getConfig();
        const grid = getGridElement();
        const countEl = document.getElementById("libraryPageUnifiedCount");

        if (!config || !countEl) return;

        let count = 0;

        if (grid) {
            count = grid.querySelectorAll("article.card, .card").length;

            const isEmpty =
                grid.querySelector(".local-list-empty") ||
                grid.querySelector(".history-force-empty") ||
                grid.querySelector(".history-patch-empty");

            if (isEmpty) {
                count = 0;
            }
        }

        countEl.textContent = `${config.countLabel} ${safeNumberText(count)}개`;
    }

    function normalizeGrid() {
        const grid = getGridElement();
        if (!grid) return;

        grid.classList.add("library-page-video-grid");
    }

    function normalizeSidebar() {
        const sidebar = document.getElementById("sidebar") || document.querySelector(".sidebar");
        if (!sidebar) return;

        sidebar.classList.remove("wide");
        sidebar.classList.remove("collapsed");

        const currentPage = getPageName();

        sidebar.querySelectorAll(".sidebar-item").forEach((item) => {
            const label = item.querySelector(".sidebar-label")?.textContent.trim() || item.textContent.trim();

            item.classList.remove("active");

            if (
                (currentPage === "history" && label.includes("시청 기록")) ||
                (currentPage === "saved" && label.includes("저장")) ||
                (currentPage === "liked" && label.includes("좋아요"))
            ) {
                item.classList.add("active");
            }
        });
    }

    function bindMenuButtonAgain() {
        const menuBtn = document.getElementById("menuBtn");
        const sidebar = document.getElementById("sidebar") || document.querySelector(".sidebar");

        if (!menuBtn || !sidebar) return;

        menuBtn.onclick = function () {
            if (window.innerWidth <= 900) {
                sidebar.classList.toggle("is-mobile-open");
                document.body.classList.toggle("sidebar-mobile-open");
                return;
            }

            sidebar.classList.toggle("is-mobile-open");
        };
    }

    function normalizePage() {
        if (!isTargetPage()) return;

        document.body.classList.add("library-home-style-unified");

        ensureUnifiedStyle();
        normalizeSidebar();
        bindMenuButtonAgain();
        hideHomeRecommendBox();
        hideDuplicateOldTitles();
        normalizeGrid();
        ensureHeader();
        moveHistoryClearButtonToHeader();
        updateHeaderCount();
    }

    function bootUnifiedLibraryPages() {
        normalizePage();

        setTimeout(normalizePage, 100);
        setTimeout(normalizePage, 500);
        setTimeout(normalizePage, 1000);
        setTimeout(normalizePage, 1800);
        setTimeout(normalizePage, 2600);
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", bootUnifiedLibraryPages);
    } else {
        bootUnifiedLibraryPages();
    }
})();

/* =========================================================
   내 채널 화면 홈 스타일 정리 패치 v1
   - channel.html 화면을 홈 화면 스타일과 통일
   - 내가 올린 영상 카드 / 관리 버튼 정리
   - 기존 코드 삭제하지 말고 script.js 맨 아래에 추가
========================================================= */

(function () {
    const PATCH_ID = "channel-home-style-patch-v1";

    function isChannelPage() {
        const page = document.body.dataset.page || "";
        const filename = window.location.pathname.split("/").pop() || "";

        return page === "channel" || filename.includes("channel");
    }

    function safeEscape(text) {
        if (typeof escapeHtml === "function") {
            return escapeHtml(text);
        }

        return String(text ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#39;");
    }

    function safeFormatCount(count) {
        if (typeof formatCount === "function") {
            return formatCount(count);
        }

        return new Intl.NumberFormat("ko-KR").format(Number(count || 0));
    }

    function getLocalUploadedVideos() {
        const keys = [
            "youtube_clone_local_uploaded_videos"
        ];

        const merged = [];
        const usedIds = new Set();
        keys.forEach((key) => {
            const raw = localStorage.getItem(key);

            if (!raw) return;

            try {
                const parsed = JSON.parse(raw);

                if (!Array.isArray(parsed)) return;

                parsed.forEach((video) => {
                    if (!video) return;

                    const id = video.id ?? video.videoId ?? video._id;

                    if (id === undefined || id === null || id === "") return;

                    const idKey = String(id);

                    if (usedIds.has(idKey)) return;

                    usedIds.add(idKey);
                    merged.push({
                        id,
                        title: video.title || "제목 없는 영상",
                        channel: video.channel || "내 채널",
                        subscribers: video.subscribers || "구독자 0명",
                        views: video.views || "조회수 0회",
                        date: video.date || "방금 전",
                        duration: video.duration || "0:00",
                        category: video.category || "미분류",
                        thumbnail:
                            video.thumbnail ||
                            video.thumbnailUrl ||
                            DEFAULT_THUMBNAIL,
                        avatar:
                            video.avatar ||
                            video.profileImage ||
                            DEFAULT_AVATAR,
                        description: video.description || "",
                        embedUrl: video.embedUrl || "",
                        videoUrl: video.videoUrl || "",
                        videoUrl720: video.videoUrl720 || "",
                        videoUrl480: video.videoUrl480 || "",
                        videoUrl360: video.videoUrl360 || "",
                        visibility: video.visibility || "공개",
                        likeCount: Number(video.likeCount || 0),
                        commentCount: Number(video.commentCount || 0),
                        uploadedAt: video.uploadedAt || new Date().toISOString()
                    });
                });
            } catch {}
        });

        return merged;
    }

    async function getChannelVideos() {
        let serverVideos = [];

        try {
            if (typeof fetchMyVideos === "function") {
                const result = await fetchMyVideos();

                if (Array.isArray(result)) {
                    serverVideos = result;
                }
            }
        } catch {}

        const localVideos = getLocalUploadedVideos();
        const merged = [];
        const usedIds = new Set();

        [...localVideos, ...serverVideos].forEach((video) => {
            if (!video) return;

            const id = video.id ?? video.videoId ?? video._id;

            if (id === undefined || id === null || id === "") return;

            const idKey = String(id);

            if (usedIds.has(idKey)) return;

            usedIds.add(idKey);

            merged.push({
                id,
                title: video.title || "제목 없는 영상",
                channel: video.channel || "내 채널",
                subscribers: video.subscribers || "구독자 0명",
                views: video.views || "조회수 0회",
                date: video.date || "방금 전",
                duration: video.duration || "0:00",
                category: video.category || "미분류",
                thumbnail:
                    video.thumbnail ||
                    video.thumbnailUrl ||
                    DEFAULT_THUMBNAIL,
                avatar:
                    video.avatar ||
                    video.profileImage ||
                    DEFAULT_AVATAR,
                description: video.description || "",
                embedUrl: video.embedUrl || "",
                videoUrl: video.videoUrl || "",
                videoUrl720: video.videoUrl720 || "",
                videoUrl480: video.videoUrl480 || "",
                videoUrl360: video.videoUrl360 || "",
                visibility: video.visibility || "공개",
                likeCount: Number(video.likeCount || 0),
                commentCount: Number(video.commentCount || 0),
                uploadedAt: video.uploadedAt || new Date().toISOString()
            });
        });

        return merged;
    }

    function getVideoUrlSafe(id) {
        if (typeof getVideoUrl === "function") {
            return getVideoUrl(id);
        }

        return `watch.html?v=${id}`;
    }

    function getEditUrlSafe(id) {
        if (typeof getEditUrl === "function") {
            return getEditUrl(id);
        }

        return `edit.html?id=${id}`;
    }

    function getViewCountSafe(video) {
        try {
            if (typeof loadViewCount === "function") {
                return loadViewCount(video);
            }
        } catch {}

        const match = String(video.views || "").replaceAll(",", "").match(/\d+/);
        return match ? Number(match[0]) : 0;
    }

    function createChannelVideoCard(video) {
        const viewCount = getViewCountSafe(video);
        const videoUrl = getVideoUrlSafe(video.id);
        const editUrl = getEditUrlSafe(video.id);

        return `
            <article class="channel-unified-card card" data-channel-video-id="${safeEscape(video.id)}">
                <a href="${safeEscape(videoUrl)}" class="card-link">
                    <div class="thumbnail-wrap">
                        <img class="thumbnail-image" src="${safeEscape(video.thumbnail)}" alt="${safeEscape(video.title)}" />
                        <span class="duration">${safeEscape(video.duration || "0:00")}</span>
                    </div>

                    <div class="meta">
                        <img class="avatar-image" src="${safeEscape(video.avatar)}" alt="${safeEscape(video.channel)}" />

                        <div class="text">
                            <h3>${safeEscape(video.title)}</h3>
                            <p class="channel-name">${safeEscape(video.channel)}</p>
                            <p class="video-info">조회수 ${safeFormatCount(viewCount)}회 · ${safeEscape(video.date || "방금 전")}</p>
                            <p class="channel-unified-category">${safeEscape(video.category || "미분류")} · ${safeEscape(video.visibility || "공개")}</p>
                        </div>
                    </div>
                </a>

                <div class="channel-unified-actions">
                    <a href="${safeEscape(videoUrl)}" class="channel-unified-btn">영상 보기</a>
                    <a href="${safeEscape(editUrl)}" class="channel-unified-btn">수정</a>
                    <button type="button" class="channel-unified-btn danger" data-channel-delete-id="${safeEscape(video.id)}">삭제</button>
                </div>
            </article>
        `;
    }

    function ensureChannelStyle() {
        if (document.getElementById(PATCH_ID + "-style")) return;

        const style = document.createElement("style");
        style.id = PATCH_ID + "-style";

        style.textContent = `
            body.channel-home-style {
                margin: 0 !important;
                padding-top: 56px !important;
                background: #0f0f0f !important;
                color: #fff !important;
                overflow-x: hidden !important;
            }

            body.channel-home-style .topbar,
            body.channel-home-style #topbar {
                position: fixed !important;
                top: 0 !important;
                left: 0 !important;
                right: 0 !important;
                height: 56px !important;
                display: flex !important;
                align-items: center !important;
                justify-content: space-between !important;
                padding: 0 16px !important;
                background: #0f0f0f !important;
                border-bottom: 1px solid #242424 !important;
                z-index: 3000 !important;
                box-sizing: border-box !important;
            }

            body.channel-home-style .sidebar,
            body.channel-home-style #sidebar {
                position: fixed !important;
                top: 56px !important;
                left: 0 !important;
                bottom: 0 !important;
                width: 72px !important;
                height: calc(100vh - 56px) !important;
                padding: 8px 4px !important;
                background: #0f0f0f !important;
                border-right: 1px solid #1f1f1f !important;
                z-index: 2500 !important;
                box-sizing: border-box !important;
            }

            body.channel-home-style .sidebar-inner {
                display: flex !important;
                flex-direction: column !important;
                align-items: center !important;
                gap: 6px !important;
            }

            body.channel-home-style .sidebar-item {
                width: 64px !important;
                min-height: 64px !important;
                padding: 8px 4px !important;
                border-radius: 10px !important;
                display: flex !important;
                flex-direction: column !important;
                align-items: center !important;
                justify-content: center !important;
                gap: 5px !important;
                color: #fff !important;
                text-decoration: none !important;
                cursor: pointer !important;
                box-sizing: border-box !important;
                text-align: center !important;
                line-height: 1.1 !important;
            }

            body.channel-home-style .sidebar-item:hover,
            body.channel-home-style .sidebar-item.active {
                background: #272727 !important;
            }

            body.channel-home-style .sidebar-icon {
                width: 24px !important;
                height: 24px !important;
                display: inline-flex !important;
                align-items: center !important;
                justify-content: center !important;
                font-size: 18px !important;
            }

            body.channel-home-style .sidebar-label {
                width: 100% !important;
                display: block !important;
                font-size: 10.5px !important;
                font-weight: 700 !important;
                line-height: 1.15 !important;
                text-align: center !important;
                word-break: keep-all !important;
                white-space: normal !important;
            }

            body.channel-home-style .sidebar-divider {
                display: none !important;
            }

            body.channel-home-style .main,
            body.channel-home-style main,
            body.channel-home-style #main {
                margin-left: 72px !important;
                padding: 28px 24px 48px !important;
                min-height: calc(100vh - 56px) !important;
                width: auto !important;
                max-width: none !important;
                box-sizing: border-box !important;
                background: #0f0f0f !important;
            }

            .channel-unified-wrap {
                width: 100% !important;
                max-width: none !important;
                margin: 0 !important;
            }

            .channel-unified-header {
                display: flex !important;
                align-items: center !important;
                justify-content: space-between !important;
                gap: 20px !important;
                margin-bottom: 28px !important;
                padding-bottom: 24px !important;
                border-bottom: 1px solid #242424 !important;
            }

            .channel-unified-profile {
                display: flex !important;
                align-items: center !important;
                gap: 18px !important;
                min-width: 0 !important;
            }

            .channel-unified-avatar {
                width: 96px !important;
                height: 96px !important;
                border-radius: 50% !important;
                background: #ff6a21 !important;
                color: #fff !important;
                display: flex !important;
                align-items: center !important;
                justify-content: center !important;
                font-size: 40px !important;
                font-weight: 800 !important;
                flex: 0 0 auto !important;
                overflow: hidden !important;
            }

            .channel-unified-avatar img {
                width: 100% !important;
                height: 100% !important;
                object-fit: cover !important;
                display: block !important;
            }

            .channel-unified-kicker {
                margin: 0 0 8px !important;
                color: #aaa !important;
                font-size: 14px !important;
                font-weight: 600 !important;
            }

            .channel-unified-title {
                margin: 0 !important;
                color: #fff !important;
                font-size: 34px !important;
                font-weight: 900 !important;
                letter-spacing: -0.04em !important;
                line-height: 1.15 !important;
            }

            .channel-unified-meta {
                margin: 10px 0 0 !important;
                color: #aaa !important;
                font-size: 14px !important;
            }

            .channel-unified-upload-btn {
                height: 40px !important;
                padding: 0 18px !important;
                border-radius: 999px !important;
                background: #fff !important;
                color: #111 !important;
                text-decoration: none !important;
                display: inline-flex !important;
                align-items: center !important;
                justify-content: center !important;
                font-weight: 800 !important;
                white-space: nowrap !important;
            }

            .channel-unified-section-title {
                margin: 0 0 18px !important;
                color: #fff !important;
                font-size: 22px !important;
                font-weight: 900 !important;
            }

            .channel-unified-grid {
                width: 100% !important;
                display: grid !important;
                grid-template-columns: repeat(auto-fill, minmax(320px, 1fr)) !important;
                gap: 28px 18px !important;
                align-items: start !important;
            }

            .channel-unified-card {
                background: transparent !important;
                border: none !important;
                box-shadow: none !important;
                display: flex !important;
                flex-direction: column !important;
                gap: 10px !important;
            }

            .channel-unified-card .card-link {
                color: inherit !important;
                text-decoration: none !important;
                display: block !important;
            }

            .channel-unified-card .thumbnail-wrap {
                position: relative !important;
                width: 100% !important;
                aspect-ratio: 16 / 9 !important;
                border-radius: 12px !important;
                overflow: hidden !important;
                background: #181818 !important;
            }

            .channel-unified-card .thumbnail-image {
                width: 100% !important;
                height: 100% !important;
                display: block !important;
                object-fit: cover !important;
            }

            .channel-unified-card .duration {
                position: absolute !important;
                right: 8px !important;
                bottom: 8px !important;
                padding: 3px 6px !important;
                border-radius: 5px !important;
                background: rgba(0, 0, 0, 0.78) !important;
                color: #fff !important;
                font-size: 12px !important;
                font-weight: 700 !important;
            }

            .channel-unified-card .meta {
                display: flex !important;
                align-items: flex-start !important;
                gap: 12px !important;
            }

            .channel-unified-card .avatar-image {
                width: 36px !important;
                height: 36px !important;
                border-radius: 50% !important;
                object-fit: cover !important;
                flex: 0 0 auto !important;
                background: #272727 !important;
            }

            .channel-unified-card .text {
                min-width: 0 !important;
                flex: 1 !important;
            }

            .channel-unified-card h3 {
                margin: 0 0 5px !important;
                color: #fff !important;
                font-size: 16px !important;
                font-weight: 800 !important;
                line-height: 1.35 !important;
                display: -webkit-box !important;
                -webkit-line-clamp: 2 !important;
                -webkit-box-orient: vertical !important;
                overflow: hidden !important;
            }

            .channel-unified-card .channel-name,
            .channel-unified-card .video-info,
            .channel-unified-category {
                margin: 0 !important;
                color: #aaa !important;
                font-size: 13px !important;
                line-height: 1.45 !important;
            }

            .channel-unified-actions {
                display: flex !important;
                align-items: center !important;
                gap: 8px !important;
                margin-left: 48px !important;
                flex-wrap: wrap !important;
            }

            .channel-unified-btn {
                min-height: 32px !important;
                padding: 0 12px !important;
                border: 1px solid #303030 !important;
                border-radius: 999px !important;
                background: #181818 !important;
                color: #fff !important;
                display: inline-flex !important;
                align-items: center !important;
                justify-content: center !important;
                text-decoration: none !important;
                font-size: 12px !important;
                cursor: pointer !important;
            }

            .channel-unified-btn:hover {
                background: #272727 !important;
            }

            .channel-unified-btn.danger {
                color: #ff9a9a !important;
            }

            .channel-unified-empty {
                min-height: 320px !important;
                display: flex !important;
                flex-direction: column !important;
                align-items: center !important;
                justify-content: center !important;
                gap: 12px !important;
                color: #aaa !important;
                text-align: center !important;
                border: 1px dashed #303030 !important;
                border-radius: 20px !important;
                background: #121212 !important;
            }

            .channel-unified-empty h2 {
                margin: 0 !important;
                color: #fff !important;
                font-size: 24px !important;
            }

            .channel-unified-empty p {
                margin: 0 !important;
                color: #aaa !important;
                font-size: 14px !important;
            }

            .channel-unified-empty a {
                margin-top: 8px !important;
                height: 38px !important;
                padding: 0 16px !important;
                border-radius: 999px !important;
                background: #fff !important;
                color: #111 !important;
                display: inline-flex !important;
                align-items: center !important;
                justify-content: center !important;
                text-decoration: none !important;
                font-weight: 800 !important;
            }

            body.channel-home-style .studio-row,
            body.channel-home-style .studio-table,
            body.channel-home-style .studio-header,
            body.channel-home-style .channel-video-grid,
            body.channel-home-style #channelVideoGrid,
            body.channel-home-style #myVideoGrid {
                display: none !important;
            }

            @media (max-width: 900px) {
                body.channel-home-style .topbar-center {
                    display: none !important;
                }

                body.channel-home-style .sidebar,
                body.channel-home-style #sidebar {
                    transform: translateX(-100%) !important;
                    width: 240px !important;
                    padding: 12px !important;
                }

                body.channel-home-style .sidebar.is-mobile-open {
                    transform: translateX(0) !important;
                }

                body.channel-home-style .sidebar .sidebar-item {
                    width: 100% !important;
                    min-height: 42px !important;
                    padding: 0 12px !important;
                    flex-direction: row !important;
                    justify-content: flex-start !important;
                    gap: 18px !important;
                    text-align: left !important;
                }

                body.channel-home-style .sidebar .sidebar-label {
                    width: auto !important;
                    font-size: 14px !important;
                    text-align: left !important;
                    white-space: nowrap !important;
                }

                body.channel-home-style .main,
                body.channel-home-style main,
                body.channel-home-style #main {
                    margin-left: 0 !important;
                    padding: 24px 16px 40px !important;
                }

                .channel-unified-header {
                    flex-direction: column !important;
                    align-items: flex-start !important;
                }

                .channel-unified-profile {
                    align-items: flex-start !important;
                }

                .channel-unified-avatar {
                    width: 72px !important;
                    height: 72px !important;
                    font-size: 32px !important;
                }

                .channel-unified-title {
                    font-size: 28px !important;
                }

                .channel-unified-grid {
                    grid-template-columns: 1fr !important;
                }
            }

            /* ── 라이트 모드 오버라이드 ── */
            html[data-theme="light"] body.channel-home-style {
                background: #f9f9f9 !important;
                color: #0f0f0f !important;
            }
            html[data-theme="light"] body.channel-home-style .topbar,
            html[data-theme="light"] body.channel-home-style #topbar {
                background: #ffffff !important;
                border-bottom-color: #e5e5e5 !important;
            }
            html[data-theme="light"] body.channel-home-style .sidebar,
            html[data-theme="light"] body.channel-home-style #sidebar {
                background: #f9f9f9 !important;
                border-right-color: #e5e5e5 !important;
            }
            html[data-theme="light"] body.channel-home-style .sidebar-item {
                color: #0f0f0f !important;
            }
            html[data-theme="light"] body.channel-home-style .sidebar-item:hover,
            html[data-theme="light"] body.channel-home-style .sidebar-item.active {
                background: #e5e5e5 !important;
            }
            html[data-theme="light"] body.channel-home-style .sidebar-label {
                color: #0f0f0f !important;
            }
            html[data-theme="light"] body.channel-home-style .main,
            html[data-theme="light"] body.channel-home-style main,
            html[data-theme="light"] body.channel-home-style #main {
                background: #f9f9f9 !important;
            }
            html[data-theme="light"] .channel-unified-header {
                border-bottom-color: #e5e5e5 !important;
            }
            html[data-theme="light"] .channel-unified-title {
                color: #0f0f0f !important;
            }
            html[data-theme="light"] .channel-unified-kicker,
            html[data-theme="light"] .channel-unified-sub {
                color: #606060 !important;
            }
            html[data-theme="light"] .channel-unified-card {
                background: #ffffff !important;
                border-color: #e5e5e5 !important;
            }
            html[data-theme="light"] .channel-unified-card .text h3,
            html[data-theme="light"] .channel-unified-card .video-title {
                color: #0f0f0f !important;
            }
            html[data-theme="light"] .channel-unified-card .channel-name,
            html[data-theme="light"] .channel-unified-card .video-info,
            html[data-theme="light"] .channel-unified-category {
                color: #606060 !important;
            }
            html[data-theme="light"] .channel-unified-btn {
                background: #f2f2f2 !important;
                border-color: #cccccc !important;
                color: #0f0f0f !important;
            }
            html[data-theme="light"] .channel-unified-btn:hover {
                background: #e5e5e5 !important;
            }
            html[data-theme="light"] .channel-unified-empty {
                background: #f9f9f9 !important;
                border-color: #e5e5e5 !important;
                color: #606060 !important;
            }
            html[data-theme="light"] .channel-unified-empty h2 {
                color: #0f0f0f !important;
            }
            html[data-theme="light"] .channel-unified-empty p {
                color: #606060 !important;
            }
        `;

        document.head.appendChild(style);
    }

    function getAuthUserInfo() {
        try {
            const me = typeof getAuthMe === "function" ? getAuthMe() : null;

            if (me && me.user) {
                return {
                    channelName:
                        me.user.channelName ||
                        me.user.nickname ||
                        me.user.username ||
                        "내 채널",
                    profileImage: me.user.profileImage || "",
                    username: me.user.username || "",
                    loggedIn: Boolean(me.loggedIn)
                };
            }
        } catch {}

        return {
            channelName: "내 채널",
            profileImage: "",
            username: "",
            loggedIn: false
        };
    }

    function ensureMain() {
        return (
            document.getElementById("main") ||
            document.querySelector("main") ||
            document.querySelector(".main") ||
            document.body
        );
    }

    async function renderChannelUnified() {
        if (!isChannelPage()) return;
        if (document.getElementById("channelVideoGrid")) return;

        document.body.classList.add("channel-home-style");

        ensureChannelStyle();

        const main = ensureMain();
        const user = getAuthUserInfo();
        const videos = await getChannelVideos();

        let wrap = document.getElementById("channelUnifiedWrap");

        if (!wrap) {
            wrap = document.createElement("section");
            wrap.id = "channelUnifiedWrap";
            wrap.className = "channel-unified-wrap";
            main.appendChild(wrap);
        }

        const firstLetter = String(user.channelName || "T").trim().charAt(0).toUpperCase() || "T";

        const avatarMarkup = user.profileImage
            ? `<img src="${safeEscape(user.profileImage)}" alt="${safeEscape(user.channelName)}" />`
            : safeEscape(firstLetter);

        const gridMarkup = videos.length
            ? `
                <h2 class="channel-unified-section-title">내가 올린 영상</h2>
                <div class="channel-unified-grid">
                    ${videos.map(createChannelVideoCard).join("")}
                </div>
            `
            : `
                <div class="channel-unified-empty">
                    <h2>아직 업로드한 영상이 없습니다.</h2>
                    <p>첫 영상을 업로드해서 채널을 채워보세요.</p>
                    <a href="upload.html">영상 업로드</a>
                </div>
            `;

        wrap.innerHTML = `
            <div class="channel-unified-header">
                <div class="channel-unified-profile">
                    <div class="channel-unified-avatar">${avatarMarkup}</div>

                    <div>
                        <p class="channel-unified-kicker">내 채널</p>
                        <h1 class="channel-unified-title">${safeEscape(user.channelName)}</h1>
                        <p class="channel-unified-meta">
                            업로드한 영상 ${safeFormatCount(videos.length)}개
                            ${user.username ? ` · @${safeEscape(user.username)}` : ""}
                        </p>
                    </div>
                </div>

                <a href="upload.html" class="channel-unified-upload-btn">영상 업로드</a>
            </div>

            ${gridMarkup}
        `;

        bindDeleteButtons(wrap);
        markSidebarActive();
        bindChannelMenuButton();
    }

    function removeLocalUploadedVideo(videoId) {
        const raw = localStorage.getItem("youtube_clone_local_uploaded_videos");

        if (!raw) return;

        try {
            const parsed = JSON.parse(raw);

            if (!Array.isArray(parsed)) return;

            const filtered = parsed.filter((video) => {
                const id = video.id ?? video.videoId ?? video._id;
                return String(id) !== String(videoId);
            });

            localStorage.setItem("youtube_clone_local_uploaded_videos", JSON.stringify(filtered));
        } catch {}
    }

    function bindDeleteButtons(root) {
        const buttons = root.querySelectorAll("[data-channel-delete-id]");

        buttons.forEach((button) => {
            if (button.dataset.channelDeleteBound === "true") return;

            button.dataset.channelDeleteBound = "true";

            button.addEventListener("click", async () => {
                const videoId = button.dataset.channelDeleteId;

                const ok = window.confirm("이 영상을 삭제할까요?");
                if (!ok) return;

                try {
                    if (typeof deleteVideoById === "function") {
                        await deleteVideoById(videoId);
                    }
                } catch {}

                removeLocalUploadedVideo(videoId);

                if (typeof showToast === "function") {
                    showToast("영상이 삭제되었습니다.");
                }

                renderChannelUnified();
            });
        });
    }

    function markSidebarActive() {
        const sidebar = document.getElementById("sidebar") || document.querySelector(".sidebar");
        if (!sidebar) return;

        sidebar.querySelectorAll(".sidebar-item").forEach((item) => {
            const label = item.querySelector(".sidebar-label")?.textContent.trim() || item.textContent.trim();
            item.classList.toggle("active", label.includes("채널"));
        });
    }

    function bindChannelMenuButton() {
        const menuBtn = document.getElementById("menuBtn");
        const sidebar = document.getElementById("sidebar") || document.querySelector(".sidebar");

        if (!menuBtn || !sidebar) return;

        menuBtn.onclick = function () {
            if (window.innerWidth <= 900) {
                sidebar.classList.toggle("is-mobile-open");
                document.body.classList.toggle("sidebar-mobile-open");
            }
        };
    }

    function bootChannelPatch() {
        if (!isChannelPage()) return;

        renderChannelUnified();

        setTimeout(renderChannelUnified, 300);
        setTimeout(renderChannelUnified, 900);
        setTimeout(renderChannelUnified, 1600);
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", bootChannelPatch);
    } else {
        bootChannelPatch();
    }
})();
/* =========================================================
   보관함/내 채널 사이드바 햄버거 확장 복구 패치 v1
   - 홈 화면은 건드리지 않음
   - history / saved / liked / channel 에서만 72px ↔ 240px 전환
========================================================= */

(function () {
    const PATCH_ID = "subpage-sidebar-expand-fix-v1";

    function getPageName() {
        const page = document.body.dataset.page || "";
        const file = window.location.pathname.split("/").pop() || "";

        if (page) return page;
        if (file.includes("history")) return "history";
        if (file.includes("saved")) return "saved";
        if (file.includes("liked")) return "liked";
        if (file.includes("channel")) return "channel";

        return "";
    }

    function isTargetPage() {
        return ["history", "saved", "liked", "channel"].includes(getPageName());
    }

    function getMenuButton() {
        return (
            document.getElementById("menuBtn") ||
            document.querySelector(".menu-btn") ||
            document.querySelector('[aria-label="메뉴"]')
        );
    }

    function getSidebar() {
        return (
            document.getElementById("sidebar") ||
            document.querySelector(".sidebar")
        );
    }

    function getMain() {
        return (
            document.getElementById("main") ||
            document.querySelector("main") ||
            document.querySelector(".main")
        );
    }

    function ensureStyle() {
        if (document.getElementById(PATCH_ID + "-style")) return;

        const style = document.createElement("style");
        style.id = PATCH_ID + "-style";
        style.textContent = `
            body.subpage-sidebar-expanded .sidebar,
            body.subpage-sidebar-expanded #sidebar {
                width: 240px !important;
                padding: 12px !important;
                box-sizing: border-box !important;
            }

            body.subpage-sidebar-expanded .sidebar-inner {
                align-items: stretch !important;
            }

            body.subpage-sidebar-expanded .sidebar-item {
                width: 100% !important;
                min-height: 42px !important;
                height: 42px !important;
                padding: 0 12px !important;
                flex-direction: row !important;
                align-items: center !important;
                justify-content: flex-start !important;
                gap: 18px !important;
                text-align: left !important;
            }

            body.subpage-sidebar-expanded .sidebar-icon {
                width: 24px !important;
                height: 24px !important;
                min-width: 24px !important;
                min-height: 24px !important;
                flex: 0 0 24px !important;
                margin: 0 !important;
            }

            body.subpage-sidebar-expanded .sidebar-label {
                width: auto !important;
                min-height: auto !important;
                font-size: 14px !important;
                line-height: 1.2 !important;
                white-space: nowrap !important;
                text-align: left !important;
                justify-content: flex-start !important;
            }

            body.subpage-sidebar-expanded .main,
            body.subpage-sidebar-expanded main,
            body.subpage-sidebar-expanded #main {
                margin-left: 240px !important;
            }

            body.subpage-sidebar-collapsed .sidebar,
            body.subpage-sidebar-collapsed #sidebar {
                width: 72px !important;
                padding: 8px 4px !important;
                box-sizing: border-box !important;
            }

            body.subpage-sidebar-collapsed .sidebar-inner {
                align-items: center !important;
            }

            body.subpage-sidebar-collapsed .sidebar-item {
                width: 64px !important;
                min-height: 64px !important;
                height: auto !important;
                padding: 7px 4px !important;
                flex-direction: column !important;
                align-items: center !important;
                justify-content: center !important;
                gap: 5px !important;
                text-align: center !important;
            }

            body.subpage-sidebar-collapsed .sidebar-icon {
                width: 24px !important;
                height: 24px !important;
                min-width: 24px !important;
                min-height: 24px !important;
                flex: 0 0 24px !important;
                margin: 0 auto !important;
            }

            body.subpage-sidebar-collapsed .sidebar-label {
                width: 100% !important;
                min-height: 24px !important;
                font-size: 10px !important;
                line-height: 1.15 !important;
                white-space: normal !important;
                text-align: center !important;
                justify-content: center !important;
                word-break: keep-all !important;
            }

            body.subpage-sidebar-collapsed .main,
            body.subpage-sidebar-collapsed main,
            body.subpage-sidebar-collapsed #main {
                margin-left: 72px !important;
            }

            @media (max-width: 900px) {
                body.subpage-sidebar-expanded .main,
                body.subpage-sidebar-expanded main,
                body.subpage-sidebar-expanded #main,
                body.subpage-sidebar-collapsed .main,
                body.subpage-sidebar-collapsed main,
                body.subpage-sidebar-collapsed #main {
                    margin-left: 0 !important;
                }

                body.subpage-sidebar-expanded .sidebar,
                body.subpage-sidebar-expanded #sidebar {
                    transform: translateX(0) !important;
                    width: 240px !important;
                }

                body.subpage-sidebar-collapsed .sidebar,
                body.subpage-sidebar-collapsed #sidebar {
                    transform: translateX(-100%) !important;
                    width: 240px !important;
                }
            }
        `;

        document.head.appendChild(style);
    }

    function applySidebarState(expanded) {
        const sidebar = getSidebar();
        const main = getMain();

        if (!sidebar) return;

        document.body.classList.toggle("subpage-sidebar-expanded", expanded);
        document.body.classList.toggle("subpage-sidebar-collapsed", !expanded);

        sidebar.classList.remove("collapsed");
        sidebar.classList.toggle("is-mobile-open", expanded);

        if (expanded) {
            sidebar.style.setProperty("width", "240px", "important");
            sidebar.style.setProperty("padding", "12px", "important");

            if (main && window.innerWidth > 900) {
                main.style.setProperty("margin-left", "240px", "important");
            }

            sidebar.querySelectorAll(".sidebar-item").forEach((item) => {
                item.style.setProperty("width", "100%", "important");
                item.style.setProperty("min-height", "42px", "important");
                item.style.setProperty("height", "42px", "important");
                item.style.setProperty("padding", "0 12px", "important");
                item.style.setProperty("flex-direction", "row", "important");
                item.style.setProperty("align-items", "center", "important");
                item.style.setProperty("justify-content", "flex-start", "important");
                item.style.setProperty("gap", "18px", "important");
                item.style.setProperty("text-align", "left", "important");
            });

            sidebar.querySelectorAll(".sidebar-label").forEach((label) => {
                label.style.setProperty("width", "auto", "important");
                label.style.setProperty("min-height", "auto", "important");
                label.style.setProperty("font-size", "14px", "important");
                label.style.setProperty("white-space", "nowrap", "important");
                label.style.setProperty("text-align", "left", "important");
                label.style.setProperty("justify-content", "flex-start", "important");
            });
        } else {
            sidebar.style.setProperty("width", "72px", "important");
            sidebar.style.setProperty("padding", "8px 4px", "important");

            if (main && window.innerWidth > 900) {
                main.style.setProperty("margin-left", "72px", "important");
            }

            sidebar.querySelectorAll(".sidebar-item").forEach((item) => {
                item.style.setProperty("width", "64px", "important");
                item.style.setProperty("min-height", "64px", "important");
                item.style.removeProperty("height");
                item.style.setProperty("padding", "7px 4px", "important");
                item.style.setProperty("flex-direction", "column", "important");
                item.style.setProperty("align-items", "center", "important");
                item.style.setProperty("justify-content", "center", "important");
                item.style.setProperty("gap", "5px", "important");
                item.style.setProperty("text-align", "center", "important");
            });

            sidebar.querySelectorAll(".sidebar-label").forEach((label) => {
                label.style.setProperty("width", "100%", "important");
                label.style.setProperty("min-height", "24px", "important");
                label.style.setProperty("font-size", "10px", "important");
                label.style.setProperty("white-space", "normal", "important");
                label.style.setProperty("text-align", "center", "important");
                label.style.setProperty("justify-content", "center", "important");
            });
        }
    }

    function toggleSidebar() {
        const isExpanded = document.body.classList.contains("subpage-sidebar-expanded");
        applySidebarState(!isExpanded);
    }

    function bindMenuButton() {
        if (document.body.dataset.subpageSidebarExpandBound === "true") return;
        document.body.dataset.subpageSidebarExpandBound = "true";

        document.addEventListener(
            "click",
            function (event) {
                if (!isTargetPage()) return;

                const menuBtn = event.target.closest("#menuBtn, .menu-btn, [aria-label='메뉴']");
                if (!menuBtn) return;

                event.preventDefault();
                event.stopPropagation();
                event.stopImmediatePropagation();

                toggleSidebar();
            },
            true
        );
    }

    function boot() {
        if (!isTargetPage()) return;

        ensureStyle();
        bindMenuButton();

        if (
            !document.body.classList.contains("subpage-sidebar-expanded") &&
            !document.body.classList.contains("subpage-sidebar-collapsed")
        ) {
            applySidebarState(false);
        }
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", boot);
    } else {
        boot();
    }

    setTimeout(boot, 300);
    setTimeout(boot, 1000);
})();
/* =========================================================
   전체 페이지 사이드바 통합 패치 v1
   - 메인 화면 사이드바 기준으로 모든 페이지 통일
   - upload.html 제외
   - history / saved / liked / channel / watch / edit / home 공통 적용
========================================================= */

(function () {
    const PATCH_ID = "global-main-sidebar-unify-v1";
    const STORAGE_KEY = "youtube_clone_sidebar_expanded";

    function getPageName() {
        const page = document.body.dataset.page || "";
        const file = window.location.pathname.split("/").pop() || "index.html";

        if (page) return page;
        if (file.includes("upload")) return "upload";
        if (file.includes("history")) return "history";
        if (file.includes("saved")) return "saved";
        if (file.includes("liked")) return "liked";
        if (file.includes("channel")) return "channel";
        if (file.includes("watch")) return "watch";
        if (file.includes("edit")) return "edit";
        if (file.includes("login")) return "login";
        if (file.includes("signup")) return "signup";

        return "home";
    }

    function shouldSkipPage() {
        const page = getPageName();

        return page === "upload" || page === "login" || page === "signup";
    }

    function getMenuButton() {
        return (
            document.getElementById("menuBtn") ||
            document.querySelector(".menu-btn") ||
            document.querySelector('[aria-label="메뉴"]')
        );
    }

    function getSidebar() {
        return (
            document.getElementById("sidebar") ||
            document.querySelector(".sidebar")
        );
    }

    function getMain() {
        return (
            document.getElementById("main") ||
            document.querySelector("main") ||
            document.querySelector(".main") ||
            document.querySelector(".user-channel-content")
        );
    }

    function getActiveLabel() {
        const page = getPageName();

        if (page === "history") return "시청 기록";
        if (page === "saved") return "저장한 영상";
        if (page === "liked") return "좋아요한 영상";
        if (page === "channel") return "내 채널";
        if (page === "upload") return "업로드";

        return "홈";
    }

    function normalizeSidebarText() {
        const sidebar = getSidebar();
        if (!sidebar) return;

        sidebar.querySelectorAll(".sidebar-label").forEach((label) => {
            const text = label.textContent.replace(/\s+/g, " ").trim();

            if (text === "좋아요 표시한 동영상") {
                label.textContent = "좋아요한 영상";
            }

            if (text === "시청기록") {
                label.textContent = "시청 기록";
            }
        });
    }

    function markActiveMenu() {
        const sidebar = getSidebar();
        if (!sidebar) return;

        const activeLabel = getActiveLabel();

        sidebar.querySelectorAll(".sidebar-item").forEach((item) => {
            const label = item.querySelector(".sidebar-label")?.textContent.trim() || item.textContent.trim();

            item.classList.toggle("active", label.includes(activeLabel));
        });
    }

    function ensureStyle() {
        if (document.getElementById(PATCH_ID + "-style")) return;

        const style = document.createElement("style");
        style.id = PATCH_ID + "-style";

        style.textContent = `
            body.${PATCH_ID} {
                padding-top: 56px !important;
            }

            body.${PATCH_ID} .sidebar,
            body.${PATCH_ID} #sidebar {
                position: fixed !important;
                top: 56px !important;
                left: 0 !important;
                bottom: 0 !important;
                height: calc(100vh - 56px) !important;
                background: #0f0f0f !important;
                border-right: 1px solid #1f1f1f !important;
                box-sizing: border-box !important;
                overflow-x: hidden !important;
                overflow-y: auto !important;
                z-index: 2500 !important;
                transition: width 0.18s ease !important;
                transform: none !important;
            }

            body.${PATCH_ID} .sidebar-inner,
            body.${PATCH_ID} #sidebar .sidebar-inner {
                width: 100% !important;
                display: flex !important;
                flex-direction: column !important;
                box-sizing: border-box !important;
            }

            body.${PATCH_ID} .sidebar-item,
            body.${PATCH_ID} #sidebar .sidebar-item {
                color: #fff !important;
                text-decoration: none !important;
                border-radius: 10px !important;
                box-sizing: border-box !important;
                cursor: pointer !important;
                overflow: hidden !important;
            }

            body.${PATCH_ID} .sidebar-item:hover,
            body.${PATCH_ID} .sidebar-item.active,
            body.${PATCH_ID} #sidebar .sidebar-item:hover,
            body.${PATCH_ID} #sidebar .sidebar-item.active {
                background: #272727 !important;
            }

            body.${PATCH_ID} .sidebar-icon,
            body.${PATCH_ID} #sidebar .sidebar-icon {
                width: 24px !important;
                height: 24px !important;
                min-width: 24px !important;
                min-height: 24px !important;
                display: inline-flex !important;
                align-items: center !important;
                justify-content: center !important;
                font-size: 18px !important;
                line-height: 1 !important;
                flex: 0 0 24px !important;
            }

            body.${PATCH_ID} .sidebar-icon svg,
            body.${PATCH_ID} .sidebar-item svg,
            body.${PATCH_ID} #sidebar .sidebar-icon svg,
            body.${PATCH_ID} #sidebar .sidebar-item svg {
                width: 22px !important;
                height: 22px !important;
                display: block !important;
            }

            body.${PATCH_ID} .sidebar-label,
            body.${PATCH_ID} #sidebar .sidebar-label {
                color: #fff !important;
                font-weight: 700 !important;
                box-sizing: border-box !important;
            }

            body.${PATCH_ID} .sidebar-divider,
            body.${PATCH_ID} #sidebar .sidebar-divider {
                height: 1px !important;
                background: #242424 !important;
                flex: 0 0 auto !important;
            }

            body.${PATCH_ID}.sidebar-unified-collapsed .sidebar,
            body.${PATCH_ID}.sidebar-unified-collapsed #sidebar {
                width: 72px !important;
                padding: 8px 4px !important;
            }

            body.${PATCH_ID}.sidebar-unified-collapsed .sidebar-inner,
            body.${PATCH_ID}.sidebar-unified-collapsed #sidebar .sidebar-inner {
                align-items: center !important;
                gap: 4px !important;
            }

            body.${PATCH_ID}.sidebar-unified-collapsed .sidebar-item,
            body.${PATCH_ID}.sidebar-unified-collapsed #sidebar .sidebar-item {
                width: 64px !important;
                min-height: 64px !important;
                height: auto !important;
                padding: 7px 4px !important;
                display: flex !important;
                flex-direction: column !important;
                align-items: center !important;
                justify-content: center !important;
                gap: 5px !important;
                text-align: center !important;
                line-height: 1 !important;
                white-space: normal !important;
            }

            body.${PATCH_ID}.sidebar-unified-collapsed .sidebar-icon,
            body.${PATCH_ID}.sidebar-unified-collapsed #sidebar .sidebar-icon {
                margin: 0 auto !important;
            }

            body.${PATCH_ID}.sidebar-unified-collapsed .sidebar-label,
            body.${PATCH_ID}.sidebar-unified-collapsed #sidebar .sidebar-label {
                width: 100% !important;
                min-height: 24px !important;
                display: flex !important;
                align-items: center !important;
                justify-content: center !important;
                font-size: 10px !important;
                line-height: 1.15 !important;
                text-align: center !important;
                white-space: normal !important;
                word-break: keep-all !important;
                overflow: hidden !important;
            }

            body.${PATCH_ID}.sidebar-unified-collapsed .sidebar-divider,
            body.${PATCH_ID}.sidebar-unified-collapsed #sidebar .sidebar-divider {
                width: 56px !important;
                margin: 4px 0 !important;
            }

            body.${PATCH_ID}.sidebar-unified-collapsed .main,
            body.${PATCH_ID}.sidebar-unified-collapsed main,
            body.${PATCH_ID}.sidebar-unified-collapsed #main,
            body.${PATCH_ID}.sidebar-unified-collapsed .user-channel-content {
                margin-left: 72px !important;
            }

            body.${PATCH_ID}.sidebar-unified-expanded .sidebar,
            body.${PATCH_ID}.sidebar-unified-expanded #sidebar {
                width: 240px !important;
                padding: 12px !important;
            }

            body.${PATCH_ID}.sidebar-unified-expanded .sidebar-inner,
            body.${PATCH_ID}.sidebar-unified-expanded #sidebar .sidebar-inner {
                align-items: stretch !important;
                gap: 4px !important;
            }

            body.${PATCH_ID}.sidebar-unified-expanded .sidebar-item,
            body.${PATCH_ID}.sidebar-unified-expanded #sidebar .sidebar-item {
                width: 100% !important;
                min-height: 42px !important;
                height: 42px !important;
                padding: 0 12px !important;
                display: flex !important;
                flex-direction: row !important;
                align-items: center !important;
                justify-content: flex-start !important;
                gap: 18px !important;
                text-align: left !important;
                line-height: 1.2 !important;
                white-space: nowrap !important;
            }

            body.${PATCH_ID}.sidebar-unified-expanded .sidebar-icon,
            body.${PATCH_ID}.sidebar-unified-expanded #sidebar .sidebar-icon {
                margin: 0 !important;
            }

            body.${PATCH_ID}.sidebar-unified-expanded .sidebar-label,
            body.${PATCH_ID}.sidebar-unified-expanded #sidebar .sidebar-label {
                width: auto !important;
                min-height: auto !important;
                display: inline-flex !important;
                align-items: center !important;
                justify-content: flex-start !important;
                font-size: 14px !important;
                line-height: 1.2 !important;
                text-align: left !important;
                white-space: nowrap !important;
                overflow: hidden !important;
                text-overflow: ellipsis !important;
            }

            body.${PATCH_ID}.sidebar-unified-expanded .sidebar-divider,
            body.${PATCH_ID}.sidebar-unified-expanded #sidebar .sidebar-divider {
                width: 100% !important;
                margin: 8px 0 !important;
            }

            body.${PATCH_ID}.sidebar-unified-expanded .main,
            body.${PATCH_ID}.sidebar-unified-expanded main,
            body.${PATCH_ID}.sidebar-unified-expanded #main,
            body.${PATCH_ID}.sidebar-unified-expanded .user-channel-content {
                margin-left: 240px !important;
            }

            @media (max-width: 900px) {
                body.${PATCH_ID}.sidebar-unified-collapsed .sidebar,
                body.${PATCH_ID}.sidebar-unified-collapsed #sidebar {
                    width: 240px !important;
                    transform: translateX(-100%) !important;
                }

                body.${PATCH_ID}.sidebar-unified-expanded .sidebar,
                body.${PATCH_ID}.sidebar-unified-expanded #sidebar {
                    width: 240px !important;
                    transform: translateX(0) !important;
                }

                body.${PATCH_ID}.sidebar-unified-collapsed .main,
                body.${PATCH_ID}.sidebar-unified-collapsed main,
                body.${PATCH_ID}.sidebar-unified-collapsed #main,
                body.${PATCH_ID}.sidebar-unified-collapsed .user-channel-content,
                body.${PATCH_ID}.sidebar-unified-expanded .main,
                body.${PATCH_ID}.sidebar-unified-expanded main,
                body.${PATCH_ID}.sidebar-unified-expanded #main,
                body.${PATCH_ID}.sidebar-unified-expanded .user-channel-content {
                    margin-left: 0 !important;
                }
            }

            /* ── 라이트 모드 오버라이드 ── */
            html[data-theme="light"] body.${PATCH_ID} .sidebar,
            html[data-theme="light"] body.${PATCH_ID} #sidebar {
                background: #f9f9f9 !important;
                border-right-color: #e5e5e5 !important;
            }

            html[data-theme="light"] body.${PATCH_ID} .sidebar-item,
            html[data-theme="light"] body.${PATCH_ID} #sidebar .sidebar-item {
                color: #0f0f0f !important;
            }

            html[data-theme="light"] body.${PATCH_ID} .sidebar-item:hover,
            html[data-theme="light"] body.${PATCH_ID} .sidebar-item.active,
            html[data-theme="light"] body.${PATCH_ID} #sidebar .sidebar-item:hover,
            html[data-theme="light"] body.${PATCH_ID} #sidebar .sidebar-item.active {
                background: #e5e5e5 !important;
            }

            html[data-theme="light"] body.${PATCH_ID} .sidebar-label,
            html[data-theme="light"] body.${PATCH_ID} #sidebar .sidebar-label {
                color: #0f0f0f !important;
            }

            html[data-theme="light"] body.${PATCH_ID} .sidebar-icon svg,
            html[data-theme="light"] body.${PATCH_ID} #sidebar .sidebar-icon svg {
                fill: #0f0f0f;
            }

            html[data-theme="light"] body.${PATCH_ID} .sidebar-divider,
            html[data-theme="light"] body.${PATCH_ID} #sidebar .sidebar-divider {
                background: #e5e5e5 !important;
            }
        `;

        document.head.appendChild(style);
    }

    function setInlineState(expanded) {
        const sidebar = getSidebar();
        const main = getMain();

        if (!sidebar) return;

        document.body.classList.add(PATCH_ID);
        document.body.classList.toggle("sidebar-unified-expanded", expanded);
        document.body.classList.toggle("sidebar-unified-collapsed", !expanded);

        sidebar.classList.remove("collapsed");
        sidebar.classList.toggle("is-collapsed", !expanded);
        sidebar.classList.toggle("is-mobile-open", expanded);

        sidebar.style.setProperty("width", expanded ? "240px" : "72px", "important");
        sidebar.style.setProperty("padding", expanded ? "12px" : "8px 4px", "important");
        sidebar.style.setProperty("box-sizing", "border-box", "important");

        if (main && window.innerWidth > 900) {
            main.style.setProperty("margin-left", expanded ? "240px" : "72px", "important");
        }

        sidebar.querySelectorAll(".sidebar-item").forEach((item) => {
            item.style.setProperty("display", "flex", "important");
            item.style.setProperty("box-sizing", "border-box", "important");

            if (expanded) {
                item.style.setProperty("width", "100%", "important");
                item.style.setProperty("min-height", "42px", "important");
                item.style.setProperty("height", "42px", "important");
                item.style.setProperty("padding", "0 12px", "important");
                item.style.setProperty("flex-direction", "row", "important");
                item.style.setProperty("align-items", "center", "important");
                item.style.setProperty("justify-content", "flex-start", "important");
                item.style.setProperty("gap", "18px", "important");
                item.style.setProperty("text-align", "left", "important");
                item.style.setProperty("white-space", "nowrap", "important");
            } else {
                item.style.setProperty("width", "64px", "important");
                item.style.setProperty("min-height", "64px", "important");
                item.style.removeProperty("height");
                item.style.setProperty("padding", "7px 4px", "important");
                item.style.setProperty("flex-direction", "column", "important");
                item.style.setProperty("align-items", "center", "important");
                item.style.setProperty("justify-content", "center", "important");
                item.style.setProperty("gap", "5px", "important");
                item.style.setProperty("text-align", "center", "important");
                item.style.setProperty("white-space", "normal", "important");
            }
        });

        sidebar.querySelectorAll(".sidebar-icon").forEach((icon) => {
            icon.style.setProperty("width", "24px", "important");
            icon.style.setProperty("height", "24px", "important");
            icon.style.setProperty("min-width", "24px", "important");
            icon.style.setProperty("min-height", "24px", "important");
            icon.style.setProperty("display", "inline-flex", "important");
            icon.style.setProperty("align-items", "center", "important");
            icon.style.setProperty("justify-content", "center", "important");
            icon.style.setProperty("flex", "0 0 24px", "important");
            icon.style.setProperty("margin", expanded ? "0" : "0 auto", "important");
        });

        sidebar.querySelectorAll(".sidebar-label").forEach((label) => {
            if (expanded) {
                label.style.setProperty("width", "auto", "important");
                label.style.setProperty("min-height", "auto", "important");
                label.style.setProperty("display", "inline-flex", "important");
                label.style.setProperty("align-items", "center", "important");
                label.style.setProperty("justify-content", "flex-start", "important");
                label.style.setProperty("font-size", "14px", "important");
                label.style.setProperty("line-height", "1.2", "important");
                label.style.setProperty("text-align", "left", "important");
                label.style.setProperty("white-space", "nowrap", "important");
            } else {
                label.style.setProperty("width", "100%", "important");
                label.style.setProperty("min-height", "24px", "important");
                label.style.setProperty("display", "flex", "important");
                label.style.setProperty("align-items", "center", "important");
                label.style.setProperty("justify-content", "center", "important");
                label.style.setProperty("font-size", "10px", "important");
                label.style.setProperty("line-height", "1.15", "important");
                label.style.setProperty("text-align", "center", "important");
                label.style.setProperty("white-space", "normal", "important");
                label.style.setProperty("word-break", "keep-all", "important");
            }
        });

        localStorage.setItem(STORAGE_KEY, expanded ? "true" : "false");
    }

    function getCurrentExpandedState() {
        return localStorage.getItem(STORAGE_KEY) === "true";
    }

    function toggleSidebar() {
        const next = !document.body.classList.contains("sidebar-unified-expanded");
        setInlineState(next);
    }

    function bindMenuButton() {
        if (document.body.dataset.globalSidebarUnifyBound === "true") return;

        document.body.dataset.globalSidebarUnifyBound = "true";

        document.addEventListener(
            "click",
            function (event) {
                if (shouldSkipPage()) return;

                const menuButton = event.target.closest("#menuBtn, .menu-btn, [aria-label='메뉴']");

                if (!menuButton) return;

                event.preventDefault();
                event.stopPropagation();
                event.stopImmediatePropagation();

                toggleSidebar();
            },
            true
        );
    }

    function bootUnifiedSidebar() {
        if (shouldSkipPage()) return;

        const sidebar = getSidebar();
        const menuButton = getMenuButton();

        if (!sidebar || !menuButton) return;

        ensureStyle();
        normalizeSidebarText();
        markActiveMenu();
        bindMenuButton();
        setInlineState(getCurrentExpandedState());
    }

    function watchSidebarChanges() {
        if (document.body.dataset.globalSidebarUnifyObserver === "true") return;

        document.body.dataset.globalSidebarUnifyObserver = "true";

        let timer = null;

        const observer = new MutationObserver(() => {
            if (shouldSkipPage()) return;

            clearTimeout(timer);

            timer = setTimeout(() => {
                bootUnifiedSidebar();
            }, 60);
        });

        observer.observe(document.body, {
            childList: true,
            subtree: true
        });
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", () => {
            bootUnifiedSidebar();
            watchSidebarChanges();
        });
    } else {
        bootUnifiedSidebar();
        watchSidebarChanges();
    }

    setTimeout(bootUnifiedSidebar, 300);
    setTimeout(bootUnifiedSidebar, 1000);
    setTimeout(bootUnifiedSidebar, 2000);
})();

/* =========================================================
   재생목록 추가 버튼 전역 처리
   ========================================================= */
(function initPlaylistButton() {
    const style = document.createElement("style");
    style.textContent = `
        .card-pl-btn {
            position: absolute;
            bottom: 6px;
            left: 6px;
            display: none;
            align-items: center;
            justify-content: center;
            width: 30px;
            height: 30px;
            border-radius: 6px;
            background: rgba(0,0,0,0.75);
            border: none;
            color: #fff;
            cursor: pointer;
            z-index: 2;
        }
        .thumbnail-wrap:hover .card-pl-btn { display: inline-flex; }
        .pl-dropdown {
            position: fixed;
            background: #1a1a1a;
            border: 1px solid #333;
            border-radius: 10px;
            padding: 8px 0;
            min-width: 200px;
            z-index: 9999;
            box-shadow: 0 8px 24px rgba(0,0,0,0.5);
        }
        .pl-dropdown-item {
            display: flex;
            align-items: center;
            gap: 10px;
            padding: 10px 16px;
            cursor: pointer;
            font-size: 14px;
            color: #f1f1f1;
        }
        .pl-dropdown-item:hover { background: #2a2a2a; }
        .pl-dropdown-item.added { color: #aaa; }
        .pl-dropdown-create { color: #ff4444; font-weight: 600; }
        .pl-dropdown-title { padding: 6px 16px 8px; font-size: 12px; color: #888; border-bottom: 1px solid #2a2a2a; margin-bottom: 4px; }
        html[data-theme="light"] .pl-dropdown { background: #fff; border-color: #e0e0e0; }
        html[data-theme="light"] .pl-dropdown-item { color: #0f0f0f; }
        html[data-theme="light"] .pl-dropdown-item:hover { background: #f5f5f5; }
        html[data-theme="light"] .pl-dropdown-title { color: #666; border-color: #e0e0e0; }
    `;
    document.head.appendChild(style);

    let dropdown = null;
    let activeVideoId = null;

    function closeDropdown() {
        dropdown?.remove();
        dropdown = null;
        activeVideoId = null;
    }

    document.addEventListener("click", e => {
        const btn = e.target.closest(".card-pl-btn");
        if (btn) {
            e.preventDefault();
            e.stopPropagation();
            const videoId = Number(btn.dataset.videoId);
            if (activeVideoId === videoId) { closeDropdown(); return; }
            closeDropdown();
            openPlaylistDropdown(btn, videoId);
            return;
        }
        if (!e.target.closest(".pl-dropdown")) closeDropdown();
    });

    async function openPlaylistDropdown(btn, videoId) {
        const authMe = window.__AUTH_ME__;
        if (!authMe?.loggedIn) { window.location.href = "login.html"; return; }

        activeVideoId = videoId;
        dropdown = document.createElement("div");
        dropdown.className = "pl-dropdown";

        const res = await fetch("/api/playlists/me");
        const playlists = res.ok ? await res.json() : [];

        const rect = btn.getBoundingClientRect();
        dropdown.innerHTML = `<div class="pl-dropdown-title">재생목록에 추가</div>` +
            playlists.map(p => {
                const added = false;
                return `<div class="pl-dropdown-item" data-pl-id="${p.id}">
                    <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M3 5h12v2H3V5zm0 4h12v2H3V9zm0 4h8v2H3v-2zm13 3v-6l5 3-5 3z"/></svg>
                    ${escapeHtml(p.name)}
                </div>`;
            }).join("") +
            `<div class="pl-dropdown-item pl-dropdown-create" id="plDropCreateNew">
                <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M11 6h2v5h5v2h-5v5h-2v-5H6v-2h5V6Z"/></svg>
                새 재생목록 만들기
            </div>`;

        document.body.appendChild(dropdown);

        const top = Math.min(rect.bottom + 4, window.innerHeight - 240);
        const left = Math.min(rect.left, window.innerWidth - 220);
        dropdown.style.top = top + "px";
        dropdown.style.left = left + "px";

        dropdown.querySelectorAll(".pl-dropdown-item[data-pl-id]").forEach(item => {
            item.addEventListener("click", async () => {
                const plId = Number(item.dataset.plId);
                const result = await fetch(`/api/playlists/${plId}/videos`, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ videoId })
                });
                const data = await result.json();
                showToast(data.success ? "재생목록에 추가됐어요." : (data.message || "이미 추가된 영상이에요."));
                closeDropdown();
            });
        });

        dropdown.querySelector("#plDropCreateNew")?.addEventListener("click", async () => {
            closeDropdown();
            const name = prompt("새 재생목록 이름을 입력해줘:");
            if (!name?.trim()) return;
            const res = await fetch("/api/playlists", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ name: name.trim() })
            });
            const pl = await res.json();
            if (pl.success) {
                await fetch(`/api/playlists/${pl.id}/videos`, {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ videoId })
                });
                showToast("재생목록을 만들고 추가했어요.");
            }
        });
    }
})();
