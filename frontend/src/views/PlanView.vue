<script setup>
import { inject, onMounted, reactive, ref } from 'vue'
import { api } from '../api'

const toast = inject('toast')

const iso = (date) => date.toISOString().slice(0, 10)
const today = new Date()

const plans = ref([])
const planId = ref(null)
const todayTasks = ref([])
const reminders = ref([])
const unread = ref(0)

const planForm = reactive({
    title: '',
    startDate: iso(today),
    endDate: iso(new Date(today.getTime() + 7 * 86400000))
})

const taskForm = reactive({
    subject: '',
    content: '',
    plannedDate: iso(today),
    plannedStart: '09:00',
    plannedMinutes: 60
})

async function loadPlans() {
    try {
        const page = await api.plans()
        plans.value = page.items || []
    } catch (e) {
        toast(e.message, true)
    }
}

async function selectPlan(id) {
    planId.value = planId.value === id ? null : id
}

async function createPlan() {
    if (!planForm.title.trim()) return
    try {
        await api.createPlan({
            title: planForm.title.trim(),
            startDate: planForm.startDate || null,
            endDate: planForm.endDate || null
        })
        planForm.title = ''
        toast('计划已创建')
        await loadPlans()
    } catch (e) {
        toast(e.message, true)
    }
}

async function addTask() {
    if (!planId.value) {
        toast('请先选择一个计划', true)
        return
    }
    const time = taskForm.plannedStart.length === 5 ? `${taskForm.plannedStart}:00` : taskForm.plannedStart
    try {
        await api.addTask(planId.value, {
            subject: taskForm.subject.trim() || '未分类',
            content: taskForm.content.trim(),
            plannedDate: taskForm.plannedDate,
            plannedStart: time,
            plannedMinutes: Number(taskForm.plannedMinutes) || 60
        })
        taskForm.subject = ''
        taskForm.content = ''
        toast('任务已添加')
        await loadToday()
    } catch (e) {
        toast(e.message, true)
    }
}

async function loadToday() {
    try {
        todayTasks.value = (await api.todayTasks()) || []
    } catch (e) {
        toast(e.message, true)
    }
}

async function complete(task) {
    try {
        await api.completeTask(task.taskId)
        toast('已完成（相关缓存已失效）')
        await loadToday()
    } catch (e) {
        toast(e.message, true)
    }
}

async function loadReminders() {
    try {
        const count = await api.unreadReminderCount()
        unread.value = count.count
    } catch (e) {
        /* 角标失败可忽略 */
    }
    try {
        reminders.value = (await api.unreadReminders()) || []
    } catch (e) {
        toast(e.message, true)
    }
}

async function readOne(reminder) {
    try {
        await api.readReminder(reminder.id)
        await loadReminders()
    } catch (e) {
        toast(e.message, true)
    }
}

async function readAll() {
    try {
        await api.readAllReminders()
        toast('已全部标记为已读')
        await loadReminders()
    } catch (e) {
        toast(e.message, true)
    }
}

onMounted(async () => {
    await loadPlans()
    await loadToday()
    await loadReminders()
})
</script>

<template>
    <div class="grid">
        <div class="card">
            <h3>创建学习计划</h3>
            <form @submit.prevent="createPlan">
                <input v-model="planForm.title" placeholder="计划名称，如：面试冲刺"/>
                <div class="row">
                    <label class="inline">开始 <input v-model="planForm.startDate" type="date"/></label>
                    <label class="inline">结束 <input v-model="planForm.endDate" type="date"/></label>
                </div>
                <button type="submit">创建计划</button>
            </form>
            <ul class="list" style="margin-top:10px">
                <li v-for="p in plans" :key="p.id" :class="{ selected: p.id === planId }" @click="selectPlan(p.id)">
                    {{ p.title }}
                    <div class="hint">{{ p.startDate || '-' }} ~ {{ p.endDate || '-' }} · {{ p.status }}</div>
                </li>
                <li v-if="!plans.length" class="static hint">还没有计划</li>
            </ul>
        </div>

        <div class="card">
            <h3>添加计划任务</h3>
            <form @submit.prevent="addTask">
                <input v-model="taskForm.subject" placeholder="科目，如：线性代数"/>
                <input v-model="taskForm.content" placeholder="学习内容"/>
                <div class="row">
                    <label class="inline">日期 <input v-model="taskForm.plannedDate" type="date"/></label>
                    <label class="inline">开始 <input v-model="taskForm.plannedStart" type="time"/></label>
                    <input v-model="taskForm.plannedMinutes" type="number" min="1" title="预计分钟"/>
                </div>
                <button type="submit">添加任务</button>
            </form>
            <p class="hint">{{ planId ? `已选计划 #${planId}` : '请先在左侧选择一个计划' }}</p>
        </div>

        <div class="card">
            <div class="side-head">
                <h3>今日学习</h3>
                <button class="ghost" type="button" @click="loadToday">刷新</button>
            </div>
            <ul class="list">
                <li v-for="t in todayTasks" :key="t.taskId" class="static">
                    <span v-if="t.overdue" class="badge badge-off">已逾期</span>
                    <b>{{ t.subject }}</b> {{ t.content }}
                    <div class="hint">
                        {{ t.plannedDate }} {{ t.plannedStart }} · {{ t.plannedMinutes }} 分钟 · {{ t.done ? '已完成' : '未完成' }}
                    </div>
                    <button v-if="!t.done" class="ghost" type="button" @click="complete(t)">标记完成</button>
                </li>
                <li v-if="!todayTasks.length" class="static hint">今天没有安排</li>
            </ul>
        </div>

        <div class="card">
            <div class="side-head">
                <h3>学习提醒 <span class="badge">未读 {{ unread }}</span></h3>
                <button class="ghost" type="button" @click="readAll">全部已读</button>
            </div>
            <ul class="list">
                <li v-for="r in reminders" :key="r.id" class="static">
                    <b>{{ r.title }}</b>
                    <div class="hint">{{ r.message }}</div>
                    <button class="ghost" type="button" @click="readOne(r)">标记已读</button>
                </li>
                <li v-if="!reminders.length" class="static hint">没有未读提醒（调度器每分钟扫描到期任务）</li>
            </ul>
        </div>
    </div>
</template>
