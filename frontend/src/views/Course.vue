<template>
  <div>
    <div class="head"><h2>课程</h2><span class="tip">点「报名」从会员下拉选人，建立会员×课程关联。压在浇冰窗口下的课显示「浇冰中」，只冻新报名。</span></div>

    <div class="cards">
      <div v-for="c in filtered" :key="c.id" :class="['card', { freezing: c.resurfacing }]">
        <div class="card-top">
          <div class="cname">{{ c.name }}</div>
          <el-tag v-if="c.resurfacing" type="warning" size="small" effect="dark">🚜 浇冰中</el-tag>
        </div>
        <div class="clane">冰面 {{ laneCode(c.laneId) }}</div>
        <div class="ctime" v-if="c.sessionDate">📅 {{ c.sessionDate }} {{ c.startTime }}-{{ c.endTime }}</div>
        <div class="ctime ctime-none" v-else>未排期</div>
        <div v-if="c.resurfacing" class="freeze-info">{{ c.resurfaceInfo }} · 暂不接受新报名</div>
        <div class="cmeta">
          <span>容量 {{ c.capacity }}</span>
          <span>已报 {{ c.enrolled }}</span>
        </div>
        <el-progress :percentage="pct(c)" :stroke-width="10" />
        <el-button type="primary" size="small" class="enroll-btn"
                   :disabled="c.resurfacing" @click="openEnroll(c)">
          {{ c.resurfacing ? '浇冰中 · 暂停报名' : '报名' }}
        </el-button>
        <el-button size="small" class="skate-btn" @click="goRent(c)">⛸ 发冰刀</el-button>
      </div>
      <el-empty v-if="filtered.length === 0" description="无匹配课程" />
    </div>

    <el-dialog v-model="show" :title="'报名 · ' + (current?.name || '')" width="400px">
      <el-form label-width="80px">
        <el-form-item label="选择会员">
          <el-select v-model="memberId" filterable placeholder="请选择会员" style="width:100%">
            <el-option v-for="m in members" :key="m.id" :label="m.name + '（' + m.cardNo + '·' + m.status + '）'" :value="m.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="show = false">取消</el-button>
        <el-button type="primary" :loading="enrolling" @click="enroll">确认报名</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, inject, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import http from '../api'

const router = useRouter()

const categoryValue = inject('categoryValue')
const courses = ref([])
const members = ref([])
const lanes = ref([])
const show = ref(false)
const current = ref(null)
const memberId = ref(null)
const enrolling = ref(false)

const laneMap = computed(() => Object.fromEntries(lanes.value.map(l => [l.id, l])))
function laneCode(id) { const l = laneMap.value[id]; return l ? (l.code + ' ' + l.name) : '—' }
function pct(c) { return c.capacity > 0 ? Math.min(100, Math.round(c.enrolled / c.capacity * 100)) : 0 }

const filtered = computed(() => {
  if (categoryValue.value === '全部') return courses.value
  return courses.value.filter(c => laneMap.value[c.laneId]?.code === categoryValue.value)
})

async function load() {
  const [c, m, l] = await Promise.all([http.get('/courses'), http.get('/members'), http.get('/ice-lanes')])
  courses.value = c; members.value = m; lanes.value = l
}
function openEnroll(c) { current.value = c; memberId.value = null; show.value = true }
function goRent(c) { router.push({ path: '/rentals', query: { courseId: c.id } }) }
async function enroll() {
  if (enrolling.value) return
  enrolling.value = true
  try {
    await http.post('/enrollments', { memberId: memberId.value, courseId: current.value.id })
    show.value = false
    await load()
  } finally {
    enrolling.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.head { display: flex; align-items: baseline; gap: 14px; }
.tip { color: #8aa07a; font-size: 13px; }
.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 14px; margin-top: 8px; }
.card {
  background: #fff; border: 1px solid #e3efd6; border-radius: 12px;
  padding: 14px 16px; box-shadow: 0 2px 6px rgba(125,179,79,0.08);
}
/* 浇冰中：橙色边框提示，课程本体不作废 */
.card.freezing { border-color: #ffb74d; background: #fffaf0; box-shadow: 0 2px 8px rgba(255,183,77,0.25); }
.card-top { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
.cname { font-weight: 700; font-size: 16px; }
.clane { color: #8aa07a; font-size: 12px; margin-bottom: 6px; }
.ctime { font-size: 12.5px; color: #5f7a3a; margin-bottom: 6px; }
.ctime-none { color: #b7c6a2; }
.freeze-info {
  font-size: 12px; color: #c98a2b; background: #fff3e0;
  border-radius: 6px; padding: 4px 8px; margin-bottom: 8px;
}
.cmeta { display: flex; justify-content: space-between; font-size: 13px; color: #5f7a3a; margin-bottom: 6px; }
.enroll-btn { margin-top: 10px; width: 100%; }
.skate-btn { margin-top: 6px; width: 100%; }
</style>
