package com.odysseygen.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.odysseygen.annotation.Idempotent;
import com.odysseygen.common.BusinessException;
import com.odysseygen.common.Result;
import com.odysseygen.dto.request.GenerateRequest;
import com.odysseygen.dto.response.PathResponse;
import com.odysseygen.dto.response.TaskResponse;
import com.odysseygen.entity.PlanRecord;
import com.odysseygen.service.PlanService;
import com.odysseygen.service.RateLimiterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/plan")
@RequiredArgsConstructor
@Slf4j
public class PlanController {

    private final PlanService planService;
    private final RateLimiterService rateLimiterService;  // ✅ 注入限流器

    /**
     * 生成规划（保存画像 + AI 生成三条路径）
     */
    @PostMapping("/generate")
    public Result<PathResponse> generate(@Valid @RequestBody GenerateRequest request,
                                         @RequestAttribute Long userId) {
        try {
            PathResponse response = planService.generatePlan(userId, request.getProfile());
            return Result.success("生成成功", response);
        } catch (BusinessException e) {
            // 业务异常保留错误码（如锁竞争、AI 生成失败等），避免被兜底 catch 吞成 500
            log.warn("生成规划业务异常: code={}, message={}", e.getCode(), e.getMessage());
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("生成规划失败", e);
            return Result.error("生成失败：" + (e.getMessage() != null ? e.getMessage() : "未知错误"));
        }
    }

    /**
     * 获取某次规划的路径详情
     */
    @GetMapping("/{planId}")
    public Result<PathResponse> getPlanDetail(@PathVariable Long planId,
                                              @RequestAttribute Long userId) {
        try {
            PathResponse response = planService.getPlanDetail(planId, userId);
            return Result.success(response);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("获取规划详情失败", e);
            return Result.error("加载失败：" + (e.getMessage() != null ? e.getMessage() : "未知错误"));
        }
    }

    /**
     * 获取用户所有规划记录（历史列表）
     */
    @GetMapping("/history")
    public Result<IPage<PlanRecord>> getHistory(
            @RequestAttribute Long userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        IPage<PlanRecord> records = planService.getPlanRecordsByUserId(userId, page, size);
        return Result.success(records);
    }

    /**
     * 收藏/取消收藏
     */
    @PutMapping("/{planId}/favorite")
    public Result<?> toggleFavorite(@PathVariable Long planId,
                                    @RequestAttribute Long userId) {
        planService.toggleFavorite(planId, userId);
        return Result.success("操作成功", null);
    }

    /**
     * 删除规划（逻辑删除）
     */
    @DeleteMapping("/{planId}")
    public Result<?> deletePlan(@PathVariable Long planId,
                                @RequestAttribute Long userId) {
        planService.deletePlan(planId, userId);
        return Result.success("删除成功", null);
    }

    /**
     * 异步生成规划（带缓存策略 + 幂等 + 限流）
     */
    @PostMapping("/generate-async")
    @Idempotent(ttl = 300)
    public Result<String> generateAsync(@Valid @RequestBody GenerateRequest request,
                                        @RequestAttribute Long userId) {
        if (!rateLimiterService.tryAcquire(userId)) {
            return Result.error(429, "请求过于频繁，请稍后再试（每分钟最多3次）");
        }

        String taskId = planService.generatePlanAsyncWithBreaker(userId, request.getProfile());
        return Result.success("任务已提交", taskId);
    }

    /**
     * 查询任务状态（前端轮询调用）
     */
    @GetMapping("/task/{taskId}")
    public Result<TaskResponse> getTaskStatus(@PathVariable String taskId) {
        try {
            TaskResponse task = planService.getTaskStatus(taskId);
            return Result.success(task);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("查询任务状态失败", e);
            return Result.error("系统繁忙");
        }
    }

    @GetMapping("/compare")
    public Result<Map<Integer, List<PathResponse.PathItem>>> compareThreeGoals(
            @RequestAttribute Long userId) {
        Map<Integer, List<PathResponse.PathItem>> result =
                planService.compareThreeGoals(userId);
        return Result.success(result);
    }


}