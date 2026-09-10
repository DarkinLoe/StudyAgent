<script setup>
import { inject, onMounted, reactive, ref } from 'vue'
import { api } from '../api'

const toast = inject('toast')

const banks = ref([])
const bankId = ref(null)
const questions = ref([])
const wrongs = ref([])
const pending = ref(0)
const answers = reactive({})
const bankName = ref('')
const form = reactive({
    type: 'SINGLE_CHOICE',
    stem: '',
    optionsJson: '',
    answer: '',
    explanation: '',
    difficulty: 3,
    tagsJson: ''
})

async function loadBanks() {
    try {
        const page = await api.banks()
        banks.value = page.items || []
    } catch (e) {
        toast(e.message, true)
    }
}

async function loadQuestions() {
    try {
        const page = await api.questions(1, 10, bankId.value)
        questions.value = page.items || []
    } catch (e) {
        toast(e.message, true)
    }
}

async function loadWrongs() {
    try {
        pending.value = await api.wrongPendingCount()
    } catch (e) {
        /* 角标失败可忽略 */
    }
    try {
        const page = await api.wrongs()
        wrongs.value = page.items || []
    } catch (e) {
        toast(e.message, true)
    }
}

async function selectBank(id) {
    bankId.value = bankId.value === id ? null : id
    await loadQuestions()
}

async function createBank() {
    if (!bankName.value.trim()) return
    try {
        await api.createBank({ name: bankName.value.trim() })
        bankName.value = ''
        toast('题库已创建')
        await loadBanks()
    } catch (e) {
        toast(e.message, true)
    }
}

async function createQuestion() {
    if (!form.stem.trim() || !form.answer.trim()) {
        toast('题干与答案必填', true)
        return
    }
    try {
        await api.createQuestion({
            bankId: bankId.value,
            type: form.type,
            stem: form.stem.trim(),
            optionsJson: form.optionsJson.trim() || null,
            answer: form.answer.trim(),
            explanation: form.explanation.trim() || null,
            difficulty: Number(form.difficulty) || 3,
            tagsJson: form.tagsJson.trim() || null
        })
        form.stem = ''
        form.optionsJson = ''
        form.answer = ''
        form.explanation = ''
        toast('题目已保存')
        await loadQuestions()
    } catch (e) {
        toast(e.message, true)
    }
}

async function practice(question) {
    const answer = (answers[question.id] || '').trim()
    if (!answer) {
        toast('请先填写作答', true)
        return
    }
    try {
        const result = await api.practice(question.id, answer)
        if (result.autoJudged && result.correct) {
            toast('答对了 ✅')
        } else if (result.autoJudged) {
            toast(`答错了，已进错题本（正确答案 ${result.expectedAnswer}）`, true)
        } else {
            toast(`主观题未自动判分（参考答案：${result.expectedAnswer}）`)
        }
        answers[question.id] = ''
        await loadWrongs()
    } catch (e) {
        toast(e.message, true)
    }
}

async function setStatus(wrong, status) {
    try {
        await api.setWrongStatus(wrong.id, status)
        toast('状态已更新')
        await loadWrongs()
    } catch (e) {
        toast(e.message, true)
    }
}

onMounted(async () => {
    await loadBanks()
    await loadQuestions()
    await loadWrongs()
})
</script>

<template>
    <div class="grid">
        <div class="card">
            <h3>题库</h3>
            <form class="row" @submit.prevent="createBank">
                <input v-model="bankName" placeholder="题库名称，如：高数期中"/>
                <button type="submit">创建</button>
            </form>
            <ul class="list" style="margin-top:10px">
                <li v-for="b in banks" :key="b.id" :class="{ selected: b.id === bankId }" @click="selectBank(b.id)">
                    {{ b.name }}
                    <div class="hint">{{ b.description || '点击筛选题目' }}</div>
                </li>
                <li v-if="!banks.length" class="static hint">还没有题库</li>
            </ul>
        </div>

        <div class="card">
            <h3>新增题目</h3>
            <form @submit.prevent="createQuestion">
                <label class="inline">题型
                    <select v-model="form.type">
                        <option value="SINGLE_CHOICE">单选题</option>
                        <option value="MULTIPLE_CHOICE">多选题</option>
                        <option value="TRUE_FALSE">判断题</option>
                        <option value="FILL_BLANK">填空题</option>
                        <option value="SHORT_ANSWER">简答题</option>
                    </select>
                </label>
                <textarea v-model="form.stem" rows="2" placeholder="题干"></textarea>
                <input v-model="form.optionsJson" placeholder='选项 JSON 数组，如 ["选项A","选项B","选项C"]'/>
                <input v-model="form.answer" placeholder="答案：单选 A / 多选 A,B / 判断 对 / 其他填参考答案"/>
                <input v-model="form.explanation" placeholder="解析（可选）"/>
                <div class="row">
                    <input v-model="form.difficulty" type="number" min="1" max="5" title="难度 1-5"/>
                    <input v-model="form.tagsJson" placeholder='标签 JSON，如 ["矩阵"]'/>
                    <button type="submit">保存题目</button>
                </div>
            </form>
        </div>

        <div class="card">
            <h3>刷题（答错自动进错题本）</h3>
            <ul class="list">
                <li v-for="q in questions" :key="q.id" class="static">
                    <b>#{{ q.id }}</b> {{ q.stem }}
                    <div class="hint">{{ q.type }}</div>
                    <div class="row" style="margin-top:8px">
                        <input v-model="answers[q.id]" placeholder="你的作答，如 A / 对"/>
                        <button class="ghost" type="button" @click="practice(q)">提交</button>
                    </div>
                </li>
                <li v-if="!questions.length" class="static hint">暂无题目，先在上方新增</li>
            </ul>
        </div>

        <div class="card">
            <div class="side-head">
                <h3>错题本 <span class="badge">待复习 {{ pending }}</span></h3>
                <button class="ghost" type="button" @click="loadWrongs">刷新</button>
            </div>
            <ul class="list">
                <li v-for="w in wrongs" :key="w.id" class="static">
                    <b>#{{ w.questionId }}</b> {{ w.stem }}
                    <div class="hint">
                        你的作答：{{ w.userAnswer }} · 答错 {{ w.mistakeCount }} 次 · 正确答案 {{ w.expectedAnswer }} · 状态 {{ w.reviewStatus }}
                    </div>
                    <button class="ghost" type="button" @click="setStatus(w, 'REVIEWED')">已复习</button>
                    <button class="ghost" type="button" @click="setStatus(w, 'MASTERED')">已掌握</button>
                </li>
                <li v-if="!wrongs.length" class="static hint">错题本是空的（刷题答错会自动进来）</li>
            </ul>
        </div>
    </div>
</template>
