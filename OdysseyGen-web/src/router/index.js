import { createRouter, createWebHistory } from 'vue-router'

const routes = [
    {
        path: '/',
        name: 'Home',
        component: () => import('../views/HomeView.vue'),
        meta: { requiresAuth: true }
    },
    {
        path: '/login',
        name: 'Login',
        component: () => import('../views/LoginView.vue'),
        meta: { requiresAuth: false }
    },
    {
        path: '/register',
        name: 'Register',
        component: () => import('../views/RegisterView.vue'),
        meta: { requiresAuth: false }
    },
    {
        path: '/result',
        name: 'Result',
        component: () => import('../views/ResultView.vue'),
        meta: { requiresAuth: true }
    },
    {
        path: '/history',
        name: 'History',
        component: () => import('../views/HistoryView.vue'),
        meta: { requiresAuth: true }
    },
    {
        path: '/compare',
        name: 'Compare',
        component: () => import('../views/CompareView.vue'),
        meta: { requiresAuth: true }
    },
    {
        path: '/my-path',
        name: 'MyPath',
        component: () => import('../views/MyPathView.vue'),
        meta: { requiresAuth: true }
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

router.beforeEach((to, from, next) => {
    const token = localStorage.getItem('token')
    if (to.meta.requiresAuth && !token) {
        next('/login')
    } else {
        next()
    }
})

export default router