<script setup>
import { onMounted, provide, ref } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import { api, getUserId, setUserId } from './api'

const userIdInput = ref(getUserId())
const healthState = ref('unknown') // up | warn | down
const toastMessage = ref('')
const toastError = ref(false)
let toastTimer = null

/** 简易全局提示：子组件通过 inject('toast') 使用 */
function toast(message, isError = false) {
    toastMessage.value = message
    toastError.value = isError
    clearTimeout(toastTimer)
    toastTimer = setTimeout(() => (toastMessage.value = ''), 3600)
}
provide('toast', toast)

function applyUserId() {
    setUserId(userIdInput.value)
    toast(`已切换为用户 ${getUserId()}`)
    checkHealth()
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
        <div class="controls">
            <label class="inline">用户
                <input v-model="userIdInput" type="number" min="1" @change="applyUserId"/>
            </label>
            <span class="badge"
                  :class="{ 'badge-ok': healthState === 'up', 'badge-off': healthState === 'down', 'badge-warn': healthState === 'warn' }">
                {{ healthState === 'up' ? '已连接' : healthState === 'down' ? '未连接' : '检测中' }}
            </span>
            <button class="ghost" type="button" @click="checkHealth">重新检测</button>
        </div>
    </header>

    <nav class="tabs">
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
