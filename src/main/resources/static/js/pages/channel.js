/* MyTube - pages/channel.js : 채널 페이지 */

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
        applyServerProgress();
    } catch {
        if (channelEmptyState) channelEmptyState.hidden = false;
    }
}


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