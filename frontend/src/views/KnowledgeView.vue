<script setup>
import { inject, onMounted, reactive, ref } from 'vue'
import { api } from '../api'

const toast = inject('toast')

const notes = ref([])
const sessions = ref([])
const sessionId = ref(null)
const stats = ref(null)
const aiSummary = ref('')
const loadingSummary = ref(false)

const noteForm = reactive({ title: '', content: '' })

async function loadNotes() {
    try {
        const page = await api.notes()
        notes.value = page.items || []
    } catch (e) {
        toast(e.message, true)
    }
}

async function loadSessions() {
    try {
        const page = await api.sessions()
        sessions.value = page.items || []
    } catch (e) {
        toast(e.message, true)
    }
}

async function createNote() {
    if (!noteForm.title.trim() || !noteForm.content.trim()) {
        toast('标题与内容必填', true)
        return
    }
    try {
        await api.createNote({ title: noteForm.title.trim(), content: noteForm.content.trim() })
        noteForm.title = ''
        noteForm.content = ''
        toast('笔记已保存')
        await loadNotes()
    } catch (e) {
        toast(e.message, true)
    }
}

async function autoNote() {
    if (!sessionId.value) {
        toast('请先选择一个会话', true)
        return
    }
    toast('Agent 整理中…')
    try {
        const note = await api.autoNoteFromSession(sessionId.value)
        toast(`已生成笔记：${note.title}`)
        await loadNotes()
    } catch (e) {
        toast(e.message, true)
    }
}

async function loadStats() {
    try {
        stats.value = await api.learningStats()
    } catch (e) {
        toast(e.message, true)
    }
}

async function summary() {
    loadingSummary.value = true
    aiSummary.value = 'AI 分析中…'
    try {
        const data = await api.learningSummary()
        aiSummary.value = data.summary || ''
    } catch (e) {
        aiSummary.value = '生成失败：' + e.message
    } finally {
        loadingSummary.value = false
    }
}

onMounted(async () => {
    await loadNotes()
    await loadSessions()
    await loadStats()
})
</script>

<template>
    <div class="grid">
        <div class="card">
            <h3>写笔记</h3>
            <form @submit.prevent="createNote">
                <input v-model="noteForm.title" placeholder="标题"/>
                <textarea v-model="noteForm.content" rows="5" placeholder="内容（Markdown）"></textarea>
                <button type="submit">保存笔记</button>
            </form>
        </div>

        <div class="card">
            <h3>Agent 自动整理</h3>
            <p class="hint">选择一次对话，让 Agent 整理成结构化的学习笔记（需要 AI Key）。</p>
            <form class="row" @submit.prevent="autoNote">
                <select v-model="sessionId">
                    <option :value="null">选择会话…</option>
                    <option v-for="s in sessions" :key="s.id" :value="s.id">{{ s.title }}</option>
                </select>
                <button type="submit">开始整理</button>
            </form>
            <ul class="list" style="margin-top:10px">
                <li v-for="n in notes" :key="n.id" class="static">
                    {{ n.title }}
                    <div class="hint">{{ n.sourceType }} · {{ String(n.content || '').slice(0, 60) }}…</div>
                </li>
                <li v-if="!notes.length" class="static hint">还没有笔记</li>
            </ul>
        </div>

        <div class="card wide">
            <div class="side-head">
                <h3>学习分析</h3>
                <div>
                    <button class="ghost" type="button" @click="loadStats">刷新</button>
                    <button type="button" :disabled="loadingSummary" @click="summary">AI 总结</button>
                </div>
            </div>
            <div v-if="stats" class="stats">
                <div class="stat"><b>{{ stats.planTotal }}</b><span>学习计划</span></div>
                <div class="stat"><b>{{ stats.taskTotal }}</b><span>计划任务</span></div>
                <div class="stat"><b>{{ stats.taskDone }}</b><span>已完成任务</span></div>
                <div class="stat"><b>{{ (Number(stats.completionRate || 0) * 100).toFixed(0) }}%</b><span>完成率</span></div>
                <div class="stat"><b>{{ stats.doneMinutes }}</b><span>已投入分钟</span></div>
                <div class="stat"><b>{{ stats.doneMinutesLast7 }}</b><span>近 7 天分钟</span></div>
                <div class="stat"><b>{{ stats.wrongTotal }}</b><span>错题总数</span></div>
                <div class="stat"><b>{{ stats.wrongPending }}</b><span>待复习错题</span></div>
                <div class="stat"><b>{{ stats.noteTotal }}</b><span>笔记数</span></div>
                <div class="stat">
                    <b style="font-size:14px">
                        {{ (stats.weakTags || []).map(t => `${t.name}×${t.count}`).join('、') || '暂无' }}
                    </b>
                    <span>高频错题知识点</span>
                </div>
            </div>
            <div v-if="aiSummary" class="small" style="margin-top:12px; white-space:pre-wrap">{{ aiSummary }}</div>
        </div>
    </div>
</template>
