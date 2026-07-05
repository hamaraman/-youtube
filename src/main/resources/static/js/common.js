/* MyTube - common.js : 공통 유틸, API, UI 컴포넌트 */

const DEFAULT_AVATAR = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI4MCIgaGVpZ2h0PSI4MCI+PGNpcmNsZSBjeD0iNDAiIGN5PSI0MCIgcj0iNDAiIGZpbGw9IiM2MTYxNjEiLz48L3N2Zz4=";
const DEFAULT_THUMBNAIL = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxMjgwIiBoZWlnaHQ9IjcyMCI+PHJlY3Qgd2lkdGg9IjEyODAiIGhlaWdodD0iNzIwIiBmaWxsPSIjMjEyMTIxIi8+PHBvbHlnb24gcG9pbnRzPSI1NjAsMzEwIDU2MCw0MTAgNjgwLDM2MCIgZmlsbD0iIzQyNDI0MiIvPjwvc3ZnPg==";

(function () {
    if (typeof Kakao !== "undefined") {
        if (!Kakao.isInitialized()) Kakao.init("d65b87ff6b7b33d1cdd9d141a913ba39");
        return;
    }
    const s = document.createElement("script");
    s.src = "https://t1.kakaocdn.net/kakao_js_sdk/2.7.2/kakao.min.js";
    s.crossOrigin = "anonymous";
    s.onload = () => {
        if (typeof Kakao !== "undefined" && !Kakao.isInitialized())
            Kakao.init("d65b87ff6b7b33d1cdd9d141a913ba39");
    };
    document.head.appendChild(s);
})();

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

    // PUT /api/videos/{id}는 성공 시 수정된 VideoItem을 그대로 반환(success 필드 없음).
    // 따라서 HTTP 200 여부로만 판단해야 함 — result.success를 요구하면 정상 응답도 실패로 오독됨.
    if (!response.ok) {
        throw new Error(result.message || "수정 실패");
    }

    return result;
}

async function replaceVideoThumbnail(id, file) {
    const formData = new FormData();
    formData.append("thumbnailFile", file);

    const response = await fetch(`/api/videos/${id}/thumbnail`, {
        method: "POST",
        body: formData
    });

    const result = await response.json().catch(() => ({}));

    if (!response.ok || !result.success) {
        throw new Error(result.message || "썸네일 교체 실패");
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

function showShareModal(videoId, getCurrentTime, videoData = {}) {
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
    const hasNativeShare = typeof navigator.share === "function";
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
            <div class="share-sns-row">
                <button class="share-sns-btn kakao" id="shareKakaoBtn" type="button">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M12 3C6.477 3 2 6.477 2 10.5c0 2.548 1.516 4.787 3.812 6.187l-.971 3.626a.25.25 0 0 0 .374.28L9.5 18.25c.825.15 1.672.25 2.5.25 5.523 0 10-3.477 10-7.5S17.523 3 12 3z"/></svg>
                    카카오톡
                </button>
                <button class="share-sns-btn twitter" id="shareTwitterBtn" type="button">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-4.714-6.231-5.401 6.231H2.742l7.73-8.835L2.018 2.25H8.056l4.261 5.632 5.927-5.632Zm-1.161 17.52h1.833L7.084 4.126H5.117z"/></svg>
                    X(Twitter)
                </button>
                ${hasNativeShare ? `<button class="share-sns-btn native" id="shareNativeBtn" type="button">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92s2.92-1.31 2.92-2.92-1.31-2.92-2.92-2.92z"/></svg>
                    공유
                </button>` : ""}
            </div>
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

    modal.querySelector("#shareKakaoBtn")?.addEventListener("click", () => {
        if (typeof Kakao === "undefined" || !Kakao.isInitialized()) {
            alert("카카오 SDK 로딩 중이에요. 잠시 후 다시 시도해줘.");
            return;
        }
        const thumb = videoData.thumbnail || "";
        const isAbsoluteUrl = thumb.startsWith("http://") || thumb.startsWith("https://");
        const watchUrl = `${window.location.origin}/watch.html?v=${videoId}`;
        const shareParams = {
            objectType: "feed",
            content: {
                title: videoData.title || "영상 공유",
                description: videoData.description || "",
                imageUrl: isAbsoluteUrl ? thumb : `${window.location.origin}/image/img.png`,
                link: { mobileWebUrl: watchUrl, webUrl: watchUrl },
            },
            buttons: [{ title: "영상 보기", link: { mobileWebUrl: watchUrl, webUrl: watchUrl } }],
        };
        try {
            Kakao.Share.sendDefault(shareParams);
        } catch (e) {
            alert("카카오톡 공유 실패: " + e.message);
        }
    });

    modal.querySelector("#shareTwitterBtn")?.addEventListener("click", () => {
        const url = input.value;
        const twitterUrl = `https://twitter.com/intent/tweet?url=${encodeURIComponent(url)}`;
        window.open(twitterUrl, "_blank", "noopener,noreferrer");
    });

    modal.querySelector("#shareNativeBtn")?.addEventListener("click", async () => {
        try {
            await navigator.share({ url: input.value });
        } catch {}
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

function createSkeletonCards(count = 8) {
    const card = `
    <article class="card skeleton-card">
      <div class="skeleton-thumb"></div>
      <div class="skeleton-meta">
        <div class="skeleton-avatar"></div>
        <div class="skeleton-lines">
          <div class="skeleton-line"></div>
          <div class="skeleton-line short"></div>
          <div class="skeleton-line shorter"></div>
        </div>
      </div>
    </article>`;
    return Array.from({ length: count }, () => card).join("");
}

let _serverProgressMap = null;
let _serverProgressFetch = null;


function initThemeToggle() {
    if (!window.ThemeManager) return;
    const topbarRight = document.querySelector(".topbar-right");
    if (!topbarRight) return;
    if (topbarRight.querySelector(".theme-toggle-btn")) return;
    const btn = window.ThemeManager.createToggleBtn();
    const uploadBtn = topbarRight.querySelector('a[href="upload.html"]');
    if (uploadBtn) topbarRight.insertBefore(btn, uploadBtn);
    else topbarRight.prepend(btn);
}

function initGlobalTopSearch() {
    if (page === "search") return;

    const searchForms = document.querySelectorAll(".search-form");
    if (!searchForms.length) return;

    searchForms.forEach((form) => {
        const input = form.querySelector('input[type="text"]');
        if (!input) return;

        // 자동완성 드롭다운 생성
        const suggestBox = document.createElement("ul");
        suggestBox.className = "search-suggest-list";
        form.style.position = "relative";
        form.appendChild(suggestBox);

        let debounceTimer = null;
        let activeIdx = -1;
        let lastQuery = "";

        function hideSuggest() {
            suggestBox.style.display = "none";
            suggestBox.innerHTML = "";
            activeIdx = -1;
        }

        function setActive(idx) {
            const items = suggestBox.querySelectorAll(".search-suggest-item");
            items.forEach((el, i) => el.classList.toggle("is-active", i === idx));
            activeIdx = idx;
            if (idx >= 0 && idx < items.length) input.value = items[idx].dataset.query;
        }

        async function fetchSuggestions(q) {
            try {
                const res = await fetch(`/api/videos/feed?keyword=${encodeURIComponent(q)}&size=6`);
                if (!res.ok) return [];
                const data = await res.json();
                return (data.videos || []).map(v => v.title).filter(Boolean);
            } catch { return []; }
        }

        function renderSuggestions(titles, q) {
            if (!titles.length) { hideSuggest(); return; }
            suggestBox.innerHTML = titles.map(t => `
                <li class="search-suggest-item" data-query="${escapeHtml(t)}">
                    <svg class="search-suggest-icon" viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
                        <path d="M10 4a6 6 0 1 0 3.87 10.58l4.27 4.27 1.41-1.41-4.27-4.27A6 6 0 0 0 10 4Zm0 2a4 4 0 1 1 0 8 4 4 0 0 1 0-8Z"/>
                    </svg>
                    ${escapeHtml(t)}
                </li>
            `).join("");
            suggestBox.style.display = "block";
            suggestBox.querySelectorAll(".search-suggest-item").forEach(item => {
                item.addEventListener("mousedown", (e) => {
                    e.preventDefault();
                    input.value = item.dataset.query;
                    hideSuggest();
                    const url = new URL("search.html", window.location.href);
                    url.searchParams.set("q", item.dataset.query);
                    window.location.href = url.toString();
                });
            });
        }

        input.addEventListener("input", () => {
            const q = input.value.trim();
            clearTimeout(debounceTimer);
            activeIdx = -1;
            if (!q) { hideSuggest(); return; }
            lastQuery = q;
            debounceTimer = setTimeout(async () => {
                if (input.value.trim() !== lastQuery) return;
                const titles = await fetchSuggestions(q);
                if (input.value.trim() === lastQuery) renderSuggestions(titles, q);
            }, 250);
        });

        input.addEventListener("keydown", (e) => {
            const items = suggestBox.querySelectorAll(".search-suggest-item");
            if (suggestBox.style.display === "none" || !items.length) return;
            if (e.key === "ArrowDown") {
                e.preventDefault();
                setActive(Math.min(activeIdx + 1, items.length - 1));
            } else if (e.key === "ArrowUp") {
                e.preventDefault();
                if (activeIdx <= 0) { activeIdx = -1; input.value = lastQuery; items.forEach(el => el.classList.remove("is-active")); }
                else setActive(activeIdx - 1);
            } else if (e.key === "Escape") {
                hideSuggest();
            }
        });

        input.addEventListener("blur", () => setTimeout(hideSuggest, 150));

        form.addEventListener("submit", (event) => {
            event.preventDefault();
            const keyword = input.value.trim() || "";
            hideSuggest();
            const targetUrl = new URL("search.html", window.location.href);
            if (keyword) targetUrl.searchParams.set("q", keyword);
            window.location.href = targetUrl.toString();
        });
    });
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
