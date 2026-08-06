package com.odysseygen.controller;

import com.odysseygen.common.Result;
import com.odysseygen.dto.request.UpdateMilestoneRequest;
import com.odysseygen.dto.response.MilestoneProgressResponse;
import com.odysseygen.service.MilestoneTrackingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/milestone")
@RequiredArgsConstructor
@Slf4j
public class MilestoneController {

    private final MilestoneTrackingService milestoneService;

    /**
     * 获取当前路径的里程碑进度
     */
    @GetMapping("/progress")
    public Result<MilestoneProgressResponse> getProgress(@RequestAttribute Long userId) {
        MilestoneProgressResponse response = milestoneService.getMilestoneProgress(userId);
        if (response == null) {
            return Result.success("暂无进行中的路径", null);
        }
        return Result.success(response);
    }

    /**
     * 更新里程碑状态
     */
    @PutMapping("/status")
    public Result<MilestoneProgressResponse> updateStatus(
            @Valid @RequestBody UpdateMilestoneRequest request,
            @RequestAttribute Long userId) {
        MilestoneProgressResponse response = milestoneService.updateMilestoneStatus(userId, request);
        return Result.success("更新成功", response);
    }
}
