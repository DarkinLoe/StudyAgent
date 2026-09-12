<script setup>
import { computed, inject, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, setSession } from '../api'

const route = useRoute()
const router = useRouter()
const toast = inject('toast')

const mode = ref('login') // login | register
const loading = ref(false)
const form = reactive({ username: '', password: '', nickname: '' })

const isRegister = computed(() => mode.value === 'register')

async function submit() {
    if (!form.username.trim() || !form.password) {
        toast('请输入用户名和密码', true)
        return
    }
    loading.value = true
    try {
        const auth = isRegister.value
            ? await api.register(form.username.trim(), form.password, form.nickname.trim() || null)
            : await api.login(form.username.trim(), form.password)
        setSession(auth)
        toast(`${isRegister.value ? '注册成功' : '登录成功'}，欢迎 ${auth.nickname || auth.username}`)
        router.push(route.query.redirect || '/chat')
    } catch (e) {
        toast(e.message, true)
    } finally {
        loading.value = false
    }
}

function switchMode() {
    mode.value = isRegister.value ? 'login' : 'register'
}
</script>

<template>
    <div class="login-wrap">
        <div class="card login-card">
            <h2>{{ isRegister ? '注册账号' : '登录 StudyAgent' }}</h2>
            <p class="hint">
                {{ isRegister ? '注册后自动登录；用户数据彼此隔离' : '首次使用请先注册账号' }}
            </p>
            <form @submit.prevent="submit">
                <input v-model="form.username" placeholder="用户名（3~32 位）" autocomplete="username"/>
                <input v-model="form.password" type="password" placeholder="密码（至少 6 位）" autocomplete="current-password"/>
                <input v-if="isRegister" v-model="form.nickname" placeholder="昵称（可选）"/>
                <button type="submit" :disabled="loading">
                    {{ loading ? '处理中…' : (isRegister ? '注册并登录' : '登录') }}
                </button>
            </form>
            <button class="ghost" type="button" @click="switchMode">
                {{ isRegister ? '已有账号？去登录' : '没有账号？去注册' }}
            </button>
        </div>
    </div>
</template>

<style scoped>
.login-wrap {
    display: flex;
    justify-content: center;
    padding-top: 8vh;
}

.login-card {
    width: 360px;
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.login-card h2 {
    margin: 0;
    font-size: 18px;
}
</style>
