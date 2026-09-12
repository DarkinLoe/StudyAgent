/**
 * 后端 API 封装（唯一的"后端契约"入口）。
 *
 * 约定：
 * - 统一响应 {code, message, data}：code!==0 视为业务失败并抛错
 * - 认证：登录后把 JWT 存 localStorage，后续请求带 `Authorization: Bearer <token>`
 * - 401 统一处理：清除本地会话并跳转登录页；429 提示限流
 * - 路径一律使用相对路径，开发由 Vite 代理、生产由 Nginx 反向代理
 */

const TOKEN_KEY = 'studyAgent.token'
const USER_KEY = 'studyAgent.user'

export function getToken() {
    return localStorage.getItem(TOKEN_KEY)
}

export function setSession(auth) {
    localStorage.setItem(TOKEN_KEY, auth.token)
    localStorage.setItem(USER_KEY, JSON.stringify({
        userId: auth.userId,
        username: auth.username,
        nickname: auth.nickname
    }))
}

export function clearSession() {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
}

export function currentUser() {
    try {
        return JSON.parse(localStorage.getItem(USER_KEY))
    } catch (e) {
        return null
    }
}

function toLogin() {
    clearSession()
    if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login'
    }
}

async function request(path, options = {}) {
    const headers = {}
    const token = getToken()
    if (token) {
        headers['Authorization'] = 'Bearer ' + token
    }
    let body
    if (options.form) {
        body = options.form // FormData：交给浏览器设置 Content-Type
    } else if (options.body !== undefined) {
        headers['Content-Type'] = 'application/json; charset=utf-8'
        body = JSON.stringify(options.body)
    }

    const res = await fetch(path, { method: options.method || 'GET', headers, body })

    if (res.status === 401) {
        toLogin()
        throw new Error('登录已过期，请重新登录')
    }
    if (res.status === 429) {
        throw new Error('请求过于频繁，请稍后再试')
    }

    let json = null
    try {
        json = await res.json()
    } catch (e) {
        json = null
    }
    if (!res.ok) {
        throw new Error(json && json.message ? json.message : `HTTP ${res.status}`)
    }
    if (json && typeof json.code === 'number' && json.code !== 0) {
        throw new Error(json.message || `业务错误 ${json.code}`)
    }
    return json && Object.prototype.hasOwnProperty.call(json, 'data') ? json.data : json
}

export const api = {
    // ---------- 认证 ----------
    login: (username, password) => request('/api/auth/login', { method: 'POST', body: { username, password } }),
    register: (username, password, nickname) =>
        request('/api/auth/register', { method: 'POST', body: { username, password, nickname } }),
    me: () => request('/api/auth/me'),

    // 健康检查（公开接口）
    health: () => request('/actuator/health'),

    // ---------- Agent 对话 ----------
    chat: (payload) => request('/api/agent/chat', { method: 'POST', body: payload }),
    sessions: (page = 1, size = 20) => request(`/api/chat/sessions?page=${page}&size=${size}`),
    messages: (sessionId) => request(`/api/chat/sessions/${sessionId}/messages`),

    // ---------- 知识库 ----------
    documents: (page = 1, size = 20) => request(`/api/rag/documents?page=${page}&size=${size}`),
    uploadDocument: (form) => request('/api/rag/documents', { method: 'POST', form }),
    reindexDocument: (id) => request(`/api/rag/documents/${id}/reindex`, { method: 'POST' }),
    deleteDocument: (id) => request(`/api/rag/documents/${id}`, { method: 'DELETE' }),
    ragSearch: (query, topK = 5) => request(`/api/rag/search?q=${encodeURIComponent(query)}&topK=${topK}`),

    // ---------- 题库 / 错题 ----------
    banks: (page = 1, size = 20) => request(`/api/banks?page=${page}&size=${size}`),
    createBank: (body) => request('/api/banks', { method: 'POST', body }),
    questions: (page = 1, size = 10, bankId = null) =>
        request(`/api/questions?page=${page}&size=${size}` + (bankId ? `&bankId=${bankId}` : '')),
    createQuestion: (body) => request('/api/questions', { method: 'POST', body }),
    practice: (questionId, userAnswer) =>
        request(`/api/questions/${questionId}/practice`, { method: 'POST', body: { userAnswer } }),
    questionsFromDocument: (docId) =>
        request('/api/questions/from-document', { method: 'POST', body: { docId } }),
    wrongs: (page = 1, size = 10, status = null) =>
        request(`/api/wrongs?page=${page}&size=${size}` + (status ? `&status=${status}` : '')),
    wrongPendingCount: () => request('/api/wrongs/pending-count'),
    setWrongStatus: (id, status) => request(`/api/wrongs/${id}/status`, { method: 'PATCH', body: { status } }),

    // ---------- 学习计划 / 提醒 ----------
    plans: (page = 1, size = 10) => request(`/api/plans?page=${page}&size=${size}`),
    createPlan: (body) => request('/api/plans', { method: 'POST', body }),
    planTasks: (planId) => request(`/api/plans/${planId}/tasks`),
    addTask: (planId, body) => request(`/api/plans/${planId}/tasks`, { method: 'POST', body }),
    completeTask: (taskId) => request(`/api/plans/tasks/${taskId}/complete`, { method: 'POST' }),
    todayTasks: () => request('/api/plans/today'),
    unreadReminders: () => request('/api/reminders/unread'),
    unreadReminderCount: () => request('/api/reminders/unread-count'),
    readReminder: (id) => request(`/api/reminders/${id}/read`, { method: 'POST' }),
    readAllReminders: () => request('/api/reminders/read-all', { method: 'POST' }),

    // ---------- 笔记 / 分析 ----------
    notes: (page = 1, size = 10) => request(`/api/notes?page=${page}&size=${size}`),
    createNote: (body) => request('/api/notes', { method: 'POST', body }),
    autoNoteFromSession: (sessionId, title = null) =>
        request('/api/notes/auto-from-session', { method: 'POST', body: { sessionId, title } }),
    autoNoteFromDocument: (docId, title = null) =>
        request('/api/notes/auto-from-document', { method: 'POST', body: { docId, title } }),
    learningStats: () => request('/api/analytics/learning'),
    learningSummary: () => request('/api/analytics/learning/summary')
}
