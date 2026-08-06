package com.odysseygen.controller;

import com.odysseygen.common.Result;
import com.odysseygen.dto.request.TrackPathRequest;
import com.odysseygen.dto.response.UserPathTrackingResponse;
import com.odysseygen.service.UserPathTrackingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tracking")
@RequiredArgsConstructor
@Slf4j
public class UserPathTrackingController {

    private final UserPathTrackingService trackingService;

    /**
     * 选定路径（开始跟踪）
     */
    @PostMapping("/select")
    public Result<UserPathTrackingResponse> selectPath(
            @Valid @RequestBody TrackPathRequest request,
            @RequestAttribute Long userId) {
        UserPathTrackingResponse response = trackingService.selectPath(
                userId, request.getPlanId(), request.getPathType());
        return Result.success("已选定路径", response);
    }

    /**
     * 获取当前跟踪状态
     */
    @GetMapping("/current")
    public Result<UserPathTrackingResponse> getCurrentTracking(@RequestAttribute Long userId) {
        UserPathTrackingResponse response = trackingService.getCurrentTracking(userId);
        if (response == null) {
            return Result.success("暂无进行中的路径", null);
        }
        return Result.success(response);
    }

    /**
     * 更新跟踪状态（完成/放弃）
     */
    @PutMapping("/status")
    public Result<UserPathTrackingResponse> updateStatus(
            @RequestParam Integer status,
            @RequestAttribute Long userId) {
        UserPathTrackingResponse response = trackingService.updateStatus(userId, status);
        String message = status == 2 ? "路径已完成" : "已放弃路径";
        return Result.success(message, response);
    }

    /**
     * 放弃当前路径
     */
    @DeleteMapping("/abandon")
    public Result<?> abandonPath(@RequestAttribute Long userId) {
        trackingService.abandonPath(userId);
        return Result.success("已放弃当前路径", null);
    }
}
