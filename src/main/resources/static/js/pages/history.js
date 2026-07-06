/* MyTube - pages/history.js : 시청 기록 페이지 및 패치 통합 */

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
            <article class="card history-force-card" data-video-id="${item.id}">
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

    function getDateGroupLabel(dateStr) {
        const now = new Date();
        const d = new Date(dateStr || 0);
        const diffMs = now - d;
        const diffDays = Math.floor(diffMs / 86400000);
        const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate());
        const itemStart  = new Date(d.getFullYear(), d.getMonth(), d.getDate());
        const diffCalDays = Math.round((todayStart - itemStart) / 86400000);
        if (diffCalDays === 0) return "오늘";
        if (diffCalDays === 1) return "어제";
        if (diffCalDays <= 7) return "이번 주";
        if (diffCalDays <= 14) return "지난 주";
        const diffMonths = (now.getFullYear() - d.getFullYear()) * 12 + (now.getMonth() - d.getMonth());
        if (diffMonths < 1) return "이번 달";
        if (diffMonths < 2) return "지난 달";
        return `${d.getFullYear()}년 ${d.getMonth() + 1}월`;
    }

    function buildHistoryGroupedHtml(videos) {
        const groups = [];
        const groupMap = new Map();
        for (const v of videos) {
            const label = getDateGroupLabel(v.watchedAt);
            if (!groupMap.has(label)) {
                groupMap.set(label, []);
                groups.push(label);
            }
            groupMap.get(label).push(v);
        }
        return groups.map(label => `
            <div class="history-date-group">
                <h3 class="history-date-label">${safeEscape(label)}</h3>
                <div class="history-force-grid">${groupMap.get(label).map(createHistoryCard).join("")}</div>
            </div>
        `).join("");
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

        grid.classList.remove("history-force-grid");
        grid.classList.add("history-grouped-root");

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
        grid.innerHTML = buildHistoryGroupedHtml(videos);
        applyServerProgress();
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
