/* MyTube - pages/user.js : 사용자 채널 페이지 */

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
                applyServerProgress();
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