<template>
  <div class="compare-container">
    <div class="compare-header">
      <h2>📊 3×3 多目标路径对比</h2>
      <p>对比就业、考研、考公三种目标下的三条路径，辅助你做出最佳选择</p>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-wrapper">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>加载对比数据...</span>
    </div>

    <!-- 无数据状态 -->
    <el-empty v-else-if="!hasData" description="暂无规划数据，请先生成至少一种目标的规划">
      <el-button type="primary" @click="goHome">去生成规划</el-button>
    </el-empty>

    <!-- 对比矩阵 -->
    <div v-else class="compare-matrix">
      <!-- 表头：目标类型 -->
      <el-row class="matrix-header" :gutter="20">
        <el-col :span="8" v-for="goal in goalTypes" :key="goal.value">
          <div class="goal-header" :style="{ borderColor: goal.color }">
            <span class="goal-icon">{{ goal.icon }}</span>
            <span class="goal-name">{{ goal.label }}</span>
            <el-tag size="small" :type="goal.tagType" v-if="getGoalData(goal.value).length === 0">暂无规划</el-tag>
          </div>
        </el-col>
      </el-row>

      <!-- 行：三条路径（主流/备用/理想） -->
      <el-row v-for="pathType in pathTypes" :key="pathType.value" class="matrix-row" :gutter="20">
        <el-col :span="8" v-for="goal in goalTypes" :key="goal.value">
          <div class="path-cell">
            <div v-if="getGoalData(goal.value).length > 0">
              <PathCard
                  :path="getPathItem(getGoalData(goal.value), pathType.value)"
                  :path-type-label="pathType.label"
                  :goal-type="goal"
                  mode="compact"
                  :default-expanded="false"
              />
            </div>
            <div v-else class="empty-cell">
              <el-empty description="未生成该目标规划" :image-size="60" />
            </div>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- 止损建议汇总 -->
    <el-card v-if="hasStopLoss" class="stop-loss-summary" shadow="hover">
      <template #header>
        <span>⚠️ 止损建议汇总</span>
      </template>
      <el-row :gutter="20">
        <el-col :span="8" v-for="goal in goalTypes" :key="goal.value">
          <div v-for="path in getGoalData(goal.value)" :key="path.pathType">
            <div v-if="path.stopLossAdvice" class="stop-loss-item">
              <el-tag size="small" :type="goal.tagType">{{ goal.label }}</el-tag>
              <strong>{{ path.pathName }}</strong>
              <p><span class="label">触发条件：</span>{{ path.stopLossAdvice.trigger }}</p>
              <p><span class="label">建议行动：</span>{{ path.stopLossAdvice.action }}</p>
              <p><span class="label">截止时间：</span>{{ path.stopLossAdvice.deadline }}</p>
              <p><span class="label">备选路径：</span>{{ path.stopLossAdvice.alternativePath }}</p>
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { compareThreeGoalsApi } from '../api/plan.js'
import PathCard from '../components/plan/PathCard.vue'

const router = useRouter()
const loading = ref(false)
const compareData = ref({})

const goalTypes = [
  { value: 1, label: '就业', icon: '💼', color: '#409eff', tagType: 'primary' },
  { value: 2, label: '考研', icon: '📚', color: '#67c23a', tagType: 'success' },
  { value: 3, label: '考公', icon: '🏛️', color: '#e6a23c', tagType: 'warning' }
]

const pathTypes = [
  { value: 1, label: '主流路径' },
  { value: 2, label: '备用路径' },
  { value: 3, label: '理想路径' }
]

const hasData = computed(() => {
  return Object.values(compareData.value).some(arr => arr && arr.length > 0)
})

const hasStopLoss = computed(() => {
  for (const goal of goalTypes) {
    const paths = compareData.value[goal.value] || []
    if (paths.some(p => p.stopLossAdvice)) return true
  }
  return false
})

const getGoalData = (goalType) => {
  return compareData.value[goalType] || []
}

const getPathItem = (paths, pathType) => {
  return paths.find(p => p.pathType === pathType) || null
}

const goHome = () => router.push('/')

const loadCompare = async () => {
  loading.value = true
  try {
    const res = await compareThreeGoalsApi()
    compareData.value = res.data || {}
    if (!hasData.value) {
      ElMessage.info('暂无规划数据，请先生成规划')
    }
  } catch (error) {
    ElMessage.error(error.message || '加载对比数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadCompare()
})
</script>

<style scoped>
.compare-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 20px;
}

.compare-header {
  margin-bottom: 24px;
}
.compare-header h2 {
  margin: 0;
  color: #2c3e50;
}
.compare-header p {
  margin: 8px 0 0;
  color: #909399;
}

.loading-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  font-size: 16px;
  color: #409eff;
}
.loading-wrapper .el-icon {
  font-size: 40px;
  margin-bottom: 12px;
}

.matrix-header {
  margin-bottom: 16px;
}
.goal-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 3px solid #ddd;
  font-weight: 600;
  font-size: 18px;
}
.goal-icon {
  font-size: 24px;
}
.goal-name {
  flex: 1;
}

.matrix-row {
  margin-bottom: 20px;
}
.path-cell {
  min-height: 280px;
  background: #fafafa;
  border-radius: 8px;
  padding: 12px;
  border: 1px solid #e6e6e6;
  transition: box-shadow 0.2s;
}
.path-cell:hover {
  box-shadow: 0 2px 12px rgba(0,0,0,0.06);
}

.empty-cell {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 200px;
}

.stop-loss-summary {
  margin-top: 24px;
}
.stop-loss-item {
  background: #fef9e7;
  border-left: 3px solid #e6a23c;
  padding: 12px 16px;
  margin-bottom: 12px;
  border-radius: 4px;
  font-size: 14px;
}
.stop-loss-item strong {
  display: block;
  margin: 4px 0 8px;
}
.stop-loss-item p {
  margin: 4px 0;
  color: #606266;
}
.stop-loss-item .label {
  color: #909399;
  font-weight: 500;
}
</style>