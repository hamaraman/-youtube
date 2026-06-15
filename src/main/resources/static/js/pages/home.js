/* MyTube - pages/home.js : 홈 페이지 */

async function initHomePage() {
    const videoGrid = document.getElementById("videoGrid");
    const categoryBar = document.getElementById("categoryBar");
    const homeEmptyState = document.getElementById("homeEmptyState");
    const scrollLoader = document.getElementById("scrollLoader");
    const scrollSentinel = document.getElementById("scrollSentinel");
    const channelResults = document.getElementById("channelResults");
    const channelResultsList = document.getElementById("channelResultsList");

    if (!videoGrid) return;

    const authMe = getAuthMe();
    const myId = authMe?.user?.id || null;

    // 구독 상태 맵 (ownerId -> boolean)
    const subMap = new Map();
    if (authMe?.loggedIn) {
        try {
            const r = await fetch("/api/users/me/subscriptions");
            if (r.ok) {
                const subs = await r.json();
                subs.forEach(s => subMap.set(String(s.channelOwnerId), true));
            }
        } catch {}
    }

    let currentKeyword = "";
    let selectedCategory = "";
    let sortMode = "";
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

        const popularChip = `<button class="category-chip${sortMode === "popular" ? " is-active" : ""}" data-sort="popular">🔥 인기</button>`;
        const chips = [{ label: "전체", value: "" }, ...categories.map((c) => ({ label: c, value: c }))];
        const categoryChips = chips.map((chip) => `
            <button class="category-chip${sortMode !== "popular" && chip.value === selectedCategory ? " is-active" : ""}"
                    data-category="${chip.value}">
                ${chip.label}
            </button>
        `).join("");

        categoryBar.innerHTML = popularChip + categoryChips;

        categoryBar.querySelectorAll(".category-chip[data-sort='popular']").forEach((btn) => {
            btn.addEventListener("click", () => {
                sortMode = "popular";
                selectedCategory = "";
                categoryBar.querySelectorAll(".category-chip").forEach((b) => b.classList.remove("is-active"));
                btn.classList.add("is-active");
                resetAndLoad();
            });
        });

        categoryBar.querySelectorAll(".category-chip[data-category]").forEach((btn) => {
            btn.addEventListener("click", () => {
                sortMode = "";
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

        if (currentPage === 0) {
            videoGrid.innerHTML = createSkeletonCards(8);
        } else {
            showLoader(true);
        }

        try {
            const params = new URLSearchParams({ page: currentPage, size: 12 });
            if (sortMode === "popular") {
                params.set("sort", "popular");
            } else {
                if (currentKeyword.trim()) params.set("keyword", currentKeyword.trim());
                if (selectedCategory) params.set("category", selectedCategory);
            }

            const res = await fetch(`/api/videos/feed?${params}`);
            if (!res.ok) throw new Error();
            const data = await res.json();

            if (currentPage === 0) {
                videoGrid.innerHTML = "";
                if (homeEmptyState) {
                    homeEmptyState.hidden = data.videos.length > 0;
                    const titleEl = homeEmptyState.querySelector(".home-empty-title");
                    const textEl = homeEmptyState.querySelector(".home-empty-text");
                    if (sortMode === "popular") {
                        if (titleEl) titleEl.textContent = "인기 영상이 없습니다";
                        if (textEl) textEl.textContent = "아직 조회수가 집계된 영상이 없어.";
                    } else if (currentKeyword.trim() || selectedCategory) {
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
                applyServerProgress();
                if (authMe?.loggedIn && myId) {
                    data.videos.forEach(v => {
                        if (String(v.ownerId) === String(myId)) return;
                        const btn = videoGrid.querySelector(`.card-sub-btn[data-owner-id="${v.ownerId}"]`);
                        if (!btn) return;
                        const subscribed = subMap.get(String(v.ownerId)) || false;
                        btn.textContent = subscribed ? "구독중" : "구독";
                        btn.classList.toggle("is-subscribed", subscribed);
                        btn.style.display = "inline-flex";
                        btn.addEventListener("click", async () => {
                            const res = await fetch(`/api/users/${v.ownerId}/subscribe`, { method: "POST" });
                            if (!res.ok) return;
                            const result = await res.json();
                            subMap.set(String(v.ownerId), result.subscribed);
                            videoGrid.querySelectorAll(`.card-sub-btn[data-owner-id="${v.ownerId}"]`).forEach(b => {
                                b.textContent = result.subscribed ? "구독중" : "구독";
                                b.classList.toggle("is-subscribed", result.subscribed);
                            });
                        });
                    });
                }
            }

            hasMore = data.hasMore;
            currentPage++;
        } catch {
            if (currentPage === 0) videoGrid.innerHTML = "";
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
