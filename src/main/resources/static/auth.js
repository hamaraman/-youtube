async function fetchMe() {
    try {
        const response = await fetch("/api/me");
        if (!response.ok) {
            return { loggedIn: false, user: null };
        }

        const result = await response.json();
        return {
            loggedIn: Boolean(result.loggedIn),
            user: result.user || null
        };
    } catch {
        return { loggedIn: false, user: null };
    }
}

function getNextUrl() {
    const params = new URLSearchParams(window.location.search);
    return params.get("next") || "index.html";
}

function injectAuthStyles() {
    if (document.getElementById("auth-runtime-style")) return;

    const style = document.createElement("style");
    style.id = "auth-runtime-style";
    style.textContent = `
        .auth-topbar-btn {
            min-width: 82px;
            height: 36px;
            padding: 0 14px;
            border-radius: 999px;
            border: 1px solid #303030;
            background: #202020;
            color: #fff;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            text-decoration: none;
            cursor: pointer;
            font-size: 13px;
        }

        .auth-topbar-btn.primary {
            background: #fff;
            color: #111;
            border-color: #fff;
            font-weight: 700;
        }

        .auth-topbar-btn.logout {
            background: #2a1616;
            border-color: #4c2424;
            color: #ffb4b4;
        }

        .auth-topbar-user {
            width: 32px;
            height: 32px;
            border-radius: 50%;
            overflow: hidden;
            background: linear-gradient(135deg, #ff4e45, #ff8a00);
            color: #fff;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            font-weight: 700;
            font-size: 14px;
            flex-shrink: 0;
            border: 1px solid rgba(255,255,255,0.08);
        }

        .auth-topbar-user img {
            width: 100%;
            height: 100%;
            object-fit: cover;
            display: block;
        }
    `;
    document.head.appendChild(style);
}

function renderTopbarAuth(user) {
    injectAuthStyles();

    const container = document.querySelector(".topbar-right");
    if (!container) return;

    if (!user) {
        container.innerHTML = `
            <a href="login.html" class="auth-topbar-btn">로그인</a>
            <a href="signup.html" class="auth-topbar-btn primary">회원가입</a>
        `;
        return;
    }

    const displayName = user.channelName || user.nickname || user.username;
    const first = String(displayName || "U").charAt(0).toUpperCase();
    const profileVisual = user.profileImage
        ? `<img src="${user.profileImage}" alt="${displayName}" />`
        : first;

    const adminLink = user.username === 'admin'
        ? `<a href="admin.html" class="auth-topbar-btn" style="border-color:#c00;color:#ff6b6b">관리자 패널</a>`
        : '';

    container.innerHTML = `
        <a href="upload.html" class="icon-btn" aria-label="업로드">
            <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M11 6h2v5h5v2h-5v5h-2v-5H6v-2h5V6Z"></path>
            </svg>
        </a>
        <div class="notif-wrap" id="notifWrap">
            <button class="icon-btn notif-btn" id="notifBtn" aria-label="알림">
                <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 22a2.5 2.5 0 0 0 2.45-2h-4.9A2.5 2.5 0 0 0 12 22Zm7-6v-5a7 7 0 1 0-14 0v5l-2 2v1h18v-1l-2-2Z"/></svg>
                <span class="notif-badge" id="notifBadge" style="display:none"></span>
            </button>
            <div class="notif-dropdown" id="notifDropdown" style="display:none">
                <div class="notif-header" id="notifMainHeader"><span>알림</span><button class="notif-read-all" id="notifReadAll">모두 읽음</button></div>
                <div class="notif-list" id="notifList"></div>
                <div class="notif-comment-view" id="notifCommentView" style="display:none">
                    <div class="notif-comment-nav">
                        <button class="notif-back-btn" id="notifBackBtn">← 뒤로</button>
                        <a class="notif-go-video" id="notifGoVideo" href="#">영상으로 이동 →</a>
                    </div>
                    <div class="notif-comment-list" id="notifCommentList"></div>
                </div>
            </div>
        </div>
        ${adminLink}
        <a href="channel.html" class="auth-topbar-btn">내 채널</a>
        <a href="profile.html" class="auth-topbar-btn">프로필</a>
        <button type="button" class="auth-topbar-btn logout" id="logoutBtn">로그아웃</button>
        <a href="profile.html" class="auth-topbar-user" title="${displayName}">${profileVisual}</a>
    `;

    const logoutBtn = document.getElementById("logoutBtn");
    logoutBtn?.addEventListener("click", async () => {
        const response = await fetch("/api/logout", {
            method: "POST"
        });

        if (response.ok) {
            window.location.href = "index.html";
        }
    });
}

function validateLoginForm() {
    const username = document.getElementById("loginUsername");
    const password = document.getElementById("loginPassword");
    const usernameError = document.getElementById("loginUsernameError");
    const passwordError = document.getElementById("loginPasswordError");
    const commonError = document.getElementById("loginCommonError");

    let isValid = true;

    username.classList.remove("is-invalid");
    password.classList.remove("is-invalid");
    usernameError.textContent = "";
    passwordError.textContent = "";
    commonError.textContent = "";

    if (!username.value.trim()) {
        username.classList.add("is-invalid");
        usernameError.textContent = "아이디를 입력해줘.";
        isValid = false;
    }

    if (!password.value.trim()) {
        password.classList.add("is-invalid");
        passwordError.textContent = "비밀번호를 입력해줘.";
        isValid = false;
    }

    return isValid;
}

function validateSignupForm() {
    const username = document.getElementById("signupUsername");
    const nickname = document.getElementById("signupNickname");
    const email = document.getElementById("signupEmail");
    const password = document.getElementById("signupPassword");
    const passwordConfirm = document.getElementById("signupPasswordConfirm");

    const usernameError = document.getElementById("signupUsernameError");
    const nicknameError = document.getElementById("signupNicknameError");
    const emailError = document.getElementById("signupEmailError");
    const passwordError = document.getElementById("signupPasswordError");
    const passwordConfirmError = document.getElementById("signupPasswordConfirmError");
    const commonError = document.getElementById("signupCommonError");

    [username, nickname, email, password, passwordConfirm].forEach((input) => {
        input.classList.remove("is-invalid");
    });

    usernameError.textContent = "";
    nicknameError.textContent = "";
    emailError.textContent = "";
    passwordError.textContent = "";
    passwordConfirmError.textContent = "";
    commonError.textContent = "";

    let isValid = true;

    if (!/^[a-zA-Z0-9_]{4,20}$/.test(username.value.trim())) {
        username.classList.add("is-invalid");
        usernameError.textContent = "아이디 형식을 확인해줘.";
        isValid = false;
    }

    if (!nickname.value.trim()) {
        nickname.classList.add("is-invalid");
        nicknameError.textContent = "닉네임을 입력해줘.";
        isValid = false;
    }

    if (email.value.trim()) {
        const emailOk = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value.trim());
        if (!emailOk) {
            email.classList.add("is-invalid");
            emailError.textContent = "이메일 형식을 확인해줘.";
            isValid = false;
        }
    }

    if (password.value.length < 4) {
        password.classList.add("is-invalid");
        passwordError.textContent = "비밀번호는 4자 이상이어야 해.";
        isValid = false;
    }

    if (password.value !== passwordConfirm.value) {
        passwordConfirm.classList.add("is-invalid");
        passwordConfirmError.textContent = "비밀번호가 서로 다릅니다.";
        isValid = false;
    }

    return isValid;
}

function initLoginPage(me) {
    if (document.body.dataset.page !== "login") return;

    if (me.loggedIn) {
        window.location.href = getNextUrl();
        return;
    }

    const form = document.getElementById("loginForm");
    const username = document.getElementById("loginUsername");
    const commonError = document.getElementById("loginCommonError");

    const params = new URLSearchParams(window.location.search);
    const prefUsername = params.get("username");
    if (prefUsername) {
        username.value = prefUsername;
    }

    form?.addEventListener("submit", async (event) => {
        event.preventDefault();

        if (!validateLoginForm()) return;

        commonError.textContent = "";

        const response = await fetch("/api/login", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                username: document.getElementById("loginUsername").value.trim(),
                password: document.getElementById("loginPassword").value
            })
        });

        const result = await response.json().catch(() => ({}));

        if (!response.ok || !result.success) {
            commonError.textContent = result.message || "로그인에 실패했어.";
            return;
        }

        window.location.href = getNextUrl();
    });
}

function initSignupPage(me) {
    if (document.body.dataset.page !== "signup") return;

    if (me.loggedIn) {
        window.location.href = getNextUrl();
        return;
    }

    const form = document.getElementById("signupForm");
    const commonError = document.getElementById("signupCommonError");

    form?.addEventListener("submit", async (event) => {
        event.preventDefault();

        if (!validateSignupForm()) return;

        commonError.textContent = "";

        const payload = {
            username: document.getElementById("signupUsername").value.trim(),
            nickname: document.getElementById("signupNickname").value.trim(),
            email: document.getElementById("signupEmail").value.trim(),
            password: document.getElementById("signupPassword").value
        };

        const response = await fetch("/api/signup", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(payload)
        });

        const result = await response.json().catch(() => ({}));

        if (!response.ok || !result.success) {
            commonError.textContent = result.message || "회원가입에 실패했어.";
            return;
        }

        window.location.href = `login.html?username=${encodeURIComponent(payload.username)}`;
    });
}

function updateProfilePreview(imageUrl, displayName) {
    const preview = document.getElementById("profileAvatarPreview");
    if (!preview) return;

    if (imageUrl) {
        preview.innerHTML = `<img src="${imageUrl}" alt="${displayName}" />`;
        return;
    }

    preview.textContent = String(displayName || "U").charAt(0).toUpperCase();
}

function validateProfileForm() {
    const nickname = document.getElementById("profileNickname");
    const email = document.getElementById("profileEmail");
    const channelName = document.getElementById("profileChannelName");
    const profileImage = document.getElementById("profileImage");

    const nicknameError = document.getElementById("profileNicknameError");
    const emailError = document.getElementById("profileEmailError");
    const channelNameError = document.getElementById("profileChannelNameError");
    const profileImageError = document.getElementById("profileImageError");
    const commonError = document.getElementById("profileCommonError");

    [nickname, email, channelName, profileImage].forEach((input) => {
        input.classList.remove("is-invalid");
    });

    nicknameError.textContent = "";
    emailError.textContent = "";
    channelNameError.textContent = "";
    profileImageError.textContent = "";
    commonError.textContent = "";

    let isValid = true;

    if (!nickname.value.trim()) {
        nickname.classList.add("is-invalid");
        nicknameError.textContent = "닉네임을 입력해줘.";
        isValid = false;
    }

    if (!channelName.value.trim()) {
        channelName.classList.add("is-invalid");
        channelNameError.textContent = "채널명을 입력해줘.";
        isValid = false;
    }

    if (email.value.trim()) {
        const emailOk = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value.trim());
        if (!emailOk) {
            email.classList.add("is-invalid");
            emailError.textContent = "이메일 형식을 확인해줘.";
            isValid = false;
        }
    }

    if (profileImage.value.trim()) {
        try {
            new URL(profileImage.value.trim());
        } catch {
            profileImage.classList.add("is-invalid");
            profileImageError.textContent = "프로필 이미지 URL 형식을 확인해줘.";
            isValid = false;
        }
    }

    return isValid;
}

function validatePasswordForm() {
    const currentPassword = document.getElementById("currentPassword");
    const newPassword = document.getElementById("newPassword");
    const confirmNewPassword = document.getElementById("confirmNewPassword");

    const currentPasswordError = document.getElementById("currentPasswordError");
    const newPasswordError = document.getElementById("newPasswordError");
    const confirmNewPasswordError = document.getElementById("confirmNewPasswordError");
    const commonError = document.getElementById("passwordCommonError");

    [currentPassword, newPassword, confirmNewPassword].forEach((input) => {
        input.classList.remove("is-invalid");
    });

    currentPasswordError.textContent = "";
    newPasswordError.textContent = "";
    confirmNewPasswordError.textContent = "";
    commonError.textContent = "";

    let isValid = true;

    if (!currentPassword.value) {
        currentPassword.classList.add("is-invalid");
        currentPasswordError.textContent = "현재 비밀번호를 입력해줘.";
        isValid = false;
    }

    if (newPassword.value.length < 4) {
        newPassword.classList.add("is-invalid");
        newPasswordError.textContent = "새 비밀번호는 4자 이상이어야 해.";
        isValid = false;
    }

    if (newPassword.value !== confirmNewPassword.value) {
        confirmNewPassword.classList.add("is-invalid");
        confirmNewPasswordError.textContent = "새 비밀번호 확인이 일치하지 않아.";
        isValid = false;
    }

    return isValid;
}

async function initProfilePage(me) {
    if (document.body.dataset.page !== "profile") return;

    if (!me.loggedIn) {
        const next = `${window.location.pathname.split("/").pop() || "profile.html"}${window.location.search || ""}`;
        window.location.href = `login.html?next=${encodeURIComponent(next)}`;
        return;
    }

    const form = document.getElementById("profileForm");
    const username = document.getElementById("profileUsername");
    const nickname = document.getElementById("profileNickname");
    const email = document.getElementById("profileEmail");
    const channelName = document.getElementById("profileChannelName");
    const profileImage = document.getElementById("profileImage");
    const commonError = document.getElementById("profileCommonError");
    const successMessage = document.getElementById("profileSuccessMessage");
    const submitBtn = document.getElementById("profileSubmitBtn");

    const response = await fetch("/api/profile");
    const result = await response.json().catch(() => ({}));

    if (!response.ok || !result.success || !result.user) {
        commonError.textContent = result.message || "프로필 정보를 불러오지 못했어.";
        return;
    }

    const user = result.user;

    username.value = user.username || "";
    nickname.value = user.nickname || "";
    email.value = user.email || "";
    channelName.value = user.channelName || "";
    profileImage.value = user.profileImage || "";

    const bannerImageInput = document.getElementById("bannerImage");
    const channelBioInput = document.getElementById("channelBio");
    const bioCharCount = document.getElementById("bioCharCount");
    const bannerPreviewWrap = document.getElementById("bannerPreviewWrap");
    const bannerPreview = document.getElementById("bannerPreview");

    if (bannerImageInput) bannerImageInput.value = user.bannerImage || "";
    if (channelBioInput) channelBioInput.value = user.bio || "";
    if (bioCharCount) bioCharCount.textContent = (user.bio || "").length;

    if (bannerImageInput && user.bannerImage) {
        if (bannerPreviewWrap) bannerPreviewWrap.style.display = "block";
        if (bannerPreview) bannerPreview.src = user.bannerImage;
    }

    bannerImageInput?.addEventListener("input", () => {
        const url = bannerImageInput.value.trim();
        if (url && bannerPreviewWrap && bannerPreview) {
            bannerPreviewWrap.style.display = "block";
            bannerPreview.src = url;
        } else if (bannerPreviewWrap) {
            bannerPreviewWrap.style.display = "none";
        }
    });

    channelBioInput?.addEventListener("input", () => {
        if (bioCharCount) bioCharCount.textContent = channelBioInput.value.length;
    });

    updateProfilePreview(user.profileImage || "", user.channelName || user.nickname || user.username);

    profileImage.addEventListener("input", () => {
        updateProfilePreview(
            profileImage.value.trim(),
            channelName.value.trim() || nickname.value.trim() || username.value.trim()
        );
    });

        const passwordForm = document.getElementById("passwordForm");
    const currentPassword = document.getElementById("currentPassword");
    const newPassword = document.getElementById("newPassword");
    const confirmNewPassword = document.getElementById("confirmNewPassword");
    const passwordCommonError = document.getElementById("passwordCommonError");
    const passwordSuccessMessage = document.getElementById("passwordSuccessMessage");
    const passwordSubmitBtn = document.getElementById("passwordSubmitBtn");

    passwordForm?.addEventListener("submit", async (event) => {
        event.preventDefault();

        passwordCommonError.textContent = "";
        passwordSuccessMessage.textContent = "";

        if (!validatePasswordForm()) return;

        passwordSubmitBtn.disabled = true;
        passwordSubmitBtn.textContent = "변경 중...";

        const response = await fetch("/api/profile/password", {
            method: "PUT",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                currentPassword: currentPassword.value,
                newPassword: newPassword.value,
                confirmPassword: confirmNewPassword.value
            })
        });

        const result = await response.json().catch(() => ({}));

        if (!response.ok || !result.success) {
            passwordCommonError.textContent = result.message || "비밀번호 변경에 실패했어.";
            passwordSubmitBtn.disabled = false;
            passwordSubmitBtn.textContent = "비밀번호 변경";
            return;
        }

        passwordSuccessMessage.textContent = result.message || "비밀번호가 변경되었습니다.";

        currentPassword.value = "";
        newPassword.value = "";
        confirmNewPassword.value = "";

        passwordSubmitBtn.disabled = false;
        passwordSubmitBtn.textContent = "비밀번호 변경";
    });

    channelName.addEventListener("input", () => {
        updateProfilePreview(
            profileImage.value.trim(),
            channelName.value.trim() || nickname.value.trim() || username.value.trim()
        );
    });

    nickname.addEventListener("input", () => {
        updateProfilePreview(
            profileImage.value.trim(),
            channelName.value.trim() || nickname.value.trim() || username.value.trim()
        );
    });

    form?.addEventListener("submit", async (event) => {
        event.preventDefault();

        successMessage.textContent = "";

        if (!validateProfileForm()) return;

        submitBtn.disabled = true;
        submitBtn.textContent = "저장 중...";

        const payload = {
            nickname: nickname.value.trim(),
            email: email.value.trim(),
            channelName: channelName.value.trim(),
            profileImage: profileImage.value.trim(),
            bannerImage: (document.getElementById("bannerImage")?.value || "").trim(),
            bio: (document.getElementById("channelBio")?.value || "").trim()
        };

        const saveResponse = await fetch("/api/profile", {
            method: "PUT",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(payload)
        });

        const saveResult = await saveResponse.json().catch(() => ({}));

        if (!saveResponse.ok || !saveResult.success) {
            commonError.textContent = saveResult.message || "프로필 저장에 실패했어.";
            submitBtn.disabled = false;
            submitBtn.textContent = "저장";
            return;
        }

        successMessage.textContent = saveResult.message || "프로필이 저장되었습니다.";
        submitBtn.disabled = false;
        submitBtn.textContent = "저장";

        window.__AUTH_ME__ = {
            loggedIn: true,
            user: saveResult.user
        };

        renderTopbarAuth(saveResult.user);
        updateProfilePreview(
            saveResult.user.profileImage || "",
            saveResult.user.channelName || saveResult.user.nickname || saveResult.user.username
        );
    });
}

function protectPages(me) {
    const page = document.body.dataset.page;
    const protectedPages = new Set(["upload", "channel", "edit", "profile"]);

    if (!protectedPages.has(page)) return;
    if (me.loggedIn) return;

    const next = `${window.location.pathname.split("/").pop() || "index.html"}${window.location.search || ""}`;
    window.location.href = `login.html?next=${encodeURIComponent(next)}`;
}

window.__AUTH_READY__ = (async function initAuth() {
    const me = await fetchMe();

    window.__AUTH_ME__ = me;

    renderTopbarAuth(me.user);
    initLoginPage(me);
    initSignupPage(me);
    await initProfilePage(me);
    protectPages(me);

    return me;
})();