import request from './request'

// 选定路径（开始跟踪）
export const selectPathApi = (data) => request.post('/tracking/select', data)

// 获取当前跟踪状态
export const getCurrentTrackingApi = () => request.get('/tracking/current')

// 更新跟踪状态（2-已完成 / 3-已放弃）
export const updateTrackingStatusApi = (status) =>
    request.put(`/tracking/status?status=${status}`)

// 放弃当前路径
export const abandonPathApi = () => request.delete('/tracking/abandon')