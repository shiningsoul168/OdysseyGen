import request from './request'

// 获取里程碑进度
export const getMilestoneProgressApi = () => request.get('/milestone/progress')

// 更新里程碑状态
export const updateMilestoneStatusApi = (data) => request.put('/milestone/status', data)