package com.odysseygen.service;

import com.odysseygen.dto.request.TrackPathRequest;
import com.odysseygen.dto.response.UserPathTrackingResponse;

public interface UserPathTrackingService {

    /**
     * 选定路径（开始跟踪）
     */
    UserPathTrackingResponse selectPath(Long userId, Long planId, Integer pathType);

    /**
     * 更新跟踪状态
     */
    UserPathTrackingResponse updateStatus(Long userId, Integer status);

    /**
     * 获取当前跟踪状态
     */
    UserPathTrackingResponse getCurrentTracking(Long userId);

    /**
     * 放弃当前路径
     */
    void abandonPath(Long userId);
}
