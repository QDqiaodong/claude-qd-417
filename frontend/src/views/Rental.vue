<template>
  <div>
    <div class="head">
      <h2>冰刀租借</h2>
      <span class="tip">按尺码看可借 / 已借 / 待检实物；发鞋由柜员按尺码实际占住一双，抢最后一双时败方整单失败。</span>
    </div>

    <!-- 库存总览 -->
    <div class="summary">
      <div class="sum s-avail"><b>{{ stat.available }}</b><span>可借</span></div>
      <div class="sum s-borrow"><b>{{ stat.borrowed }}</b><span>已借（在会员手上）</span></div>
      <div class="sum s-inspect"><b>{{ stat.inspecting }}</b><span>待检（刀刃损坏）</span></div>
      <div class="sum s-total"><b>{{ skates.length }}</b><span>冰刀总数</span></div>
      <el-button type="primary" class="issue-entry" @click="openPick">按尺码发鞋</el-button>
    </div>

    <!-- 按尺码分组的实物 -->
    <h3 class="block-title">冰刀实物（按尺码）</h3>
    <div v-for="g in sizeGroups" :key="g.size" class="size-row">
      <div class="size-tag">{{ g.size }} 码</div>
      <div class="skate-list">
        <div v-for="s in g.items" :key="s.id" :class="['skate', stClass(s.status)]">
          <div class="skate-code">{{ s.code }}</div>
          <el-tag size="small" :type="tagType(s.status)" effect="dark">{{ s.status }}</el-tag>
          <div v-if="s.status !== '可借'" class="skate-who">
            <template v-if="s.memberName">{{ s.memberName }} · {{ s.courseName }}</template>
            <template v-else>检修中</template>
          </div>
          <div class="skate-actions">
            <template v-if="s.status === '已借'">
              <el-button size="small" type="success" plain @click="returnRental(s.rentalId, false)">正常归还</el-button>
              <el-button size="small" type="danger" plain @click="askDamage(s.rentalId)">损坏归还</el-button>
            </template>
            <el-button v-else-if="s.status === '待检'" size="small" warning plain @click="inspectDone(s.id)">
              检修完成上架
            </el-button>
            <span v-else class="ok-hint">在架可领</span>
          </div>
        </div>
      </div>
    </div>
    <el-empty v-if="filteredSkates.length === 0" description="无该状态冰刀" />

    <!-- 租借记录 -->
    <h3 class="block-title">租借记录</h3>
    <el-table :data="filteredRentals" size="small" border stripe>
      <el-table-column prop="id" label="单号" width="64" />
      <el-table-column label="会员" min-width="120">
        <template #default="{ row }">{{ row.memberName }}（{{ row.memberCard }}）</template>
      </el-table-column>
      <el-table-column prop="courseName" label="课程" min-width="130" />
      <el-table-column label="冰刀" min-width="120">
        <template #default="{ row }">{{ row.skateCode }} · {{ row.shoeSize }}码</template>
      </el-table-column>
      <el-table-column label="状态" width="96">
        <template #default="{ row }">
          <el-tag size="small" :type="rentalTagType(row.status)" effect="dark">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="领取" width="120">
        <template #default="{ row }">{{ row.rentDate }} {{ row.rentTime }}</template>
      </el-table-column>
      <el-table-column label="归还" width="120">
        <template #default="{ row }">
          <span v-if="row.returnDate">{{ row.returnDate }} {{ row.returnTime }}</span>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="damageNote" label="损坏备注" min-width="140">
        <template #default="{ row }"><span v-if="row.damageNote" class="dam-note">{{ row.damageNote }}</span><span v-else class="muted">—</span></template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === '已领取'">
            <el-button size="small" type="success" link @click="returnRental(row.id, false)">正常归还</el-button>
            <el-button size="small" type="danger" link @click="askDamage(row.id)">损坏归还</el-button>
          </template>
          <span v-else class="muted">已收口</span>
        </template>
      </el-table-column>
    </el-table>

    <!-- 发鞋对话框 -->
    <el-dialog v-model="pickShow" title="按尺码发鞋（领取冰刀）" width="430px">
      <el-form label-width="72px">
        <el-form-item label="当天课程">
          <el-select v-model="pickCourseId" placeholder="选择当天课程" style="width:100%" @change="pickMemberId = null">
            <el-option v-for="c in todayCourses" :key="c.id"
                       :label="c.name + '（' + c.sessionDate + ' ' + c.startTime + '）'" :value="c.id" />
          </el-select>
          <div v-if="todayCourses.length === 0" class="form-hint warn">今天（{{ today }}）没有排课，发鞋会被拦下</div>
        </el-form-item>
        <el-form-item label="会员">
          <el-select v-model="pickMemberId" filterable placeholder="选择领取会员" style="width:100%">
            <el-option v-for="m in memberOptions" :key="m.id"
                       :label="m.name + '（' + m.cardNo + '）' + (m.ok ? '' : ' · ' + m.reason)"
                       :value="m.id" :disabled="!m.ok" />
          </el-select>
          <div v-if="pickCourseId && pickedInvalidReason" class="form-hint warn">{{ pickedInvalidReason }}</div>
        </el-form-item>
        <el-form-item label="尺码">
          <el-input-number v-model="pickSize" :min="20" :max="50" />
          <span class="form-hint">当前可借 <b>{{ sizeAvailCount }}</b> 双</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pickShow = false">取消</el-button>
        <el-button type="primary" :loading="loading" @click="confirmPick">确认发鞋</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, inject, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '../api'

const route = useRoute()
const categoryValue = inject('categoryValue')

const skates = ref([])
const rentals = ref([])
const members = ref([])
const courses = ref([])

const pickShow = ref(false)
const pickCourseId = ref(null)
const pickMemberId = ref(null)
const pickSize = ref(38)
const loading = ref(false)

const today = new Date().toISOString().slice(0, 10)

const stat = computed(() => ({
  available: skates.value.filter(s => s.status === '可借').length,
  borrowed: skates.value.filter(s => s.status === '已借').length,
  inspecting: skates.value.filter(s => s.status === '待检').length
}))

function stClass(st) {
  return { 'st-avail': st === '可借', 'st-borrow': st === '已借', 'st-inspect': st === '待检' }
}
function tagType(st) {
  return st === '可借' ? 'success' : st === '已借' ? 'primary' : 'warning'
}
function rentalTagType(st) {
  return st === '已领取' ? 'primary' : st === '已归还' ? 'success' : 'danger'
}

const filteredSkates = computed(() =>
  categoryValue.value === '全部' ? skates.value : skates.value.filter(s => s.status === categoryValue.value)
)
const sizeGroups = computed(() => {
  const map = {}
  for (const s of filteredSkates.value) (map[s.shoeSize] ||= []).push(s)
  return Object.keys(map).map(Number).sort((a, b) => a - b).map(size => ({ size, items: map[size] }))
})

const filteredRentals = computed(() => {
  if (categoryValue.value === '全部') return rentals.value
  // 左侧状态筛选同时作用于实物与租借单
  return rentals.value.filter(r => r.status === categoryValue.value)
})

const todayCourses = computed(() =>
  courses.value.filter(c => c.sessionDate === today)
)

// 会员下拉：把不可领的人标出来（过期 / 无当天有效报名 / 名下有未归还），并禁用
const memberOptions = computed(() => {
  const cid = pickCourseId.value
  return members.value.map(m => {
    const reasons = []
    if (m.expireDate && m.expireDate < today) reasons.push('会员已过期')
    if (m.status && m.status !== '正常') reasons.push('账户' + m.status)
    const enrolled = cid && enrollSet.value.has(m.id + '-' + cid)
    if (cid && !enrolled) reasons.push('无该课程当天有效报名')
    if (openMemberIds.value.has(m.id)) reasons.push('名下有未归还冰刀')
    return { ...m, ok: reasons.length === 0, reason: reasons.join('、') }
  })
})

const enrollSet = computed(() => {
  // 有效报名集合（memberId-courseId），来自租借记录无法推导，单独拉 enrollments
  const set = new Set()
  for (const e of enrollments.value) if (e.status === '已报') set.add(e.memberId + '-' + e.courseId)
  return set
})
const enrollments = ref([])

const openMemberIds = computed(() => {
  const set = new Set()
  for (const r of rentals.value) if (r.status === '已领取') set.add(r.memberId)
  return set
})

const pickedInvalidReason = computed(() => {
  const m = memberOptions.value.find(x => x.id === pickMemberId.value)
  return m && !m.ok ? m.reason : ''
})

const sizeAvailCount = computed(() =>
  skates.value.filter(s => s.shoeSize === pickSize.value && s.status === '可借').length
)

async function load() {
  const [sk, rt, me, co, en] = await Promise.all([
    http.get('/skates'),
    http.get('/skates/rentals'),
    http.get('/members'),
    http.get('/courses'),
    http.get('/enrollments')
  ])
  skates.value = sk; rentals.value = rt; members.value = me; courses.value = co; enrollments.value = en

  // 从课程卡片「发冰刀」跳来：预选当天课程
  const q = Number(route.query.courseId)
  if (q && courses.value.some(c => c.id === q)) {
    pickCourseId.value = q
    pickShow.value = true
  }
}

function openPick() {
  pickMemberId.value = null
  pickShow.value = true
}

async function confirmPick() {
  if (!pickCourseId.value) return ElMessage.warning('请选择当天课程')
  if (!pickMemberId.value) return ElMessage.warning('请选择会员')
  if (!pickSize.value) return ElMessage.warning('请填写尺码')
  loading.value = true
  try {
    const r = await http.post('/skates/rentals', {
      memberId: pickMemberId.value,
      courseId: pickCourseId.value,
      shoeSize: pickSize.value
    })
    ElMessage.success('发鞋成功：' + r.skateCode + '（' + r.shoeSize + '码）')
    pickShow.value = false
    await load()
  } finally {
    loading.value = false
  }
}

async function returnRental(id, damaged) {
  try {
    await ElMessageBox.confirm(
      damaged ? '确认该冰刀刀刃损坏？单据将收口，冰刀转入待检，不会立即释放。' : '确认正常归还？冰刀将回到可借库存。',
      damaged ? '损坏归还' : '正常归还',
      { type: damaged ? 'warning' : 'info', confirmButtonText: '确认', cancelButtonText: '取消' }
    )
  } catch { return }
  await http.put('/skates/rentals/' + id + '/return', { damaged })
  ElMessage.success(damaged ? '已登记损坏归还，冰刀待检' : '归还成功，冰刀已回架')
  await load()
}

async function askDamage(id) {
  let note = '刀刃损坏，待检'
  try {
    const res = await ElMessageBox.prompt('请描述刀刃问题', '损坏归还', {
      confirmButtonText: '确认损坏归还',
      cancelButtonText: '取消',
      inputValue: note,
      inputValidator: v => (v && v.trim() ? true : '请填写问题描述')
    })
    note = res.value
  } catch { return }
  await http.put('/skates/rentals/' + id + '/return', { damaged: true, note })
  ElMessage.success('已登记损坏归还，冰刀转入待检')
  await load()
}

async function inspectDone(id) {
  await http.put('/skates/' + id + '/inspect-done')
  ElMessage.success('检修完成，已重新上架为可借')
  await load()
}

onMounted(load)
</script>

<style scoped>
.head { display: flex; align-items: baseline; gap: 14px; }
.tip { color: #8aa07a; font-size: 13px; }

.summary { display: flex; align-items: center; gap: 14px; margin: 14px 0 18px; flex-wrap: wrap; }
.sum {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  min-width: 118px; padding: 10px 16px; border-radius: 12px; background: #fff;
  border: 1px solid #e3efd6; box-shadow: 0 2px 6px rgba(125,179,79,0.08);
}
.sum b { font-size: 24px; }
.sum span { font-size: 12px; color: #8aa07a; margin-top: 2px; }
.s-avail b { color: #67c23a; }
.s-borrow b { color: #409eff; }
.s-inspect b { color: #e6a23c; }
.s-total b { color: #7db34f; }
.issue-entry { margin-left: auto; }

.block-title { color: #5f7a3a; margin: 18px 0 10px; font-size: 15px; }
.size-row { display: flex; align-items: stretch; gap: 10px; margin-bottom: 10px; }
.size-tag {
  width: 56px; flex-shrink: 0; display: flex; align-items: center; justify-content: center;
  background: #9ccc65; color: #fff; font-weight: 700; border-radius: 10px; font-size: 14px;
}
.skate-list { display: flex; gap: 10px; flex-wrap: wrap; flex: 1; }
.skate {
  min-width: 172px; flex: 1; border-radius: 10px; padding: 8px 10px; background: #fff;
  border: 1px solid #e3efd6; border-left-width: 4px;
}
.skate.st-avail { border-left-color: #67c23a; }
.skate.st-borrow { border-left-color: #409eff; }
.skate.st-inspect { border-left-color: #e6a23c; background: #fffaf0; }
.skate-code { font-weight: 700; font-size: 13.5px; margin-bottom: 4px; }
.skate-who { font-size: 12px; color: #8aa07a; margin: 4px 0; }
.skate-actions { display: flex; gap: 4px; flex-wrap: wrap; margin-top: 2px; }
.ok-hint { font-size: 12px; color: #b7c6a2; align-self: center; }
.muted { color: #b7c6a2; }
.dam-note { color: #d97706; font-size: 12.5px; }

.form-hint { margin-left: 10px; color: #8aa07a; font-size: 12.5px; }
.form-hint.warn { color: #d97706; margin-left: 0; margin-top: 4px; }
</style>
