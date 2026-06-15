/* MyTube - sidebar.js : 구독 사이드바 및 통합 사이드바 */

async function initSubscriptionSidebar() {
    const sidebar = document.querySelector(".sidebar");
    if (!sidebar) return;

    const authMe = getAuthMe();
    if (!authMe?.loggedIn) return;
    const currentPage = document.body.dataset.page || "";

    // 구독 피드 링크 주입 (subscriptions.html 외 모든 페이지)
    if (currentPage !== "subscriptions") {
        const firstSection = sidebar.querySelector(".sidebar-section");
        if (firstSection && !firstSection.querySelector('a[href="subscriptions.html"]')) {
            firstSection.insertAdjacentHTML("beforeend", `
                <a href="subscriptions.html" class="sidebar-link">
                    <span class="sidebar-icon">
                        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M10 18v-2h4v2h-4Zm-4-5v-2h12v2H6Zm-3-5V6h18v2H3Z"/></svg>
                    </span>
                    <span class="sidebar-label">구독</span>
                </a>
            `);
        }
    }

    // 재생목록 링크 주입
    const secondSection = sidebar.querySelectorAll(".sidebar-section")[1];
    if (secondSection && !secondSection.querySelector('a[href="playlist.html"]')) {
        const historyLink = secondSection.querySelector('a[href="history.html"]');
        const plLink = `
            <a href="playlist.html" class="sidebar-link${currentPage === "playlist" ? " is-active" : ""}">
                <span class="sidebar-icon">
                    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 5h12v2H3V5zm0 4h12v2H3V9zm0 4h8v2H3v-2zm13 3v-6l5 3-5 3z"/></svg>
                </span>
                <span class="sidebar-label">재생목록</span>
            </a>`;
        if (historyLink) historyLink.insertAdjacentHTML("afterend", plLink);
        else secondSection.insertAdjacentHTML("beforeend", plLink);
    }

}


/* =========================================================
   공통 상단바 / 사이드바 자동 복구 패치 v1
   - 기존 코드 삭제하지 말고 script.js 맨 아래에 추가
   - history / saved / liked / channel / upload 페이지 레이아웃 보강
========================================================= */

(function () {
    const PATCH_NAME = "commonLayoutRecoveryPatchV1";

    function getPageName() {
        const bodyPage = document.body.dataset.page || "";
        if (bodyPage) return bodyPage;

        const filename = window.location.pathname.split("/").pop() || "index.html";

        if (filename.includes("history")) return "history";
        if (filename.includes("saved")) return "saved";
        if (filename.includes("liked")) return "liked";
        if (filename.includes("channel")) return "channel";
        if (filename.includes("upload")) return "upload";
        if (filename.includes("watch")) return "watch";
        if (filename.includes("edit")) return "edit";
        if (filename.includes("login")) return "login";
        if (filename.includes("signup")) return "signup";

        return "home";
    }

    function shouldApplyLayoutPatch() {
        return false;
    }

    function getActiveLabel() {
        const page = getPageName();

        if (page === "history") return "시청 기록";
        if (page === "saved") return "저장한 영상";
        if (page === "liked") return "좋아요 표시한 동영상";
        if (page === "channel") return "내 채널";
        if (page === "upload") return "업로드";
        if (page === "edit") return "내 채널";

        return "홈";
    }

    function createTopbarMarkup() {
        return `
            <header class="topbar" id="topbar">
                <div class="topbar-left">
                    <button class="icon-btn menu-btn" id="menuBtn" type="button" aria-label="메뉴">
                        <svg viewBox="0 0 24 24" aria-hidden="true">
                            <path d="M4 6h16M4 12h16M4 18h16"></path>
                        </svg>
                    </button>

                    <a href="index.html" class="logo-wrap" aria-label="MyTube 홈">
                        <span class="logo-badge">
                            <svg viewBox="0 0 24 24" aria-hidden="true" fill="none">
                                <rect x="4" y="7" width="16" height="10" rx="2" stroke="white" stroke-width="1.4"/>
                                <rect x="2" y="9.5" width="2" height="2" rx="0.5" fill="white"/>
                                <rect x="2" y="12.5" width="2" height="2" rx="0.5" fill="white"/>
                                <rect x="20" y="9.5" width="2" height="2" rx="0.5" fill="white"/>
                                <rect x="20" y="12.5" width="2" height="2" rx="0.5" fill="white"/>
                                <path d="M10.5 10l4 2-4 2v-4z" fill="white"/>
                            </svg>
                        </span>
                        <span class="logo-text">MY</span>
                    </a>
                </div>

                <div class="topbar-center">
                    <form class="search-form common-layout-search-form">
                        <input type="text" placeholder="검색" autocomplete="off" />
                        <button class="search-btn" type="submit" aria-label="검색">
                            <svg viewBox="0 0 24 24" aria-hidden="true">
                                <path d="M9.5 3a6.5 6.5 0 0 1 5.17 10.44l4.45 4.44-1.41 1.41-4.44-4.45A6.5 6.5 0 1 1 9.5 3Zm0 2a4.5 4.5 0 1 0 0 9 4.5 4.5 0 0 0 0-9Z"></path>
                            </svg>
                        </button>
                    </form>

                    <button class="icon-btn mic-btn" type="button" aria-label="음성 검색">
                        <svg viewBox="0 0 24 24" aria-hidden="true">
                            <path d="M12 14a3 3 0 0 0 3-3V6a3 3 0 0 0-6 0v5a3 3 0 0 0 3 3Zm5-3a5 5 0 0 1-10 0H5a7 7 0 0 0 6 6.92V21h2v-3.08A7 7 0 0 0 19 11h-2Z"></path>
                        </svg>
                    </button>
                </div>

                <div class="topbar-right">
                    <a class="icon-btn" href="upload.html" aria-label="업로드">
                        <svg viewBox="0 0 24 24" aria-hidden="true">
                            <path d="M14 10.5V6l5 5-5 5v-4.5H5v-2h9Z"></path>
                        </svg>
                    </a>

                    <div class="notif-wrap" id="notifWrap">
                        <button class="icon-btn notif-btn" id="notifBtn" aria-label="알림">
                            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 22a2.5 2.5 0 0 0 2.45-2h-4.9A2.5 2.5 0 0 0 12 22Zm7-6v-5a7 7 0 1 0-14 0v5l-2 2v1h18v-1l-2-2Z"/></svg>
                            <span class="notif-badge" id="notifBadge" style="display:none"></span>
                        </button>
                        <div class="notif-dropdown" id="notifDropdown" style="display:none">
                            <div class="notif-header"><span>알림</span><button class="notif-read-all" id="notifReadAll">모두 읽음</button></div>
                            <div class="notif-list" id="notifList"></div>
                        </div>
                    </div>

                    <a href="channel.html" class="profile" aria-label="내 채널">T</a>
                </div>
            </header>
        `;
    }

    function createSidebarMarkup() {
        const activeLabel = getActiveLabel();

        const items = [
            {
                label: "홈",
                icon: "⌂",
                href: "index.html"
            },
            {
                label: "Shorts",
                icon: "▶",
                href: ""
            },
            {
                label: "구독",
                icon: "▣",
                href: ""
            },
            {
                divider: true
            },
            {
                label: "시청 기록",
                icon: "↺",
                href: "history.html"
            },
            {
                label: "좋아요 표시한 동영상",
                icon: "👍",
                href: "liked.html"
            },
            {
                label: "저장한 영상",
                icon: "▤",
                href: "saved.html"
            },
            {
                label: "내 채널",
                icon: "◉",
                href: "channel.html"
            },
            {
                label: "업로드",
                icon: "＋",
                href: "upload.html"
            }
        ];

        const itemMarkup = items.map((item) => {
            if (item.divider) {
                return `<div class="sidebar-divider"></div>`;
            }

            const activeClass = item.label === activeLabel ? " active" : "";

            if (item.href) {
                return `
                    <a href="${item.href}" class="sidebar-item${activeClass}">
                        <span class="sidebar-icon">${item.icon}</span>
                        <span class="sidebar-label">${item.label}</span>
                    </a>
                `;
            }

            return `
                <div class="sidebar-item${activeClass}" data-common-layout-ready-menu="${item.label}">
                    <span class="sidebar-icon">${item.icon}</span>
                    <span class="sidebar-label">${item.label}</span>
                </div>
            `;
        }).join("");

        return `
            <aside class="sidebar" id="sidebar">
                <nav class="sidebar-inner" aria-label="사이드바 메뉴">
                    ${itemMarkup}
                </nav>
            </aside>
        `;
    }

    function ensureStyle() {
        if (document.getElementById("common-layout-recovery-style")) return;

        const style = document.createElement("style");
        style.id = "common-layout-recovery-style";
        style.textContent = `
            body.common-layout-recovered {
                padding-top: 56px;
            }

            body.common-layout-recovered .main {
                margin-left: 240px;
                padding: 24px 24px 32px;
                min-width: 0;
                transition: margin-left 0.2s ease;
            }

            body.common-layout-recovered .main.expanded {
                margin-left: 72px;
            }

            body.common-layout-recovered .sidebar {
                z-index: 999;
            }

            body.common-layout-recovered .sidebar a.sidebar-item {
                text-decoration: none;
                color: inherit;
            }

            body.common-layout-recovered .sidebar-item {
                cursor: pointer;
            }

            body.common-layout-recovered .sidebar-item.active {
                background: #272727;
                color: #fff;
            }

            body.common-layout-recovered .sidebar-item:focus-visible,
            body.common-layout-recovered .profile:focus-visible {
                outline: 2px solid #3ea6ff;
                outline-offset: 2px;
            }

            @media (max-width: 768px) {
                body.common-layout-recovered .main,
                body.common-layout-recovered .main.expanded {
                    margin-left: 0;
                    padding: 20px 16px 32px;
                }

                body.common-layout-recovered .sidebar {
                    transform: translateX(-100%);
                    width: 240px;
                    transition: transform 0.2s ease;
                }

                body.common-layout-recovered .sidebar.is-mobile-open {
                    transform: translateX(0);
                }

                body.common-layout-recovered.sidebar-mobile-open::after {
                    content: "";
                    position: fixed;
                    inset: 56px 0 0 0;
                    background: rgba(0, 0, 0, 0.45);
                    z-index: 998;
                }
            }
        `;

        document.head.appendChild(style);
    }

    function ensureTopbar() {
        const existingTopbar = document.getElementById("topbar") || document.querySelector(".topbar");

        if (existingTopbar) {
            if (!existingTopbar.id) existingTopbar.id = "topbar";
            return existingTopbar;
        }

        document.body.insertAdjacentHTML("afterbegin", createTopbarMarkup());

        return document.getElementById("topbar") || document.querySelector(".topbar");
    }

    function ensureSidebar() {
        const existingSidebar = document.getElementById("sidebar") || document.querySelector(".sidebar");

        if (existingSidebar) {
            if (!existingSidebar.id) existingSidebar.id = "sidebar";
            return existingSidebar;
        }

        const topbar = document.getElementById("topbar") || document.querySelector(".topbar");

        if (topbar) {
            topbar.insertAdjacentHTML("afterend", createSidebarMarkup());
        } else {
            document.body.insertAdjacentHTML("afterbegin", createSidebarMarkup());
        }

        return document.getElementById("sidebar") || document.querySelector(".sidebar");
    }

    function ensureMain() {
        let main =
            document.getElementById("main") ||
            document.querySelector("main") ||
            document.querySelector(".main");

        if (!main) {
            main = document.createElement("main");
            main.id = "main";
            main.className = "main";

            const children = Array.from(document.body.children);

            children.forEach((child) => {
                const isLayoutElement =                    child.classList.contains("topbar") ||
                    child.classList.contains("sidebar") ||
                    child.tagName === "SCRIPT" ||
                    child.tagName === "STYLE";

                if (!isLayoutElement) {
                    main.appendChild(child);
                }
            });

            document.body.appendChild(main);
        }

        main.id = main.id || "main";
        main.classList.add("main");

        return main;
    }

    function markActiveSidebarItem() {
        const sidebar = document.getElementById("sidebar") || document.querySelector(".sidebar");
        if (!sidebar) return;

        const activeLabel = getActiveLabel();

        sidebar.querySelectorAll(".sidebar-item").forEach((item) => {
            const labelEl = item.querySelector(".sidebar-label");
            const label = labelEl ? labelEl.textContent.trim() : item.textContent.trim();

            item.classList.toggle("active", label === activeLabel);
        });
    }

    function bindMenuButton() {
        const menuBtn = document.getElementById("menuBtn");
        const sidebar = document.getElementById("sidebar") || document.querySelector(".sidebar");
        const main = document.getElementById("main") || document.querySelector(".main");

        if (!menuBtn || !sidebar) return;

        if (
            menuBtn.dataset.commonLayoutMenuBound === "true" ||
            menuBtn.dataset.historyLayoutFixed === "true" ||
            menuBtn.dataset.sidebarTogglePatched === "true"
        ) {
            return;
        }

        menuBtn.dataset.commonLayoutMenuBound = "true";

        menuBtn.addEventListener("click", () => {
            if (window.innerWidth <= 768) {
                sidebar.classList.toggle("is-mobile-open");
                document.body.classList.toggle("sidebar-mobile-open");
                return;
            }

            sidebar.classList.toggle("collapsed");

            if (main) {
                main.classList.toggle("expanded");
            }
        });
    }

    function bindSearchForm() {
        const forms = document.querySelectorAll(".common-layout-search-form");

        forms.forEach((form) => {
            if (form.dataset.commonLayoutSearchBound === "true") return;

            form.dataset.commonLayoutSearchBound = "true";

            form.addEventListener("submit", (event) => {
                event.preventDefault();

                const input = form.querySelector('input[type="text"]');
                const keyword = input ? input.value.trim() : "";

                const url = new URL("index.html", window.location.href);

                if (keyword) {
                    url.searchParams.set("q", keyword);
                }

                window.location.href = url.toString();
            });
        });
    }

    function bindReadyMenus() {
        const menus = document.querySelectorAll("[data-common-layout-ready-menu]");

        menus.forEach((menu) => {
            if (menu.dataset.commonLayoutReadyBound === "true") return;

            menu.dataset.commonLayoutReadyBound = "true";
            menu.setAttribute("role", "button");
            menu.setAttribute("tabindex", "0");

            const label = menu.dataset.commonLayoutReadyMenu || "해당";

            function showReadyMessage() {
                if (typeof showToast === "function") {
                    showToast(`${label} 페이지는 아직 준비 중입니다.`);
                } else {
                    alert(`${label} 페이지는 아직 준비 중입니다.`);
                }
            }

            menu.addEventListener("click", showReadyMessage);

            menu.addEventListener("keydown", (event) => {
                if (event.key !== "Enter" && event.key !== " ") return;

                event.preventDefault();
                showReadyMessage();
            });
        });
    }

    function bindTopbarButtons() {
        const micButtons = document.querySelectorAll(".mic-btn");

        micButtons.forEach((button) => {
            if (button.dataset.commonLayoutMicBound === "true") return;

            button.dataset.commonLayoutMicBound = "true";

            button.addEventListener("click", () => {
                if (typeof showToast === "function") {
                    showToast("음성 검색 기능은 아직 준비 중입니다.");
                } else {
                    alert("음성 검색 기능은 아직 준비 중입니다.");
                }
            });
        });
    }

    function recoverCommonLayout() {
        if (!shouldApplyLayoutPatch()) return;

        if (document.body.dataset[PATCH_NAME] === "true") {
            return;
        }

        document.body.dataset[PATCH_NAME] = "true";
        document.body.classList.add("common-layout-recovered");

        ensureStyle();
        ensureTopbar();
        ensureSidebar();
        ensureMain();
        markActiveSidebarItem();
        bindMenuButton();
        bindSearchForm();
        bindReadyMenus();
        bindTopbarButtons();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", recoverCommonLayout);
    } else {
        recoverCommonLayout();
    }
})();

/* =========================================================
   업로드 페이지 상단바 / 사이드바 제거 패치 v1
   - upload.html에서는 상단바, 사이드바 제거
   - 업로드 화면을 단독 작업 화면처럼 정리
   - 기존 코드 삭제하지 말고 script.js 맨 아래에 추가
========================================================= */

(function () {
    function isUploadPage() {
        const page = document.body.dataset.page || "";
        const filename = window.location.pathname.split("/").pop() || "";

        return page === "upload" || filename.includes("upload");
    }

    function ensureUploadCleanStyle() {
        if (document.getElementById("upload-clean-layout-style")) return;

        const style = document.createElement("style");
        style.id = "upload-clean-layout-style";
        style.textContent = `
            body.upload-clean-layout {
                padding-top: 0 !important;
                margin: 0 !important;
                background: #0f0f0f !important;
                overflow-x: hidden !important;
            }

            body.upload-clean-layout .topbar,
            body.upload-clean-layout #topbar,
            body.upload-clean-layout .sidebar,
            body.upload-clean-layout #sidebar {
                display: none !important;
            }

            body.upload-clean-layout .main,
            body.upload-clean-layout main,
            body.upload-clean-layout #main {
                margin-left: 0 !important;
                padding: 0 !important;
                width: 100% !important;
                max-width: none !important;
                min-height: 100vh !important;
                box-sizing: border-box !important;
            }

            body.upload-clean-layout .upload-page {
                min-height: 100vh !important;
                padding: 56px 24px !important;
                display: flex !important;
                align-items: flex-start !important;
                justify-content: center !important;
                box-sizing: border-box !important;
            }

            body.upload-clean-layout .upload-wizard-card {
                width: 100% !important;
                max-width: 1280px !important;
                margin: 0 auto !important;
            }

            @media (max-width: 768px) {
                body.upload-clean-layout .upload-page {
                    padding: 28px 16px !important;
                }
            }
        `;

        document.head.appendChild(style);
    }

    function removeUploadLayoutElements() {
        const topbars = document.querySelectorAll(".topbar, #topbar");
        const sidebars = document.querySelectorAll(".sidebar, #sidebar");

        topbars.forEach((element) => {
            element.remove();
        });

        sidebars.forEach((element) => {
            element.remove();
        });
    }

    function resetUploadMainLayout() {
        const main =
            document.getElementById("main") ||
            document.querySelector("main") ||
            document.querySelector(".main");

        if (!main) return;

        main.classList.remove("expanded");
        main.style.marginLeft = "0";
    }

    function runUploadCleanLayoutPatch() {
        if (!isUploadPage()) return;

        document.body.classList.add("upload-clean-layout");

        ensureUploadCleanStyle();
        removeUploadLayoutElements();
        resetUploadMainLayout();
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", runUploadCleanLayoutPatch);
    } else {
        runUploadCleanLayoutPatch();
    }

    setTimeout(runUploadCleanLayoutPatch, 100);
    setTimeout(runUploadCleanLayoutPatch, 500);
    setTimeout(runUploadCleanLayoutPatch, 1200);
})();

/* =========================================================
   전체 페이지 사이드바 통합 패치 v1
   - 메인 화면 사이드바 기준으로 모든 페이지 통일
   - upload.html 제외
   - history / saved / liked / channel / watch / edit / home 공통 적용
========================================================= */

(function () {
    const PATCH_ID = "global-main-sidebar-unify-v1";
    const STORAGE_KEY = "youtube_clone_sidebar_expanded";

    function getPageName() {
        const page = document.body.dataset.page || "";
        const file = window.location.pathname.split("/").pop() || "index.html";

        if (page) return page;
        if (file.includes("upload")) return "upload";
        if (file.includes("history")) return "history";
        if (file.includes("saved")) return "saved";
        if (file.includes("liked")) return "liked";
        if (file.includes("channel")) return "channel";
        if (file.includes("watch")) return "watch";
        if (file.includes("edit")) return "edit";
        if (file.includes("login")) return "login";
        if (file.includes("signup")) return "signup";

        return "home";
    }

    function shouldSkipPage() {
        const page = getPageName();

        return page === "upload" || page === "login" || page === "signup" || page === "watch";
    }

    function getMenuButton() {
        return (
            document.getElementById("menuBtn") ||
            document.querySelector(".menu-btn") ||
            document.querySelector('[aria-label="메뉴"]')
        );
    }

    function getSidebar() {
        return (
            document.getElementById("sidebar") ||
            document.querySelector(".sidebar")
        );
    }

    function getMain() {
        return (
            document.getElementById("main") ||
            document.querySelector("main") ||
            document.querySelector(".main") ||
            document.querySelector(".user-channel-content")
        );
    }

    function getActiveLabel() {
        const page = getPageName();

        if (page === "history") return "시청 기록";
        if (page === "saved") return "저장한 영상";
        if (page === "liked") return "좋아요한 영상";
        if (page === "channel") return "내 채널";
        if (page === "upload") return "업로드";

        return "홈";
    }

    function normalizeSidebarText() {
        const sidebar = getSidebar();
        if (!sidebar) return;

        sidebar.querySelectorAll(".sidebar-label").forEach((label) => {
            const text = label.textContent.replace(/\s+/g, " ").trim();

            if (text === "좋아요 표시한 동영상") {
                label.textContent = "좋아요한 영상";
            }

            if (text === "시청기록") {
                label.textContent = "시청 기록";
            }
        });
    }

    function markActiveMenu() {
        const sidebar = getSidebar();
        if (!sidebar) return;

        const activeLabel = getActiveLabel();

        sidebar.querySelectorAll(".sidebar-item").forEach((item) => {
            const label = item.querySelector(".sidebar-label")?.textContent.trim() || item.textContent.trim();

            item.classList.toggle("active", label.includes(activeLabel));
        });
    }

    function ensureStyle() {
        if (document.getElementById(PATCH_ID + "-style")) return;

        const style = document.createElement("style");
        style.id = PATCH_ID + "-style";

        style.textContent = `
            body.${PATCH_ID} {
                padding-top: 56px !important;
            }

            body.${PATCH_ID} .sidebar,
            body.${PATCH_ID} #sidebar {
                position: fixed !important;
                top: 56px !important;
                left: 0 !important;
                bottom: 0 !important;
                height: calc(100vh - 56px) !important;
                background: #0f0f0f !important;
                border-right: 1px solid #1f1f1f !important;
                box-sizing: border-box !important;
                overflow-x: hidden !important;
                overflow-y: auto !important;
                z-index: 2500 !important;
                transition: width 0.18s ease !important;
                transform: none !important;
            }

            body.${PATCH_ID} .sidebar-inner,
            body.${PATCH_ID} #sidebar .sidebar-inner {
                width: 100% !important;
                display: flex !important;
                flex-direction: column !important;
                box-sizing: border-box !important;
            }

            body.${PATCH_ID} .sidebar-item,
            body.${PATCH_ID} #sidebar .sidebar-item {
                color: #fff !important;
                text-decoration: none !important;
                border-radius: 10px !important;
                box-sizing: border-box !important;
                cursor: pointer !important;
                overflow: hidden !important;
            }

            body.${PATCH_ID} .sidebar-item:hover,
            body.${PATCH_ID} .sidebar-item.active,
            body.${PATCH_ID} #sidebar .sidebar-item:hover,
            body.${PATCH_ID} #sidebar .sidebar-item.active {
                background: #272727 !important;
            }

            body.${PATCH_ID} .sidebar-icon,
            body.${PATCH_ID} #sidebar .sidebar-icon {
                width: 24px !important;
                height: 24px !important;
                min-width: 24px !important;
                min-height: 24px !important;
                display: inline-flex !important;
                align-items: center !important;
                justify-content: center !important;
                font-size: 18px !important;
                line-height: 1 !important;
                flex: 0 0 24px !important;
            }

            body.${PATCH_ID} .sidebar-icon svg,
            body.${PATCH_ID} .sidebar-item svg,
            body.${PATCH_ID} #sidebar .sidebar-icon svg,
            body.${PATCH_ID} #sidebar .sidebar-item svg {
                width: 22px !important;
                height: 22px !important;
                display: block !important;
            }

            body.${PATCH_ID} .sidebar-label,
            body.${PATCH_ID} #sidebar .sidebar-label {
                color: #fff !important;
                font-weight: 700 !important;
                box-sizing: border-box !important;
            }

            body.${PATCH_ID} .sidebar-divider,
            body.${PATCH_ID} #sidebar .sidebar-divider {
                height: 1px !important;
                background: #242424 !important;
                flex: 0 0 auto !important;
            }

            body.${PATCH_ID}.sidebar-unified-collapsed .sidebar,
            body.${PATCH_ID}.sidebar-unified-collapsed #sidebar {
                width: 72px !important;
                padding: 8px 4px !important;
            }

            body.${PATCH_ID}.sidebar-unified-collapsed .sidebar-inner,
            body.${PATCH_ID}.sidebar-unified-collapsed #sidebar .sidebar-inner {
                align-items: center !important;
                gap: 4px !important;
            }

            body.${PATCH_ID}.sidebar-unified-collapsed .sidebar-item,
            body.${PATCH_ID}.sidebar-unified-collapsed #sidebar .sidebar-item {
                width: 64px !important;
                min-height: 64px !important;
                height: auto !important;
                padding: 7px 4px !important;
                display: flex !important;
                flex-direction: column !important;
                align-items: center !important;
                justify-content: center !important;
                gap: 5px !important;
                text-align: center !important;
                line-height: 1 !important;
                white-space: normal !important;
            }

            body.${PATCH_ID}.sidebar-unified-collapsed .sidebar-icon,
            body.${PATCH_ID}.sidebar-unified-collapsed #sidebar .sidebar-icon {
                margin: 0 auto !important;
            }

            body.${PATCH_ID}.sidebar-unified-collapsed .sidebar-label,
            body.${PATCH_ID}.sidebar-unified-collapsed #sidebar .sidebar-label {
                width: 100% !important;
                min-height: 24px !important;
                display: flex !important;
                align-items: center !important;
                justify-content: center !important;
                font-size: 10px !important;
                line-height: 1.15 !important;
                text-align: center !important;
                white-space: normal !important;
                word-break: keep-all !important;
                overflow: hidden !important;
            }

            body.${PATCH_ID}.sidebar-unified-collapsed .sidebar-divider,
            body.${PATCH_ID}.sidebar-unified-collapsed #sidebar .sidebar-divider {
                width: 56px !important;
                margin: 4px 0 !important;
            }

            body.${PATCH_ID}.sidebar-unified-collapsed .main,
            body.${PATCH_ID}.sidebar-unified-collapsed main,
            body.${PATCH_ID}.sidebar-unified-collapsed #main,
            body.${PATCH_ID}.sidebar-unified-collapsed .user-channel-content {
                margin-left: 72px !important;
            }

            body.${PATCH_ID}.sidebar-unified-expanded .sidebar,
            body.${PATCH_ID}.sidebar-unified-expanded #sidebar {
                width: 240px !important;
                padding: 12px !important;
            }

            body.${PATCH_ID}.sidebar-unified-expanded .sidebar-inner,
            body.${PATCH_ID}.sidebar-unified-expanded #sidebar .sidebar-inner {
                align-items: stretch !important;
                gap: 4px !important;
            }

            body.${PATCH_ID}.sidebar-unified-expanded .sidebar-item,
            body.${PATCH_ID}.sidebar-unified-expanded #sidebar .sidebar-item {
                width: 100% !important;
                min-height: 42px !important;
                height: 42px !important;
                padding: 0 12px !important;
                display: flex !important;
                flex-direction: row !important;
                align-items: center !important;
                justify-content: flex-start !important;
                gap: 18px !important;
                text-align: left !important;
                line-height: 1.2 !important;
                white-space: nowrap !important;
            }

            body.${PATCH_ID}.sidebar-unified-expanded .sidebar-icon,
            body.${PATCH_ID}.sidebar-unified-expanded #sidebar .sidebar-icon {
                margin: 0 !important;
            }

            body.${PATCH_ID}.sidebar-unified-expanded .sidebar-label,
            body.${PATCH_ID}.sidebar-unified-expanded #sidebar .sidebar-label {
                width: auto !important;
                min-height: auto !important;
                display: inline-flex !important;
                align-items: center !important;
                justify-content: flex-start !important;
                font-size: 14px !important;
                line-height: 1.2 !important;
                text-align: left !important;
                white-space: nowrap !important;
                overflow: hidden !important;
                text-overflow: ellipsis !important;
            }

            body.${PATCH_ID}.sidebar-unified-expanded .sidebar-divider,
            body.${PATCH_ID}.sidebar-unified-expanded #sidebar .sidebar-divider {
                width: 100% !important;
                margin: 8px 0 !important;
            }

            body.${PATCH_ID}.sidebar-unified-expanded .main,
            body.${PATCH_ID}.sidebar-unified-expanded main,
            body.${PATCH_ID}.sidebar-unified-expanded #main,
            body.${PATCH_ID}.sidebar-unified-expanded .user-channel-content {
                margin-left: 240px !important;
            }

            @media (max-width: 900px) {
                body.${PATCH_ID}.sidebar-unified-collapsed .sidebar,
                body.${PATCH_ID}.sidebar-unified-collapsed #sidebar {
                    width: 240px !important;
                    transform: translateX(-100%) !important;
                }

                body.${PATCH_ID}.sidebar-unified-expanded .sidebar,
                body.${PATCH_ID}.sidebar-unified-expanded #sidebar {
                    width: 240px !important;
                    transform: translateX(0) !important;
                }

                body.${PATCH_ID}.sidebar-unified-collapsed .main,
                body.${PATCH_ID}.sidebar-unified-collapsed main,
                body.${PATCH_ID}.sidebar-unified-collapsed #main,
                body.${PATCH_ID}.sidebar-unified-collapsed .user-channel-content,
                body.${PATCH_ID}.sidebar-unified-expanded .main,
                body.${PATCH_ID}.sidebar-unified-expanded main,
                body.${PATCH_ID}.sidebar-unified-expanded #main,
                body.${PATCH_ID}.sidebar-unified-expanded .user-channel-content {
                    margin-left: 0 !important;
                }
            }

            /* ── 라이트 모드 오버라이드 ── */
            html[data-theme="light"] body.${PATCH_ID} .sidebar,
            html[data-theme="light"] body.${PATCH_ID} #sidebar {
                background: #f9f9f9 !important;
                border-right-color: #e5e5e5 !important;
            }

            html[data-theme="light"] body.${PATCH_ID} .sidebar-item,
            html[data-theme="light"] body.${PATCH_ID} #sidebar .sidebar-item {
                color: #0f0f0f !important;
            }

            html[data-theme="light"] body.${PATCH_ID} .sidebar-item:hover,
            html[data-theme="light"] body.${PATCH_ID} .sidebar-item.active,
            html[data-theme="light"] body.${PATCH_ID} #sidebar .sidebar-item:hover,
            html[data-theme="light"] body.${PATCH_ID} #sidebar .sidebar-item.active {
                background: #e5e5e5 !important;
            }

            html[data-theme="light"] body.${PATCH_ID} .sidebar-label,
            html[data-theme="light"] body.${PATCH_ID} #sidebar .sidebar-label {
                color: #0f0f0f !important;
            }

            html[data-theme="light"] body.${PATCH_ID} .sidebar-icon svg,
            html[data-theme="light"] body.${PATCH_ID} #sidebar .sidebar-icon svg {
                fill: #0f0f0f;
            }

            html[data-theme="light"] body.${PATCH_ID} .sidebar-divider,
            html[data-theme="light"] body.${PATCH_ID} #sidebar .sidebar-divider {
                background: #e5e5e5 !important;
            }
        `;

        document.head.appendChild(style);
    }

    function setInlineState(expanded) {
        const sidebar = getSidebar();
        const main = getMain();

        if (!sidebar) return;

        const isMobile = window.innerWidth <= 900;

        document.body.classList.add(PATCH_ID);
        document.body.classList.toggle("sidebar-unified-expanded", expanded);
        document.body.classList.toggle("sidebar-unified-collapsed", !expanded);

        sidebar.classList.remove("collapsed");

        // Mobile: overlay mode (is-open + backdrop)
        const backdrop = document.getElementById("sidebarBackdrop");
        if (isMobile) {
            sidebar.classList.toggle("is-open", expanded);
            sidebar.classList.remove("is-collapsed");
            if (backdrop) {
                backdrop.classList.toggle("is-open", expanded);
                if (!backdrop.dataset.unifyBound) {
                    backdrop.dataset.unifyBound = "true";
                    backdrop.addEventListener("click", function () { setInlineState(false); });
                }
            }

            // Mobile sidebar always shows full-row items
            sidebar.style.setProperty("width", "240px", "important");
            sidebar.style.setProperty("padding", "12px", "important");
            sidebar.style.setProperty("box-sizing", "border-box", "important");
            if (main) main.style.setProperty("margin-left", "0", "important");

            sidebar.querySelectorAll(".sidebar-item").forEach((item) => {
                item.style.setProperty("display", "flex", "important");
                item.style.setProperty("width", "100%", "important");
                item.style.setProperty("min-height", "44px", "important");
                item.style.setProperty("height", "44px", "important");
                item.style.setProperty("padding", "0 16px", "important");
                item.style.setProperty("flex-direction", "row", "important");
                item.style.setProperty("align-items", "center", "important");
                item.style.setProperty("justify-content", "flex-start", "important");
                item.style.setProperty("gap", "18px", "important");
                item.style.setProperty("text-align", "left", "important");
                item.style.setProperty("white-space", "nowrap", "important");
            });

            sidebar.querySelectorAll(".sidebar-icon").forEach((icon) => {
                icon.style.setProperty("width", "24px", "important");
                icon.style.setProperty("height", "24px", "important");
                icon.style.setProperty("min-width", "24px", "important");
                icon.style.setProperty("flex", "0 0 24px", "important");
                icon.style.setProperty("margin", "0", "important");
            });

            sidebar.querySelectorAll(".sidebar-label").forEach((label) => {
                label.style.setProperty("display", "inline-flex", "important");
                label.style.setProperty("font-size", "14px", "important");
                label.style.setProperty("white-space", "nowrap", "important");
                label.style.setProperty("text-align", "left", "important");
            });

            return; // mobile done, skip desktop logic below
        }

        // Desktop: collapsed (72px icon) vs expanded (240px)
        sidebar.classList.toggle("is-collapsed", !expanded);
        sidebar.classList.remove("is-open");
        if (backdrop) backdrop.classList.remove("is-open");

        sidebar.style.setProperty("width", expanded ? "240px" : "72px", "important");
        sidebar.style.setProperty("padding", expanded ? "12px" : "8px 4px", "important");
        sidebar.style.setProperty("box-sizing", "border-box", "important");

        if (main) {
            main.style.setProperty("margin-left", expanded ? "240px" : "72px", "important");
        }

        sidebar.querySelectorAll(".sidebar-item").forEach((item) => {
            item.style.setProperty("display", "flex", "important");
            item.style.setProperty("box-sizing", "border-box", "important");

            if (expanded) {
                item.style.setProperty("width", "100%", "important");
                item.style.setProperty("min-height", "42px", "important");
                item.style.setProperty("height", "42px", "important");
                item.style.setProperty("padding", "0 12px", "important");
                item.style.setProperty("flex-direction", "row", "important");
                item.style.setProperty("align-items", "center", "important");
                item.style.setProperty("justify-content", "flex-start", "important");
                item.style.setProperty("gap", "18px", "important");
                item.style.setProperty("text-align", "left", "important");
                item.style.setProperty("white-space", "nowrap", "important");
            } else {
                item.style.setProperty("width", "64px", "important");
                item.style.setProperty("min-height", "64px", "important");
                item.style.removeProperty("height");
                item.style.setProperty("padding", "7px 4px", "important");
                item.style.setProperty("flex-direction", "column", "important");
                item.style.setProperty("align-items", "center", "important");
                item.style.setProperty("justify-content", "center", "important");
                item.style.setProperty("gap", "5px", "important");
                item.style.setProperty("text-align", "center", "important");
                item.style.setProperty("white-space", "normal", "important");
            }
        });

        sidebar.querySelectorAll(".sidebar-icon").forEach((icon) => {
            icon.style.setProperty("width", "24px", "important");
            icon.style.setProperty("height", "24px", "important");
            icon.style.setProperty("min-width", "24px", "important");
            icon.style.setProperty("min-height", "24px", "important");
            icon.style.setProperty("display", "inline-flex", "important");
            icon.style.setProperty("align-items", "center", "important");
            icon.style.setProperty("justify-content", "center", "important");
            icon.style.setProperty("flex", "0 0 24px", "important");
            icon.style.setProperty("margin", expanded ? "0" : "0 auto", "important");
        });

        sidebar.querySelectorAll(".sidebar-label").forEach((label) => {
            if (expanded) {
                label.style.setProperty("width", "auto", "important");
                label.style.setProperty("min-height", "auto", "important");
                label.style.setProperty("display", "inline-flex", "important");
                label.style.setProperty("align-items", "center", "important");
                label.style.setProperty("justify-content", "flex-start", "important");
                label.style.setProperty("font-size", "14px", "important");
                label.style.setProperty("line-height", "1.2", "important");
                label.style.setProperty("text-align", "left", "important");
                label.style.setProperty("white-space", "nowrap", "important");
            } else {
                label.style.setProperty("width", "100%", "important");
                label.style.setProperty("min-height", "24px", "important");
                label.style.setProperty("display", "flex", "important");
                label.style.setProperty("align-items", "center", "important");
                label.style.setProperty("justify-content", "center", "important");
                label.style.setProperty("font-size", "10px", "important");
                label.style.setProperty("line-height", "1.15", "important");
                label.style.setProperty("text-align", "center", "important");
                label.style.setProperty("white-space", "normal", "important");
                label.style.setProperty("word-break", "keep-all", "important");
            }
        });

        localStorage.setItem(STORAGE_KEY, expanded ? "true" : "false");
    }

    function getCurrentExpandedState() {
        return localStorage.getItem(STORAGE_KEY) === "true";
    }

    function toggleSidebar() {
        const isMobile = window.innerWidth <= 900;
        if (isMobile) {
            const sidebar = getSidebar();
            const isOpen = sidebar && sidebar.classList.contains("is-open");
            setInlineState(!isOpen);
        } else {
            const next = !document.body.classList.contains("sidebar-unified-expanded");
            setInlineState(next);
        }
    }

    function bindMenuButton() {
        if (document.body.dataset.globalSidebarUnifyBound === "true") return;

        document.body.dataset.globalSidebarUnifyBound = "true";

        document.addEventListener(
            "click",
            function (event) {
                if (shouldSkipPage()) return;

                const menuButton = event.target.closest(
                    "#menuBtn, #menuToggle, .menu-btn, [aria-label='메뉴'], [aria-label='메뉴 열기']"
                );

                if (!menuButton) return;

                event.preventDefault();
                event.stopPropagation();
                event.stopImmediatePropagation();

                toggleSidebar();
            },
            true
        );

        // Close mobile sidebar when resizing to desktop
        let resizeTimer;
        window.addEventListener("resize", function () {
            clearTimeout(resizeTimer);
            resizeTimer = setTimeout(function () {
                if (shouldSkipPage()) return;
                if (window.innerWidth > 900) {
                    const sidebar = getSidebar();
                    if (sidebar) sidebar.classList.remove("is-open");
                    const bd = document.getElementById("sidebarBackdrop");
                    if (bd) bd.classList.remove("is-open");
                    // Re-apply desktop state from localStorage
                    setInlineState(getCurrentExpandedState());
                }
            }, 200);
        });
    }

    function bootUnifiedSidebar() {
        if (shouldSkipPage()) return;

        const sidebar = getSidebar();
        if (!sidebar) return;

        if (document.body.dataset.unifiedSidebarBooted === "true") return;
        document.body.dataset.unifiedSidebarBooted = "true";

        ensureStyle();
        normalizeSidebarText();
        markActiveMenu();
        bindMenuButton();
        setInlineState(false);
    }

    function watchSidebarChanges() {
        if (document.body.dataset.globalSidebarUnifyObserver === "true") return;

        document.body.dataset.globalSidebarUnifyObserver = "true";

        let timer = null;

        const observer = new MutationObserver(() => {
            if (shouldSkipPage()) return;

            clearTimeout(timer);

            timer = setTimeout(() => {
                bootUnifiedSidebar();
            }, 60);
        });

        observer.observe(document.body, {
            childList: true,
            subtree: true
        });
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", () => {
            bootUnifiedSidebar();
            watchSidebarChanges();
        });
    } else {
        bootUnifiedSidebar();
        watchSidebarChanges();
    }

    setTimeout(bootUnifiedSidebar, 300);
    setTimeout(bootUnifiedSidebar, 1000);
    setTimeout(bootUnifiedSidebar, 2000);
})();
