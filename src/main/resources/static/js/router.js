/* MyTube - router.js : 페이지 라우터 */

const page = document.body.dataset.page;

(async function initPage() {
    if (window.__AUTH_READY__) {
        await window.__AUTH_READY__;
    }

    if (typeof consumePendingToast === "function") consumePendingToast();
    if (typeof initThemeToggle === "function") initThemeToggle();
    if (typeof initGlobalTopSearch === "function") initGlobalTopSearch();
    if (typeof initResumeBar === "function") initResumeBar();
    if (typeof initNotifications === "function") initNotifications();
    if (typeof initSubscriptionSidebar === "function") initSubscriptionSidebar();

    if (page === "upload" && typeof initUploadPage === "function") initUploadPage();
    if (page === "home" && typeof initHomePage === "function") initHomePage();
    if (page === "search" && typeof initSearchPage === "function") initSearchPage();
    if (page === "saved" && typeof initSavedPage === "function") initSavedPage();
    if (page === "liked" && typeof initLikedPage === "function") initLikedPage();
    if (page === "watch" && typeof initWatchPage === "function") await initWatchPage();
    if (page === "edit" && typeof initEditPage === "function") initEditPage();
    if (page === "channel" && typeof initChannelPage === "function") initChannelPage();
    if (page === "history" && typeof initHistoryPage === "function") initHistoryPage();
    if (page === "studio" && typeof initStudioPage === "function") initStudioPage();
    if (page === "subscriptions" && typeof initSubscriptionPage === "function") initSubscriptionPage();
    if (page === "user" && typeof initUserPage === "function") initUserPage();
    if (page === "playlist" && typeof initPlaylistPage === "function") initPlaylistPage();
})();
