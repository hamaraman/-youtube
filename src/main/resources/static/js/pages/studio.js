/* MyTube - pages/studio.js : 스튜디오 페이지 */

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

    let currentChartDays = 28;

    async function renderDateViewsChart(days) {
        const chartEl = document.getElementById("studioChart");
        if (!chartEl) return;
        currentChartDays = days;

        // Header: title + period buttons
        const periodBtns = [7, 28, 90].map(d =>
            `<button class="studio-period-btn${d === days ? " is-active" : ""}" data-days="${d}">${d}일</button>`
        ).join("");

        chartEl.innerHTML = `
            <div class="studio-chart-header">
                <span class="studio-chart-title">날짜별 조회수</span>
                <div class="studio-period-group">${periodBtns}</div>
            </div>
            <div class="studio-chart-body" id="studioChartBody">
                <p style="color:#555;font-size:14px;padding:40px 0;text-align:center">불러오는 중...</p>
            </div>`;

        chartEl.querySelectorAll(".studio-period-btn").forEach(btn => {
            btn.addEventListener("click", () => renderDateViewsChart(Number(btn.dataset.days)));
        });

        try {
            const res = await fetch(`/api/studio/view-trend?days=${days}`);
            if (!res.ok) throw new Error();
            const data = await res.json();
            const body = document.getElementById("studioChartBody");
            if (!body) return;

            if (!data.length || data.every(d => d.count === 0)) {
                body.innerHTML = `<p class="studio-chart-empty">해당 기간에 조회 기록이 없습니다.</p>`;
                return;
            }

            body.innerHTML = buildLineChartSvg(data, days);
        } catch {
            const body = document.getElementById("studioChartBody");
            if (body) body.innerHTML = `<p class="studio-chart-empty">데이터를 불러올 수 없습니다.</p>`;
        }
    }

    function buildLineChartSvg(data, days) {
        const W = 800, H = 220;
        const padL = 48, padR = 20, padT = 16, padB = 44;
        const chartW = W - padL - padR;
        const chartH = H - padT - padB;
        const counts = data.map(d => Number(d.count));
        const maxVal = Math.max(...counts, 1);
        const n = data.length;

        // Y-axis grid lines
        const ySteps = 4;
        let grid = "";
        for (let i = 0; i <= ySteps; i++) {
            const v = Math.round(maxVal * i / ySteps);
            const y = padT + chartH - (chartH * i / ySteps);
            grid += `<line x1="${padL}" y1="${y}" x2="${W - padR}" y2="${y}" stroke="#1f1f1f" stroke-width="1"/>
                <text x="${padL - 6}" y="${y + 4}" text-anchor="end" fill="#555" font-size="10">${formatCount(v)}</text>`;
        }

        // X-axis labels: show every Nth date
        const labelStep = days <= 7 ? 1 : days <= 28 ? 7 : 15;
        let xLabels = "";
        data.forEach((d, i) => {
            if (i % labelStep !== 0 && i !== n - 1) return;
            const x = padL + (i / (n - 1)) * chartW;
            const parts = d.date.split("-");
            const label = `${parts[1]}/${parts[2]}`;
            xLabels += `<text x="${x}" y="${H - 8}" text-anchor="middle" fill="#555" font-size="10">${label}</text>`;
        });

        // Points for line
        const pts = data.map((d, i) => {
            const x = padL + (n === 1 ? chartW / 2 : (i / (n - 1)) * chartW);
            const y = padT + chartH - (Number(d.count) / maxVal) * chartH;
            return [x, y];
        });

        // SVG path
        const linePath = pts.map((p, i) => `${i === 0 ? "M" : "L"}${p[0].toFixed(1)},${p[1].toFixed(1)}`).join(" ");
        const fillPath = `${linePath} L${pts[pts.length - 1][0].toFixed(1)},${(padT + chartH).toFixed(1)} L${pts[0][0].toFixed(1)},${(padT + chartH).toFixed(1)} Z`;

        // Tooltips as circles with <title>
        const circles = pts.map((p, i) =>
            `<circle cx="${p[0].toFixed(1)}" cy="${p[1].toFixed(1)}" r="4" fill="#3ea6ff" stroke="#141414" stroke-width="2" opacity="${counts[i] > 0 ? 1 : 0}">
                <title>${data[i].date}: ${formatCount(counts[i])}회</title>
            </circle>`
        ).join("");

        return `<svg viewBox="0 0 ${W} ${H}" style="width:100%;overflow:visible" xmlns="http://www.w3.org/2000/svg">
            ${grid}
            <line x1="${padL}" y1="${padT}" x2="${padL}" y2="${padT + chartH}" stroke="#2a2a2a" stroke-width="1"/>
            <line x1="${padL}" y1="${padT + chartH}" x2="${W - padR}" y2="${padT + chartH}" stroke="#2a2a2a" stroke-width="1"/>
            <defs>
                <linearGradient id="chartFill" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stop-color="#3ea6ff" stop-opacity="0.18"/>
                    <stop offset="100%" stop-color="#3ea6ff" stop-opacity="0"/>
                </linearGradient>
            </defs>
            <path d="${fillPath}" fill="url(#chartFill)"/>
            <path d="${linePath}" fill="none" stroke="#3ea6ff" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"/>
            ${circles}
            ${xLabels}
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
            renderAnalyticsExtra(videos);
            uploadedVideosCache = normalizeVideos(videos);
            renderFilteredList();
            startEncodeStatusPolling();
        } catch {
            channelManageList.innerHTML = `<p class="studio-error">영상을 불러오는 중 오류가 발생했습니다.</p>`;
        }
    }

    let encodeStatusPollTimer = null;

    async function applyEncodeStatusBadges() {
        const STEP_LABELS = {
            QUEUED: "대기 중", CONVERTING: "H.264 변환 중",
            ENCODING: "해상도 변환 중",
            "1080p": "1080p 인코딩", "720p": "720p 인코딩",
            "480p": "480p 인코딩", "360p": "360p 인코딩",
            UPLOADING: "클라우드 업로드 중", DONE: "완료", IDLE: ""
        };
        let hasActive = false;
        try {
            const res = await fetch("/api/videos/encode-statuses");
            if (!res.ok) return false;
            const statuses = await res.json();
            // 응답에 없는 배지는 숨김 (서버 재시작 후 상태 유실 대응)
            document.querySelectorAll("[id^='encodeBadge-']").forEach(badge => {
                const id = badge.id.replace("encodeBadge-", "");
                if (!(id in statuses)) badge.style.display = "none";
            });
            for (const [id, status] of Object.entries(statuses)) {
                const badge = document.getElementById(`encodeBadge-${id}`);
                if (!badge) continue;
                const isActive = status !== "IDLE" && status !== "DONE" && !status.startsWith("ERROR");
                if (isActive) hasActive = true;
                if (status === "IDLE" || status === "DONE") {
                    badge.style.display = "none";
                    if (status === "DONE") {
                        fetch(`/api/videos/${id}`)
                            .then(r => r.ok ? r.json() : null)
                            .then(v => {
                                if (!v || !v.thumbnail) return;
                                const row = document.querySelector(`.studio-row[data-id="${id}"]`);
                                const img = row?.querySelector(".studio-thumb img");
                                if (img) img.src = v.thumbnail;
                            }).catch(() => {});
                    }
                } else if (status.startsWith("ERROR")) {
                    badge.style.display = "";
                    badge.className = "studio-encode-badge is-error";
                    badge.textContent = "인코딩 오류";
                } else {
                    badge.style.display = "";
                    badge.className = "studio-encode-badge is-encoding";
                    badge.textContent = `⏳ ${STEP_LABELS[status] || status}`;
                }
            }
        } catch {}
        return hasActive;
    }

    function startEncodeStatusPolling() {
        if (encodeStatusPollTimer) clearInterval(encodeStatusPollTimer);
        let idleCount = 0;
        applyEncodeStatusBadges();
        encodeStatusPollTimer = setInterval(async () => {
            const hasActive = await applyEncodeStatusBadges();
            if (!hasActive) {
                idleCount++;
                if (idleCount >= 5) {
                    clearInterval(encodeStatusPollTimer);
                    encodeStatusPollTimer = null;
                }
            } else {
                idleCount = 0;
            }
        }, 4000);
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
    await Promise.all([loadAndRender(), renderDateViewsChart(28)]);
}