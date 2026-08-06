import request from './request'

export const registerApi = (data) => request.post('/user/register', data)
export const loginApi = (data) => request.post('/user/login', data)
export const getUserInfoApi = () => request.get('/user/info')
export const updateUserInfoApi = (data) => request.put('/user/info', data)
export const checkProfileCompleteApi = () => request.get('/user/profile/complete')
export const verifyTokenApi = () => request.get('/user/verify')