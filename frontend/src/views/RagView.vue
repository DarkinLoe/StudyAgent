<script setup>
import { inject, onMounted, ref } from 'vue'
import { api } from '../api'

const toast = inject('toast')
const docs = ref([])
const file = ref(null)
const sourceType = ref('PPT')
const tags = ref('')
const uploading = ref(false)
const query = ref('')
const hits = ref([])
const searched = ref(false)

const sourceTypes = [
    { value: 'COURSE_TABLE', label: '课程表' },
    { value: 'STUDY_ARRANGEMENT', label: '学习安排' },
    { value: 'PPT', label: 'PPT / 课件' },
    { value: 'QUESTION_BANK', label: '题目资料' },
    { value: 'NOTE', label: '笔记资料' },
    { value: 'OTHER', label: '其他' }
]

function onFile(event) {
    file.value = event.target.files[0] || null
}

async function load() {
    try {
        const page = await api.documents()
        docs.value = page.items || []
    } catch (e) {
        toast(e.message, true)
    }
}

async function upload() {
    if (!file.value) {
        toast('请选择文件', true)
        return
    }
    const form = new FormData()
    form.append('file', file.value)
    form.append('sourceType', sourceType.value)
    if (tags.value.trim()) form.append('tags', tags.value.trim())
    uploading.value = true
    try {
        const doc = await api.uploadDocument(form)
        toast(`已入库：${doc.name}（状态 ${doc.status}）`)
        file.value = null
        tags.value = ''
        await load()
    } catch (e) {
        toast(e.message, true)
    } finally {
        uploading.value = false
    }
}

async function reindex(id) {
    try {
        await api.reindexDocument(id)
        toast('已触发重索引')
        await load()
    } catch (e) {
        toast(e.message, true)
    }
}

async function remove(id) {
    if (!window.confirm('确认删除该文档及其向量索引？')) return
    try {
        await api.deleteDocument(id)
        toast('已删除')
        await load()
    } catch (e) {
        toast(e.message, true)
    }
}

async function makeNote(doc) {
    try {
        const note = await api.autoNoteFromDocument(doc.id)
        toast(`已生成笔记：${note.title}`)
    } catch (e) {
        toast(e.message, true)
    }
}

async function makeQuestions(doc) {
    try {
        const list = await api.questionsFromDocument(doc.id)
        toast(`已从《${doc.name}》生成 ${list.length} 道题`)
    } catch (e) {
        toast(e.message, true)
    }
}

async function search() {
    const q = query.value.trim()
    if (!q) return
    try {
        hits.value = (await api.ragSearch(q)) || []
        searched.value = true
    } catch (e) {
        toast(e.message, true)
    }
}

onMounted(load)
</script>

<template>
    <div class="grid">
        <div class="card">
            <h3>上传资料（解析 + 索引）</h3>
            <form @submit.prevent="upload">
                <input type="file" @change="onFile"/>
                <label class="inline">来源类型
                    <select v-model="sourceType">
                        <option v-for="t in sourceTypes" :key="t.value" :value="t.value">{{ t.label }}</option>
                    </select>
                </label>
                <input v-model="tags" placeholder='标签 JSON，如 ["高数","期中"]'/>
                <button type="submit" :disabled="uploading">{{ uploading ? '上传中…' : '上传并索引' }}</button>
            </form>

            <h3 style="margin-top:16px">检索测试</h3>
            <form class="row" @submit.prevent="search">
                <input v-model="query" placeholder="如：矩阵 乘法"/>
                <button type="submit">检索</button>
            </form>
            <div class="small" style="margin-top:8px">
                <template v-if="hits.length">
                    <div v-for="(h, i) in hits" :key="i">
                        《{{ h.docName }}》 score={{ Number(h.score || 0).toFixed(3) }}
                        <div class="hint">{{ String(h.text || '').slice(0, 160) }}…</div>
                    </div>
                </template>
                <span v-else-if="searched" class="hint">没有命中（文档未索引，或关键词不匹配）</span>
            </div>
        </div>

        <div class="card">
            <div class="side-head">
                <h3>我的文档</h3>
                <button class="ghost" type="button" @click="load">刷新</button>
            </div>
            <table class="table">
                <thead>
                <tr><th>名称</th><th>类型</th><th>状态</th><th>块数</th><th>操作</th></tr>
                </thead>
                <tbody>
                <tr v-for="d in docs" :key="d.id">
                    <td :title="d.errorMsg || ''">{{ d.name }}</td>
                    <td>{{ d.sourceType }}</td>
                    <td>
                        <span class="badge"
                              :class="{ 'badge-ok': d.status === 'INDEXED', 'badge-off': d.status === 'FAILED', 'badge-warn': d.status !== 'INDEXED' && d.status !== 'FAILED' }">
                            {{ d.status }}
                        </span>
                    </td>
                    <td>{{ d.chunkCount || 0 }}</td>
                    <td>
                        <button class="ghost" type="button" @click="reindex(d.id)">重索引</button>
                        <button class="ghost" type="button" @click="makeNote(d)">整理笔记</button>
                        <button class="ghost" type="button" @click="makeQuestions(d)">出题</button>
                        <button class="ghost" type="button" @click="remove(d.id)">删除</button>
                    </td>
                </tr>
                <tr v-if="!docs.length">
                    <td colspan="5" class="hint">还没有文档，先上传一份 PPT 或课程表</td>
                </tr>
                </tbody>
            </table>
            <p class="hint">「整理笔记 / 出题」需要 AI Key；没有 embedding 能力时检索会自动降级为关键词匹配。</p>
        </div>
    </div>
</template>
