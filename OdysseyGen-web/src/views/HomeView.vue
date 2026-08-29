<template>
  <div class="home-container">
    <el-card class="welcome-card">
      <h1>🧭 我的职业规划</h1>
      <p>填写你的个人画像，系统将为你生成三条不同的职业发展路径</p>
    </el-card>

    <el-card class="form-card">
      <template #header>
        <div class="form-header">
          <span>📋 个人画像填写</span>
          <el-tag type="info" size="small">标注 * 为必填</el-tag>
        </div>
      </template>

      <el-form
          ref="profileFormRef"
          :model="profileForm"
          :rules="getProfileRules()"
          label-width="120px"
          label-position="left"
      >
        <!-- ====== 目标类型切换 ====== -->
        <el-form-item label="目标类型" prop="goalType">
          <el-radio-group v-model="profileForm.goalType" size="large" @change="onGoalTypeChange">
            <el-radio-button :value="1">💼 就业</el-radio-button>
            <el-radio-button :value="2">📚 考研</el-radio-button>
            <el-radio-button :value="3">🏛️ 考公</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <!-- ====== 生成模式切换 ====== -->
        <el-form-item label="生成模式" prop="generateMode">
          <el-radio-group v-model="generateMode" size="large">
            <el-radio-button value="quick">
              ⚡ 快速模式 <span style="font-size:12px;color:#909399;">（仅需基础信息）</span>
            </el-radio-button>
            <el-radio-button value="full">
              🎯 完整模式 <span style="font-size:12px;color:#909399;">（精准画像，结果更准）</span>
            </el-radio-button>
          </el-radio-group>
          <div style="margin-top:6px;font-size:13px;color:#909399;">
            {{ generateMode === 'quick' ? '📌 快速模式只填专业、GPA等基础信息即可生成，适合快速预览' : '📌 完整模式需填写目标专属字段，生成的路径更贴合你的实际情况' }}
          </div>
        </el-form-item>

        <el-divider />

        <!-- ====== 通用信息 ====== -->
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="专业名称" prop="major">
              <el-input v-model="profileForm.major" placeholder="如：软件工程" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="GPA" prop="gpa">
              <el-input-number
                  v-model="profileForm.gpa"
                  :min="0"
                  :max="4"
                  :precision="2"
                  :step="0.1"
                  placeholder="0.00"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="学校层次" prop="schoolLevel">
              <el-select v-model="profileForm.schoolLevel" placeholder="请选择">
                <el-option label="985 / 211" :value="1" />
                <el-option label="双一流" :value="2" />
                <el-option label="普通本科" :value="3" />
                <el-option label="专科" :value="4" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="英语水平" prop="englishLevel">
              <el-select v-model="profileForm.englishLevel" placeholder="请选择">
                <el-option label="CET-4" :value="1" />
                <el-option label="CET-6" :value="2" />
                <el-option label="雅思 / 托福" :value="3" />
                <el-option label="无" :value="4" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="是否党员" prop="isPartyMember">
              <el-switch v-model="profileForm.isPartyMember" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="毕业年份" prop="graduationYear">
              <el-input-number
                  v-model="profileForm.graduationYear"
                  :min="2025"
                  :max="2030"
                  placeholder="2027"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider />

        <!-- ====== 目标专属字段（仅在完整模式下显示） ====== -->
        <!-- 就业方向 -->
        <template v-if="profileForm.goalType === 1 && generateMode === 'full'">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="目标岗位" prop="goalData.targetJob">
                <el-input v-model="profileForm.goalData.targetJob" placeholder="如：Java开发工程师" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="目标行业" prop="goalData.targetIndustry">
                <el-input v-model="profileForm.goalData.targetIndustry" placeholder="如：互联网 / 金融" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="期望城市" prop="goalData.preferredCity">
                <el-input v-model="profileForm.goalData.preferredCity" placeholder="如：杭州 / 上海" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="期望薪资（K）" prop="goalData.expectedSalaryMin">
                <el-input-number
                    v-model="profileForm.goalData.expectedSalaryMin"
                    :min="0"
                    placeholder="最低"
                />
                <span style="margin: 0 8px">~</span>
                <el-input-number
                    v-model="profileForm.goalData.expectedSalaryMax"
                    :min="0"
                    placeholder="最高"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="技术栈" prop="goalData.skillStack">
            <el-select
                v-model="profileForm.goalData.skillStack"
                multiple
                filterable
                allow-create
                placeholder="输入技术栈后按回车添加"
                style="width: 100%"
            >
              <el-option label="Java" value="Java" />
              <el-option label="Spring Boot" value="Spring Boot" />
              <el-option label="MySQL" value="MySQL" />
              <el-option label="Redis" value="Redis" />
              <el-option label="Vue" value="Vue" />
              <el-option label="Python" value="Python" />
              <el-option label="Go" value="Go" />
            </el-select>
          </el-form-item>
          <el-form-item label="实习经历">
            <el-input-number
                v-model="profileForm.goalData.internshipCount"
                :min="0"
                :max="5"
                placeholder="0"
            />
            <span style="margin-left: 8px; color: #909399">段</span>
          </el-form-item>
        </template>

        <!-- 考研方向 -->
        <template v-if="profileForm.goalType === 2 && generateMode === 'full'">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="目标专业" prop="goalData.targetMajor">
                <el-input v-model="profileForm.goalData.targetMajor" placeholder="如：计算机科学与技术" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="目标院校" prop="goalData.targetSchool">
                <el-input v-model="profileForm.goalData.targetSchool" placeholder="如：浙江大学" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="院校层次" prop="goalData.schoolLevelTarget">
                <el-select v-model="profileForm.goalData.schoolLevelTarget" placeholder="请选择">
                  <el-option label="985" value="985" />
                  <el-option label="211" value="211" />
                  <el-option label="双一流" value="双一流" />
                  <el-option label="普通本科" value="普通本科" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="备考阶段" prop="goalData.currentPrepStage">
                <el-select v-model="profileForm.goalData.currentPrepStage" placeholder="请选择">
                  <el-option label="基础阶段" value="基础阶段" />
                  <el-option label="强化阶段" value="强化阶段" />
                  <el-option label="冲刺阶段" value="冲刺阶段" />
                  <el-option label="未开始" value="未开始" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="考试科目" prop="goalData.examSubjects">
            <el-select
                v-model="profileForm.goalData.examSubjects"
                multiple
                filterable
                allow-create
                placeholder="输入科目后按回车添加"
                style="width: 100%"
            >
              <el-option label="数学一" value="数学一" />
              <el-option label="数学二" value="数学二" />
              <el-option label="英语一" value="英语一" />
              <el-option label="英语二" value="英语二" />
              <el-option label="408计算机统考" value="408计算机统考" />
              <el-option label="政治" value="政治" />
            </el-select>
          </el-form-item>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="有无科研经历" prop="goalData.hasResearchExperience">
                <el-switch v-model="profileForm.goalData.hasResearchExperience" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="论文发表数" prop="goalData.publishedPapers">
                <el-input-number v-model="profileForm.goalData.publishedPapers" :min="0" :max="10" />
              </el-form-item>
            </el-col>
          </el-row>
        </template>

        <!-- 考公方向 -->
        <template v-if="profileForm.goalType === 3 && generateMode === 'full'">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="考试类型" prop="goalData.targetLevel">
                <el-select v-model="profileForm.goalData.targetLevel" placeholder="请选择">
                  <el-option label="国考" value="国考" />
                  <el-option label="省考" value="省考" />
                  <el-option label="选调生" value="选调生" />
                  <el-option label="事业单位" value="事业单位" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="目标部门" prop="goalData.targetDepartment">
                <el-input v-model="profileForm.goalData.targetDepartment" placeholder="如：税务局" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="期望城市" prop="goalData.preferredCity">
                <el-input v-model="profileForm.goalData.preferredCity" placeholder="如：家乡 / 省会" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="是否应届" prop="goalData.isGraduateStudent">
                <el-switch v-model="profileForm.goalData.isGraduateStudent" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="学生干部经历" prop="goalData.hasServedAsStudentCadre">
            <el-switch v-model="profileForm.goalData.hasServedAsStudentCadre" />
            <span style="margin-left: 12px; color: #909399; font-size: 13px">选调生/部分岗位有加分</span>
          </el-form-item>
        </template>

        <el-divider />

        <!-- ====== 性格标签 ====== -->
        <el-form-item label="性格标签" prop="personalityTags">
          <el-select
              v-model="profileForm.personalityTags"
              multiple
              filterable
              allow-create
              placeholder="选择或输入性格标签"
              style="width: 100%"
          >
            <el-option label="逻辑强" value="逻辑强" />
            <el-option label="外向" value="外向" />
            <el-option label="内向" value="内向" />
            <el-option label="喜欢团队协作" value="喜欢团队协作" />
            <el-option label="喜欢独立工作" value="喜欢独立工作" />
            <el-option label="抗压能力强" value="抗压能力强" />
            <el-option label="喜欢稳定" value="喜欢稳定" />
            <el-option label="喜欢钻研" value="喜欢钻研" />
            <el-option label="学术导向" value="学术导向" />
            <el-option label="责任心强" value="责任心强" />
          </el-select>
        </el-form-item>

        <!-- ====== 提交按钮 ====== -->
        <el-form-item>
          <div style="display: flex; gap: 12px; width: 100%;">
            <el-button
                type="primary"
                size="large"
                :loading="submitting"
                @click="handleGenerate"
                style="flex: 1"
            >
              🚀 生成三条职业路径
            </el-button>
            <el-button
                size="large"
                @click="resetForm"
                :disabled="generating"
                style="flex: 0 0 auto;"
            >
              🗑️ 重置表单
            </el-button>
          </div>
          <!-- 生成中的醒目提示 -->
          <el-alert
              v-if="generating"
              type="info"
              :closable="false"
              show-icon
              style="margin-top: 12px;"
          >
            <template #title>
              任务已提交，正在后台生成职业路径，预计 20-40 秒，完成后将跳转到结果页…
            </template>
          </el-alert>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- ====== 完善信息弹窗 ====== -->
    <el-dialog v-model="showProfileDialog" title="完善个人信息" width="500px">
      <el-form :model="profileForm" label-width="100px">
        <el-form-item label="专业名称">
          <el-input v-model="profileForm.major" placeholder="如：软件工程" />
        </el-form-item>
        <el-form-item label="GPA">
          <el-input-number v-model="profileForm.gpa" :min="0" :max="4" :precision="2" />
        </el-form-item>
        <el-form-item label="学校层次">
          <el-select v-model="profileForm.schoolLevel" placeholder="请选择">
            <el-option label="985 / 211" :value="1" />
            <el-option label="双一流" :value="2" />
            <el-option label="普通本科" :value="3" />
            <el-option label="专科" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="英语水平">
          <el-select v-model="profileForm.englishLevel" placeholder="请选择">
            <el-option label="CET-4" :value="1" />
            <el-option label="CET-6" :value="2" />
            <el-option label="雅思/托福" :value="3" />
            <el-option label="无" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="毕业年份">
          <el-input-number v-model="profileForm.graduationYear" :min="2025" :max="2030" />
        </el-form-item>
        <el-form-item label="性格标签">
          <el-select v-model="profileForm.personalityTags" multiple filterable allow-create placeholder="选择或输入">
            <el-option label="逻辑强" value="逻辑强" />
            <el-option label="外向" value="外向" />
            <el-option label="内向" value="内向" />
            <el-option label="喜欢钻研" value="喜欢钻研" />
            <el-option label="抗压能力强" value="抗压能力强" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showProfileDialog = false">稍后完善</el-button>
        <el-button type="primary" @click="submitProfile(profileForm)">保存并继续</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { generatePlanAsyncApi, getTaskStatusApi } from '../api/plan.js'
import { checkProfileCompleteApi, getUserInfoApi, updateUserInfoApi } from '../api/user.js'
import { usePlanStore } from '../stores/planStore.js'
import { generateIdempotentKey } from '../utils/idempotent.js'

const router = useRouter()
const planStore = usePlanStore()
const profileFormRef = ref(null)
const generating = ref(false)
const submitting = ref(false)
const showProfileDialog = ref(false)
const generateMode = ref('quick')

// ====== 幂等 Key ======
const idempotentKey = ref('')

const refreshIdempotentKey = () => {
  idempotentKey.value = generateIdempotentKey()
}

const resetIdempotentKey = () => {
  setTimeout(() => {
    refreshIdempotentKey()
  }, 500)
}

// ====== 草稿功能（按目标类型分别存储） ======
const getDraftKey = () => {
  const userId = localStorage.getItem('userId') || 'default'
  const goalType = profileForm.goalType || 1
  return `odyssey_draft_${userId}_type${goalType}`
}

const loadDraft = () => {
  try {
    const key = getDraftKey()
    const draft = localStorage.getItem(key)
    if (draft) {
      const parsed = JSON.parse(draft)
      Object.keys(parsed).forEach(key => {
        if (key in profileForm) {
          if (key === 'goalData') {
            Object.keys(parsed.goalData || {}).forEach(subKey => {
              if (subKey in profileForm.goalData) {
                profileForm.goalData[subKey] = parsed.goalData[subKey]
              }
            })
          } else {
            profileForm[key] = parsed[key]
          }
        }
      })
    }
  } catch (e) {
    console.warn('加载草稿失败:', e)
  }
}

const saveDraft = () => {
  try {
    const key = getDraftKey()
    const draft = JSON.parse(JSON.stringify(profileForm))
    localStorage.setItem(key, JSON.stringify(draft))
  } catch (e) {
    console.warn('保存草稿失败:', e)
  }
}

const clearDraft = () => {
  try {
    const key = getDraftKey()
    localStorage.removeItem(key)
  } catch (e) {
    console.warn('清除草稿失败:', e)
  }
}

// ====== 切换目标类型 ======
const onGoalTypeChange = () => {
  saveDraft()
  loadDraft()
}

// ====== 表单数据 ======
const profileForm = reactive({
  goalType: 1,
  major: '',
  gpa: null,
  schoolLevel: null,
  englishLevel: null,
  isPartyMember: false,
  graduationYear: new Date().getFullYear() + 1,
  goalData: {
    targetJob: '',
    targetIndustry: '',
    preferredCity: '',
    expectedSalaryMin: null,
    expectedSalaryMax: null,
    skillStack: [],
    internshipCount: 0,
    targetMajor: '',
    targetSchool: '',
    schoolLevelTarget: '',
    currentPrepStage: '',
    examSubjects: [],
    hasResearchExperience: false,
    publishedPapers: 0,
    targetLevel: '',
    targetDepartment: '',
    isGraduateStudent: true,
    hasServedAsStudentCadre: false
  },
  personalityTags: []
})

// ====== 自动保存（防抖） ======
let saveTimer = null
watch(profileForm, () => {
  clearTimeout(saveTimer)
  saveTimer = setTimeout(() => {
    saveDraft()
  }, 500)
}, { deep: true })

// ====== 动态校验规则（函数方式） ======
const getProfileRules = () => {
  const baseRules = {
    goalType: [{ required: true, message: '请选择目标类型', trigger: 'change' }],
    major: [{ required: true, message: '请输入专业名称', trigger: 'blur' }]
  }

  // ✅ 快速模式：只校验基础字段
  if (generateMode.value === 'quick') {
    return baseRules
  }

  // 完整模式：校验目标专属字段
  if (profileForm.goalType === 1) {
    baseRules['goalData.targetJob'] = [{ required: true, message: '请输入目标岗位', trigger: 'blur' }]
    baseRules['goalData.targetIndustry'] = [{ required: true, message: '请输入目标行业', trigger: 'blur' }]
  } else if (profileForm.goalType === 2) {
    baseRules['goalData.targetMajor'] = [{ required: true, message: '请输入目标专业', trigger: 'blur' }]
    baseRules['goalData.targetSchool'] = [{ required: true, message: '请输入目标院校', trigger: 'blur' }]
  } else if (profileForm.goalType === 3) {
    baseRules['goalData.targetLevel'] = [{ required: true, message: '请选择考试类型', trigger: 'change' }]
  }

  return baseRules
}

// ====== 加载用户信息 ======
const loadUserInfo = async () => {
  console.log('loadUserInfo 被执行了')
  try {
    const res = await getUserInfoApi()
    const info = res.data
    if (info) {
      profileForm.major = info.major || ''
      profileForm.gpa = info.gpa || null
      profileForm.schoolLevel = info.schoolLevel || null
      profileForm.englishLevel = info.englishLevel || null
      profileForm.isPartyMember = info.isPartyMember || false
      profileForm.graduationYear = info.graduationYear || new Date().getFullYear() + 1
      if (info.personalityTags) {
        try {
          profileForm.personalityTags = JSON.parse(info.personalityTags)
        } catch (e) {
          console.warn('性格标签解析失败，使用默认值')
          profileForm.personalityTags = []
        }
      }
    }
  } catch (e) {
    console.error('加载用户信息失败', e)
  }
}

// ====== 完善信息弹窗提交 ======
const submitProfile = async (formData) => {
  try {
    await updateUserInfoApi(formData)
    showProfileDialog.value = false
    ElMessage.success('信息完善成功')
    await loadUserInfo()
    loadDraft()
    saveDraft()
  } catch (error) {
    ElMessage.error(error.message || '保存失败')
  }
}

// ====== 生成规划 ======
const handleGenerate = async () => {
  const valid = await profileFormRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    saveDraft()

    // ✅ 根据模式构造请求体
    const requestData = { profile: { ...profileForm } }

    // ✅ 快速模式：清空 goalData，只保留基础信息
    if (generateMode.value === 'quick') {
      requestData.profile.goalData = {}
    }

    const res = await generatePlanAsyncApi(
        requestData,
        { 'Idempotent-Key': idempotentKey.value }
    )
    submitting.value = false
    generating.value = true
    const taskId = res.data
    ElMessage.info('任务已提交，正在生成路径...')

    // 轮询任务状态直到 SUCCESS / FAILED / 超时。
    // 用 for + await sleep 而不是递归 setTimeout：递归写法下 await 只等第一次轮询，
    // 会导致 finally 提前把 generating 置回 false，提示条显示一下就消失。
    const maxAttempts = 30
    for (let attempts = 1; attempts <= maxAttempts; attempts++) {
      try {
        const statusRes = await getTaskStatusApi(taskId)
        const task = statusRes.data

        if (task.status === 'SUCCESS') {
          planStore.setCurrentPlan(task.result)
          ElMessage.success('生成成功！')
          resetIdempotentKey()
          const planId = task.result?.planId
          router.push(planId ? { path: '/result', query: { planId } } : '/result')
          return
        } else if (task.status === 'FAILED') {
          ElMessage.error(task.error || '生成失败，请重试')
          resetIdempotentKey()
          return
        }
      } catch (e) {
        ElMessage.error('获取任务状态失败，请稍后重试')
        resetIdempotentKey()
        return
      }
      // PENDING：等待 2 秒后再查
      await new Promise(resolve => setTimeout(resolve, 2000))
    }
    // 轮询超时
    ElMessage.error('生成超时，请稍后重试')
    resetIdempotentKey()

  } catch (error) {
    ElMessage.error(error.message || '提交失败，请重试')
    resetIdempotentKey()
  } finally {
    submitting.value = false
    generating.value = false
  }
}

// ====== 重置表单 ======
const resetForm = () => {
  clearDraft()
  profileForm.goalType = 1
  profileForm.major = ''
  profileForm.gpa = null
  profileForm.schoolLevel = null
  profileForm.englishLevel = null
  profileForm.isPartyMember = false
  profileForm.graduationYear = new Date().getFullYear() + 1
  profileForm.goalData = {
    targetJob: '',
    targetIndustry: '',
    preferredCity: '',
    expectedSalaryMin: null,
    expectedSalaryMax: null,
    skillStack: [],
    internshipCount: 0,
    targetMajor: '',
    targetSchool: '',
    schoolLevelTarget: '',
    currentPrepStage: '',
    examSubjects: [],
    hasResearchExperience: false,
    publishedPapers: 0,
    targetLevel: '',
    targetDepartment: '',
    isGraduateStudent: true,
    hasServedAsStudentCadre: false
  }
  profileForm.personalityTags = []
  refreshIdempotentKey()
  ElMessage.success('已重置表单')
}

// ====== 生命周期 ======
onMounted(async () => {
  refreshIdempotentKey()

  const token = localStorage.getItem('token')
  if (token) {
    try {
      // ✅ 先加载草稿
      loadDraft()
      // ✅ 再用用户信息覆盖（草稿里有的字段会被覆盖）
      await loadUserInfo()

      const res = await checkProfileCompleteApi()
      if (!res.data) {
        showProfileDialog.value = true
      }
    } catch (e) {
      console.error('加载失败:', e)
    }
  }
})
</script>

<style scoped>
.home-container {
  max-width: 900px;
  margin: 0 auto;
}

.welcome-card {
  margin-bottom: 24px;
  text-align: center;
  background: linear-gradient(135deg, #e8f4fd, #f0f9ff);
}

.welcome-card h1 {
  margin: 0;
  font-size: 26px;
  color: #2c3e50;
}

.welcome-card p {
  margin: 8px 0 0;
  color: #606266;
}

.form-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 16px;
  font-weight: 600;
}

.el-divider {
  margin: 16px 0;
}
</style>