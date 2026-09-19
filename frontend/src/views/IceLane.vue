<template>
  <div>
    <div class="head">
      <h2>冰面</h2>
      <el-button type="primary" @click="openAdd">+ 新增冰面</el-button>
    </div>
    <p class="tip">每片冰面一条泳道横条，颜色代表状态（绿=开放 / 橙=维护 / 灰=关闭）。</p>

    <div class="lanes">
      <div v-for="l in filtered" :key="l.id" class="lane">
        <div class="lane-bar" :style="{ background: statusColor(l.status) }">
          <span class="lane-code">{{ l.code }}</span>
          <span class="lane-name">{{ l.name }}</span>
          <span class="lane-status">{{ l.status }}</span>
          <el-button size="small" class="lane-btn" @click="openEdit(l)">改状态</el-button>
        </div>
      </div>
      <el-empty v-if="filtered.length === 0" description="无匹配冰面" />
    </div>

    <el-dialog v-model="show" :title="editing ? '修改冰面' : '新增冰面'" width="420px">
      <el-form label-width="80px">
        <el-form-item label="编号"><el-input v-model="form.code" :disabled="!!editing" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width:100%">
            <el-option label="开放" value="开放" />
            <el-option label="维护" value="维护" />
            <el-option label="关闭" value="关闭" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="show = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, inject, onMounted } from 'vue'
import http from '../api'

const categoryValue = inject('categoryValue')
const lanes = ref([])
const show = ref(false)
const editing = ref(null)
const form = ref({ code: '', name: '', status: '开放' })

const filtered = computed(() =>
  categoryValue.value === '全部' ? lanes.value : lanes.value.filter(l => l.status === categoryValue.value)
)

function statusColor(s) {
  if (s === '开放') return '#9ccc65'
  if (s === '维护') return '#ffb74d'
  return '#bdbdbd'
}

async function load() { lanes.value = await http.get('/ice-lanes') }
function openAdd() { editing.value = null; form.value = { code: '', name: '', status: '开放' }; show.value = true }
function openEdit(l) { editing.value = l; form.value = { code: l.code, name: l.name, status: l.status }; show.value = true }
async function save() {
  if (editing.value) await http.put('/ice-lanes/' + editing.value.id, form.value)
  else await http.post('/ice-lanes', form.value)
  show.value = false
  await load()
}
onMounted(load)
</script>

<style scoped>
.head { display: flex; align-items: center; justify-content: space-between; }
.tip { color: #8aa07a; font-size: 13px; margin: 4px 0 16px; }
.lanes { display: flex; flex-direction: column; gap: 12px; }
.lane-bar {
  height: 54px; border-radius: 10px; display: flex; align-items: center; gap: 16px;
  padding: 0 16px; color: #fff; box-shadow: 0 2px 6px rgba(0,0,0,0.08);
}
.lane-code { font-weight: 700; font-size: 16px; }
.lane-name { font-size: 14px; opacity: .95; flex: 1; }
.lane-status { font-size: 13px; background: rgba(255,255,255,0.25); padding: 2px 10px; border-radius: 20px; }
.lane-btn { background: rgba(255,255,255,0.9); color: #5f7a3a; border: none; }
.lane-btn:hover { background: #fff; }
</style>
