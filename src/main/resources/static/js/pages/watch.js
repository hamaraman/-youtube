/* MyTube - pages/watch.js : 동영상 시청 페이지 */

function timestampToSeconds(ts) {
    const parts = ts.split(':').map(Number);
    if (parts.length === 3) return parts[0] * 3600 + parts[1] * 60 + parts[2];
    return parts[0] * 60 + (parts[1] || 0);
}

function parseChapters(description) {
    if (!description) return [];
    const re = /^((?:\d{1,2}:)?\d{1,2}:\d{2})\s+(.+)/;
    return description.split('\n')
        .map(line => {
            const m = line.trim().match(re);
            return m ? { timestamp: m[1], seconds: timestampToSeconds(m[1]), title: m[2].trim() } : null;
        })
        .filter(Boolean);
}

function renderDescriptionWithLinks(text) {
    if (!text) return '';
    const tsRe = /((?:\d{1,2}:)?\d{1,2}:\d{2})/g;
    return text.split('\n').map(line => {
        const escaped = escapeHtml(line);
        return escaped.replace(tsRe, match => {
            const secs = timestampToSeconds(match);
            return `<button class="ts-link" type="button" data-secs="${secs}">${match}</button>`;
        });
    }).join('\n');
}

function seekVideo(secs) {
    const pv = document.querySelector('#customPlayer video.player-video');
    if (!pv) return;
    const doSeek = () => { pv.currentTime = secs; pv.play(); };
    pv.readyState >= 1 ? doSeek() : pv.addEventListener('loadedmetadata', doSeek, { once: true });
}

async function initWatchPage() {
    const watchMain = document.getElementById("watchMain");
    function bindCommentActionButtons(){}
    if (!watchMain) return;

    const authMe = getAuthMe();
    const watchRecommendList = document.getElementById("watchRecommendList");
    const params = new URLSearchParams(window.location.search);
    const rawVideoId = params.get("v") || params.get("id");
    const videoId = Number(rawVideoId);
    const listId = params.get("list") ? Number(params.get("list")) : null;

    let currentVideo = null;
    let videoLoadError = "영상을 찾을 수 없습니다.";
    try {
        if (rawVideoId != null && Number.isFinite(videoId)) {
            const res = await fetch(`/api/videos/${videoId}`);
            if (res.ok) {
                currentVideo = await res.json();
            } else if (res.status === 403) {
                videoLoadError = "비공개 영상입니다.";
            }
        } else {
            // v 파라미터 없이 진입: 최신 영상으로 폴백 (기존 동작 유지)
            const feedRes = await fetch("/api/videos/feed?page=0&size=1");
            if (feedRes.ok) {
                const feed = await feedRes.json();
                currentVideo = (feed.videos || [])[0] || null;
            }
        }
    } catch {}
    if (!currentVideo) {
        watchMain.innerHTML = `<p style="color:#aaa;">${escapeHtml(videoLoadError)}</p>`;
        return;
    }

    try {
        await addVideoToHistory(currentVideo.id);
    } catch (error) {
        console.warn("시청 기록 저장 실패:", error);
    }

    const updatedViewCount = incrementViewCount(currentVideo);

    let isLiked = Boolean(currentVideo.likedByMe);
    let likeCount = Number(currentVideo.likeCount || 0);
    let isDisliked = Boolean(currentVideo.dislikedByMe);
    let dislikeCount = Number(currentVideo.dislikeCount || 0);
    let isSubscribed = false;
    let subscriberCount = 0;
    let isSaved = Boolean(currentVideo.savedByMe);

    const isOwnVideo = authMe.loggedIn && authMe.user && String(authMe.user.id) === String(currentVideo.ownerId);

    if (currentVideo.ownerId && !isOwnVideo) {
        try {
            const subStatus = await fetchSubscriptionStatus(currentVideo.ownerId);
            isSubscribed = Boolean(subStatus.subscribed);
            subscriberCount = Number(subStatus.subscriberCount || 0);
        } catch {}
    }

    let comments = [];
    let commentPage = 0;
    let commentHasMore = false;
    let commentTotal = 0;
    let commentSort = "latest";

    const initCommentData = await fetchCommentsByVideoId(currentVideo.id, 0);
    comments = initCommentData.comments || [];
    commentHasMore = initCommentData.hasMore || false;
    commentTotal = Number(initCommentData.total || 0);

    let recommendVideos = [];
    let channelVideos = [];
    try {
        const relRes = await fetch(`/api/videos/${currentVideo.id}/related?limit=12`);
        if (relRes.ok) {
            const related = await relRes.json();
            const baseCategory = String(currentVideo.category || "").trim();
            const baseChannel = String(currentVideo.channel || "").trim();
            recommendVideos = (related.recommended || []).map((video) => ({
                ...video,
                _recTag: (() => {
                    const tc = String(video.category || "").trim();
                    if (baseCategory && tc && baseCategory === tc) return "카테고리";
                    if (baseChannel && baseChannel === String(video.channel || "").trim()) return "채널";
                    return null;
                })(),
            }));
            channelVideos = related.channel || [];
        }
    } catch {}

    const descriptionText = String(currentVideo.description || "");
    const shouldCollapseDescription = descriptionText.length > 140 || descriptionText.includes("\n");
    const chapters = parseChapters(descriptionText);
    const chapterHtml = chapters.length >= 2 ? `
    <div class="chapter-list" id="chapterList">
      <div class="chapter-list-title">챕터</div>
      ${chapters.map(ch => `
        <button class="chapter-item" type="button" data-secs="${ch.seconds}">
          <span class="chapter-timestamp">${escapeHtml(ch.timestamp)}</span>
          <span class="chapter-title">${escapeHtml(ch.title)}</span>
        </button>`).join('')}
    </div>` : '';

    const resolutionOptions = [];
    if (currentVideo.videoUrl) {
        resolutionOptions.push({ label: "원본", src: currentVideo.videoUrl });
        if (currentVideo.videoUrl1080) resolutionOptions.push({ label: "1080p", src: currentVideo.videoUrl1080 });
        if (currentVideo.videoUrl720) resolutionOptions.push({ label: "720p", src: currentVideo.videoUrl720 });
        if (currentVideo.videoUrl480) resolutionOptions.push({ label: "480p", src: currentVideo.videoUrl480 });
        if (currentVideo.videoUrl360) resolutionOptions.push({ label: "360p", src: currentVideo.videoUrl360 });
    }
    watchMain.innerHTML = `
    <div class="player-box">${createPlayerMarkup(currentVideo, resolutionOptions)}</div>
    <h1 class="watch-title">${escapeHtml(currentVideo.title)}</h1>

    <div class="watch-meta-row">
      <div class="watch-channel-box">
        <img class="watch-channel-avatar" src="${escapeHtml(currentVideo.avatar || DEFAULT_AVATAR)}" alt="${escapeHtml(currentVideo.channel)}" />
        <div class="watch-channel-text">
          <strong>${escapeHtml(currentVideo.channel)}</strong>
          <span id="subscriberCount">구독자 ${formatCount(subscriberCount)}명</span>
        </div>
        ${!isOwnVideo ? `<button class="watch-action-btn ${isSubscribed ? "" : "primary"}" id="subscribeBtn" type="button">${isSubscribed ? "구독중" : "구독"}</button>` : ""}
      </div>

      <div class="watch-actions">
        <button class="watch-action-btn ${isLiked ? "active" : ""}" id="likeBtn" type="button">
          좋아요 ${formatCount(likeCount)}
        </button>
        <button class="watch-action-btn ${isDisliked ? "active" : ""}" id="dislikeBtn" type="button">
          싫어요 ${formatCount(dislikeCount)}
        </button>
        <button class="watch-action-btn" id="shareBtn" type="button">공유</button>
        <button class="watch-action-btn ${isSaved ? "active" : ""}" id="saveBtn" type="button">
          ${isSaved ? "저장됨" : "저장"}
        </button>
        <button class="watch-action-btn" id="plAddBtn" type="button">재생목록</button>
        <button class="watch-action-btn" id="reportBtn" type="button">신고</button>
      </div>
    </div>

    ${chapterHtml}
    <div class="watch-description-box ${shouldCollapseDescription ? "is-collapsed" : ""}" id="watchDescriptionBox">
      <span class="watch-description-meta">조회수 ${formatCount(updatedViewCount)}회 · ${escapeHtml(currentVideo.date || "방금 전")}</span>
      <div class="watch-description-text" id="watchDescriptionText">${renderDescriptionWithLinks(descriptionText || "설명이 없습니다.")}</div>
      ${shouldCollapseDescription ? '<button type="button" class="watch-description-toggle" id="watchDescriptionToggle">더보기</button>' : ""}
    </div>

    <section class="comments-section">
      <div class="comments-header">
        <h2 id="commentsCount"></h2>
        <div class="comment-sort-btns">
          <button class="comment-sort-btn is-active" data-sort="latest" type="button">최신순</button>
          <button class="comment-sort-btn" data-sort="top" type="button">좋아요순</button>
        </div>
      </div>

      <div class="comment-form">
        <div class="comment-form-avatar">${escapeHtml((authMe.user?.nickname || authMe.user?.username || "G").charAt(0).toUpperCase())}</div>
        <div class="comment-form-body">
          <input type="text" class="comment-input" id="commentInput" placeholder="${authMe.loggedIn ? "댓글 추가..." : "로그인 후 댓글을 입력할 수 있어"}" maxlength="300" ${authMe.loggedIn ? "" : "readonly"} />
          <div class="comment-form-actions">
            <span class="comment-char-counter" id="commentCharCounter" style="display:none">0/300</span>
            <button class="comment-btn cancel" id="commentCancelBtn" type="button">취소</button>
            <button class="comment-btn submit" id="commentSubmitBtn" type="button" disabled>댓글</button>
          </div>
        </div>
      </div>

      <div class="comment-list" id="commentList"></div>
    </section>
  `;

    if (watchRecommendList) {
        watchRecommendList.innerHTML = recommendVideos.map(createRecommendCard).join("");
    }

    let playlistVideos = [];
    let playlistIndex = -1;
    if (listId) {
        try {
            const plRes = await fetch(`/api/playlists/${listId}/videos`);
            if (plRes.ok) {
                const plData = await plRes.json();
                playlistVideos = (plData.videos || []).slice().reverse();
                playlistIndex = playlistVideos.findIndex(v => v.id === videoId);
                const panel = document.getElementById("watchPlaylistPanel");
                if (panel) {
                    panel.style.display = "block";
                    panel.innerHTML = `
                        <div class="watch-pl-header">
                            <a href="playlist.html?id=${listId}" class="watch-pl-title">${escapeHtml(plData.name || "재생목록")}</a>
                            <span class="watch-pl-pos">${playlistIndex >= 0 ? `${playlistIndex + 1} / ${playlistVideos.length}` : ""}</span>
                        </div>
                        <div class="watch-pl-list">
                            ${playlistVideos.map((v, i) => `
                                <a href="watch.html?v=${v.id}&list=${listId}" class="watch-pl-item${v.id === videoId ? " is-active" : ""}">
                                    <div class="watch-pl-thumb">
                                        <img src="${escapeHtml(v.thumbnail)}" alt="${escapeHtml(v.title)}">
                                        ${v.id === videoId
                                            ? '<span class="watch-pl-now">▶</span>'
                                            : `<span class="watch-pl-num">${i + 1}</span>`}
                                    </div>
                                    <div class="watch-pl-info">
                                        <div class="watch-pl-item-title">${escapeHtml(v.title)}</div>
                                        <div class="watch-pl-item-ch">${escapeHtml(v.channel)}</div>
                                    </div>
                                </a>`).join("")}
                        </div>`;
                    setTimeout(() => {
                        panel.querySelector(".watch-pl-item.is-active")?.scrollIntoView({ block: "nearest" });
                    }, 150);
                }
            }
        } catch {}
    }

    const watchRecommendChipbar = document.getElementById("watchRecommendChipbar");
    const watchRecommendChannelChip = document.getElementById("watchRecommendChannelChip");
    const watchRecommendPrev = document.getElementById("watchRecommendPrev");
    const watchRecommendNext = document.getElementById("watchRecommendNext");

    if (watchRecommendChannelChip) {
        watchRecommendChannelChip.textContent = `${currentVideo.channel} 채널`;
    }

    if (watchRecommendChipbar && watchRecommendList) {
        watchRecommendChipbar.addEventListener("click", (event) => {
            const chip = event.target.closest(".recommend-chip");
            if (!chip || chip.classList.contains("is-active")) return;

            watchRecommendChipbar.querySelectorAll(".recommend-chip").forEach((btn) => btn.classList.remove("is-active"));
            chip.classList.add("is-active");

            const list = chip.dataset.filter === "channel" ? channelVideos : recommendVideos;
            watchRecommendList.innerHTML = list.length
                ? list.map(createRecommendCard).join("")
                : `<p style="color:#aaa; padding:12px;">표시할 영상이 없습니다.</p>`;
            watchRecommendList.scrollLeft = 0;
        });
    }

    function scrollWatchRecommendList(direction) {
        if (!watchRecommendList) return;
        watchRecommendList.scrollBy({ left: Math.round(watchRecommendList.clientWidth * 0.9) * direction, behavior: "smooth" });
    }

    if (watchRecommendPrev) watchRecommendPrev.addEventListener("click", () => scrollWatchRecommendList(-1));
    if (watchRecommendNext) watchRecommendNext.addEventListener("click", () => scrollWatchRecommendList(1));

    initCustomPlayer();
    initMiniPlayerWatcher(currentVideo);

    const startTime = parseFloat(params.get("t"));
    const fromMini  = params.get("autoplay") === "1";

    // 미니 플레이어에서 왔을 때: FLIP 애니메이션 (우측 하단에서 커지는 효과)
    if (fromMini) {
        const player = document.getElementById("customPlayer");
        if (player) {
            const rect = player.getBoundingClientRect();
            const mW = 320, mH = Math.round(320 * 9 / 16);
            const sx = mW / rect.width;
            const sy = mH / rect.height;
            const dx = (window.innerWidth  - 24 - mW / 2) - (rect.left + rect.width  / 2);
            const dy = (window.innerHeight - 24 - mH / 2) - (rect.top  + rect.height / 2);

            player.style.cssText += `
                transform-origin: center center;
                transform: translate(${dx}px, ${dy}px) scale(${sx}, ${sy});
                transition: none;
                border-radius: 12px;
                overflow: hidden;
                z-index: 100;
            `;
            requestAnimationFrame(() => requestAnimationFrame(() => {
                player.style.transition = "transform 0.42s cubic-bezier(0.4, 0, 0.2, 1), border-radius 0.42s";
                player.style.transform  = "";
                player.style.borderRadius = "";
                player.addEventListener("transitionend", () => {
                    player.style.zIndex = player.style.transition = "";
                }, { once: true });
            }));
        }
    }

    if (startTime > 0) {
        const playerVideo = document.querySelector("#customPlayer video.player-video");
        if (playerVideo) {
            const doSeek = () => {
                playerVideo.currentTime = startTime;
                if (fromMini) {
                    playerVideo.addEventListener("seeked", () => {
                        playerVideo.play().catch(() => {});
                    }, { once: true });
                }
            };
            if (playerVideo.readyState >= 1) doSeek();
            else playerVideo.addEventListener("loadedmetadata", doSeek, { once: true });
        }
    }

    // 이어보기: 저장된 재생 위치 확인 후 토스트 표시
    if (authMe.loggedIn && !fromMini && !(startTime > 0)) {
        const savedPos = await fetchVideoProgress(currentVideo.id);
        if (savedPos > 5) {
            const playerBox = document.querySelector(".player-box");
            if (playerBox) {
                const mins = Math.floor(savedPos / 60);
                const secs = Math.floor(savedPos % 60);
                const timeStr = mins > 0 ? `${mins}분 ${secs}초` : `${secs}초`;

                const toast = document.createElement("div");
                toast.className = "resume-toast";
                toast.innerHTML = `
                    <span class="resume-toast-text">${timeStr}에서 이어보기</span>
                    <button class="resume-toast-btn resume-btn" type="button">이어보기</button>
                    <button class="resume-toast-btn dismiss-btn" type="button">처음부터</button>
                `;
                playerBox.appendChild(toast);

                toast.querySelector(".resume-btn").addEventListener("click", () => {
                    const pv = document.querySelector("#customPlayer video.player-video");
                    if (pv) {
                        const doSeek = () => { pv.currentTime = savedPos; };
                        if (pv.readyState >= 1) doSeek();
                        else pv.addEventListener("loadedmetadata", doSeek, { once: true });
                    }
                    toast.remove();
                });
                toast.querySelector(".dismiss-btn").addEventListener("click", () => toast.remove());
                setTimeout(() => { if (toast.parentNode) toast.remove(); }, 6000);
            }
        }
    }

    // 재생 위치 자동 저장 (로그인 상태일 때)
    if (authMe.loggedIn) {
        const pv = document.querySelector("#customPlayer video.player-video");
        if (pv) {
            let lastSaved = 0;
            const doSave = () => {
                const pos = pv.currentTime;
                if (pos > 1 && Math.abs(pos - lastSaved) > 3) {
                    lastSaved = pos;
                    saveVideoProgress(currentVideo.id, pos);
                }
            };
            setInterval(doSave, 15000);
            pv.addEventListener("pause", doSave);
            document.addEventListener("visibilitychange", () => {
                if (document.visibilityState === "hidden") doSave();
            });
            window.addEventListener("beforeunload", doSave);
        }
    }

    // 영상 종료 후 다음 영상 오버레이
    {
        const pv = document.querySelector("#customPlayer video.player-video");
        const playerBox = document.querySelector(".player-box");
        const nextPlVideo = playlistIndex >= 0 && playlistIndex < playlistVideos.length - 1
            ? playlistVideos[playlistIndex + 1] : null;
        const nextVideo = nextPlVideo || recommendVideos[0];
        const getNextUrl = () => nextPlVideo
            ? `watch.html?v=${nextPlVideo.id}&list=${listId}`
            : getVideoUrl(nextVideo?.id);
        if (pv && playerBox && nextVideo) {
            pv.addEventListener("ended", () => {
                const overlay = document.createElement("div");
                overlay.className = "video-end-overlay";
                let countdown = 5;
                overlay.innerHTML = `
                    <div class="video-end-card">
                        <a href="${getNextUrl()}" class="video-end-thumb-link">
                            <img class="video-end-thumb" src="${escapeHtml(nextVideo.thumbnail)}" alt="${escapeHtml(nextVideo.title)}" />
                            <span class="video-end-duration">${escapeHtml(nextVideo.duration || "0:00")}</span>
                        </a>
                        <div class="video-end-info">
                            <p class="video-end-label">${nextPlVideo ? "재생목록 다음 영상" : "다음 영상"}</p>
                            <p class="video-end-title">${escapeHtml(nextVideo.title)}</p>
                            <p class="video-end-ch">${escapeHtml(nextVideo.channel)}</p>
                            <div class="video-end-actions">
                                <a href="${getNextUrl()}" class="video-end-play-btn">
                                    <span class="video-end-countdown" id="videoEndCountdown">${countdown}초 후 재생</span>
                                </a>
                                <button class="video-end-cancel-btn" type="button">취소</button>
                            </div>
                        </div>
                    </div>
                `;
                playerBox.appendChild(overlay);

                const countdownEl = overlay.querySelector("#videoEndCountdown");
                const timer = setInterval(() => {
                    countdown--;
                    if (countdownEl) countdownEl.textContent = `${countdown}초 후 재생`;
                    if (countdown <= 0) {
                        clearInterval(timer);
                        window.location.href = getNextUrl();
                    }
                }, 1000);

                overlay.querySelector(".video-end-cancel-btn").addEventListener("click", () => {
                    clearInterval(timer);
                    overlay.remove();
                });
            }, { once: true });
        }
    }

    const subscribeBtn = document.getElementById("subscribeBtn");
    const likeBtn = document.getElementById("likeBtn");
    const dislikeBtn = document.getElementById("dislikeBtn");
    const shareBtn = document.getElementById("shareBtn");
    const saveBtn = document.getElementById("saveBtn");
    const plAddBtn = document.getElementById("plAddBtn");
    const reportBtn = document.getElementById("reportBtn");
    const commentInput = document.getElementById("commentInput");
    const commentSubmitBtn = document.getElementById("commentSubmitBtn");
    const commentCancelBtn = document.getElementById("commentCancelBtn");
    const commentList = document.getElementById("commentList");
    const commentsCount = document.getElementById("commentsCount");
    const descriptionBox = document.getElementById("watchDescriptionBox");
    const descriptionToggle = document.getElementById("watchDescriptionToggle");

    // 챕터 클릭
    document.getElementById("chapterList")?.addEventListener("click", e => {
        const btn = e.target.closest(".chapter-item");
        if (btn) seekVideo(Number(btn.dataset.secs));
    });

    // 설명 내 타임스탬프 클릭
    document.getElementById("watchDescriptionText")?.addEventListener("click", e => {
        const btn = e.target.closest(".ts-link");
        if (btn) seekVideo(Number(btn.dataset.secs));
    });

    function refreshLikeButton() {
        if (!likeBtn) return;
        likeBtn.classList.toggle("active", isLiked);
        likeBtn.textContent = `좋아요 ${formatCount(likeCount)}`;
    }

    function refreshDislikeButton() {
        if (!dislikeBtn) return;
        dislikeBtn.classList.toggle("active", isDisliked);
        dislikeBtn.textContent = `싫어요 ${formatCount(dislikeCount)}`;
    }

    function refreshSubscribeButton() {
        if (!subscribeBtn) return;
        subscribeBtn.textContent = isSubscribed ? "구독중" : "구독";
        subscribeBtn.classList.toggle("primary", !isSubscribed);
    }

    function refreshSaveButton() {
        if (!saveBtn) return;
        saveBtn.textContent = isSaved ? "저장됨" : "저장";
        saveBtn.classList.toggle("active", isSaved);
    }

    async function loadMoreComments() {
        const btn = document.getElementById("commentLoadMore");
        if (btn) { btn.disabled = true; btn.textContent = "불러오는 중..."; }
        commentPage++;
        const data = await fetchCommentsByVideoId(currentVideo.id, commentPage);
        comments = [...comments, ...(data.comments || [])];
        commentHasMore = data.hasMore || false;
        refreshComments();
    }

    function getSortedComments() {
        if (commentSort === "top") {
            return [...comments].sort((a, b) => (Number(b.likeCount) || 0) - (Number(a.likeCount) || 0));
        }
        return comments;
    }

    function refreshComments() {
        const showLoadMore = commentSort === "latest" && commentHasMore;
        renderCommentList(commentList, commentsCount, getSortedComments(), commentTotal, showLoadMore, loadMoreComments);
        bindCommentActionButtons();
    }

    document.querySelectorAll(".comment-sort-btn").forEach(btn => {
        btn.addEventListener("click", async () => {
            const sort = btn.dataset.sort;
            if (sort === commentSort) return;
            commentSort = sort;
            document.querySelectorAll(".comment-sort-btn").forEach(b => b.classList.toggle("is-active", b.dataset.sort === sort));
            if (sort === "top" && commentHasMore) {
                const data = await fetchCommentsByVideoId(currentVideo.id, 0, 999);
                comments = data.comments || [];
                commentHasMore = false;
            }
            refreshComments();
        });
    });

    function findCommentOrReply(id) {
        for (const c of comments) {
            if (Number(c.id) === id) return c;
            for (const r of (c.replies || [])) {
                if (Number(r.id) === id) return r;
            }
        }
        return null;
    }

    function bindCommentActionButtons() {
        const editButtons = commentList?.querySelectorAll("[data-comment-edit]") || [];
        const deleteButtons = commentList?.querySelectorAll("[data-comment-delete]") || [];
        const cancelButtons = commentList?.querySelectorAll("[data-comment-edit-cancel]") || [];
        const saveButtons = commentList?.querySelectorAll("[data-comment-edit-save]") || [];
        const replyBtns = commentList?.querySelectorAll("[data-reply-to]") || [];
        const replyCancels = commentList?.querySelectorAll("[data-reply-cancel]") || [];
        const replySubmits = commentList?.querySelectorAll("[data-reply-submit]") || [];
        const replyInputs = commentList?.querySelectorAll("[data-reply-input]") || [];
        const likeBtns = commentList?.querySelectorAll("[data-comment-like]") || [];

        likeBtns.forEach(btn => {
            btn.addEventListener("click", async () => {
                const commentId = btn.dataset.commentLike;
                const res = await fetch(`/api/comments/${commentId}/like`, { method: "POST" });
                if (!res.ok) return;
                const data = await res.json();
                btn.dataset.liked = data.liked ? "true" : "false";
                btn.classList.toggle("is-liked", data.liked);
                const countEl = btn.querySelector(".comment-like-count");
                if (countEl) countEl.textContent = data.likeCount > 0 ? data.likeCount : "";
            });
        });

        // 답글 버튼 토글
        replyBtns.forEach(btn => {
            btn.addEventListener("click", () => {
                const parentId = btn.dataset.replyTo;
                const form = commentList.querySelector(`[data-reply-form="${parentId}"]`);
                if (!form) return;
                if (!authMe.loggedIn) { requireAuthRedirect(); return; }
                form.hidden = !form.hidden;
                if (!form.hidden) form.querySelector(`[data-reply-input]`)?.focus();
            });
        });

        // 답글 입력 → 버튼 활성화
        replyInputs.forEach(input => {
            const parentId = input.dataset.replyInput;
            const submitBtn = commentList.querySelector(`[data-reply-submit="${parentId}"]`);
            input.addEventListener("input", () => {
                if (submitBtn) submitBtn.disabled = input.value.trim() === "";
            });
        });

        // 답글 취소
        replyCancels.forEach(btn => {
            btn.addEventListener("click", () => {
                const parentId = btn.dataset.replyCancel;
                const form = commentList.querySelector(`[data-reply-form="${parentId}"]`);
                const input = commentList.querySelector(`[data-reply-input="${parentId}"]`);
                if (form) form.hidden = true;
                if (input) input.value = "";
                const submitBtn = commentList.querySelector(`[data-reply-submit="${parentId}"]`);
                if (submitBtn) submitBtn.disabled = true;
            });
        });

        // 답글 제출
        replySubmits.forEach(btn => {
            btn.addEventListener("click", async () => {
                const parentId = Number(btn.dataset.replySubmit);
                const input = commentList.querySelector(`[data-reply-input="${parentId}"]`);
                const text = input?.value.trim() || "";
                if (!text) return;
                btn.disabled = true;
                try {
                    const newReply = await createReplyByCommentId(parentId, text);
                    const parent = comments.find(c => Number(c.id) === parentId);
                    if (parent) {
                        parent.replies = parent.replies || [];
                        parent.replies.push(newReply);
                    }
                    commentTotal++;
                    const form = commentList.querySelector(`[data-reply-form="${parentId}"]`);
                    if (form) form.hidden = true;
                    if (input) input.value = "";
                    refreshComments();
                } catch (e) {
                    alert(e.message || "답글 작성 중 오류가 발생했습니다.");
                    btn.disabled = false;
                }
            });
        });

        editButtons.forEach((button) => {
            button.addEventListener("click", () => {
                const commentId = Number(button.dataset.commentEdit);
                const editBox = commentList.querySelector(`[data-comment-edit-box="${commentId}"]`);
                if (editBox) editBox.hidden = false;
            });
        });

        cancelButtons.forEach((button) => {
            button.addEventListener("click", () => {
                const commentId = Number(button.dataset.commentEditCancel);
                const editBox = commentList.querySelector(`[data-comment-edit-box="${commentId}"]`);
                const input = commentList.querySelector(`[data-comment-edit-input="${commentId}"]`);
                const original = findCommentOrReply(commentId);
                if (input && original) {
                    input.value = original.text || "";
                }

                if (editBox) editBox.hidden = true;
            });
        });

        saveButtons.forEach((button) => {
            button.addEventListener("click", async () => {
                const commentId = Number(button.dataset.commentEditSave);
                const input = commentList.querySelector(`[data-comment-edit-input="${commentId}"]`);
                const editBox = commentList.querySelector(`[data-comment-edit-box="${commentId}"]`);
                const nextText = input?.value.trim() || "";

                if (!nextText) {
                    alert("댓글 내용을 입력해줘.");
                    return;
                }

                try {
                    const updatedComment = await updateCommentById(commentId, nextText);
                    comments = comments.map(item => {
                        if (Number(item.id) === commentId) return { ...updatedComment, replies: item.replies || [] };
                        item.replies = (item.replies || []).map(r => Number(r.id) === commentId ? updatedComment : r);
                        return item;
                    });
                    refreshComments();
                } catch (error) {
                    alert(error.message || "댓글 수정 중 오류가 발생했어.");
                    if (editBox) editBox.hidden = false;
                }
            });
        });

        deleteButtons.forEach((button) => {
            button.addEventListener("click", async () => {
                const commentId = Number(button.dataset.commentDelete);
                const ok = await confirmAction("이 댓글을 삭제할까요?", "삭제");
                if (!ok) return;

                try {
                    await deleteCommentById(commentId);
                    const deleted = comments.find(item => Number(item.id) === commentId);
                    const deletedReplies = deleted?.replies?.length || 0;
                    comments = comments.filter(item => Number(item.id) !== commentId);
                    comments = comments.map(item => {
                        const before = item.replies?.length || 0;
                        item.replies = (item.replies || []).filter(r => Number(r.id) !== commentId);
                        if (item.replies.length < before) commentTotal--;
                        return item;
                    });
                    if (deleted) commentTotal -= (1 + deletedReplies);
                    refreshComments();
                    showToast("댓글이 삭제되었습니다.");
                } catch (error) {
                    alert(error.message || "댓글 삭제 중 오류가 발생했어.");
                }
            });
        });
    }

    subscribeBtn?.addEventListener("click", async () => {
        if (!requireAuthRedirect()) return;
        try {
            const result = await toggleSubscription(currentVideo.ownerId);
            isSubscribed = Boolean(result.subscribed);
            subscriberCount = Number(result.subscriberCount || 0);
            refreshSubscribeButton();
            const countEl = document.getElementById("subscriberCount");
            if (countEl) countEl.textContent = `구독자 ${formatCount(subscriberCount)}명`;
        } catch {
            showToast("구독 처리 중 오류가 발생했습니다.");
        }
    });

    likeBtn?.addEventListener("click", async () => {
        if (!requireAuthRedirect()) return;

        try {
            const result = await toggleLikeByVideoId(currentVideo.id);
            isLiked = Boolean(result.liked);
            likeCount = Number(result.likeCount || 0);
            // 상호배타 — 좋아요를 누르면 서버가 싫어요를 해제하므로 함께 반영
            isDisliked = Boolean(result.disliked);
            dislikeCount = Number(result.dislikeCount || 0);
            refreshLikeButton();
            refreshDislikeButton();
        } catch (error) {
            alert(error.message || "좋아요 처리 중 오류가 발생했어.");
        }
    });

    dislikeBtn?.addEventListener("click", async () => {
        if (!requireAuthRedirect()) return;

        try {
            const result = await toggleDislikeByVideoId(currentVideo.id);
            isDisliked = Boolean(result.disliked);
            dislikeCount = Number(result.dislikeCount || 0);
            // 상호배타 — 싫어요를 누르면 서버가 좋아요를 해제하므로 함께 반영
            isLiked = Boolean(result.liked);
            likeCount = Number(result.likeCount || 0);
            refreshDislikeButton();
            refreshLikeButton();
        } catch (error) {
            alert(error.message || "싫어요 처리 중 오류가 발생했어.");
        }
    });

    shareBtn?.addEventListener("click", () => {
        const pv = document.querySelector("#customPlayer video.player-video");
        const getCurrentTime = () => pv ? pv.currentTime : 0;
        showShareModal(currentVideo.id, getCurrentTime, {
            title: currentVideo.title,
            description: currentVideo.description,
            thumbnail: currentVideo.thumbnail,
        });
    });

    saveBtn?.addEventListener("click", async () => {
        if (!requireAuthRedirect()) return;

        try {
            const result = await toggleSaveByVideoId(currentVideo.id);
            isSaved = Boolean(result.saved);
            refreshSaveButton();
            showToast(isSaved ? "영상이 저장되었습니다." : "저장이 해제되었습니다.");
        } catch (error) {
            alert(error.message || "저장 처리 중 오류가 발생했어.");
        }
    });

    plAddBtn?.addEventListener("click", () => {
        if (!requireAuthRedirect()) return;
        window.__openPlaylistMenu?.(plAddBtn, currentVideo.id);
    });

    reportBtn?.addEventListener("click", () => {
        if (!requireAuthRedirect()) return;
        showReportModal(currentVideo.id);
    });

    descriptionToggle?.addEventListener("click", () => {
        const collapsed = descriptionBox.classList.toggle("is-collapsed");
        descriptionToggle.textContent = collapsed ? "더보기" : "접기";
    });

    commentInput?.addEventListener("focus", () => {
        if (!authMe.loggedIn) {
            requireAuthRedirect();
        }
    });

    const commentCharCounter = document.getElementById("commentCharCounter");

    function updateCommentCounter() {
        if (!commentCharCounter || !commentInput) return;
        const len = commentInput.value.length;
        commentCharCounter.textContent = `${len}/300`;
        commentCharCounter.style.display = len > 0 ? "inline" : "none";
        commentCharCounter.classList.toggle("near-limit", len >= 270 && len < 300);
        commentCharCounter.classList.toggle("at-limit", len >= 300);
    }

    commentInput?.addEventListener("input", () => {
        if (!commentSubmitBtn) return;

        if (!authMe.loggedIn) {
            commentSubmitBtn.disabled = true;
            return;
        }

        commentSubmitBtn.disabled = commentInput.value.trim() === "";
        updateCommentCounter();
    });

    commentInput?.addEventListener("keydown", (event) => {
        if (!authMe.loggedIn) {
            requireAuthRedirect();
            return;
        }

        if (event.key === "Enter" && !event.shiftKey) {
            event.preventDefault();

            if (!commentSubmitBtn?.disabled) {
                commentSubmitBtn.click();
            }
        }
    });

    commentCancelBtn?.addEventListener("click", () => {
        if (commentInput) commentInput.value = "";
        if (commentSubmitBtn) commentSubmitBtn.disabled = true;
        updateCommentCounter();
    });

    commentSubmitBtn?.addEventListener("click", async () => {
        if (!requireAuthRedirect()) return;

        const text = commentInput.value.trim();
        if (!text) return;

        try {
            const createdComment = await createCommentByVideoId(currentVideo.id, text);
            comments.unshift(createdComment);
            commentTotal++;
            refreshComments();
            commentInput.value = "";
            commentSubmitBtn.disabled = true;
            updateCommentCounter();
        } catch (error) {
            alert(error.message || "댓글 작성 중 오류가 발생했어.");
        }
    });

    refreshLikeButton();
    refreshDislikeButton();
    refreshSubscribeButton();
    refreshSaveButton();
    refreshComments();
}

