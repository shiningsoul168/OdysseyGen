import axios from 'axios'

const request = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
    timeout: 60000
})

request.interceptors.request.use(config => {
    const token = localStorage.getItem('token')
    if (token) {
        config.headers.Authorization = `Bearer ${token}`
    }
    // ✅ 移除自动生成 Idempotent-Key 的逻辑，由组件层传入
    return config
})

request.interceptors.response.use(
    response => {
        const res = response.data
        if (res.code !== 200) {
            return Promise.reject(new Error(res.message || '请求失败'))
        }
        return res
    },
    error => {
        if (error.response?.status === 401) {
            localStorage.removeItem('token')
            localStorage.removeItem('userId')
            // 已在登录/注册页时不再跳转，避免重定向循环
            const path = window.location.pathname
            if (path !== '/login' && path !== '/register') {
                window.location.href = '/login'
            }
        }
        return Promise.reject(error)
    }
)

export default request