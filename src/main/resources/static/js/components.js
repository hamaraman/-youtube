/* MyTube - components.js : 카드, 플레이어, 댓글 컴포넌트 */

function parseDurationSecs(str) {
    if (!str) return 0;
    const parts = str.trim().split(":").map(Number);
    if (parts.length === 3) return parts[0] * 3600 + parts[1] * 60 + parts[2];
    if (parts.length === 2) return parts[0] * 60 + parts[1];
    return 0;
}

async function fetchServerProgress() {
    if (_serverProgressMap !== null) return _serverProgressMap;
    if (_serverProgressFetch) return _serverProgressFetch;
    _serverProgressFetch = fetch("/api/my-progress")
        .then(r => r.ok ? r.json() : {})
        .catch(() => ({}))
        .then(data => { _serverProgressMap = data; return data; });
    return _serverProgressFetch;
}

async function applyServerProgress() {
    const map = await fetchServerProgress();
    if (!map || !Object.keys(map).length) return;
    document.querySelectorAll(".card[data-video-id]").forEach(card => {
        const videoId = String(card.dataset.videoId);
        const lastPos = map[videoId];
        if (!lastPos || lastPos <= 0) return;
        const durEl = card.querySelector(".duration");
        const totalSecs = parseDurationSecs(durEl?.textContent);
        if (!totalSecs) return;
        const pct = Math.min(100, (lastPos / totalSecs) * 100);
        if (pct < 1) return;
        let bar = card.querySelector(".card-progress-bar");
        if (bar) {
            bar.style.width = pct.toFixed(1) + "%";
        } else {
            bar = document.createElement("div");
            bar.className = "card-progress-bar";
            bar.style.width = pct.toFixed(1) + "%";
            card.querySelector(".thumbnail-wrap")?.appendChild(bar);
        }
    });
}

function getVideoProgress(videoId) {
    try {
        const state = getMpState();
        if (state && String(state.videoId) === String(videoId) && state.duration > 0) {
            return Math.min(100, (state.currentTime / state.duration) * 100);
        }
        const map = JSON.parse(localStorage.getItem("mt-video-progress") || "{}");
        const entry = map[String(videoId)];
        if (entry && entry.duration > 0) {
            return Math.min(100, (entry.currentTime / entry.duration) * 100);
        }
    } catch {}
    return 0;
}

function createVideoCard(video) {
    const viewCount = loadViewCount(video);
    const likeCount = Number(video.likeCount || 0);
    const commentCount = Number(video.commentCount || 0);
    const stats = [
        `조회수 ${formatCount(viewCount)}회`,
        likeCount > 0 ? `👍 ${formatCount(likeCount)}` : null,
        commentCount > 0 ? `💬 ${formatCount(commentCount)}` : null,
    ].filter(Boolean).join(" · ");

    const progress = getVideoProgress(video.id);
    const progressBar = progress > 1 ? `<div class="card-progress-bar" style="width:${progress.toFixed(1)}%"></div>` : "";

    return `
    <article class="card" data-video-id="${video.id}">
      <a href="${getVideoUrl(video.id)}" class="card-link">
        <div class="thumbnail-wrap">
          <img class="thumbnail-image" src="${escapeHtml(video.thumbnail)}" alt="${escapeHtml(video.title)}" />
          <span class="duration">${escapeHtml(video.duration || "0:00")}</span>
          ${progressBar}
          <button type="button" class="card-pl-btn" data-video-id="${video.id}" title="재생목록에 추가" aria-label="재생목록에 추가">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M3 5h12v2H3V5zm0 4h12v2H3V9zm0 4h8v2H3v-2zm13 3v-6l5 3-5 3z"/></svg>
          </button>
        </div>
        <div class="meta">
          <a href="user.html?id=${video.ownerId}" class="avatar-link" onclick="event.stopPropagation()">
            <img class="avatar-image" src="${escapeHtml(video.avatar || DEFAULT_AVATAR)}" alt="${escapeHtml(video.channel)}" />
          </a>
          <div class="text">
            <h3>${escapeHtml(video.title)}</h3>
            <div class="channel-row">
              <a href="user.html?id=${video.ownerId}" class="channel-name channel-link" onclick="event.stopPropagation()">${escapeHtml(video.channel)}</a>
              <button type="button" class="card-sub-btn" data-owner-id="${video.ownerId}" onclick="event.stopPropagation()" style="display:none">구독</button>
            </div>
            <p class="video-info">${stats} · ${escapeHtml(video.date || "방금 전")}</p>
          </div>
        </div>
      </a>
    </article>
  `;
}

function createSavedVideoCard(video) {
    const viewCount = loadViewCount(video);
    const likeCount = Number(video.likeCount || 0);
    const commentCount = Number(video.commentCount || 0);
    const stats = [
        `조회수 ${formatCount(viewCount)}회`,
        likeCount > 0 ? `👍 ${formatCount(likeCount)}` : null,
        commentCount > 0 ? `💬 ${formatCount(commentCount)}` : null,
    ].filter(Boolean).join(" · ");

    return `
    <article class="card saved-card" data-saved-card-id="${video.id}">
      <a href="${getVideoUrl(video.id)}" class="card-link">
        <div class="thumbnail-wrap">
          <img class="thumbnail-image" src="${escapeHtml(video.thumbnail)}" alt="${escapeHtml(video.title)}" />
          <span class="duration">${escapeHtml(video.duration || "0:00")}</span>
        </div>
        <div class="meta">
          <a href="user.html?id=${video.ownerId}" class="avatar-link" onclick="event.stopPropagation()">
            <img class="avatar-image" src="${escapeHtml(video.avatar || DEFAULT_AVATAR)}" alt="${escapeHtml(video.channel)}" />
          </a>
          <div class="text">
            <h3>${escapeHtml(video.title)}</h3>
            <a href="user.html?id=${video.ownerId}" class="channel-name channel-link" onclick="event.stopPropagation()">${escapeHtml(video.channel)}</a>
            <p class="video-info">${stats} · ${escapeHtml(video.date || "방금 전")}</p>
          </div>
        </div>
      </a>
      <div class="saved-card-actions">
        <button type="button" class="saved-remove-btn" data-unsave-id="${video.id}">저장 해제</button>
      </div>
    </article>
  `;
}

function createLikedVideoCard(video) {
    const viewCount = loadViewCount(video);
    const likeCount = Number(video.likeCount || 0);
    const commentCount = Number(video.commentCount || 0);
    const stats = [
        `조회수 ${formatCount(viewCount)}회`,
        likeCount > 0 ? `👍 ${formatCount(likeCount)}` : null,
        commentCount > 0 ? `💬 ${formatCount(commentCount)}` : null,
    ].filter(Boolean).join(" · ");

    return `
    <article class="card liked-card" data-liked-card-id="${video.id}">
      <a href="${getVideoUrl(video.id)}" class="card-link">
        <div class="thumbnail-wrap">
          <img class="thumbnail-image" src="${escapeHtml(video.thumbnail)}" alt="${escapeHtml(video.title)}" />
          <span class="duration">${escapeHtml(video.duration || "0:00")}</span>
        </div>
        <div class="meta">
          <a href="user.html?id=${video.ownerId}" class="avatar-link" onclick="event.stopPropagation()">
            <img class="avatar-image" src="${escapeHtml(video.avatar || DEFAULT_AVATAR)}" alt="${escapeHtml(video.channel)}" />
          </a>
          <div class="text">
            <h3>${escapeHtml(video.title)}</h3>
            <a href="user.html?id=${video.ownerId}" class="channel-name channel-link" onclick="event.stopPropagation()">${escapeHtml(video.channel)}</a>
            <p class="video-info">${stats} · ${escapeHtml(video.date || "방금 전")}</p>
          </div>
        </div>
      </a>
      <div class="liked-card-actions">
        <button type="button" class="liked-remove-btn" data-unlike-id="${video.id}">좋아요 취소</button>
      </div>
    </article>
  `;
}

function createRecommendCard(video) {
    const viewCount = loadViewCount(video);
    const tag = video._recTag
        ? `<span class="recommend-tag">${escapeHtml(video._recTag)}</span>`
        : "";

    return `
    <a class="recommend-card" href="${getVideoUrl(video.id)}">
      <div class="recommend-thumb">
        <img src="${escapeHtml(video.thumbnail)}" alt="${escapeHtml(video.title)}" />
        <span class="recommend-duration">${escapeHtml(video.duration || "0:00")}</span>
      </div>
      <div class="recommend-info">
        <h4 class="recommend-title">${escapeHtml(video.title)}</h4>
        <p class="recommend-meta">${escapeHtml(video.channel)}${tag}</p>
        <p class="recommend-meta">조회수 ${formatCount(viewCount)}회 · ${escapeHtml(video.date || "방금 전")}</p>
      </div>
    </a>
  `;
}

function createManageCard(video) {
    const viewCount = loadViewCount(video);
    const likeCount = Number(video.likeCount || 0);
    const commentCount = Number(video.commentCount || 0);

    return `
    <article class="studio-row" data-id="${video.id}">
      <div class="studio-video-cell">
        <a href="${getVideoUrl(video.id)}" class="studio-thumb">
          <img src="${escapeHtml(video.thumbnail)}" alt="${escapeHtml(video.title)}" />
          <span class="studio-duration">${escapeHtml(video.duration || "0:00")}</span>
        </a>

        <div class="studio-video-info">
          <h4 class="studio-video-title">${escapeHtml(video.title)}</h4>
          <p class="studio-video-meta">${escapeHtml(video.channel)} · 조회수 ${formatCount(viewCount)}회 · ${escapeHtml(video.date || "방금 전")}</p>
          <p class="studio-video-desc">${escapeHtml(video.description || "")}</p>
          <span class="studio-encode-badge" id="encodeBadge-${video.id}" style="display:none;"></span>
        </div>
      </div>

      <div class="studio-cell-text">${escapeHtml(video.category || "미분류")}</div>
      <div class="studio-number">${formatCount(viewCount)}</div>
      <div class="studio-number">${formatCount(likeCount)}</div>
      <div class="studio-number">${formatCount(commentCount)}</div>
      <div class="studio-status-cell">
        <span class="studio-status-badge">${escapeHtml(video.visibility || "공개")}</span>
      </div>
      <div class="studio-mobile-stats">
        <span>조회 ${formatCount(viewCount)}</span>
        <span class="studio-mobile-dot">·</span>
        <span>좋아요 ${formatCount(likeCount)}</span>
        <span class="studio-mobile-dot">·</span>
        <span>댓글 ${formatCount(commentCount)}</span>
        <span class="studio-status-badge">${escapeHtml(video.visibility || "공개")}</span>
      </div>
      <div class="studio-actions">
        <a href="${getVideoUrl(video.id)}" class="studio-action-btn edit">영상 보기</a>
        <button type="button" class="studio-action-btn edit" data-copy-id="${video.id}">링크 복사</button>
        <a href="${getEditUrl(video.id)}" class="studio-action-btn edit">수정</a>
        <button type="button" class="studio-action-btn delete" data-delete-id="${video.id}">삭제</button>
      </div>
    </article>
  `;
}

function createPlayerMarkup(video, resolutionOptions) {
    if (video.videoUrl) {
        const hasThumbnail = video.thumbnail && !video.thumbnail.startsWith("data:");
        const thumbHtml = hasThumbnail
            ? `<img class="cp-thumb-cover" id="cpThumbCover" src="${escapeHtml(video.thumbnail)}" alt="">`
            : "";
        const hasQuality = resolutionOptions && resolutionOptions.length > 0;
        const qualityOptionsHtml = hasQuality
            ? resolutionOptions.map((opt, i) =>
                `<div class="cp-set-option${i === 0 ? " active" : ""}" data-src="${escapeHtml(opt.src)}" data-label="${escapeHtml(opt.label)}">${escapeHtml(opt.label)}</div>`
              ).join("")
            : "";
        const settingsMenuHtml = `
          <div class="cp-settings-wrap" id="cpSettingsWrap">
            <button class="cp-btn" id="cpSettingsBtn" type="button" title="설정">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M19.14 12.94c.04-.3.06-.61.06-.94s-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.49.49 0 0 0-.59-.22l-2.39.96a7 7 0 0 0-1.62-.94l-.36-2.54a.484.484 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54a7.21 7.21 0 0 0-1.62.94l-2.39-.96a.48.48 0 0 0-.59.22L2.74 8.87a.48.48 0 0 0 .12.61l2.03 1.58c-.05.3-.07.62-.07.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.36 1.04.67 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54a7.21 7.21 0 0 0 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32a.48.48 0 0 0-.12-.61l-2.03-1.58zM12 15.6a3.6 3.6 0 1 1 0-7.2 3.6 3.6 0 0 1 0 7.2z"/></svg>
            </button>
            <div class="cp-settings-menu" id="cpSettingsMenu">
              <div class="cp-set-panel" id="cpSetMain">
                <div class="cp-set-item" id="cpSetSpeedItem">
                  <span>재생 속도</span><span class="cp-set-val" id="cpSetSpeedVal">보통</span>
                </div>
                ${hasQuality ? `<div class="cp-set-item" id="cpSetQualItem">
                  <span>화질</span><span class="cp-set-val" id="cpSetQualVal">${escapeHtml(resolutionOptions[0]?.label || "원본")}</span>
                </div>` : ""}
              </div>
              <div class="cp-set-panel" id="cpSetSpeedPanel" style="display:none">
                <div class="cp-set-back" id="cpSetSpeedBack">재생 속도</div>
                ${["0.5","0.75","1","1.25","1.5","2"].map(s =>
                  `<div class="cp-set-option${s==="1"?" active":""}" data-speed="${s}">${s==="1"?"보통":s+"x"}</div>`
                ).join("")}
              </div>
              ${hasQuality ? `<div class="cp-set-panel" id="cpSetQualPanel" style="display:none">
                <div class="cp-set-back" id="cpSetQualBack">화질</div>
                ${qualityOptionsHtml}
              </div>` : ""}
            </div>
          </div>`;
        return `<div class="custom-player" id="customPlayer">
          <video class="player-video" preload="metadata" src="${escapeHtml(video.videoUrl)}"></video>
          ${thumbHtml}
          <div class="cp-skip-zone left-zone" id="cpSkipLeft">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor"><path d="M6 6h2v12H6zm3.5 6 8.5 6V6z"/></svg>
            <span>10초</span>
          </div>
          <div class="cp-skip-zone right-zone" id="cpSkipRight">
            <span>10초</span>
            <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor"><path d="M6 18l8.5-6L6 6v12zm2.5-6 6-4.5v9L8.5 12zM16 6h2v12h-2z"/></svg>
          </div>
          <div class="cp-controls">
            <div class="cp-progress-wrap">
              <input type="range" class="cp-progress" id="cpProgress" min="0" max="100" step="0.1" value="0">
              <div class="cp-time-tooltip" id="cpTimeTooltip"></div>
            </div>
            <div class="cp-bar">
              <div class="cp-bar-left">
                <button class="cp-btn" id="cpPlay" type="button">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
                </button>
                <button class="cp-btn" id="cpSkipBack" type="button" title="-10초">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6H4c0 4.42 3.58 8 8 8s8-3.58 8-8-3.58-8-8-8zm-1.1 11H10v-3.26L9 13.12V12.26l1.85-.61h.05V16zm4.28-1.41c0 .84-.16 1.43-.49 1.77-.32.33-.72.5-1.19.5-.45 0-.83-.16-1.14-.49s-.48-.9-.49-1.71v-.91c0-.83.16-1.41.49-1.76.32-.34.72-.51 1.19-.51.46 0 .84.16 1.15.49.31.32.47.88.48 1.68v.94zm-.92-.9c0-.46-.05-.79-.16-.98-.1-.19-.26-.28-.46-.28-.19 0-.34.09-.44.27-.1.18-.16.49-.16.94v1.15c0 .47.05.81.16 1.01.11.2.26.3.46.3.19 0 .34-.09.44-.28.1-.19.16-.52.16-.97v-1.16z"/></svg>
                </button>
                <button class="cp-btn" id="cpSkipFwd" type="button" title="+10초">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M12 5V1l5 5-5 5V7c-3.31 0-6 2.69-6 6s2.69 6 6 6 6-2.69 6-6h2c0 4.42-3.58 8-8 8s-8-3.58-8-8 3.58-8 8-8zm-1.1 11H10v-3.26L9 13.12V12.26l1.85-.61h.05V16zm4.28-1.41c0 .84-.16 1.43-.49 1.77-.32.33-.72.5-1.19.5-.45 0-.83-.16-1.14-.49s-.48-.9-.49-1.71v-.91c0-.83.16-1.41.49-1.76.32-.34.72-.51 1.19-.51.46 0 .84.16 1.15.49.31.32.47.88.48 1.68v.94zm-.92-.9c0-.46-.05-.79-.16-.98-.1-.19-.26-.28-.46-.28-.19 0-.34.09-.44.27-.1.18-.16.49-.16.94v1.15c0 .47.05.81.16 1.01.11.2.26.3.46.3.19 0 .34-.09.44-.28.1-.19.16-.52.16-.97v-1.16z"/></svg>
                </button>
                <div class="cp-vol-wrap">
                  <button class="cp-btn" id="cpVol" type="button">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02z"/></svg>
                  </button>
                  <input type="range" class="cp-volume" id="cpVolume" min="0" max="1" step="0.05" value="1">
                </div>
                <span class="cp-time" id="cpTime">0:00 / 0:00</span>
              </div>
              <div class="cp-bar-right">
                ${settingsMenuHtml}
                <button class="cp-btn" id="cpMini" type="button" title="미니 플레이어">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M21 3H3c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H3V5h18v14zm-10-7h8v5h-8z"/></svg>
                </button>
                <button class="cp-btn" id="cpTheater" type="button" title="극장 모드 (t)">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M19 6H5c-1.1 0-2 .9-2 2v8c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm0 10H5V8h14v8z"/></svg>
                </button>
                <button class="cp-btn" id="cpFs" type="button">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z"/></svg>
                </button>
              </div>
            </div>
          </div>
        </div>`;
    }

    if (video.embedUrl) {
        return `<iframe class="player-iframe"
          src="${escapeHtml(video.embedUrl)}"
          title="${escapeHtml(video.title)}"
          allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
          allowfullscreen
          referrerpolicy="strict-origin-when-cross-origin"></iframe>`;
    }

    return `<img src="${escapeHtml(video.thumbnail)}" alt="${escapeHtml(video.title)}" />`;
}

// ── 미니 플레이어 ──────────────────────────────────────────
const MP_KEY = '__miniPlayer__';
function getMpState() { try { return JSON.parse(localStorage.getItem(MP_KEY)); } catch { return null; } }
function setMpState(s) { localStorage.setItem(MP_KEY, JSON.stringify(s)); }
function clearMpState() { localStorage.removeItem(MP_KEY); }

function navigateTo(url, ms) {
    const t = ms ?? 180;
    document.body.style.transition = `opacity ${t}ms ease`;
    document.body.style.opacity = "0";
    setTimeout(() => { window.location.href = url; }, t);
}

function initResumeBar() {
    const state = getMpState();
    if (!state || !state.videoId) return;

    const params = new URLSearchParams(window.location.search);
    const vid = params.get('v') || params.get('id') || params.get('videoId');
    if (vid && String(vid) === String(state.videoId)) return;
    if (document.getElementById('resumeBar')) return;

    const t = Math.floor(state.currentTime || 0);
    const duration = state.duration || 0;
    const progress = duration > 0 ? Math.min(100, (t / duration) * 100).toFixed(1) : 0;
    const mins = Math.floor(t / 60);
    const secs = t % 60;
    const timeStr = mins > 0 ? `${mins}분 ${secs}초` : `${secs}초`;

    const bar = document.createElement('div');
    bar.id = 'resumeBar';
    bar.className = 'resume-bar';
    bar.innerHTML = `
        <div class="resume-bar-thumb-wrap">
            <img class="resume-bar-thumb" src="${escapeHtml(state.thumbnail || '')}" alt="" />
            ${duration > 0 ? `<div class="resume-bar-thumb-progress" style="width:${progress}%"></div>` : ''}
        </div>
        <div class="resume-bar-info">
            <div class="resume-bar-title">${escapeHtml(state.title || '')}</div>
            <div class="resume-bar-meta">${escapeHtml(state.channel || '')} · ${timeStr} 지점</div>
            ${duration > 0 ? `<div class="resume-bar-track"><div class="resume-bar-fill" style="width:${progress}%"></div></div>` : ''}
        </div>
        <button class="resume-bar-play" id="resumeBarPlay" type="button">이어보기</button>
        <button class="resume-bar-close" id="resumeBarClose" type="button" aria-label="닫기">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>
        </button>
    `;
    document.body.appendChild(bar);
    requestAnimationFrame(() => bar.classList.add('show'));

    bar.querySelector('#resumeBarPlay').addEventListener('click', () => {
        window.location.href = `watch.html?v=${state.videoId}&t=${t}`;
    });
    bar.querySelector('#resumeBarClose').addEventListener('click', () => {
        clearMpState();
        bar.classList.remove('show');
        bar.addEventListener('transitionend', () => bar.remove(), { once: true });
    });
}

function initMiniPlayerWatcher(videoData) {
    const player = document.getElementById("customPlayer");
    if (!player) return;
    const video = player.querySelector("video.player-video");
    if (!video) return;
    const miniBtn = document.getElementById("cpMini");

    const saveState = (playing) => {
        setMpState({
            active: true,
            videoId: videoData.id,
            videoUrl: video.src || videoData.videoUrl,
            title: videoData.title,
            thumbnail: videoData.thumbnail,
            channel: videoData.channel,
            currentTime: video.currentTime,
            duration: video.duration || 0,
            playing: playing
        });
        try {
            const map = JSON.parse(localStorage.getItem("mt-video-progress") || "{}");
            map[String(videoData.id)] = { currentTime: video.currentTime, duration: video.duration || 0 };
            const keys = Object.keys(map);
            if (keys.length > 100) delete map[keys[0]];
            localStorage.setItem("mt-video-progress", JSON.stringify(map));
        } catch {}
    };

    let saveInterval;
    video.addEventListener('play', () => {
        saveState(true);
        clearInterval(saveInterval);
        saveInterval = setInterval(() => saveState(!video.paused), 3000);
    });
    video.addEventListener('pause', () => saveState(false));
    video.addEventListener('ended', () => { clearInterval(saveInterval); clearMpState(); });

    // 이어보기 버튼: 상태 저장 후 이전 페이지로 (이어보기 바로 이어짐)
    miniBtn?.addEventListener('click', (e) => {
        e.stopPropagation();
        saveState(!video.paused);
        const prev = document.referrer;
        const dest = (prev && !prev.includes('watch.html')) ? prev : 'index.html';
        navigateTo(dest);
    });
}
// ──────────────────────────────────────────────────────────

function initCustomPlayer() {
    const player = document.getElementById("customPlayer");
    if (!player) return;
    const video = player.querySelector("video.player-video");
    if (!video) return;

    const playBtn   = document.getElementById("cpPlay");
    const progress  = document.getElementById("cpProgress");
    const timeEl    = document.getElementById("cpTime");
    const volBtn    = document.getElementById("cpVol");
    const volSlider = document.getElementById("cpVolume");
    const fsBtn     = document.getElementById("cpFs");
    const theaterBtn = document.getElementById("cpTheater");

    const I_PLAY   = `<svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>`;
    const I_PAUSE  = `<svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>`;
    const I_VOL    = `<svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02z"/></svg>`;
    const I_MUTE   = `<svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M16.5 12c0-1.77-1.02-3.29-2.5-4.03v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51C20.63 14.91 21 13.5 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3L3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06c1.38-.31 2.63-.95 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4L9.91 6.09 12 8.18V4z"/></svg>`;
    const I_FS     = `<svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z"/></svg>`;
    const I_EXIT_FS= `<svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M5 16h3v3h2v-5H5v2zm3-8H5v2h5V5H8v3zm6 11h2v-3h3v-2h-5v5zm2-11V5h-2v5h5V8h-3z"/></svg>`;

    function fmt(s) {
        s = Math.floor(s || 0);
        return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
    }

    // 첫 재생 시 썸네일 커버 페이드아웃
    const thumbCover = document.getElementById("cpThumbCover");
    if (thumbCover) {
        video.addEventListener("playing", () => {
            thumbCover.style.transition = "opacity 0.3s ease";
            thumbCover.style.opacity = "0";
            thumbCover.addEventListener("transitionend", () => thumbCover.remove(), { once: true });
        }, { once: true });
    }

    // 플레이어 클릭 → 재생/일시정지, 더블클릭 → 전체화면
    let clickTimer;
    player.addEventListener("click", (e) => {
        if (e.target.closest(".cp-controls")) return;
        clearTimeout(clickTimer);
        clickTimer = setTimeout(() => {
            video.paused ? video.play() : video.pause();
        }, 220);
    });
    player.addEventListener("dblclick", (e) => {
        if (e.target.closest(".cp-controls")) return;
        clearTimeout(clickTimer);
        const rect = player.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const thirds = rect.width / 3;
        if (x < thirds) {
            video.currentTime = Math.max(0, video.currentTime - 10);
            flashSkipZone(document.getElementById("cpSkipLeft"));
        } else if (x > rect.width - thirds) {
            video.currentTime = Math.min(video.duration || 0, video.currentTime + 10);
            flashSkipZone(document.getElementById("cpSkipRight"));
        } else {
            document.fullscreenElement ? document.exitFullscreen() : player.requestFullscreen();
        }
    });

    // 모바일 터치 지원
    let lastTapTime = 0;
    let tapTimer;
    player.addEventListener("touchstart", (e) => {
        bumpControls();

        if (e.target.closest(".cp-controls")) return;

        const now = Date.now();
        const timeSinceLast = now - lastTapTime;
        lastTapTime = now;

        if (timeSinceLast < 300) {
            // 더블탭 → 전체화면
            clearTimeout(tapTimer);
            document.fullscreenElement ? document.exitFullscreen() : player.requestFullscreen();
        } else {
            // 싱글탭 → 컨트롤 보이면 재생/일시정지, 안 보이면 컨트롤만 표시
            tapTimer = setTimeout(() => {
                if (player.classList.contains("show-controls")) {
                    video.paused ? video.play() : video.pause();
                }
            }, 220);
        }
    }, { passive: true });

    playBtn?.addEventListener("click", (e) => {
        e.stopPropagation();
        video.paused ? video.play() : video.pause();
    });
    video.addEventListener("play",  () => { if (playBtn) playBtn.innerHTML = I_PAUSE; });
    video.addEventListener("pause", () => { if (playBtn) playBtn.innerHTML = I_PLAY; });

    // 프로그레스바
    video.addEventListener("timeupdate", () => {
        if (!video.duration) return;
        if (progress) progress.value = (video.currentTime / video.duration) * 100;
        if (timeEl) timeEl.textContent = `${fmt(video.currentTime)} / ${fmt(video.duration)}`;
    });
    video.addEventListener("loadedmetadata", () => {
        if (timeEl) timeEl.textContent = `0:00 / ${fmt(video.duration)}`;
    });
    progress?.addEventListener("input", () => {
        if (video.duration) video.currentTime = (Number(progress.value) / 100) * video.duration;
    });

    // 볼륨
    volBtn?.addEventListener("click", (e) => {
        e.stopPropagation();
        video.muted = !video.muted;
        if (volBtn) volBtn.innerHTML = video.muted ? I_MUTE : I_VOL;
        if (volSlider) volSlider.value = video.muted ? 0 : video.volume;
    });
    volSlider?.addEventListener("input", (e) => {
        e.stopPropagation();
        video.volume = Number(volSlider.value);
        video.muted = video.volume === 0;
        if (volBtn) volBtn.innerHTML = video.muted ? I_MUTE : I_VOL;
    });

    // 극장 모드
    const I_THEATER     = `<svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M19 6H5c-1.1 0-2 .9-2 2v8c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm0 10H5V8h14v8z"/></svg>`;
    const I_THEATER_EXIT= `<svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M21 3H3c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H3V5h18v14z"/></svg>`;
    function toggleTheater() {
        const isTheater = document.body.classList.toggle("theater-mode");
        if (theaterBtn) theaterBtn.innerHTML = isTheater ? I_THEATER_EXIT : I_THEATER;
        try { localStorage.setItem("mt-theater", isTheater ? "1" : "0"); } catch {}
    }
    theaterBtn?.addEventListener("click", (e) => { e.stopPropagation(); toggleTheater(); });
    document.addEventListener("keydown", (e) => {
        if (e.key === "t" && !e.target.matches("input,textarea,[contenteditable]")) toggleTheater();
    });
    try {
        if (localStorage.getItem("mt-theater") === "1") {
            document.body.classList.add("theater-mode");
            if (theaterBtn) theaterBtn.innerHTML = I_THEATER_EXIT;
        }
    } catch {}

    // 전체화면
    fsBtn?.addEventListener("click", (e) => {
        e.stopPropagation();
        document.fullscreenElement ? document.exitFullscreen() : player.requestFullscreen();
    });
    document.addEventListener("fullscreenchange", () => {
        if (fsBtn) fsBtn.innerHTML = document.fullscreenElement ? I_EXIT_FS : I_FS;
    });

    // 컨트롤 자동 숨김
    let hideTimer;
    function bumpControls() {
        player.classList.add("show-controls");
        clearTimeout(hideTimer);
        if (!video.paused) hideTimer = setTimeout(() => player.classList.remove("show-controls"), 3000);
    }
    player.addEventListener("mousemove", bumpControls);
    player.addEventListener("mouseenter", bumpControls);
    video.addEventListener("pause", () => player.classList.add("show-controls"));
    video.addEventListener("play", bumpControls);

    // 프로그레스바 호버 툴팁
    const tooltip = document.getElementById("cpTimeTooltip");
    if (progress && tooltip) {
        progress.addEventListener("mousemove", (e) => {
            if (!video.duration) return;
            const rect = progress.getBoundingClientRect();
            const ratio = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
            tooltip.textContent = fmt(ratio * video.duration);
            const tipHalf = tooltip.offsetWidth / 2;
            const left = Math.max(tipHalf, Math.min(rect.width - tipHalf, e.clientX - rect.left));
            tooltip.style.left = left + "px";
            tooltip.classList.add("is-visible");
        });
        progress.addEventListener("mouseleave", () => {
            tooltip.classList.remove("is-visible");
        });
    }

    // 통합 설정 메뉴 (속도 + 화질)
    const settingsBtn   = document.getElementById("cpSettingsBtn");
    const settingsMenu  = document.getElementById("cpSettingsMenu");
    const settingsWrap  = document.getElementById("cpSettingsWrap");
    const setMain       = document.getElementById("cpSetMain");
    const setSpeedPanel = document.getElementById("cpSetSpeedPanel");
    const setQualPanel  = document.getElementById("cpSetQualPanel");
    const setSpeedVal   = document.getElementById("cpSetSpeedVal");
    const setQualVal    = document.getElementById("cpSetQualVal");

    const panels = [setMain, setSpeedPanel, setQualPanel];
    function showPanel(panel, goingBack) {
        panels.forEach(p => {
            if (!p) return;
            p.style.display = "none";
            p.classList.remove("is-entering", "is-entering-back");
        });
        if (!panel) return;
        panel.style.display = "block";
        void panel.offsetWidth;
        panel.classList.add(goingBack ? "is-entering-back" : "is-entering");
    }

    if (settingsBtn && settingsMenu) {
        settingsBtn.addEventListener("click", (e) => {
            e.stopPropagation();
            const opening = !settingsMenu.classList.contains("is-open");
            settingsMenu.classList.toggle("is-open");
            if (opening) showPanel(setMain);
        });
        document.addEventListener("click", (e) => {
            if (settingsWrap && !settingsWrap.contains(e.target)) settingsMenu.classList.remove("is-open");
        });

        // 메인 → 속도 패널
        document.getElementById("cpSetSpeedItem")?.addEventListener("click", () => showPanel(setSpeedPanel));
        document.getElementById("cpSetSpeedBack")?.addEventListener("click", () => showPanel(setMain, true));

        // 속도 옵션
        setSpeedPanel?.querySelectorAll(".cp-set-option").forEach(item => {
            item.addEventListener("click", (e) => {
                e.stopPropagation();
                const speed = parseFloat(item.dataset.speed);
                video.playbackRate = speed;
                const label = speed === 1 ? "보통" : speed + "x";
                if (setSpeedVal) setSpeedVal.textContent = label;
                setSpeedPanel.querySelectorAll(".cp-set-option").forEach(b => b.classList.remove("active"));
                item.classList.add("active");
                settingsMenu.classList.remove("is-open");
            });
        });

        // 메인 → 화질 패널
        document.getElementById("cpSetQualItem")?.addEventListener("click", () => showPanel(setQualPanel));
        document.getElementById("cpSetQualBack")?.addEventListener("click", () => showPanel(setMain, true));

        // 화질 옵션
        setQualPanel?.querySelectorAll(".cp-set-option").forEach(item => {
            item.addEventListener("click", (e) => {
                e.stopPropagation();
                const src = item.dataset.src;
                if (!src || item.classList.contains("active")) { settingsMenu.classList.remove("is-open"); return; }
                const t = video.currentTime;
                const wasPaused = video.paused;
                const canvas = document.createElement("canvas");
                canvas.width = video.videoWidth || 1280;
                canvas.height = video.videoHeight || 720;
                canvas.getContext("2d").drawImage(video, 0, 0, canvas.width, canvas.height);
                canvas.style.cssText = "position:absolute;inset:0;width:100%;height:100%;z-index:1;pointer-events:none;";
                player.appendChild(canvas);
                const removeCanvas = () => canvas.remove();
                video.src = src;
                video.load();
                video.addEventListener("loadedmetadata", () => {
                    video.currentTime = t;
                    if (!wasPaused) video.play().catch(() => {});
                }, { once: true });
                video.addEventListener("seeked", removeCanvas, { once: true });
                setTimeout(removeCanvas, 2000);
                if (setQualVal) setQualVal.textContent = item.dataset.label || item.textContent;
                setQualPanel.querySelectorAll(".cp-set-option").forEach(b => b.classList.remove("active"));
                item.classList.add("active");
                settingsMenu.classList.remove("is-open");
            });
        });
    }

    // 키보드 단축키
    document.addEventListener("keydown", (e) => {
        if (!document.getElementById("customPlayer")) return;
        const tag = document.activeElement?.tagName;
        if (tag === "INPUT" || tag === "TEXTAREA" || document.activeElement?.isContentEditable) return;

        switch (e.key) {
            case " ":
            case "k":
                e.preventDefault();
                video.paused ? video.play() : video.pause();
                break;
            case "ArrowLeft":
            case "j":
                e.preventDefault();
                video.currentTime = Math.max(0, video.currentTime - 5);
                break;
            case "ArrowRight":
            case "l":
                e.preventDefault();
                video.currentTime = Math.min(video.duration || 0, video.currentTime + 5);
                break;
            case "ArrowUp":
                if (player.matches(":hover")) {
                    e.preventDefault();
                    video.volume = Math.min(1, video.volume + 0.1);
                    video.muted = false;
                    if (volBtn) volBtn.innerHTML = I_VOL;
                    if (volSlider) volSlider.value = video.volume;
                }
                break;
            case "ArrowDown":
                if (player.matches(":hover")) {
                    e.preventDefault();
                    video.volume = Math.max(0, video.volume - 0.1);
                    if (video.volume === 0) { video.muted = true; if (volBtn) volBtn.innerHTML = I_MUTE; }
                    if (volSlider) volSlider.value = video.volume;
                }
                break;
            case "f":
            case "F":
                e.preventDefault();
                document.fullscreenElement ? document.exitFullscreen() : player.requestFullscreen();
                break;
            case "m":
            case "M":
                e.preventDefault();
                video.muted = !video.muted;
                if (volBtn) volBtn.innerHTML = video.muted ? I_MUTE : I_VOL;
                if (volSlider) volSlider.value = video.muted ? 0 : video.volume;
                break;
        }
    });

    // 스킵 버튼
    const skipBackBtn = document.getElementById("cpSkipBack");
    const skipFwdBtn  = document.getElementById("cpSkipFwd");
    const skipLeftZone  = document.getElementById("cpSkipLeft");
    const skipRightZone = document.getElementById("cpSkipRight");

    function flashSkipZone(zone) {
        if (!zone) return;
        zone.classList.add("flash");
        clearTimeout(zone._flashTimer);
        zone._flashTimer = setTimeout(() => zone.classList.remove("flash"), 600);
    }

    skipBackBtn?.addEventListener("click", (e) => {
        e.stopPropagation();
        video.currentTime = Math.max(0, video.currentTime - 10);
        flashSkipZone(skipLeftZone);
    });
    skipFwdBtn?.addEventListener("click", (e) => {
        e.stopPropagation();
        video.currentTime = Math.min(video.duration || 0, video.currentTime + 10);
        flashSkipZone(skipRightZone);
    });

    // 기존 디버그/에러 로깅 유지
    video.addEventListener("error", () => {
        const code = video.error?.code;
        const msg = ["", "ABORTED", "NETWORK", "DECODE", "SRC_NOT_SUPPORTED"][code] || "UNKNOWN";
        console.error("[Player] 영상 로드 실패:", msg, video.src);
    });
    video.addEventListener("loadedmetadata", () => {
        const w = video.videoWidth, h = video.videoHeight;
        console.log("[Player] 메타데이터 로드 완료:", w + "x" + h, video.src);
        if (w === 0 && h === 0) {
            const box = player.closest(".player-box");
            if (box) box.innerHTML = `<div style="display:flex;flex-direction:column;align-items:center;justify-content:center;min-height:200px;color:#aaa;gap:10px;padding:24px;aspect-ratio:16/9;"><span style="font-size:36px;">⚠️</span><span style="font-size:15px;text-align:center;">영상 트랙이 없습니다.<br>오디오 전용이거나 손상된 파일입니다.</span></div>`;
        }
    });
    video.addEventListener("playing", () => console.log("[Player] 재생 시작:", video.src));
    video.addEventListener("stalled", () => console.warn("[Player] 데이터 로드 정지 (stalled):", video.src));
    console.log("[Player] src =", video.src);
}

function createReplyItemHtml(r) {
    const rLetter = String(r.author || "?").trim().charAt(0) || "?";
    return `
    <div class="comment-item reply-item" data-comment-id="${r.id}">
      <div class="comment-avatar reply-avatar">${escapeHtml(rLetter)}</div>
      <div class="comment-content">
        <div class="comment-author-row">
          <span class="comment-author">${escapeHtml(r.author || "사용자")}</span>
          <span class="comment-time">${escapeHtml(r.time || "방금 전")}</span>
        </div>
        <p class="comment-text" data-comment-text>${escapeHtml(r.text || "")}</p>
        <div style="display:flex;gap:8px;margin-top:6px;flex-wrap:wrap;align-items:center;">
          <button type="button" class="comment-like-btn${r.isLiked ? " is-liked" : ""}" data-comment-like="${r.id}" data-liked="${r.isLiked ? "true" : "false"}">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M1 21h4V9H1v12zm22-11c0-1.1-.9-2-2-2h-6.31l.95-4.57.03-.32c0-.41-.17-.79-.44-1.06L14.17 1 7.59 7.59C7.22 7.95 7 8.45 7 9v10c0 1.1.9 2 2 2h9c.83 0 1.54-.5 1.84-1.22l3.02-7.05c.09-.23.14-.47.14-.73v-2z"/></svg>
            <span class="comment-like-count">${r.likeCount > 0 ? r.likeCount : ""}</span>
          </button>
        </div>
        ${(r.isMine || r.mine) ? `
          <div class="comment-owner-actions" style="display:flex;gap:8px;margin-top:6px;flex-wrap:wrap;">
            <button type="button" class="comment-btn-edit" data-comment-edit="${r.id}" style="border:none;background:transparent;color:#aaa;cursor:pointer;padding:0;font-size:13px;">수정</button>
            <button type="button" class="comment-btn-delete" data-comment-delete="${r.id}" style="border:none;background:transparent;color:#ff8d8d;cursor:pointer;padding:0;font-size:13px;">삭제</button>
          </div>
          <div class="comment-edit-box" data-comment-edit-box="${r.id}" hidden style="margin-top:10px;">
            <input type="text" class="comment-input" data-comment-edit-input="${r.id}" value="${escapeHtml(r.text || "")}" maxlength="300" />
            <div class="comment-form-actions" style="margin-top:8px;">
              <button type="button" class="comment-btn cancel" data-comment-edit-cancel="${r.id}">취소</button>
              <button type="button" class="comment-btn submit" data-comment-edit-save="${r.id}">저장</button>
            </div>
          </div>` : ""}
      </div>
    </div>`;
}

function createCommentItem(comment) {
    const firstLetter = String(comment.author || "?").trim().charAt(0) || "?";
    const replies = comment.replies || [];
    const isMine = comment.isMine || comment.mine;

    return `
    <div class="comment-item" data-comment-id="${comment.id}">
      <div class="comment-avatar">${escapeHtml(firstLetter)}</div>
      <div class="comment-content">
        <div class="comment-author-row">
          <span class="comment-author">${escapeHtml(comment.author || "사용자")}</span>
          <span class="comment-time">${escapeHtml(comment.time || "방금 전")}</span>
        </div>
        <p class="comment-text" data-comment-text>${escapeHtml(comment.text || "")}</p>
        <div style="display:flex;gap:10px;margin-top:8px;flex-wrap:wrap;align-items:center;">
          <button type="button" class="comment-like-btn${comment.isLiked ? " is-liked" : ""}" data-comment-like="${comment.id}" data-liked="${comment.isLiked ? "true" : "false"}">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M1 21h4V9H1v12zm22-11c0-1.1-.9-2-2-2h-6.31l.95-4.57.03-.32c0-.41-.17-.79-.44-1.06L14.17 1 7.59 7.59C7.22 7.95 7 8.45 7 9v10c0 1.1.9 2 2 2h9c.83 0 1.54-.5 1.84-1.22l3.02-7.05c.09-.23.14-.47.14-.73v-2z"/></svg>
            <span class="comment-like-count">${comment.likeCount > 0 ? comment.likeCount : ""}</span>
          </button>
          <button type="button" class="comment-reply-btn" data-reply-to="${comment.id}" style="border:none;background:transparent;color:#aaa;cursor:pointer;padding:0;font-size:13px;">답글</button>
          ${isMine ? `
            <button type="button" class="comment-btn-edit" data-comment-edit="${comment.id}" style="border:none;background:transparent;color:#aaa;cursor:pointer;padding:0;font-size:13px;">수정</button>
            <button type="button" class="comment-btn-delete" data-comment-delete="${comment.id}" style="border:none;background:transparent;color:#ff8d8d;cursor:pointer;padding:0;font-size:13px;">삭제</button>` : ""}
        </div>
        ${isMine ? `
          <div class="comment-edit-box" data-comment-edit-box="${comment.id}" hidden style="margin-top:10px;">
            <input type="text" class="comment-input" data-comment-edit-input="${comment.id}" value="${escapeHtml(comment.text || "")}" maxlength="300" />
            <div class="comment-form-actions" style="margin-top:10px;">
              <button type="button" class="comment-btn cancel" data-comment-edit-cancel="${comment.id}">취소</button>
              <button type="button" class="comment-btn submit" data-comment-edit-save="${comment.id}">저장</button>
            </div>
          </div>` : ""}
        <div class="reply-form-wrap" data-reply-form="${comment.id}" hidden style="margin-top:10px;">
          <input type="text" class="comment-input" data-reply-input="${comment.id}" placeholder="답글 추가..." maxlength="300" />
          <div class="comment-form-actions" style="margin-top:8px;">
            <button type="button" class="comment-btn cancel" data-reply-cancel="${comment.id}">취소</button>
            <button type="button" class="comment-btn submit" data-reply-submit="${comment.id}" disabled>답글</button>
          </div>
        </div>
        <div class="replies-list" data-replies-container="${comment.id}">
          ${replies.map(createReplyItemHtml).join("")}
        </div>
      </div>
    </div>`;
}

function renderCommentList(commentListEl, commentsCountEl, comments, serverTotal, hasMore, onLoadMore) {
    if (!commentListEl || !commentsCountEl) return;

    const displayTotal = serverTotal != null
        ? serverTotal
        : comments.reduce((sum, c) => sum + 1 + (c.replies?.length || 0), 0);
    commentsCountEl.textContent = `${formatCount(displayTotal)}개의 댓글`;

    if (comments.length === 0) {
        commentListEl.innerHTML = `<p class="comment-empty">아직 댓글이 없습니다.</p>`;
        return;
    }

    let html = comments.map(createCommentItem).join("");
    if (hasMore && onLoadMore) {
        html += `<button class="comment-load-more" id="commentLoadMore" type="button">댓글 더 보기</button>`;
    }
    commentListEl.innerHTML = html;

    if (hasMore && onLoadMore) {
        document.getElementById("commentLoadMore")?.addEventListener("click", onLoadMore);
    }
}

function readVideoDuration(file) {
    return new Promise((resolve, reject) => {
        const tempVideo = document.createElement("video");
        const objectUrl = URL.createObjectURL(file);

        const cleanup = () => { tempVideo.src = ""; URL.revokeObjectURL(objectUrl); };
        const timer = setTimeout(() => { cleanup(); reject(new Error("timeout")); }, 8000);

        tempVideo.preload = "metadata";

        tempVideo.onloadedmetadata = () => {
            clearTimeout(timer);
            const duration = tempVideo.duration;
            cleanup();
            resolve(duration);
        };

        tempVideo.onerror = () => {
            clearTimeout(timer);
            cleanup();
            reject(new Error("duration read failed"));
        };

        tempVideo.src = objectUrl;
    });
}

function formatDuration(seconds) {
    const totalSeconds = Math.floor(seconds);
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const secs = totalSeconds % 60;

    if (hours > 0) {
        return `${hours}:${String(minutes).padStart(2, "0")}:${String(secs).padStart(2, "0")}`;
    }

    return `${minutes}:${String(secs).padStart(2, "0")}`;
}
