<script setup>
import { inject, nextTick, onMounted, ref } from 'vue'
import { api } from '../api'

const toast = inject('toast')
const sessions = ref([])
const sessionId = ref(null)
const messages = ref([])
const input = ref('')
const sending = ref(false)
const box = ref(null)

const fmt = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '')

function refs(message) {
    try {
        const list = message.referencesJson ? JSON.parse(message.referencesJson) : []
        return Array.isArray(list) ? list : []
    } catch (e) {
        return []
    }
}

function usage(message) {
    try {
        return message.usageJson ? JSON.parse(message.usageJson) : null
    } catch (e) {
        return null
    }
}

async function scrollBottom() {
    await nextTick()
    if (box.value) box.value.scrollTop = box.value.scrollHeight
}

async function loadSessions() {
    try {
        const page = await api.sessions()
        sessions.value = page.items || []
    } catch (e) {
        toast(e.message, true)
    }
}

async function loadMessages() {
    if (sessionId.value === null) {
        messages.value = []
        return
    }
    try {
        messages.value = (await api.messages(sessionId.value)) || []
    } catch (e) {
        toast(e.message, true)
    }
    await scrollBottom()
}

async function openSession(id) {
    sessionId.value = id
    await loadMessages()
    await loadSessions()
}

function newChat() {
    sessionId.value = null
    messages.value = []
}

async function send() {
    const content = input.value.trim()
    if (!content || sending.value) return
    sending.value = true
    input.value = ''
    messages.value.push({ role: 'USER', content })
    const pending = { role: 'ASSISTANT', content: '思考中…' }
    messages.value.push(pending)
    await scrollBottom()
    try {
        const reply = await api.chat({ sessionId: sessionId.value, content })
        sessionId.value = reply.sessionId
        await loadMessages()
        await loadSessions()
        if (reply.references && reply.references.length) {
            toast(`回答引用了 ${reply.references.length} 条知识库来源`)
        }
    } catch (e) {
        pending.content = '调用失败：' + e.message + '\n（请检查 AI_BASE_URL / AI_API_KEY 配置）'
        toast(e.message, true)
    } finally {
        sending.value = false
        await scrollBottom()
    }
}

onMounted(async () => {
    await loadSessions()
    await loadMessages()
})
</script>

<template>
    <div class="chat-layout">
        <aside class="card">
            <div class="side-head">
                <h3>会话</h3>
                <button class="ghost" type="button" @click="newChat">新会话</button>
            </div>
            <ul class="list">
                <li v-for="s in sessions" :key="s.id" :class="{ selected: s.id === sessionId }" @click="openSession(s.id)">
                    {{ s.title }}
                    <div class="hint">{{ fmt(s.lastMessageAt) }}</div>
                </li>
                <li v-if="!sessions.length" class="static hint">暂无会话</li>
            </ul>
        </aside>

        <section class="chat">
            <div ref="box" class="messages">
                <div v-if="!messages.length" class="empty">
                    开始提问吧。试试：「我今天学什么？」（会触发 query_today_plan 工具）
                </div>
                <div v-for="(m, i) in messages" :key="i" class="msg" :class="m.role === 'USER' ? 'user' : 'assistant'">
                    {{ m.content }}
                    <div v-if="refs(m).length" class="refs">
                        引用来源：
                        <div v-for="(r, j) in refs(m)" :key="j">
                            《{{ r.docName }}》 相似度 {{ Number(r.score || 0).toFixed(3) }}
                        </div>
                    </div>
                    <div v-if="usage(m)" class="meta">
                        model={{ usage(m).model || '-' }} · tokens={{ usage(m).totalTokens ?? '-' }}
                    </div>
                </div>
            </div>

            <form class="composer" @submit.prevent="send">
                <textarea v-model="input" rows="2" placeholder="输入问题，Enter 发送 / Shift+Enter 换行"
                          @keydown.enter.exact.prevent="send"></textarea>
                <button type="submit" :disabled="sending">{{ sending ? '发送中…' : '发送' }}</button>
            </form>
            <p class="hint">回答优先引用你的知识库；命中来源与 token 用量随消息落库并显示在上方。</p>
        </section>
    </div>
</template>
