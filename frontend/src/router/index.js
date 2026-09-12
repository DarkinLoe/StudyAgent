import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '../api'

/**
 * history 模式：URL 干净（/qa 而不是 /#/qa）。
 * 生产环境由 Nginx 的 try_files 回退到 index.html（见 frontend/nginx.conf）。
 *
 * 路由守卫：除 mark meta.public 的页面外，未登录一律跳登录页并记住来源地址。
 */
const routes = [
    { path: '/', redirect: '/chat' },
    { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { public: true, title: '登录' } },
    { path: '/chat', name: 'chat', component: () => import('../views/ChatView.vue'), meta: { title: '对话' } },
    { path: '/rag', name: 'rag', component: () => import('../views/RagView.vue'), meta: { title: '知识库' } },
    { path: '/qa', name: 'qa', component: () => import('../views/QaView.vue'), meta: { title: '题库 / 错题' } },
    { path: '/plan', name: 'plan', component: () => import('../views/PlanView.vue'), meta: { title: '计划 / 提醒' } },
    { path: '/knowledge', name: 'knowledge', component: () => import('../views/KnowledgeView.vue'), meta: { title: '笔记 / 分析' } }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

router.beforeEach((to) => {
    const authed = !!getToken()
    if (!to.meta.public && !authed) {
        return { path: '/login', query: { redirect: to.fullPath } }
    }
    if (to.path === '/login' && authed) {
        return { path: '/chat' }
    }
    return true
})

export default router
