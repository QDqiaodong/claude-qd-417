<template>
  <div>
    <div class="head">
      <h2>浇冰窗口</h2>
      <el-button type="primary" @click="openAdd">+ 落浇冰窗口</el-button>
    </div>
    <div class="rules">
      <div class="rules-title">浇冰规则</div>
      <ul>
        <li>窗口写明：哪块冰、哪一天、从几点浇到几点、当班磨冰工。</li>
        <li>窗口落下后，同时段压在这块冰上的公开课标记「浇冰中」，并冻住新报名；已报名的不清、课不挪冰。</li>
        <li>白天可落窗口；把窗口改到打烊后、不再压课，原先冻住的课自动恢复加人。</li>
        <li>同一冰面、同一天时段相交，后写的整单回掉，先落下的继续压课；回掉不动冰面状态、不动已报人数。</li>
        <li>已关闭的冰面没有冰可浇，窗口建不起来。</li>
      </ul>
    </div>

    <div v-for="g in groups" :key="g.date" class="day-group">
      <div class="day-head">
        <span class="day-date">{{ g.date }}</span>
        <span class="day-lane">{{ laneCode(g.laneId) }}</span>
      </div>
      <div v-for="w in g.items" :key="w.id" class="win">
        <div class="win-main">
          <div class="win-time">🚜 {{ w.startTime }} - {{ w.endTime }}</div>
          <div class="win-meta">
            <span>冰面 {{ laneCode(w.laneId) }}</span>
            <span>当班磨冰工：{{ w.operator }}</span>
          </div>
        </div>
        <div class="win-courses">
          <template v-if="frozenCourses(w).length">
            <div class="win-courses-title">压课 {{ frozenCourses(w).length }} 门（仅冻新报名）：</div>
            <el-tag v-for="c in frozenCourses(w)" :key="c.id" class="win-tag" type="warning" effect="plain">
              {{ c.name }} {{ c.startTime }}-{{ c.endTime }}
            </el-tag>
          </template>
          <span v-else class="win-no-course">该时段无排课，不压课</span>
        </div>
        <div class="win-ops">
          <el-button size="small" @click="openEdit(w)">改到夜里</el-button>
          <el-button size="small" type="danger" text @click="remove(w)">删除</el-button>
        </div>
      </div>
    </div>
    <el-empty v-if="filtered.length === 0" description="暂无浇冰窗口" />

    <el-dialog v-model="show" :title="editing ? '改浇冰窗口' : '落浇冰窗口'" width="440px">
      <el-form label-width="96px">
        <el-form-item label="冰面">
          <el-select v-model="form.laneId" filterable placeholder="请选择冰面" style="width:100%">
            <el-option v-for="l in lanes" :key="l.id"
                       :label="l.code + ' ' + l.name + '（' + l.status + '）'" :value="l.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="form.winDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="浇冰时段">
          <el-time-picker v-model="form.startTime" format="HH:mm" value-format="HH:mm"
                          placeholder="开始" class="time-pick" />
          <span class="time-sep">至</span>
          <el-time-picker v-model="form.endTime" format="HH:mm" value-format="HH:mm"
                          placeholder="结束" class="time-pick" />
        </el-form-item>
        <el-form-item label="当班磨冰工">
          <el-input v-model="form.operator" placeholder="磨冰工姓名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="show = false">取消</el-button>
        <el-button type="primary" @click="save">落下窗口</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, inject, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api'

const categoryValue = inject('categoryValue')
const windows = ref([])
const lanes = ref([])
const courses = ref([])
const show = ref(false)
const editing = ref(null)
const form = ref(emptyForm())

function emptyForm() {
  return { laneId: null, winDate: '', startTime: '', endTime: '', operator: '' }
}

const laneMap = computed(() => Object.fromEntries(lanes.value.map(l => [l.id, l])))
function laneCode(id) { const l = laneMap.value[id]; return l ? (l.code + ' ' + l.name) : '—' }

// 左侧冰面编号筛选
const filtered = computed(() => {
  if (categoryValue.value === '全部') return windows.value
  return windows.value.filter(w => laneMap.value[w.laneId]?.code === categoryValue.value)
})

// 按 日期 + 冰面 分组
const groups = computed(() => {
  const map = new Map()
  for (const w of filtered.value) {
    const key = w.winDate + '|' + w.laneId
    if (!map.has(key)) map.set(key, { date: w.winDate, laneId: w.laneId, items: [] })
    map.get(key).items.push(w)
  }
  return [...map.values()]
    .sort((a, b) => a.date.localeCompare(b.date))
    .map(g => ({ ...g, items: g.items.sort((a, b) => a.startTime.localeCompare(b.startTime)) }))
})

// 与窗口同冰、同日、时段相交的课程 = 正在被压住的公开课
function frozenCourses(w) {
  return courses.value.filter(c =>
    c.laneId === w.laneId &&
    c.sessionDate === w.winDate &&
    c.startTime && c.endTime &&
    c.startTime < w.endTime && w.startTime < c.endTime)
}

async function load() {
  const [w, l, c] = await Promise.all([
    http.get('/resurface-windows'), http.get('/ice-lanes'), http.get('/courses')
  ])
  windows.value = w; lanes.value = l; courses.value = c
}

function openAdd() { editing.value = null; form.value = emptyForm(); show.value = true }
function openEdit(w) {
  editing.value = w
  form.value = { laneId: w.laneId, winDate: w.winDate, startTime: w.startTime, endTime: w.endTime, operator: w.operator }
  show.value = true
}

async function save() {
  const f = form.value
  if (!f.laneId) return ElMessage.warning('请选择冰面')
  if (!f.winDate) return ElMessage.warning('请选择日期')
  if (!f.startTime || !f.endTime) return ElMessage.warning('请选择浇冰时段')
  if (f.startTime >= f.endTime) return ElMessage.warning('结束时间必须晚于开始时间')
  if (!f.operator || !f.operator.trim()) return ElMessage.warning('请填写当班磨冰工')
  if (editing.value) await http.put('/resurface-windows/' + editing.value.id, f)
  else await http.post('/resurface-windows', f)
  show.value = false
  await load()
}

async function remove(w) {
  await ElMessageBox.confirm(
    `确定删除 ${w.winDate} ${w.startTime}-${w.endTime} 的浇冰窗口？删除后原先冻住的课立即恢复报名。`,
    '删除浇冰窗口', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
  await http.delete('/resurface-windows/' + w.id)
  await load()
}

onMounted(load)
</script>

<style scoped>
.head { display: flex; align-items: center; justify-content: space-between; }
.rules {
  background: #fff; border: 1px dashed #c8e3a3; border-radius: 10px;
  padding: 12px 16px; margin: 12px 0 18px;
}
.rules-title { font-weight: 700; color: #7db34f; font-size: 13px; margin-bottom: 6px; }
.rules ul { margin: 0; padding-left: 18px; color: #6b8a44; font-size: 12.5px; line-height: 1.9; }

.day-group { margin-bottom: 18px; }
.day-head { display: flex; align-items: baseline; gap: 12px; margin-bottom: 8px; }
.day-date { font-weight: 700; color: #4a6b2c; font-size: 16px; }
.day-lane { color: #8aa07a; font-size: 13px; }

.win {
  background: #fff; border: 1px solid #e3efd6; border-left: 5px solid #ffb74d;
  border-radius: 10px; padding: 12px 16px; margin-bottom: 10px;
  display: flex; align-items: center; gap: 18px;
  box-shadow: 0 2px 6px rgba(125,179,79,0.08);
}
.win-main { min-width: 250px; }
.win-time { font-weight: 700; font-size: 16px; color: #5f7a3a; }
.win-meta { display: flex; gap: 14px; font-size: 12.5px; color: #8aa07a; margin-top: 4px; }
.win-courses { flex: 1; font-size: 12.5px; }
.win-courses-title { color: #c98a2b; margin-bottom: 6px; }
.win-tag { margin: 0 6px 6px 0; }
.win-no-course { color: #a8bd92; }
.win-ops { display: flex; flex-direction: column; gap: 4px; align-items: flex-end; }

.time-pick { flex: 1; }
.time-sep { margin: 0 8px; color: #8aa07a; }
</style>
