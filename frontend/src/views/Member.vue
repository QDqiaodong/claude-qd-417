<template>
  <div>
    <div class="head">
      <h2>会员</h2>
      <el-button type="primary" @click="openAdd">+ 新增会员</el-button>
    </div>
    <p class="tip">会员卡展示到期日，过期会员标红，禁止选课。</p>

    <div class="cards">
      <div v-for="m in filtered" :key="m.id"
           :class="['card', { expired: m.status === '过期' }]">
        <div class="card-top">
          <span class="mname">{{ m.name }}</span>
          <el-tag :type="m.status === '过期' ? 'danger' : 'success'" size="small">{{ m.status }}</el-tag>
        </div>
        <div class="mcard">卡号 {{ m.cardNo }}</div>
        <div class="mdate">到期 {{ m.expireDate }}</div>
      </div>
      <el-empty v-if="filtered.length === 0" description="无匹配会员" />
    </div>

    <el-dialog v-model="show" title="新增会员" width="420px">
      <el-form label-width="80px">
        <el-form-item label="卡号"><el-input v-model="form.cardNo" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="到期日"><el-date-picker v-model="form.expireDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
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
const members = ref([])
const show = ref(false)
const form = ref({ cardNo: '', name: '', expireDate: '' })

const filtered = computed(() =>
  categoryValue.value === '全部' ? members.value : members.value.filter(m => m.status === categoryValue.value)
)

async function load() { members.value = await http.get('/members') }
function openAdd() { form.value = { cardNo: '', name: '', expireDate: '' }; show.value = true }
async function save() { await http.post('/members', form.value); show.value = false; await load() }
onMounted(load)
</script>

<style scoped>
.head { display: flex; align-items: center; justify-content: space-between; }
.tip { color: #8aa07a; font-size: 13px; margin: 4px 0 16px; }
.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 14px; }
.card {
  background: #fff; border: 1px solid #e3efd6; border-left: 5px solid #9ccc65;
  border-radius: 12px; padding: 14px 16px; box-shadow: 0 2px 6px rgba(125,179,79,0.08);
}
.card.expired { border-left-color: #f56c6c; background: #fff5f5; }
.card-top { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.mname { font-weight: 700; font-size: 16px; }
.mcard { color: #8aa07a; font-size: 12px; margin-bottom: 4px; }
.mdate { color: #5f7a3a; font-size: 13px; }
.card.expired .mdate { color: #e06b6b; }
</style>
