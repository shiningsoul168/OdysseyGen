import request from './request'

export const generatePlanApi = (data) => request.post('/plan/generate', data)
export const getPlanDetailApi = (planId) => request.get(`/plan/${planId}`)
export const getHistoryApi = (params) => request.get('/plan/history', { params })
export const toggleFavoriteApi = (planId) => request.put(`/plan/${planId}/favorite`)
export const deletePlanApi = (planId) => request.delete(`/plan/${planId}`)
export const generatePlanAsyncApi = (data, extraHeaders = {}) =>
    request.post('/plan/generate-async', data, { headers: extraHeaders })
export const getTaskStatusApi = (taskId) => request.get(`/plan/task/${taskId}`)
export const compareThreeGoalsApi = () => request.get('/plan/compare')
