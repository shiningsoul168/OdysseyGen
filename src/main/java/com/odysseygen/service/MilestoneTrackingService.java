package com.odysseygen.service;

import com.odysseygen.dto.request.UpdateMilestoneRequest;
import com.odysseygen.dto.response.MilestoneProgressResponse;

public interface MilestoneTrackingService {

    /**
     * 初始化里程碑（当用户选定路径时自动调用）
     */
    void initMilestones(Long userId, Long trackingId, Long planId, Integer pathType);

    /**
     * 更新里程碑状态
     */
    MilestoneProgressResponse updateMilestoneStatus(Long userId, UpdateMilestoneRequest request);

    /**
     * 获取当前路径的里程碑进度
     */
    MilestoneProgressResponse getMilestoneProgress(Long userId);
}
