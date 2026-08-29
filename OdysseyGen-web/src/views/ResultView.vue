<template>
  <div class="result-container">
    <!-- 顶部导航 -->
    <div class="result-header">
      <h2>📊 你的三条职业路径</h2>
      <div class="header-actions">
        <el-button type="primary" @click="goHome">重新填写</el-button>
        <el-button type="success" @click="savePlan">⭐ 收藏此规划</el-button>
      </div>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-wrapper">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>路径生成中，请稍候...</span>
    </div>

    <!-- 三条路径卡片 -->
    <el-row v-else :gutter="20" class="path-row">
      <el-col :span="8" v-for="(path, index) in paths" :key="index">
        <el-card class="path-card" :class="getPathClass(path.pathType)" shadow="hover">
          <template #header>
            <div class="path-header">
              <span class="path-badge" :style="{ background: getPathColor(path.pathType) }">
                {{ getPathLabel(path.pathType) }}
              </span>
              <span class="path-name">{{ path.pathName }}</span>
            </div>
          </template>

          <div class="path-body">
            <p class="path-summary">{{ path.pathSummary }}</p>

            <!-- 时间线 -->
            <div class="path-section">
              <h4>📅 时间线</h4>
              <ul>
                <li v-for="(item, idx) in path.timeline" :key="idx">
                  <strong>{{ item.year }}</strong>：{{ item.action }}
                </li>
              </ul>
            </div>

            <!-- 关键里程碑 -->
            <div class="path-section">
              <h4>🎯 关键里程碑</h4>
              <ul>
                <li v-for="(item, idx) in path.keyNodes" :key="idx">
                  {{ item.node }}（{{ item.deadline }}）
                </li>
              </ul>
            </div>

            <!-- 技能差距 -->
            <div class="path-section" v-if="path.skillGap?.length">
              <h4>🛠️ 技能差距</h4>
              <el-tag
                  v-for="(skill, idx) in path.skillGap"
                  :key="idx"
                  type="warning"
                  size="small"
                  style="margin: 2px"
              >
                {{ skill }}
              </el-tag>
            </div>

            <!-- 薪资 -->
            <div class="path-section" v-if="path.salaryExpectation">
              <h4>💰 薪资预期（K/月）</h4>
              <div class="salary-display">
                <el-tag size="small" type="success">入职: {{ path.salaryExpectation.entry }}K</el-tag>
                <el-tag size="small" type="warning">中期: {{ path.salaryExpectation.mid }}K</el-tag>
                <el-tag size="small" type="danger">资深: {{ path.salaryExpectation.senior }}K</el-tag>
              </div>
            </div>

            <!-- 风险提示 -->
            <div class="path-section" v-if="path.riskFactors?.length">
              <h4>⚠️ 风险提示</h4>
              <ul>
                <li v-for="(risk, idx) in path.riskFactors" :key="idx">{{ risk }}</li>
              </ul>
            </div>

            <!-- 推荐行动 -->
            <div class="path-section" v-if="path.recommendedActions?.length">
              <h4>💡 推荐行动</h4>
              <ul>
                <li v-for="(action, idx) in path.recommendedActions" :key="idx">{{ action }}</li>
              </ul>
            </div>

            <!-- 止损建议 -->
            <div class="path-section" v-if="path.stopLossAdvice">
              <h4>🛑 止损建议</h4>
              <div class="stop-loss-box">
                <p><span class="label">触发条件：</span>{{ path.stopLossAdvice.trigger }}</p>
                <p><span class="label">建议行动：</span>{{ path.stopLossAdvice.action }}</p>
                <p><span class="label">截止时间：</span>{{ path.stopLossAdvice.deadline }}</p>
                <p><span class="label">备选路径：</span>{{ path.stopLossAdvice.alternativePath }}</p>
              </div>
            </div>
            <!-- 操作按钮 -->
            <div class="path-actions" style="margin-top: 16px; display: flex; gap: 8px; flex-wrap: wrap;">
              <el-button type="primary" size="small" @click="startTracking(index)">
                🎯 设为我的路径
              </el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 雷达图对比 -->
    <el-card class="chart-card" v-if="!loading && paths.length > 0">
      <template #header>
        <span>📈 三条路径综合对比（雷达图）</span>
      </template>
      <div ref="chartRef" class="chart-container"></div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Loading } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getPlanDetailApi, toggleFavoriteApi } from '../api/plan.js'
import { usePlanStore } from '../stores/planStore.js'
import { ElMessage, ElMessageBox } from 'element-plus'
import { selectPathApi, getCurrentTrackingApi } from '../api/tracking.js'

const router = useRouter()
const route = useRoute()
const planStore = usePlanStore()
const loading = ref(false)
const chartRef = ref(null)
const paths = ref([])
let chartInstance = null
let resizeHandler = null

// 路径类型工具
const pathTypeMap = {
  1: { label: '主流路径', color: '#409eff' },
  2: { label: '备用路径', color: '#e6a23c' },
  3: { label: '理想路径', color: '#67c23a' }
}

const getPathLabel = (type) => pathTypeMap[type]?.label || '未知'
const getPathColor = (type) => pathTypeMap[type]?.color || '#909399'
const getPathClass = (type) => {
  const map = { 1: 'path-main', 2: 'path-backup', 3: 'path-ideal' }
  return map[type] || ''
}

// 计算雷达图分数
const calculateScores = (path) => {
  const salaryScore = path.salaryExpectation?.entry
      ? Math.min(path.salaryExpectation.entry * 3, 100)
      : 50
  const stabilityScore = path.pathType === 2 ? 80 : path.pathType === 1 ? 60 : 40
  const growthScore = path.pathType === 3 ? 90 : path.pathType === 1 ? 70 : 50
  const competitionScore = path.pathType === 1 ? 60 : path.pathType === 2 ? 70 : 50
  const fitScore = 70
  return [salaryScore, growthScore, stabilityScore, competitionScore, fitScore]
}

// 初始化雷达图
const initChart = () => {
  if (!chartRef.value) return
  if (chartInstance) chartInstance.dispose()

  chartInstance = echarts.init(chartRef.value)
  const option = {
    radar: {
      indicator: [
        { name: '薪资潜力', max: 100 },
        { name: '发展空间', max: 100 },
        { name: '稳定性', max: 100 },
        { name: '竞争程度', max: 100 },
        { name: '专业匹配度', max: 100 }
      ],
      center: ['50%', '50%'],
      radius: '65%',
      axisName: { color: '#333', fontSize: 13 }
    },
    series: [{
      type: 'radar',
      data: paths.value.map((p, i) => ({
        value: p.scores || calculateScores(p),
        name: p.pathName,
        areaStyle: { color: `rgba(${['64,158,255', '230,162,60', '103,194,58'][i]}, 0.2)` },
        lineStyle: { color: ['#409eff', '#e6a23c', '#67c23a'][i], width: 2 },
        itemStyle: { color: ['#409eff', '#e6a23c', '#67c23a'][i] }
      })),
      symbol: 'circle',
      symbolSize: 6
    }],
    legend: {
      data: paths.value.map(p => p.pathName),
      bottom: 0,
      textStyle: { fontSize: 13 }
    },
    tooltip: {
      trigger: 'item'
    }
  }

  chartInstance.setOption(option)
  // 先移除旧的监听，避免重复挂载导致内存泄漏
  if (resizeHandler) window.removeEventListener('resize', resizeHandler)
  resizeHandler = () => chartInstance?.resize()
  window.addEventListener('resize', resizeHandler)
}

// 页面操作
const goHome = () => {
  try {
    router.push('/')
  } catch {
    window.location.href = '/'
  }
}
const savePlan = async () => {
  try {
    // 优先取 store 里的 planId，否则从路由 query 取（从历史页进入时 store 已被清空）
    const planId = planStore.currentPlan?.planId || parseInt(route.query.planId)
    if (planId) {
      await toggleFavoriteApi(planId)
      ElMessage.success('操作成功')
    } else {
      ElMessage.warning('请先生成规划')
    }
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

// 生命周期
onMounted(async () => {
  loading.value = true
  try {
    if (planStore.currentPlan) {
      paths.value = planStore.currentPlan.paths.map(p => ({
        ...p,
        scores: calculateScores(p)
      }))
    } else if (route.query.planId) {
      const res = await getPlanDetailApi(route.query.planId)
      paths.value = res.data.paths.map(p => ({
        ...p,
        scores: calculateScores(p)
      }))
    }
    nextTick(() => initChart())
  } catch (error) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
})

watch(paths, () => {
  nextTick(() => initChart())
}, { deep: true })

const startTracking = async (index) => {
  const path = paths.value[index]
  if (!path) return

  try {
    // 先检查是否已有进行中的路径
    const currentRes = await getCurrentTrackingApi()
    let confirmMsg = '确定将这条路径设为你的当前目标吗？'

    if (currentRes.data) {
      confirmMsg = '你已有一条进行中的路径，确定要切换吗？之前的路径将被标记为已放弃。'
    }

    await ElMessageBox.confirm(confirmMsg, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const planId = planStore.currentPlan?.planId || parseInt(route.query.planId)
    if (!planId) {
      ElMessage.error('无法获取规划ID，请重新生成')
      return
    }

    await selectPathApi({
      planId: planId,
      pathType: path.pathType
    })

    ElMessage.success('已选定路径，开始跟踪！')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '操作失败')
    }
  }
}

onUnmounted(() => {
  if (resizeHandler) {
    window.removeEventListener('resize', resizeHandler)
    resizeHandler = null
  }
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
})
</script>

<style scoped>
.result-container {
  max-width: 1400px;
  margin: 0 auto;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
.result-header h2 {
  margin: 0;
}

.loading-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  font-size: 18px;
  color: #409eff;
}
.loading-wrapper .el-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.path-row {
  margin-bottom: 24px;
}

.path-card {
  height: 100%;
  border-radius: 12px;
  border-left: 6px solid #909399;
  transition: transform 0.2s;
}
.path-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0,0,0,0.10);
}
.path-main { border-left-color: #409eff; }
.path-backup { border-left-color: #e6a23c; }
.path-ideal { border-left-color: #67c23a; }

.path-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.path-badge {
  font-size: 12px;
  color: #fff;
  padding: 2px 12px;
  border-radius: 12px;
  white-space: nowrap;
}
.path-name {
  font-weight: 600;
  font-size: 15px;
}

.path-summary {
  color: #606266;
  font-size: 14px;
  padding: 8px 0;
  border-bottom: 1px solid #eee;
}

.path-body {
  font-size: 14px;
}
.path-section {
  margin-top: 12px;
}
.path-section h4 {
  margin: 0 0 4px 0;
  font-size: 14px;
  color: #333;
}
.path-section ul {
  margin: 4px 0 0 0;
  padding-left: 18px;
}
.path-section ul li {
  line-height: 1.6;
  color: #555;
}

.salary-display {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 4px;
}
.salary-display .el-tag {
  font-size: 14px;
  padding: 4px 12px;
}

.stop-loss-box {
  background: #fef9e7;
  border-left: 3px solid #e6a23c;
  padding: 8px 12px;
  border-radius: 4px;
  font-size: 13px;
}
.stop-loss-box p {
  margin: 4px 0;
  color: #606266;
}
.stop-loss-box .label {
  color: #909399;
  font-weight: 500;
}

.chart-card {
  margin-top: 12px;
}
.chart-container {
  width: 100%;
  height: 380px;
}
</style>