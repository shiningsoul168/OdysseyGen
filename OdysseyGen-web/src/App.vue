<template>
  <div id="app">
    <!-- 只在需要登录的页面显示导航栏（登录页/注册页不显示） -->
    <AppHeader v-if="$route.meta.requiresAuth !== false" />
    <main class="app-main">
      <router-view />
    </main>
    <AppFooter v-if="$route.meta.requiresAuth !== false" />
  </div>
</template>

<script setup>
import AppHeader from './components/common/AppHeader.vue'
import AppFooter from './components/common/AppFooter.vue'
</script>

<style scoped>
#app {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.app-main {
  flex: 1;
  padding: 20px;
  background-color: #f5f7fa;
}
</style>
