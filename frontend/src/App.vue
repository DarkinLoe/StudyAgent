<script setup>
import { computed, onMounted, provide, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { api, clearSession, currentUser } from './api'

const route = useRoute()
const router = useRouter()

const healthState = ref('unknown') // up | warn | down
const toastMessage = ref('')
const toastError = ref(false)
let toastTimer = null

const isLoginPage = computed(() => route.path === '/login')
const user = computed(() => currentUser())

/** 简易全局提示：子组件通过 inject('toast') 使用 */
function toast(message, isError = false) {
    toastMessage.value = message
    toastError.value = isError
    clearTimeout(toastTimer)
    toastTimer = setTimeout(() => (toastMessage.value = ''), 3600)
}
provide('toast', toast)

async function logout() {
    try {
        // 先让服务端吊销令牌：无状态 JWT 自己不会失效，只清本地存储的话，
        // 被拿走的 token 依然能一直用到过期
        await api.logout()
    } catch (e) {
        // 服务端不可达也要保证本地退出，不阻塞用户
    }
    clearSession()
    toast('已退出登录')
    router.push('/login')
}

async function checkHealth() {
    try {
        const health = await api.health()
        healthState.value = health && health.status === 'UP' ? 'up' : 'warn'
    } catch (e) {
        healthState.value = 'down'
    }
}

onMounted(checkHealth)
</script>

<template>
    <header class="topbar">
        <div class="brand">StudyAgent <span class="sub">个人学习 Agent 控制台（Vue 3 + Vite）</span></div>
        <div v-if="!isLoginPage" class="controls">
            <span class="inline" v-if="user">👤 {{ user.nickname || user.username }}</span>
            <span class="badge"
                  :class="{ 'badge-ok': healthState === 'up', 'badge-off': healthState === 'down', 'badge-warn': healthState === 'warn' }">
                {{ healthState === 'up' ? '已连接' : healthState === 'down' ? '未连接' : '检测中' }}
            </span>
            <button class="ghost" type="button" @click="checkHealth">重新检测</button>
            <button class="ghost" type="button" @click="logout">退出登录</button>
        </div>
    </header>

    <nav class="tabs" v-if="!isLoginPage">
        <RouterLink class="tab" active-class="active" to="/chat">对话</RouterLink>
        <RouterLink class="tab" active-class="active" to="/rag">知识库</RouterLink>
        <RouterLink class="tab" active-class="active" to="/qa">题库 / 错题</RouterLink>
        <RouterLink class="tab" active-class="active" to="/plan">计划 / 提醒</RouterLink>
        <RouterLink class="tab" active-class="active" to="/knowledge">笔记 / 分析</RouterLink>
    </nav>

    <main>
        <RouterView/>
    </main>

    <div class="toast" :class="{ show: !!toastMessage, err: toastError }">{{ toastMessage }}</div>
</template>
