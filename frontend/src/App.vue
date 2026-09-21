<template>
  <div class="shell">
    <!-- 左侧分类筛选 -->
    <aside class="left">
      <div class="left-title">分类筛选</div>
      <div class="left-hint">{{ currentLabel }}</div>
      <div class="chips">
        <button v-for="o in categoryOptions" :key="o"
                :class="['chip', { active: categoryValue === o }]"
                @click="categoryValue = o">{{ o }}</button>
      </div>
    </aside>

    <!-- 主区 -->
    <main class="main">
      <header class="topbar">
        <span class="brand">❄️ 冰场管理系统</span>
        <span class="sub">Ice Rink · 冰面 / 会员 / 课程 / 选课 / 租借 / 浇冰</span>
      </header>
      <section class="content"><router-view /></section>
    </main>

    <!-- 右侧悬浮竖向 tab -->
    <nav class="right-tab">
      <button v-for="m in modules" :key="m.path"
              :class="['rtab', { active: active === m.path }]"
              @click="$router.push(m.path)">
        <span class="rtab-ico">{{ m.ico }}</span>
        <span class="rtab-label">{{ m.label }}</span>
      </button>
    </nav>
  </div>
</template>

<script setup>
import { ref, computed, provide, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import http from './api'

const route = useRoute()
const modules = [
  { path: '/ice-lanes', label: '冰面', ico: '❄' },
  { path: '/members', label: '会员', ico: '🪪' },
  { path: '/courses', label: '课程', ico: '🏒' },
  { path: '/enrollments', label: '选课', ico: '📅' },
  { path: '/rentals', label: '租借', ico: '⛸' },
  { path: '/resurface', label: '浇冰', ico: '🚜' }
]
const active = computed(() => route.path)
const currentLabel = computed(() => modules.find(m => m.path === route.path)?.label || '')

const categoryOptions = ref(['全部'])
const categoryValue = ref('全部')
provide('categoryOptions', categoryOptions)
provide('categoryValue', categoryValue)

function resetCategory() {
  categoryValue.value = '全部'
  if (route.path === '/ice-lanes') categoryOptions.value = ['全部', '开放', '维护', '关闭']
  else if (route.path === '/members') categoryOptions.value = ['全部', '正常', '过期']
  else if (route.path === '/enrollments') categoryOptions.value = ['全部', '已报', '已退']
  else if (route.path === '/rentals') categoryOptions.value = ['全部', '可借', '已借', '待检', '已领取', '已归还', '损坏归还']
  else categoryOptions.value = ['全部']
}
watch(() => route.path, () => { resetCategory(); loadLanesIfNeeded() })
onMounted(() => { resetCategory(); loadLanesIfNeeded() })

async function loadLanesIfNeeded() {
  if (route.path === '/courses' || route.path === '/resurface') {
    try {
      const lanes = await http.get('/ice-lanes')
      categoryOptions.value = ['全部', ...lanes.map(l => l.code)]
    } catch (e) { /* ignore */ }
  }
}
</script>

<style>
html, body, #app { margin: 0; height: 100%; }
.shell { height: 100vh; display: flex; background: #f4f9ec; }
.left {
  width: 168px; background: #fff; border-right: 1px solid #e3efd6;
  padding: 16px 12px; box-shadow: 2px 0 8px rgba(125,179,79,0.06);
}
.left-title { font-weight: 700; color: #7db34f; font-size: 13px; margin-bottom: 4px; }
.left-hint { font-size: 12px; color: #9caf88; margin-bottom: 12px; }
.chips { display: flex; flex-direction: column; gap: 8px; }
.chip {
  border: 1px solid #d9e8c5; background: #f4f9ec; color: #5f7a3a;
  border-radius: 8px; padding: 8px 10px; cursor: pointer; font-size: 13px; text-align: left;
  transition: all .15s;
}
.chip:hover { background: #eaf4d8; }
.chip.active { background: #9ccc65; color: #fff; border-color: #9ccc65; }

.main { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.topbar {
  height: 56px; display: flex; align-items: center; gap: 14px;
  padding: 0 22px; background: #9ccc65; color: #fff;
}
.brand { font-weight: 700; font-size: 18px; }
.sub { font-size: 12px; opacity: .9; }
.content { flex: 1; overflow: auto; padding: 20px 26px 40px; }

.right-tab {
  position: fixed; right: 0; top: 50%; transform: translateY(-50%);
  display: flex; flex-direction: column; gap: 8px; z-index: 20;
  background: #fff; padding: 10px 8px; border-radius: 14px 0 0 14px;
  box-shadow: -4px 0 16px rgba(125,179,79,0.18); border: 1px solid #e3efd6; border-right: none;
}
.rtab {
  width: 56px; border: none; background: transparent; cursor: pointer;
  display: flex; flex-direction: column; align-items: center; gap: 2px; padding: 8px 0;
  border-radius: 10px; color: #6b8a44;
}
.rtab-ico { font-size: 18px; }
.rtab-label { font-size: 12px; }
.rtab:hover { background: #eaf4d8; }
.rtab.active { background: #9ccc65; color: #fff; }
</style>
