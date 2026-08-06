<template>
  <div class="history-container">
    <div class="history-header">
      <h2>📂 我的历史规划</h2>
      <el-button type="primary" @click="loadHistory" :loading="loading">
        🔄 刷新
      </el-button>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-wrapper">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>加载中...</span>
    </div>

    <!-- 空状态 -->
    <el-empty v-else-if="historyList.length === 0" description="暂无历史规划，快去生成第一条吧！">
      <el-button type="primary" @click="goHome">去生成规划</el-button>
    </el-empty>

    <!-- 历史记录列表 -->
    <div v-else>
      <el-card
          v-for="item in historyList"
          :key="item.planId"
          class="history-item"
          shadow="hover"
      >
        <div class="history-item-header">
          <div class="item-info">
            <span class="item-time">{{ formatTime(item.createdAt) }}</span>
            <el-tag size="small" :type="getGoalTypeTag(item.goalType)">
              {{ getGoalTypeLabel(item.goalType) }}
            </el-tag>
            <el-tag v-if="item.isFavorite" type="warning" size="small">⭐ 已收藏</el-tag>
          </div>
          <div class="item-actions">
            <el-button type="primary" link @click="viewPlan(item.planId)">查看详情</el-button>
            <el-button type="warning" link @click="toggleFavorite(item)">⭐ 收藏</el-button>
            <el-button type="success" link @click="openSelectPathDialog(item.planId)">🎯 设为我的路径</el-button>
            <el-popconfirm title="确定删除此规划吗？" @confirm="deletePlan(item.planId)">
              <template #reference>
                <el-button type="danger" link>删除</el-button>
              </template>
            </el-popconfirm>
          </div>
        </div>

        <div class="history-item-body">
          <!-- 展示三条路径名称 -->
          <div class="path-preview">
            <el-tag
                v-for="(path, idx) in item.pathNames"
                :key="idx"
                :type="['primary', 'warning', 'success'][idx]"
                size="small"
            >
              {{ path }}
            </el-tag>
          </div>
        </div>
      </el-card>

      <!-- 分页组件 -->
      <div class="pagination-wrapper">
        <el-pagination
            v-model:page-size="pageSize"
            v-model:current-page="currentPage"
            :total="total"
            :page-sizes="[5, 10, 20]"
            layout="total, sizes, prev, pager, next"
            @size-change="loadHistory"
            @current-change="loadHistory"
        />
      </div>
    </div>

    <!-- ====== 选择路径弹窗 ====== -->
    <el-dialog v-model="showSelectPathDialog" title="选择要跟踪的路径" width="500px">
      <el-radio-group v-model="selectedPathType" style="display: flex; flex-direction: column; gap: 12px;">
        <el-radio :value="1" v-if="pathOptions.length >= 1">
          <strong>主流路径</strong>：{{ pathOptions[0]?.pathName || '未命名' }}
        </el-radio>
        <el-radio :value="2" v-if="pathOptions.length >= 2">
          <strong>备用路径</strong>：{{ pathOptions[1]?.pathName || '未命名' }}
        </el-radio>
        <el-radio :value="3" v-if="pathOptions.length >= 3">
          <strong>理想路径</strong>：{{ pathOptions[2]?.pathName || '未命名' }}
        </el-radio>
      </el-radio-group>
      <template #footer>
        <el-button @click="showSelectPathDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmSelectPath" :loading="selectingPath">确认跟踪</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { getHistoryApi, toggleFavoriteApi, deletePlanApi } from '../api/plan.js'
import { getPlanDetailApi } from '../api/plan.js'
import { selectPathApi, getCurrentTrackingApi } from '../api/tracking.js'
import { usePlanStore } from '../stores/planStore.js'

const router = useRouter()
const planStore = usePlanStore()
const loading = ref(false)
const historyList = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// ====== 路径选择弹窗 ======
const showSelectPathDialog = ref(false)
const selectingPath = ref(false)
const selectedPlanId = ref(null)
const selectedPathType = ref(1)
const pathOptions = ref([])

// 目标类型映射
const goalTypeMap = {
  1: { label: '💼 就业', tag: 'primary' },
  2: { label: '📚 考研', tag: 'success' },
  3: { label: '🏛️ 考公', tag: 'warning' }
}
const getGoalTypeLabel = (type) => goalTypeMap[type]?.label || '未知'
const getGoalTypeTag = (type) => goalTypeMap[type]?.tag || 'info'

// 时间格式化
const formatTime = (time) => dayjs(time).format('YYYY-MM-DD HH:mm')

// 加载历史记录
const loadHistory = async () => {
  loading.value = true
  try {
    const res = await getHistoryApi({
      page: currentPage.value,
      size: pageSize.value
    })
    historyList.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (error) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

// 查看详情
const viewPlan = (planId) => {
  planStore.clearCurrentPlan()
  router.push(`/result?planId=${planId}`)
}

// 收藏/取消收藏
const toggleFavorite = async (item) => {
  try {
    await toggleFavoriteApi(item.planId)
    item.isFavorite = !item.isFavorite
    ElMessage.success(item.isFavorite ? '已收藏' : '已取消收藏')
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

// 删除规划
const deletePlan = async (planId) => {
  try {
    await deletePlanApi(planId)
    ElMessage.success('删除成功')
    await loadHistory()
  } catch (error) {
    ElMessage.error('删除失败')
  }
}

// ====== 打开选择路径弹窗 ======
const openSelectPathDialog = async (planId) => {
  selectedPlanId.value = planId
  selectedPathType.value = 1

  try {
    const res = await getPlanDetailApi(planId)
    pathOptions.value = res.data.paths || []
    if (pathOptions.value.length === 0) {
      ElMessage.warning('该规划暂无路径数据')
      return
    }
    showSelectPathDialog.value = true
  } catch (error) {
    ElMessage.error('获取路径详情失败')
  }
}

// ====== 确认选择路径 ======
const confirmSelectPath = async () => {
  if (!selectedPlanId.value || !selectedPathType.value) {
    ElMessage.warning('请选择一条路径')
    return
  }

  selectingPath.value = true
  try {
    // 检查是否已有进行中的路径
    const currentRes = await getCurrentTrackingApi()
    if (currentRes.data) {
      await ElMessageBox.confirm('你已有一条进行中的路径，确定要切换吗？之前的路径将被标记为已放弃。', '提示', {
        confirmButtonText: '确定切换',
        cancelButtonText: '取消',
        type: 'warning'
      })
    }

    await selectPathApi({
      planId: selectedPlanId.value,
      pathType: selectedPathType.value
    })

    ElMessage.success('已选定路径，开始跟踪！')
    showSelectPathDialog.value = false
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '操作失败')
    }
  } finally {
    selectingPath.value = false
  }
}

// 跳转首页
const goHome = () => router.push('/')

// 生命周期
onMounted(() => {
  loadHistory()
})
</script>

<style scoped>
.history-container {
  max-width: 1000px;
  margin: 0 auto;
}

.history-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
.history-header h2 {
  margin: 0;
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

.history-item {
  margin-bottom: 16px;
  border-radius: 10px;
}

.history-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.item-info {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.item-time {
  color: #909399;
  font-size: 14px;
}

.item-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.history-item-body {
  margin-top: 10px;
}
.path-preview {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>