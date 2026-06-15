/* MyTube - pages/search.js : 검색 페이지 */

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
    let searchSort = url.searchParams.get("sort") || "";
    let searchCategory = url.searchParams.get("category") || "";

    // 필터 바 주입
    const filterBarId = "searchFilterBar";
    if (!document.getElementById(filterBarId) && videoGrid.parentElement) {
        let cats = [];
        try {
            const r = await fetch("/api/videos/categories");
            if (r.ok) cats = await r.json();
        } catch {}

        const filterBar = document.createElement("div");
        filterBar.id = filterBarId;
        filterBar.className = "search-filter-bar";
        filterBar.innerHTML = `
            <div class="search-filter-group">
                <span class="search-filter-label">정렬</span>
                <button class="search-filter-btn${!searchSort ? " is-active" : ""}" data-sort="">관련성</button>
                <button class="search-filter-btn${searchSort === "latest" ? " is-active" : ""}" data-sort="latest">최신순</button>
                <button class="search-filter-btn${searchSort === "popular" ? " is-active" : ""}" data-sort="popular">조회수순</button>
                <button class="search-filter-btn${searchSort === "likes" ? " is-active" : ""}" data-sort="likes">좋아요순</button>
            </div>
            ${cats.length ? `
            <div class="search-filter-group">
                <span class="search-filter-label">카테고리</span>
                <button class="search-filter-btn${!searchCategory ? " is-active" : ""}" data-cat="">전체</button>
                ${cats.map(c => `<button class="search-filter-btn${searchCategory === c ? " is-active" : ""}" data-cat="${escapeHtml(c)}">${escapeHtml(c)}</button>`).join("")}
            </div>` : ""}
        `;
        videoGrid.parentElement.insertBefore(filterBar, videoGrid);

        filterBar.querySelectorAll(".search-filter-btn[data-sort]").forEach(btn => {
            btn.addEventListener("click", () => {
                searchSort = btn.dataset.sort;
                filterBar.querySelectorAll(".search-filter-btn[data-sort]").forEach(b => b.classList.toggle("is-active", b.dataset.sort === searchSort));
                resetAndLoad();
            });
        });
        filterBar.querySelectorAll(".search-filter-btn[data-cat]").forEach(btn => {
            btn.addEventListener("click", () => {
                searchCategory = btn.dataset.cat;
                filterBar.querySelectorAll(".search-filter-btn[data-cat]").forEach(b => b.classList.toggle("is-active", b.dataset.cat === searchCategory));
                resetAndLoad();
            });
        });
    }

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
            if (searchSort === "popular") params.set("sort", "popular");
            else if (searchSort) params.set("sortBy", searchSort);
            if (searchCategory) params.set("category", searchCategory);

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
                applyServerProgress();
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
