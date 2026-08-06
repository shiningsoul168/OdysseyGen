<template>
  <header class="app-header">
    <div class="header-left">
      <span class="logo" @click="goHome">🧭 OdysseyGen</span>
      <span class="subtitle">职业规划多路径生成</span>
    </div>
    <nav class="header-nav">
      <el-button link @click="goHome">首页</el-button>
      <el-button link @click="goMyPath">我的路径</el-button>
      <el-button link @click="goCompare">路径对比</el-button>
      <el-button link @click="goHistory">历史记录</el-button>

      <!-- ✅ 用户头像下拉菜单 -->
      <el-dropdown trigger="click" @command="handleCommand">
        <div class="user-avatar">
          <el-avatar :size="36" :src="userAvatar">
            {{ userInitial }}
          </el-avatar>
          <span class="username">{{ username }}</span>
          <el-icon><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">
              <el-icon><User /></el-icon>
              个人信息
            </el-dropdown-item>
            <el-dropdown-item divided command="logout">
              <el-icon><SwitchButton /></el-icon>
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </nav>

    <!-- 个人信息弹窗 -->
    <el-dialog v-model="showProfileDialog" title="个人信息" width="500px">
      <el-form :model="userForm" label-width="100px">
        <el-form-item label="用户名">
          <el-input v-model="userForm.username" disabled />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="userForm.email" disabled />
        </el-form-item>
        <el-form-item label="专业">
          <el-input v-model="userForm.major" placeholder="请输入专业" />
        </el-form-item>
        <el-form-item label="GPA">
          <el-input-number v-model="userForm.gpa" :min="0" :max="4" :precision="2" />
        </el-form-item>
        <el-form-item label="学校层次">
          <el-select v-model="userForm.schoolLevel" placeholder="请选择">
            <el-option label="985 / 211" :value="1" />
            <el-option label="双一流" :value="2" />
            <el-option label="普通本科" :value="3" />
            <el-option label="专科" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="英语水平">
          <el-select v-model="userForm.englishLevel" placeholder="请选择">
            <el-option label="CET-4" :value="1" />
            <el-option label="CET-6" :value="2" />
            <el-option label="雅思/托福" :value="3" />
            <el-option label="无" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="毕业年份">
          <el-input-number v-model="userForm.graduationYear" :min="2025" :max="2030" />
        </el-form-item>
        <el-form-item label="性格标签">
          <el-select v-model="userForm.personalityTags" multiple filterable allow-create placeholder="选择或输入">
            <el-option label="逻辑强" value="逻辑强" />
            <el-option label="外向" value="外向" />
            <el-option label="内向" value="内向" />
            <el-option label="喜欢钻研" value="喜欢钻研" />
            <el-option label="抗压能力强" value="抗压能力强" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showProfileDialog = false">取消</el-button>
        <el-button type="primary" @click="saveUserInfo">保存</el-button>
      </template>
    </el-dialog>
  </header>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowDown, User, SwitchButton } from '@element-plus/icons-vue'
import { useUserStore } from '../../stores/userStore'
import { getUserInfoApi, updateUserInfoApi } from '../../api/user.js'

const router = useRouter()
const userStore = useUserStore()

// ====== 用户信息 ======
const userInfo = ref({})
const showProfileDialog = ref(false)

const username = computed(() => userInfo.value?.username || '用户')
const userAvatar = computed(() => userInfo.value?.avatar || '')
const userInitial = computed(() => {
  const name = userInfo.value?.username || 'U'
  return name.charAt(0).toUpperCase()
})

// 个人信息表单
const userForm = ref({
  username: '',
  email: '',
  major: '',
  gpa: null,
  schoolLevel: null,
  englishLevel: null,
  graduationYear: null,
  personalityTags: []
})

// ====== 加载用户信息 ======
const loadUserInfo = async () => {
  try {
    const res = await getUserInfoApi()
    userInfo.value = res.data || {}
    // 填充表单
    userForm.value = {
      username: userInfo.value.username || '',
      email: userInfo.value.email || '',
      major: userInfo.value.major || '',
      gpa: userInfo.value.gpa || null,
      schoolLevel: userInfo.value.schoolLevel || null,
      englishLevel: userInfo.value.englishLevel || null,
      graduationYear: userInfo.value.graduationYear || new Date().getFullYear() + 1,
      personalityTags: userInfo.value.personalityTags ? JSON.parse(userInfo.value.personalityTags) : []
    }
  } catch (e) {
    console.error('加载用户信息失败', e)
  }
}

// ====== 保存个人信息 ======
const saveUserInfo = async () => {
  try {
    const payload = { ...userForm.value }
    await updateUserInfoApi(payload)
    ElMessage.success('信息更新成功')
    showProfileDialog.value = false
    await loadUserInfo() // 刷新
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  }
}

// ====== 下拉菜单命令 ======
const handleCommand = (command) => {
  if (command === 'profile') {
    showProfileDialog.value = true
  } else if (command === 'logout') {
    handleLogout()
  }
}

// ====== 退出登录 ======
const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('userId')
  userStore.logout()
  ElMessage.success('已退出')
  router.push('/login')
}

// ====== 导航 ======
const goHome = () => router.push('/')
const goMyPath = () => router.push('/my-path')
const goCompare = () => router.push('/compare')
const goHistory = () => router.push('/history')

// ====== 生命周期 ======
onMounted(() => {
  loadUserInfo()
})
</script>

<style scoped>
.app-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 30px;
  background: #fff;
  border-bottom: 1px solid #e6e6e6;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}
.header-left {
  display: flex;
  align-items: center;
  cursor: pointer;
}
.logo {
  font-size: 22px;
  font-weight: 700;
  color: #409eff;
}
.subtitle {
  font-size: 14px;
  color: #909399;
  margin-left: 12px;
}
.header-nav {
  display: flex;
  align-items: center;
  gap: 8px;
}
.header-nav .el-button {
  font-size: 15px;
}

.user-avatar {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 12px 4px 4px;
  border-radius: 20px;
  transition: background 0.2s;
}
.user-avatar:hover {
  background: #f0f2f5;
}
.user-avatar .username {
  font-size: 14px;
  color: #333;
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-avatar .el-icon {
  font-size: 14px;
  color: #909399;
}
</style>