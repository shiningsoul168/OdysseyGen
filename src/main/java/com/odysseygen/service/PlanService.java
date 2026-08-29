package com.odysseygen.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.odysseygen.dto.request.ProfileRequest;
import com.odysseygen.dto.response.PathResponse;
import com.odysseygen.dto.response.TaskResponse;
import com.odysseygen.entity.PlanRecord;

import java.util.List;
import java.util.Map;

public interface PlanService {
    PathResponse generatePlan(Long userId, ProfileRequest request) throws Exception;

    List<PlanRecord> getPlanRecordsByUserId(Long userId);

    PathResponse getPlanDetail(Long planId, Long userId) throws Exception;

    IPage<PlanRecord> getPlanRecordsByUserId(Long userId, Integer page, Integer size);

    void toggleFavorite(Long planId, Long userId);

    void deletePlan(Long planId, Long userId);

    TaskResponse getTaskStatus(String taskId) throws Exception;

    /**
     * 带熔断保护的生成
     */
    PathResponse generatePlanWithBreaker(Long userId, ProfileRequest request) throws Exception;

    /**
     * 异步生成（带熔断）
     */
    String generatePlanAsyncWithBreaker(Long userId, ProfileRequest request);

    Map<Integer, List<PathResponse.PathItem>> compareThreeGoals(Long userId);
}
