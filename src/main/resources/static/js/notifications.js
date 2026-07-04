/* MyTube - notifications.js : 알림 모듈 */

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
    let lastUnreadCount = -1;

    function ringBell() {
        if (!btn) return;
        btn.classList.remove("notif-bell-ring");
        void btn.offsetWidth;
        btn.classList.add("notif-bell-ring");
        setTimeout(() => btn.classList.remove("notif-bell-ring"), 700);
    }

    function showBrowserNotif(message, icon) {
        if (!("Notification" in window) || Notification.permission !== "granted") return;
        try { new Notification("새 알림", { body: message, icon: icon || "/favicon.ico", tag: "youtube-notif" }); }
        catch (e) {}
    }

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
        if (type === "VIDEO")        return `<div class="notif-type-icon">🎬</div>`;
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
                <button class="notif-delete-btn" data-delete-id="${n.id}" title="삭제">✕</button>
            </div>
        `).join("");

        list.querySelectorAll(".notif-delete-btn").forEach((delBtn) => {
            delBtn.addEventListener("click", async (e) => {
                e.stopPropagation();
                const id = delBtn.dataset.deleteId;
                await fetch(`/api/notifications/${id}`, { method: "DELETE" });
                const data = await fetchNotifs();
                if (data) {
                    lastUnreadCount = data.unreadCount;
                    if (data.unreadCount > 0) {
                        badge.textContent = data.unreadCount > 99 ? "99+" : data.unreadCount;
                        badge.style.display = "flex";
                    } else {
                        badge.style.display = "none";
                    }
                    renderList(data.notifications);
                }
            });
        });

        list.querySelectorAll(".notif-item").forEach((item) => {
            item.addEventListener("click", async (e) => {
                if (e.target.closest(".notif-delete-btn")) return;
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

        const prev = lastUnreadCount;
        lastUnreadCount = data.unreadCount;

        if (data.unreadCount > 0) {
            badge.textContent = data.unreadCount > 99 ? "99+" : data.unreadCount;
            badge.style.display = "flex";
        } else {
            badge.style.display = "none";
        }

        if (prev !== -1 && data.unreadCount > prev && !open) {
            ringBell();
            if (data.notifications && data.notifications.length > 0) {
                showBrowserNotif(data.notifications[0].message, data.notifications[0].thumbnail);
            }
        }

        if (open && list.style.display !== "none") renderList(data.notifications);
    }

    btn.addEventListener("click", async (e) => {
        e.stopPropagation();
        if ("Notification" in window && Notification.permission === "default") {
            Notification.requestPermission();
        }
        open = !open;
        dropdown.style.display = open ? "block" : "none";
        if (open) {
            showMainView();
            const data = await fetchNotifs();
            if (data) {
                lastUnreadCount = data.unreadCount;
                renderList(data.notifications);
            }
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

    let pollTimer = null;

    function startPollingFallback() {
        if (pollTimer) return;
        pollTimer = setInterval(refresh, 30000);
    }

    function connectSSE() {
        const es = new EventSource("/api/notifications/stream");

        // 서버/인프라 문제로 SSE 응답이 전혀 오지 않는 경우 폴링으로 전환
        const fallbackTimer = setTimeout(() => {
            es.close();
            startPollingFallback();
        }, 10000);

        es.onopen = () => clearTimeout(fallbackTimer);
        es.addEventListener("connected", () => clearTimeout(fallbackTimer));

        es.addEventListener("notification", (e) => {
            let data;
            try { data = JSON.parse(e.data); } catch { return; }
            const n = data.notification;
            const unreadCount = data.unreadCount;

            lastUnreadCount = unreadCount;
            if (unreadCount > 0) {
                badge.textContent = unreadCount > 99 ? "99+" : unreadCount;
                badge.style.display = "flex";
            } else {
                badge.style.display = "none";
            }

            if (!open) {
                ringBell();
                showBrowserNotif(n.message, n.thumbnail);
            } else if (list.style.display !== "none") {
                fetchNotifs().then((data) => { if (data) renderList(data.notifications); });
            }
        });

        es.onerror = () => {
            // EventSource는 연결이 끊기면 브라우저가 자동으로 재연결을 시도함
        };
    }

    refresh();
    connectSSE();
}
