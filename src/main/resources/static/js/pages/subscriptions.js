/* MyTube - pages/subscriptions.js : 구독 채널 목록 페이지 */

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
                applyServerProgress();
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
