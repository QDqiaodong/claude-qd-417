<template>
  <div>
    <div class="head"><h2>选课</h2><span class="tip">场次日历：按报名日期分格，格内显示当日课程与学员。</span></div>

    <div class="calendar">
      <div v-for="g in groups" :key="g.date" class="day">
        <div class="day-head">{{ g.date }}</div>
        <div v-for="e in g.items" :key="e.id" :class="['evt', { withdrawn: e.status === '已退' }]">
          <div class="evt-course">{{ courseName(e.courseId) }}</div>
          <el-tag v-if="courseMap[e.courseId]?.resurfacing" type="warning" size="small" class="evt-freeze">
            🚜 浇冰中 · 仅冻新报名
          </el-tag>
          <div class="evt-member">{{ memberName(e.memberId) }}</div>
          <div class="evt-foot">
            <el-tag size="small" :type="e.status === '已退' ? 'info' : 'success'">{{ e.status }}</el-tag>
            <el-button v-if="e.status === '已报'" size="small" text type="danger"
                       :loading="withdrawing.has(e.id)" @click="withdraw(e)">退课</el-button>
          </div>
        </div>
        <el-empty v-if="g.items.length === 0" :image-size="40" description="无" />
      </div>
      <el-empty v-if="groups.length === 0" description="无匹配报名" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, inject, onMounted } from 'vue'
import http from '../api'

const categoryValue = inject('categoryValue')
const enrollments = ref([])
const courses = ref([])
const members = ref([])

const courseMap = computed(() => Object.fromEntries(courses.value.map(c => [c.id, c])))
const memberMap = computed(() => Object.fromEntries(members.value.map(m => [m.id, m])))
function courseName(id) { return courseMap.value[id]?.name || '—' }
function memberName(id) { return memberMap.value[id]?.name || '—' }

const filtered = computed(() =>
  categoryValue.value === '全部' ? enrollments.value : enrollments.value.filter(e => e.status === categoryValue.value)
)

const groups = computed(() => {
  const map = {}
  for (const e of filtered.value) (map[e.enrollDate] ||= []).push(e)
  return Object.keys(map).sort().map(date => ({ date, items: map[date] }))
})

async function load() {
  const [e, c, m] = await Promise.all([http.get('/enrollments'), http.get('/courses'), http.get('/members')])
  enrollments.value = e; courses.value = c; members.value = m
}
const withdrawing = ref(new Set())
async function withdraw(e) {
  if (withdrawing.value.has(e.id)) return
  withdrawing.value.add(e.id)
  try {
    await http.put('/enrollments/' + e.id, { status: '已退' })
    await load()
  } finally {
    withdrawing.value.delete(e.id)
  }
}
onMounted(load)
</script>

<style scoped>
.head { display: flex; align-items: baseline; gap: 14px; }
.tip { color: #8aa07a; font-size: 13px; }
.calendar { display: flex; gap: 12px; overflow-x: auto; padding-bottom: 12px; margin-top: 8px; }
.day { min-width: 168px; background: #fff; border: 1px solid #e3efd6; border-radius: 12px; padding: 10px; flex-shrink: 0; }
.day-head { font-weight: 700; color: #7db34f; margin-bottom: 8px; text-align: center; }
.evt { border: 1px solid #e3efd6; border-left: 4px solid #9ccc65; border-radius: 8px; padding: 8px 10px; margin-bottom: 8px; }
.evt.withdrawn { border-left-color: #c0c4cc; opacity: .6; }
.evt-course { font-weight: 600; font-size: 14px; }
.evt-freeze { margin: 4px 0 2px; }
.evt-member { font-size: 12px; color: #8aa07a; margin: 2px 0 6px; }
.evt-foot { display: flex; align-items: center; justify-content: space-between; }
</style>
