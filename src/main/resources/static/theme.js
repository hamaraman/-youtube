// Apply saved theme before CSS renders (prevents flash)
(function () {
    try {
        var t = localStorage.getItem('mt-theme') || 'dark';
        document.documentElement.setAttribute('data-theme', t);
    } catch (e) {}
})();

window.ThemeManager = {
    getTheme: function () {
        return document.documentElement.getAttribute('data-theme') || 'dark';
    },

    setTheme: function (theme) {
        document.documentElement.setAttribute('data-theme', theme);
        try { localStorage.setItem('mt-theme', theme); } catch (e) {}
        document.querySelectorAll('.theme-toggle-btn').forEach(function (btn) {
            window.ThemeManager._updateIcon(btn, theme);
        });
    },

    toggle: function () {
        var next = window.ThemeManager.getTheme() === 'dark' ? 'light' : 'dark';
        window.ThemeManager.setTheme(next);
    },

    createToggleBtn: function () {
        var btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'icon-btn theme-toggle-btn';
        btn.setAttribute('aria-label', '다크/라이트 모드 전환');
        btn.setAttribute('title', '다크/라이트 모드');
        window.ThemeManager._updateIcon(btn, window.ThemeManager.getTheme());
        btn.addEventListener('click', window.ThemeManager.toggle);
        return btn;
    },

    _sunSvg: '<svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true" fill="currentColor"><path d="M12 7c-2.76 0-5 2.24-5 5s2.24 5 5 5 5-2.24 5-5-2.24-5-5-5zM2 13h2c.55 0 1-.45 1-1s-.45-1-1-1H2c-.55 0-1 .45-1 1s.45 1 1 1zm18 0h2c.55 0 1-.45 1-1s-.45-1-1-1h-2c-.55 0-1 .45-1 1s.45 1 1 1zM11 2v2c0 .55.45 1 1 1s1-.45 1-1V2c0-.55-.45-1-1-1s-1 .45-1 1zm0 18v2c0 .55.45 1 1 1s1-.45 1-1v-2c0-.55-.45-1-1-1s-1 .45-1 1zM5.99 4.58c-.39-.39-1.03-.39-1.41 0-.39.39-.39 1.03 0 1.41l1.06 1.06c.39.39 1.03.39 1.41 0s.39-1.03 0-1.41zm12.37 12.37c-.39-.39-1.03-.39-1.41 0-.39.39-.39 1.03 0 1.41l1.06 1.06c.39.39 1.03.39 1.41 0s.39-1.03 0-1.41zm1.06-12.37-1.06 1.06c-.39.39-.39 1.03 0 1.41.39.39 1.03.39 1.41 0l1.06-1.06c.39-.39.39-1.03 0-1.41-.38-.39-1.03-.39-1.41 0zM7.05 18.36l-1.06 1.06c-.39.39-.39 1.03 0 1.41.39.39 1.03.39 1.41 0l1.06-1.06c.39-.39.39-1.03 0-1.41-.39-.39-1.03-.39-1.41 0z"/></svg>',

    _moonSvg: '<svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true" fill="currentColor"><path d="M12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9 9-4.03 9-9c0-.46-.04-.92-.1-1.36-.98 1.37-2.58 2.26-4.4 2.26-2.98 0-5.4-2.42-5.4-5.4 0-1.81.89-3.42 2.26-4.4-.44-.06-.9-.1-1.36-.1z"/></svg>',

    _updateIcon: function (btn, theme) {
        btn.innerHTML = theme === 'dark'
            ? window.ThemeManager._sunSvg
            : window.ThemeManager._moonSvg;
    }
};
