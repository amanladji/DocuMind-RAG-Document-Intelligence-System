const TOKEN_KEY = 'rag_chatbot_token';
const USER_KEY = 'rag_chatbot_user';

function getToken() {
    return localStorage.getItem(TOKEN_KEY);
}

function getUser() {
    const data = localStorage.getItem(USER_KEY);
    const user = data ? JSON.parse(data) : null;
    if (user && !user.name) {
        user.name = user.email ? user.email.split('@')[0] : '';
    }
    return user;
}

function setAuth(token, email, name) {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, JSON.stringify({ email, name: name || '' }));
}

function clearAuth() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
}

function isAuthenticated() {
    const token = getToken();
    if (!token) return false;
    try {
        const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
        if (payload.exp * 1000 < Date.now()) {
            clearAuth();
            return false;
        }
        return true;
    } catch (e) {
        clearAuth();
        return false;
    }
}

function requireAuth() {
    if (!isAuthenticated()) {
        window.location.href = '/login.html';
    }
}

async function authFetch(url, options = {}) {
    const token = getToken();
    const headers = { ...options.headers };

    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }

    if (!headers['Content-Type'] && !(options.body instanceof FormData)) {
        headers['Content-Type'] = 'application/json';
    }

    const res = await fetch(url, { ...options, headers });

    if (res.status === 401 || res.status === 403) {
        console.warn('authFetch: received', res.status, 'for', url);
        clearAuth();
        window.location.href = '/login.html';
        throw new Error('Unauthorized');
    }

    return res;
}

function logout() {
    clearAuth();
    window.location.href = '/login.html';
}
