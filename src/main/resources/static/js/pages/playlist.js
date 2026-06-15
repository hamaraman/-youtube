/* MyTube - pages/playlist.js : 재생목록 페이지 및 패치 */

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
        plDetailTitle.textContent = name || "로딩 중...";

        const res = await fetch(`/api/playlists/${id}/videos`);
        if (!res.ok) { plDetailTitle.textContent = name || "재생목록"; return; }
        const data = await res.json();
        const videos = data.videos || [];
        const isOwner = !!data.isOwner;
        const currentName = data.name || name;
        plDetailTitle.textContent = currentName;

        plDetailView.querySelector(".pl-rename-btn")?.remove();
        plDetailView.querySelector(".pl-play-all-btn")?.remove();

        if (isOwner) {
            const renameBtn = document.createElement("button");
            renameBtn.type = "button";
            renameBtn.className = "pl-rename-btn";
            renameBtn.title = "이름 변경";
            renameBtn.innerHTML = `<svg viewBox="0 0 24 24" width="15" height="15" fill="currentColor"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1 1 0 0 0 0-1.41l-2.34-2.34a1 1 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/></svg>`;
            plDetailView.querySelector(".pl-detail-header").appendChild(renameBtn);
            renameBtn.addEventListener("click", async () => {
                const newName = prompt("새 이름:", plDetailTitle.textContent);
                if (!newName?.trim()) return;
                const r = await fetch(`/api/playlists/${id}`, {
                    method: "PUT",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ name: newName.trim() })
                });
                if (r.ok) { plDetailTitle.textContent = newName.trim(); data.name = newName.trim(); }
            });
        }

        if (videos.length) {
            const playAllBtn = document.createElement("a");
            playAllBtn.href = `watch.html?v=${videos[0].id}&list=${id}`;
            playAllBtn.className = "pl-play-all-btn";
            playAllBtn.innerHTML = `<svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M8 5v14l11-7z"/></svg> 전체 재생`;
            plDetailView.querySelector(".pl-detail-header").appendChild(playAllBtn);
        }

        if (!videos.length) { plDetailEmpty.style.display = "block"; plVideoList.innerHTML = ""; return; }
        plDetailEmpty.style.display = "none";
        plVideoList.innerHTML = videos.map((v, i) => `
            <div class="pl-video-item">
                <span class="pl-video-num">${videos.length - i}</span>
                <a href="watch.html?v=${v.id}&list=${id}" class="pl-video-link">
                    <div class="pl-video-thumb"><img src="${escapeHtml(v.thumbnail)}" alt="${escapeHtml(v.title)}"></div>
                </a>
                <div class="pl-video-info">
                    <a href="watch.html?v=${v.id}&list=${id}" class="pl-video-link">
                        <div class="pl-video-title">${escapeHtml(v.title)}</div>
                        <div class="pl-video-meta">${escapeHtml(v.channel)} · 조회수 ${formatCount(v.viewCount)}회 · ${escapeHtml(v.date)}</div>
                    </a>
                </div>
                ${isOwner ? `<button type="button" class="pl-video-remove" data-pl-id="${id}" data-video-id="${v.id}">제거</button>` : ""}
            </div>`).join("");

        if (isOwner) {
            plVideoList.querySelectorAll(".pl-video-remove").forEach(btn => {
                btn.addEventListener("click", async () => {
                    await fetch(`/api/playlists/${btn.dataset.plId}/videos/${btn.dataset.videoId}`, { method: "DELETE" });
                    openDetail(id, data.name);
                });
            });
        }
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

    await loadPlaylists();
    const urlId = new URLSearchParams(window.location.search).get("id");
    if (urlId) openDetail(Number(urlId), "");
}


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
    window.__openPlaylistMenu = openPlaylistDropdown;
})();
