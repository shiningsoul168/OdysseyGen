<template>
  <div class="my-path-container">
    <div class="page-header">
      <h2>🎯 我的路径</h2>
      <p>跟踪你当前选定的职业发展路径，逐项完成里程碑</p>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-wrapper">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>加载中...</span>
    </div>

    <!-- 无跟踪路径 -->
    <el-empty v-else-if="!milestoneData" description="你还没有选定任何路径">
      <el-button type="primary" @click="goGenerate">去生成规划</el-button>
    </el-empty>

    <!-- 有跟踪路径 -->
    <div v-else class="tracking-content">
      <!-- 进度卡片 -->
      <el-card class="progress-card" shadow="hover">
        <div class="progress-header">
          <div class="path-info">
            <el-tag :type="getStatusTagType()" size="large">{{ getStatusLabel() }}</el-tag>
            <span class="path-name">{{ milestoneData.pathName }}</span>
            <span class="path-type">{{ getPathTypeLabel() }}</span>
          </div>
          <div class="progress-stats">
            <span>{{ milestoneData.completedMilestones }} / {{ milestoneData.totalMilestones }} 已完成</span>
            <span class="percent">{{ milestoneData.progressPercent }}%</span>
          </div>
        </div>
        <el-progress
            :percentage="milestoneData.progressPercent"
            :color="progressColor"
            :stroke-width="12"
            striped
        />
        <div class="progress-meta">
          <span>开始时间：{{ milestoneData?.startedAt || '--' }}</span>
          <span v-if="milestoneData?.completedAt">完成时间：{{ milestoneData.completedAt }}</span>
        </div>
      </el-card>

      <!-- 里程碑列表 -->
      <el-card class="milestone-card" shadow="hover">
        <template #header>
          <span>📋 里程碑列表</span>
        </template>
        <div class="milestone-list">
          <div
              v-for="item in milestoneData.milestones"
              :key="item.id"
              class="milestone-item"
              :class="getMilestoneClass(item.status)"
          >
            <div class="milestone-left">
              <el-checkbox
                  :model-value="item.status === 2"
                  :indeterminate="item.status === 1"
                  @change="(val) => handleStatusChange(item, val)"
                  :disabled="updating"
              />
              <span class="milestone-name">{{ item.nodeName }}</span>
              <span class="milestone-deadline" v-if="item.nodeDeadline">（{{ item.nodeDeadline }}）</span>
            </div>
            <div class="milestone-right">
              <el-tag size="small" :type="getStatusTag(item.status)">
                {{ getStatusText(item.status) }}
              </el-tag>
              <span class="milestone-time" v-if="item.completedAt">
                {{ item.completedAt }}
              </span>
            </div>
          </div>
        </div>
      </el-card>
    </div>

    <!-- ====== 🎉 恭喜完成路径弹窗 ====== -->
    <el-dialog
        v-model="showCelebration"
        title="🎉 恭喜你完成路径！"
        width="480px"
        :close-on-click-modal="false"
        :close-on-press-escape="false"
    >
      <div class="celebration-content">
        <div class="emoji-big">🥳</div>
        <p class="path-name">{{ milestoneData?.pathName }}</p>
        <p class="msg">你已成功完成了这条职业发展路径的所有里程碑！</p>
        <p class="time">🕐 完成时间：{{ completedAt }}</p>
        <el-divider />
        <p style="color: #606266; font-size: 14px; margin-bottom: 12px;">💡 下一步，你可以：</p>
        <div class="next-actions">
          <el-button type="primary" @click="goGenerateNewPath">🚀 生成新的路径</el-button>
          <el-button @click="showCelebration = false">📋 查看已完成路径</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { getMilestoneProgressApi, updateMilestoneStatusApi } from '../api/milestone.js'

const router = useRouter()
const loading = ref(false)
const updating = ref(false)
const milestoneData = ref(null)
const showCelebration = ref(false)
const completedAt = ref('')
let celebrationShown = false

// ====== 路径类型映射 ======
const pathTypeMap = {
  1: '主流路径',
  2: '备用路径',
  3: '理想路径'
}

const statusTextMap = {
  0: '未开始',
  1: '进行中',
  2: '已完成'
}

const statusTagMap = {
  0: 'info',
  1: 'warning',
  2: 'success'
}

const getPathTypeLabel = () => pathTypeMap[milestoneData.value?.pathType] || '未知'
const getStatusText = (status) => statusTextMap[status] || '未知'
const getStatusTag = (status) => statusTagMap[status] || 'info'
const getMilestoneClass = (status) => {
  if (status === 2) return 'milestone-completed'
  if (status === 1) return 'milestone-in-progress'
  return 'milestone-pending'
}

const getStatusTagType = () => {
  if (!milestoneData.value) return 'info'
  const progress = milestoneData.value.progressPercent
  if (progress === 100) return 'success'
  if (progress > 0) return 'warning'
  return 'info'
}

const getStatusLabel = () => {
  if (!milestoneData.value) return '未知'
  const progress = milestoneData.value.progressPercent
  if (progress === 100) return '✅ 已完成'
  if (progress > 0) return '🔄 进行中'
  return '⏳ 未开始'
}

const progressColor = computed(() => {
  const p = milestoneData.value?.progressPercent || 0
  if (p === 100) return '#67c23a'
  if (p > 50) return '#409eff'
  if (p > 20) return '#e6a23c'
  return '#f56c6c'
})

// ====== 检查是否全部完成 ======
const checkAllCompleted = (data) => {
  if (!data || !data.milestones || data.milestones.length === 0) return false
  const allCompleted = data.milestones.every(m => m.status === 2)
  if (allCompleted && data.totalMilestones > 0 && !celebrationShown) {
    celebrationShown = true
    const celebratedKey = `celebrated_${data.trackingId}`
    localStorage.setItem(celebratedKey, 'true')
    completedAt.value = new Date().toLocaleString()
    showCelebration.value = true
    return true
  }
  return false
}

// ====== 加载数据 ======
const loadData = async () => {
  loading.value = true
  try {
    // ✅ 直接获取里程碑进度
    const res = await getMilestoneProgressApi()
    milestoneData.value = res.data

    if (!milestoneData.value) {
      return
    }

    const celebratedKey = `celebrated_${milestoneData.value.trackingId}`
    if (localStorage.getItem(celebratedKey) === 'true') {
      celebrationShown = true
    } else {
      celebrationShown = false
      setTimeout(() => {
        checkAllCompleted(milestoneData.value)
      }, 300)
    }
  } catch (error) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

// ====== 处理状态变更 ======
const handleStatusChange = async (item, checked) => {
  const newStatus = checked ? 2 : 0
  try {
    await ElMessageBox.confirm(
        checked ? '确定标记该里程碑为已完成吗？' : '确定取消该里程碑的完成状态吗？',
        '提示',
        {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }
    )
    updating.value = true
    const res = await updateMilestoneStatusApi({
      milestoneId: item.id,
      status: newStatus
    })
    ElMessage.success('更新成功')
    milestoneData.value = res.data

    if (milestoneData.value) {
      const allCompleted = milestoneData.value.milestones.every(m => m.status === 2)
      if (allCompleted && milestoneData.value.totalMilestones > 0) {
        const celebratedKey = `celebrated_${milestoneData.value.trackingId}`
        localStorage.setItem(celebratedKey, 'true')
        celebrationShown = false
        checkAllCompleted(milestoneData.value)
      }
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '操作失败')
    }
  } finally {
    updating.value = false
  }
}

const goGenerateNewPath = () => {
  showCelebration.value = false
  router.push('/')
}

const goGenerate = () => router.push('/')

watch(milestoneData, (newData) => {
  if (newData) {
    checkAllCompleted(newData)
  }
}, { deep: true })

onMounted(() => {
  loadData()
})
</script>

<style scoped>
/* 样式代码保持不变，这里省略重复内容，与之前的完全一致 */
.my-path-container {
  max-width: 900px;
  margin: 0 auto;
  padding: 20px;
}

.page-header {
  margin-bottom: 24px;
}
.page-header h2 {
  margin: 0;
  color: #2c3e50;
}
.page-header p {
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

.progress-card {
  margin-bottom: 20px;
}
.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 12px;
}
.path-info {
  display: flex;
  align-items: center;
  gap: 12px;
}
.path-info .path-name {
  font-size: 18px;
  font-weight: 600;
  color: #2c3e50;
}
.path-info .path-type {
  color: #909399;
  font-size: 14px;
}
.progress-stats {
  display: flex;
  align-items: center;
  gap: 16px;
  font-weight: 500;
  color: #606266;
}
.progress-stats .percent {
  font-size: 20px;
  color: #409eff;
}
.progress-meta {
  margin-top: 8px;
  color: #909399;
  font-size: 13px;
}
.progress-meta span {
  margin-right: 20px;
}

.milestone-card {
  margin-top: 12px;
}
.milestone-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.milestone-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border-radius: 6px;
  background: #f5f7fa;
  border-left: 3px solid #909399;
  transition: background 0.2s;
}
.milestone-item:hover {
  background: #eef1f5;
}
.milestone-completed {
  border-left-color: #67c23a;
  background: #f0f9eb;
}
.milestone-in-progress {
  border-left-color: #e6a23c;
  background: #fdf6ec;
}
.milestone-pending {
  border-left-color: #909399;
}
.milestone-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.milestone-name {
  font-size: 15px;
  color: #333;
}
.milestone-deadline {
  font-size: 13px;
  color: #909399;
}
.milestone-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.milestone-time {
  font-size: 12px;
  color: #909399;
}

/* 庆祝弹窗 */
.celebration-content {
  text-align: center;
  padding: 10px 0;
}
.celebration-content .emoji-big {
  font-size: 64px;
  margin-bottom: 16px;
}
.celebration-content .path-name {
  font-size: 18px;
  font-weight: 600;
  color: #2c3e50;
  margin-bottom: 8px;
}
.celebration-content .msg {
  color: #606266;
  margin-bottom: 4px;
}
.celebration-content .time {
  color: #909399;
  font-size: 13px;
  margin-top: 8px;
}
.celebration-content .next-actions {
  margin-top: 12px;
  display: flex;
  gap: 12px;
  justify-content: center;
  flex-wrap: wrap;
}
</style>