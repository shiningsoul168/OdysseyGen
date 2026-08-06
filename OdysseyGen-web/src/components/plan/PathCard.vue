<template>
  <div class="path-card" :class="[getCardClass(path?.pathType), `mode-${mode}`]">
    <!-- ====== 卡片头部 ====== -->
    <div class="path-card-header">
      <el-tag :type="getTagType(path?.pathType)" size="small">{{ pathTypeLabel }}</el-tag>
      <span class="path-name">{{ path?.pathName || '未生成' }}</span>
    </div>

    <div v-if="path">
      <!-- ====== 摘要（始终显示） ====== -->
      <p class="path-summary">{{ path.pathSummary }}</p>

      <!-- ====== 薪资（始终显示） ====== -->
      <div class="detail-item" v-if="path.salaryExpectation">
        <span class="label">💰 薪资</span>
        <span class="salary-range">{{ path.salaryExpectation.entry }}K / {{ path.salaryExpectation.mid }}K / {{ path.salaryExpectation.senior }}K</span>
      </div>

      <!-- ====== 技能差距（始终显示） ====== -->
      <div class="detail-item" v-if="path.skillGap?.length">
        <span class="label">🛠️ 技能差距</span>
        <span class="skill-tags">
          <el-tag v-for="(skill, idx) in displaySkills" :key="idx" size="small" type="warning">
            {{ skill }}
          </el-tag>
          <el-tag v-if="mode === 'compact' && path.skillGap.length > 3" size="small" type="info">
            +{{ path.skillGap.length - 3 }}
          </el-tag>
        </span>
      </div>

      <!-- ====== 止损建议（精简版，始终显示） ====== -->
      <div class="stop-loss-preview" v-if="path.stopLossAdvice">
        <el-tag type="danger" size="small">🛑 止损</el-tag>
        <span class="stop-loss-trigger">{{ path.stopLossAdvice.trigger }}</span>
      </div>

      <!-- ====== 展开/收起按钮（始终显示） ====== -->
      <el-button link type="primary" @click="toggleExpand" size="small" style="margin-top: 6px;">
        {{ isExpanded ? '收起详情' : '展开详情' }}
      </el-button>

      <!-- ============================================================ -->
      <!-- ====== 展开后的完整详情 ====== -->
      <!-- ============================================================ -->
      <div v-if="isExpanded" class="detail-expanded">
        <!-- 时间线 -->
        <div class="detail-section" v-if="path.timeline?.length">
          <h4>📅 时间线</h4>
          <ul>
            <li v-for="(item, idx) in path.timeline" :key="idx">
              <strong>{{ item.year }}</strong>：{{ item.action }}
            </li>
          </ul>
        </div>

        <!-- 关键里程碑 -->
        <div class="detail-section" v-if="path.keyNodes?.length">
          <h4>🎯 关键里程碑</h4>
          <ul>
            <li v-for="(item, idx) in path.keyNodes" :key="idx">
              {{ item.node }}（{{ item.deadline }}）
            </li>
          </ul>
        </div>

        <!-- 全部技能差距（展开时显示全部） -->
        <div class="detail-section" v-if="path.skillGap?.length && mode === 'compact' && path.skillGap.length > 3">
          <h4>🛠️ 全部技能差距</h4>
          <el-tag
              v-for="(skill, idx) in path.skillGap"
              :key="idx"
              size="small"
              type="warning"
              style="margin: 2px"
          >
            {{ skill }}
          </el-tag>
        </div>

        <!-- 风险提示 -->
        <div class="detail-section" v-if="path.riskFactors?.length">
          <h4>⚠️ 风险提示</h4>
          <ul>
            <li v-for="(risk, idx) in path.riskFactors" :key="idx">{{ risk }}</li>
          </ul>
        </div>

        <!-- 推荐行动 -->
        <div class="detail-section" v-if="path.recommendedActions?.length">
          <h4>💡 推荐行动</h4>
          <ul>
            <li v-for="(action, idx) in path.recommendedActions" :key="idx">{{ action }}</li>
          </ul>
        </div>

        <!-- 完整止损建议 -->
        <div class="detail-section stop-loss-full" v-if="path.stopLossAdvice">
          <h4>🛑 完整止损建议</h4>
          <p><span class="label">触发条件：</span>{{ path.stopLossAdvice.trigger }}</p>
          <p><span class="label">建议行动：</span>{{ path.stopLossAdvice.action }}</p>
          <p><span class="label">截止时间：</span>{{ path.stopLossAdvice.deadline }}</p>
          <p><span class="label">备选路径：</span>{{ path.stopLossAdvice.alternativePath }}</p>
        </div>
      </div>

    </div>
    <div v-else class="empty-path">
      <el-empty description="未生成该路径" :image-size="40" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  path: { type: Object, default: null },
  pathTypeLabel: { type: String, required: true },
  goalType: { type: Object, required: true },
  mode: { type: String, default: 'compact' },
  defaultExpanded: { type: Boolean, default: false }
})

const isExpanded = ref(props.defaultExpanded)

const toggleExpand = () => {
  isExpanded.value = !isExpanded.value
}

// 根据模式显示技能数量
const displaySkills = computed(() => {
  if (!props.path?.skillGap) return []
  // compact 模式只显示前 3 项
  if (props.mode === 'compact') {
    return props.path.skillGap.slice(0, 3)
  }
  return props.path.skillGap
})

const getTagType = (pathType) => {
  const map = { 1: 'primary', 2: 'warning', 3: 'success' }
  return map[pathType] || 'info'
}

const getCardClass = (pathType) => {
  const map = { 1: 'card-main', 2: 'card-backup', 3: 'card-ideal' }
  return map[pathType] || ''
}
</script>

<style scoped>
.path-card {
  border-radius: 8px;
  padding: 12px;
  background: #fff;
  border-left: 4px solid #ddd;
  min-height: 180px;
  display: flex;
  flex-direction: column;
}
.card-main { border-left-color: #409eff; }
.card-backup { border-left-color: #e6a23c; }
.card-ideal { border-left-color: #67c23a; }

.path-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.path-name {
  font-weight: 600;
  font-size: 15px;
}
.path-summary {
  font-size: 13px;
  color: #606266;
  margin: 4px 0 8px;
}

.detail-item {
  font-size: 13px;
  color: #606266;
  margin-top: 4px;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.detail-item .label {
  color: #909399;
  font-weight: 500;
}
.salary-range {
  font-weight: 600;
  color: #409eff;
}
.skill-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
}
.skill-tags .el-tag {
  font-size: 11px;
  padding: 0 6px;
  height: 20px;
  line-height: 20px;
}

.stop-loss-preview {
  margin-top: 6px;
  font-size: 12px;
  color: #f56c6c;
  display: flex;
  align-items: center;
  gap: 6px;
}
.stop-loss-trigger {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ====== 展开后的详情区域 ====== */
.detail-expanded {
  margin-top: 8px;
  border-top: 1px dashed #e6e6e6;
  padding-top: 8px;
}
.detail-section {
  margin-bottom: 10px;
}
.detail-section h4 {
  margin: 0 0 4px 0;
  font-size: 13px;
  color: #333;
}
.detail-section ul {
  margin: 4px 0 0 0;
  padding-left: 18px;
}
.detail-section ul li {
  line-height: 1.6;
  font-size: 13px;
  color: #555;
}
.detail-section p {
  margin: 2px 0;
  font-size: 13px;
  color: #555;
}
.detail-section .label {
  color: #909399;
}

.stop-loss-full {
  background: #fef9e7;
  border-left: 3px solid #e6a23c;
  padding: 8px 12px;
  border-radius: 4px;
}

.empty-path {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
}
</style>